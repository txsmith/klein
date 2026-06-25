package klein.check

import klein.*
import klein.check.Type.*
import klein.types.TypeError

data class TypeCheckResult(
    val program: Program,
    val type: Type,
    val errors: List<TypeError>,
) {
    val hasErrors: Boolean get() = errors.isNotEmpty()
}

data class ExpectedType(val type: Type, val source: ExpectedTypeSource)

sealed class ExpectedTypeSource {
    data class Param(val fn: String?, val name: String?, val span: SourceSpan) : ExpectedTypeSource()
    data class Return(val fn: String?, val span: SourceSpan) : ExpectedTypeSource()
    data class Binding(val name: String, val span: SourceSpan) : ExpectedTypeSource()
    data class Ascription(val span: SourceSpan) : ExpectedTypeSource()
    data class RecordField(val name: String, val span: SourceSpan) : ExpectedTypeSource()
}

class Checker {
    private val errors = mutableListOf<TypeError>()

    fun getErrors(): List<TypeError> = errors

    /** Type-check a whole program; its type is the last top-level expression's type. */
    fun synthProgram(
        program: Program,
        env: TypeEnv = TypeEnv.empty(),
    ): Type = synthBlockStmts(program.stmts, env)

    // fun check(
    //     prog: Program,
    //     env: TypeEnv = TypeEnv.empty(),
    // ): TypeCheckResult

    fun synth(
        expr: Expr,
        env: TypeEnv,
    ): Type =
        when (expr) {
            is IntLiteral -> TNum
            is DoubleLiteral -> TNum
            is StringLiteral -> TStr
            is BoolLiteral -> TBool
            is NullLiteral -> TNull
            is Ident -> synthIdent(expr, env)
            is BinaryOp -> synthBinaryOp(expr, env)
            is UnaryOp -> synthUnaryOp(expr, env)
            is Lambda -> synthLambda(expr, env)
            is Apply -> synthApply(expr, env)
            is RecordLiteral -> synthRecordLiteral(expr, env)
            is FieldAccess -> synthFieldAccess(expr, env)
            is SafeFieldAccess -> synthSafeFieldAccess(expr, env)
            is IfThenElse -> synthIfThenElse(expr, env)
            is ImplicitParam -> synthImplicitParam(expr, env)
            is Ascription -> synthAscription(expr, env)
            is Block -> synthBlockStmts(expr.stmts, env.child())
        }

    /**
     * Check [expr] against [expected] (bidirectional check mode).
     *
     * Introduction forms (lambda, record literal, if) get rules that push [expected] *inward*.
     * Every other (elimination) form falls back to **subsumption**: synthesize its type and verify
     * that type is a subtype of [expected].
     */
    fun check(
        expr: Expr,
        expected: Type,
        env: TypeEnv,
        expectedSource: List<ExpectedType> = emptyList(),
    ) {
        when (expr) {
            is Lambda -> checkLambda(expr, expected, env)
            is RecordLiteral -> checkRecordLiteral(expr, expected, env, expectedSource)
            is IfThenElse ->
                if (expr.elseBranch != null) {
                    // check mode distributes: both branches must satisfy the same expected type.
                    check(expr.condition, TBool, env)
                    check(expr.thenBranch, expected, env, expectedSource)
                    check(expr.elseBranch, expected, env, expectedSource)
                } else {
                    // No else → the result is Optional(then); synth builds it (checking the condition
                    // and yielding the Optional), then subsume verifies it against expected.
                    subsume(expr, expected, env)
                }
            else -> subsume(expr, expected, env)
        }
    }

    /** Subsumption / mode-switch: synthesize [expr], then require its type to be a subtype of [expected]. */
    private fun subsume(
        expr: Expr,
        expected: Type,
        env: TypeEnv,
    ) {
        val actual = synth(expr, env)
        if (!isSubtype(actual, expected, env)) {
            recordError(TypeError.TypeMismatch(actual.toLegacy(), expected.toLegacy(), expr.span))
        }
    }

    /** A lambda in check position takes its parameter types from the expected function type. */
    private fun checkLambda(
        expr: Lambda,
        expected: Type,
        env: TypeEnv,
    ) {
        if (expected !is TFun) {
            recordError(TypeError.Misc("Expected '${expected.toLegacy()}', but found a function", expr.span))
            return
        }
        if (expr.params.size != expected.params.size) {
            recordError(
                TypeError.Misc(
                    "Expected a function taking ${expected.params.size} parameter(s), " +
                        "but this lambda takes ${expr.params.size}",
                    expr.span,
                ),
            )
            return
        }
        val bodyEnv = env.child()
        expr.params.zip(expected.params).forEach { (param, expectedParamType) ->
            val paramType =
                if (param.typeAnnotation != null) {
                    val annotated = resolveType(param.typeAnnotation)
                    if (!isSubtype(expectedParamType, annotated, env)) {
                        recordError(
                            TypeError.TypeMismatch(expectedParamType.toLegacy(), annotated.toLegacy(), param.span),
                        )
                    }
                    annotated
                } else {
                    expectedParamType
                }
            bodyEnv.bind(param.name, paramType)
        }
        check(expr.body, expected.result, bodyEnv)
    }

    /** A record literal in check position has each field checked against the expected field type. */
    private fun checkRecordLiteral(
        expr: RecordLiteral,
        expected: Type,
        env: TypeEnv,
        expectedSource: List<ExpectedType>,
    ) {
        if (expected !is TRecord) {
            recordError(TypeError.Misc("Expected '${expected.toLegacy()}', but found a record", expr.span))
            return
        }
        val present = expr.fields.mapTo(mutableSetOf()) { it.name }
        for (field in expr.fields) {
            val fieldType = expected.fields[field.name]
            if (fieldType != null) {
                check(
                    field.value,
                    fieldType,
                    env,
                    expectedSource + ExpectedType(fieldType, ExpectedTypeSource.RecordField(field.name, field.value.span)),
                )
            } else {
                synth(field.value, env)
            }
        }
        for (name in expected.fields.keys) {
            if (name !in present) {
                recordError(TypeError.MissingField(name, expected.toLegacy(), expr.span))
            }
        }
    }

    fun isSubtype(
        lhs: Type,
        rhs: Type,
        env: TypeEnv,
    ): Boolean {
        if (lhs == rhs) return true
        return when {
            rhs is TTop -> true
            lhs is TBottom -> true

            lhs is TFun && rhs is TFun ->
                lhs.params.size == rhs.params.size &&
                    rhs.params.indices.all { isSubtype(rhs.params[it], lhs.params[it], env) } &&
                    isSubtype(lhs.result, rhs.result, env)

            lhs is TRecord && rhs is TRecord ->
                rhs.fields.all { (name, want) ->
                    val have = lhs.fields[name]
                    have != null && isSubtype(have, want, env)
                }

            rhs is TOptional ->
                lhs is TNull || // Null <: T?
                    (lhs is TOptional && isSubtype(lhs.type, rhs.type, env)) || // S? <: T?
                    isSubtype(lhs, rhs.type, env) // S <: T?  (when S <: T)

            else -> false
        }
    }

    private fun resolveType(typeExpr: TypeExpr): Type =
        when (typeExpr) {
            is TypeName ->
                when (typeExpr.name) {
                    "Num" -> TNum
                    "String" -> TStr
                    "Bool" -> TBool
                    "Unit" -> TUnit
                    "Any" -> TTop
                    "Nothing" -> TBottom
                    else -> recordError(TypeError.UnboundVariable(typeExpr.name, typeExpr.span))
                }
            is FunctionTypeExpr ->
                TFun(typeExpr.paramTypes.map { resolveType(it) }, resolveType(typeExpr.returnType))
            is RecordTypeExpr ->
                TRecord(typeExpr.fields.associate { (name, t) -> name to resolveType(t) })
            is TupleTypeExpr ->
                if (typeExpr.elements.isEmpty()) {
                    TUnit
                } else {
                    TRecord(typeExpr.elements.mapIndexed { i, t -> "_${i + 1}" to resolveType(t) }.toMap())
                }
            is TypeVar ->
                recordError(TypeError.Misc("Type variables (generics) aren't supported yet", typeExpr.span))
            is AppliedTypeExpr ->
                recordError(TypeError.Misc("Generic type application isn't supported yet", typeExpr.span))
            is UnionTypeExpr ->
                recordError(
                    TypeError.Misc("Anonymous union types ('A | B') aren't supported — define a nominal type", typeExpr.span),
                )
            is IntersectionTypeExpr ->
                recordError(TypeError.Misc("Anonymous intersection types ('A & B') aren't supported yet", typeExpr.span))
        }

    private fun synthIdent(
        expr: Ident,
        env: TypeEnv,
    ): Type =
        env.lookup(expr.name) ?: run {
            recordError(TypeError.UnboundVariable(expr.name, expr.span))
        }

    private fun synthRecordLiteral(
        expr: RecordLiteral,
        env: TypeEnv,
    ): Type {
        val fields = mutableMapOf<String, Type>()
        for (field in expr.fields) {
            if (field.name in fields) {
                errors.add(TypeError.DuplicateField(field.name, expr.span))
            }
            if (field.typeAnnotation != null) {
                val fieldType = resolveType(field.typeAnnotation)
                check(field.value, fieldType, env, listOf(ExpectedType(fieldType, ExpectedTypeSource.RecordField(field.name, field.value.span))))
                fields[field.name] = fieldType
            } else {
                fields[field.name] = synth(field.value, env)
            }
        }
        return TRecord(fields)
    }

    private fun synthFieldAccess(
        expr: FieldAccess,
        env: TypeEnv,
    ): Type {
        val target = synth(expr.target, env)
        // TODO: nominal types exposing fields (records-as-interfaces)
        return projectFieldType(target, expr.field, expr.span)
    }

    private fun synthSafeFieldAccess(
        expr: SafeFieldAccess,
        env: TypeEnv,
    ): Type {
        val target = synth(expr.target, env)
        val innerType = if (target is TOptional) target.type else target
        return projectFieldType(innerType, expr.field, expr.span)
    }

    private fun projectFieldType(
        rec: Type,
        field: String,
        span: SourceSpan,
    ): Type =
        when (rec) {
            is TRecord ->
                rec.fields[field] ?: run {
                    recordError(TypeError.MissingField(field, rec.toLegacy(), span))
                }
            else -> {
                recordError(TypeError.NotARecord(rec.toLegacy(), field, span))
            }
        }

    private fun synthBlockStmts(
        stmts: List<Stmt>,
        env: TypeEnv,
    ): Type {
        var last: Type = TUnit
        for (stmt in stmts) {
            when (stmt) {
                is Val -> synthVal(stmt, env)
                is Expr -> last = synth(stmt, env)
                is FunDef -> synthFunDef(stmt, env)
                is TypeDef -> {} // no value-level effect
            }
        }
        return last
    }

    private fun synthVal(
        stmt: Val,
        env: TypeEnv,
    ) {
        val type = if (stmt.typeAnnotation != null) {
           val t = resolveType(stmt.typeAnnotation)
           check(stmt.value, t, env, listOf(ExpectedType(t, ExpectedTypeSource.Binding(stmt.name, stmt.span))))
           t
        } else {
            synth(stmt.value, env)
        }
        env.bind(stmt.name, type)
    }

    private fun synthBinaryOp(
        expr: BinaryOp,
        env: TypeEnv,
    ): Type =
        when (expr.op) {
            Operator.Add, Operator.Sub, Operator.Mul, Operator.Div, Operator.Mod -> {
                check(expr.left, TNum, env)
                check(expr.right, TNum, env)
                TNum
            }
            Operator.Lt, Operator.LtEq, Operator.Gt, Operator.GtEq -> {
                check(expr.left, TNum, env)
                check(expr.right, TNum, env)
                TBool
            }
            Operator.And, Operator.Or -> {
                check(expr.left, TBool, env)
                check(expr.right, TBool, env)
                TBool
            }
            Operator.Eq, Operator.NotEq -> {
                check(expr.right, synth(expr.left, env), env)
                TBool
            }
        }

    private fun synthUnaryOp(
        expr: UnaryOp,
        env: TypeEnv,
    ): Type =
        when (expr.op) {
            UnaryOperator.Neg -> {
                check(expr.operand, TNum, env)
                TNum
            }
            UnaryOperator.Not -> {
                check(expr.operand, TBool, env)
                TBool
            }
        }

    private fun synthLambda(
        expr: Lambda,
        env: TypeEnv,
    ): Type {
        val bodyEnv = env.child()
        val paramTypes =
            expr.params.map { param ->
                val type =
                    if (param.typeAnnotation != null) {
                        resolveType(param.typeAnnotation)
                    } else {
                        recordError(TypeError.MissingParamAnnotation(param.name, param.span))
                    }
                bodyEnv.bind(param.name, type)
                type
            }
        val bodyType = synth(expr.body, bodyEnv)
        return TFun(paramTypes, bodyType, expr.params.map { it.name })
    }

    private fun synthFunDef(
        funDef: FunDef,
        env: TypeEnv,
    ) {
        val paramTypes =
            funDef.params.map { param ->
                if (param.typeAnnotation != null) {
                    resolveType(param.typeAnnotation)
                } else {
                    recordError(TypeError.MissingParamAnnotation(param.name, param.span))
                }
            }
        val bodyEnv = env.child()
        funDef.params.zip(paramTypes).forEach { (param, type) -> bodyEnv.bind(param.name, type) }
        val paramNames = funDef.params.map { it.name }

        if (funDef.returnType != null) {
            val declared = resolveType(funDef.returnType)
            env.bind(funDef.name, TFun(paramTypes, declared, paramNames)) // bind first → self-recursion resolves
            check(
                funDef.body,
                declared,
                bodyEnv,
                listOf(ExpectedType(declared, ExpectedTypeSource.Return(funDef.name, funDef.span))),
            )
        } else {
            val inferred = synth(funDef.body, bodyEnv)
            env.bind(funDef.name, TFun(paramTypes, inferred, paramNames))
        }
    }

    private fun synthApply(
        expr: Apply,
        env: TypeEnv,
    ): Type {
        val funType = synth(expr.callee, env)

        val calleeName =
            when (expr.callee) {
                is Ident -> expr.callee.name
                is FieldAccess -> expr.callee.field
                else -> null
            }

        return when {
            funType !is TFun ->
                recordError(TypeError.NotAFunction(funType.toLegacy(), expr.span))
            funType.params.size != expr.args.size ->
                recordError(TypeError.CallArityMismatch(funType.params.size, expr.args.size, expr.span))
            else -> {
                for (i in funType.params.indices) {
                    val paramType = funType.params[i]
                    val paramName = funType.paramNames.getOrNull(i)
                    val argExpr = expr.args[i]
                    check(
                        argExpr,
                        paramType,
                        env,
                        listOf(ExpectedType(paramType, ExpectedTypeSource.Param(calleeName, paramName, argExpr.span))),
                    )
                }
                funType.result
            }
        }
    }

    private fun synthIfThenElse(
        expr: IfThenElse,
        env: TypeEnv,
    ): Type {
        check(expr.condition, TBool, env)
        val thenBranchType = synth(expr.thenBranch, env)

        if (expr.elseBranch  == null) {
            return TOptional(thenBranchType)
        } else {
            val elseBranchType = synth(expr.elseBranch, env)
            if (isSubtype(thenBranchType, elseBranchType, env)) {
                return elseBranchType
            } else if (isSubtype(elseBranchType, thenBranchType, env)) {
                return thenBranchType
            } else {
                return recordError(TypeError.Misc("Branches of if-else need to be of the same type", expr.span))
            }
        }
    }

    private fun synthImplicitParam(
        expr: ImplicitParam,
        env: TypeEnv,
    ): Type =
        when (val ctx = env.implicitParam) {
            is ImplicitParamContext.Available -> ctx.type
            is ImplicitParamContext.BlockedByNamedFunction -> {
                recordError(TypeError.ImplicitParamInNamedFunction(expr.span))
            }
            is ImplicitParamContext.BlockedByExplicitParams -> {
                recordError(TypeError.ImplicitParamWithExplicitParams(ctx.params, expr.span))
            }
            is ImplicitParamContext.None -> {
                recordError(TypeError.ImplicitParamOutsideLambda(expr.span))
            }
        }

    private fun synthAscription(
        expr: Ascription,
        env: TypeEnv,
    ): Type {
        val type = resolveType(expr.type)
        check(expr.expr, type, env)
        return type
    }

    private fun recordError(err: TypeError): Type {
        errors.add(err)
        return TBottom
    }
}
