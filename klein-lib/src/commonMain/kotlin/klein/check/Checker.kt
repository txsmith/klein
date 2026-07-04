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

data class ExpectedType(
    val type: Type,
    val source: ExpectedTypeSource,
)

sealed class ExpectedTypeSource {
    data class Param(
        val fn: String?,
        val name: String?,
        val span: SourceSpan,
    ) : ExpectedTypeSource()

    data class Return(
        val fn: String?,
        val span: SourceSpan,
    ) : ExpectedTypeSource()

    data class Binding(
        val name: String,
        val span: SourceSpan,
    ) : ExpectedTypeSource()

    data class Ascription(
        val span: SourceSpan,
    ) : ExpectedTypeSource()

    data class RecordField(
        val name: String,
        val span: SourceSpan,
    ) : ExpectedTypeSource()
}

class Checker {
    private val errors = mutableListOf<TypeError>()
    private var skolemCounter = 0
    private val subtyping = Subtyping()
    private val constraints = ConstraintGenerator(subtyping)
    private val preprocessor = TypeDefPreprocessor(errors, ::freshSkolem, ::resolveType)

    fun getErrors(): List<TypeError> = errors

    private fun freshSkolem(name: String): TSkolem = TSkolem(name, skolemCounter++)

    fun synthProgram(
        program: Program,
        env: TypeEnv = TypeEnv.empty(),
    ): Type = synthBlockStmts(program.stmts, env)

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
     * Apply gets the expected type pushed in to aid type-argument inference.
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
            is IfThenElse -> checkIfThenElse(expr, expected, env, expectedSource)
            is Apply -> checkApply(expr, expected, env)
            else -> synthAndCheckSubtype(expr, expected, env)
        }
    }

    private fun synthBlockStmts(
        stmts: List<Stmt>,
        env: TypeEnv,
    ): Type {
        preprocessor.process(stmts.filterIsInstance<TypeDef>(), env)
        var last: Type = TUnit
        for (stmt in stmts) {
            when (stmt) {
                is Val -> synthVal(stmt, env)
                is Expr -> last = synth(stmt, env)
                is FunDef -> synthFunDef(stmt, env)
                is TypeDef -> {}
            }
        }
        return last
    }

    private fun synthVal(
        stmt: Val,
        env: TypeEnv,
    ) {
        if (stmt.typeAnnotation != null) {
            val sigEnv = env.child()
            introduceTypeVars(listOf(stmt.typeAnnotation), sigEnv)
            val t = resolveType(stmt.typeAnnotation, sigEnv)
            check(stmt.value, t, sigEnv, listOf(ExpectedType(t, ExpectedTypeSource.Binding(stmt.name, stmt.span))))
            env.bind(stmt.name, quantify(sigEnv.localTypeVars(), t))
        } else {
            env.bind(stmt.name, synth(stmt.value, env))
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
                        resolveType(param.typeAnnotation, bodyEnv)
                    } else {
                        recordError(TypeError.MissingParamAnnotation(param.name, param.span))
                    }
                bodyEnv.bind(param.name, type)
                type
            }
        val bodyType = synth(expr.body, bodyEnv)
        return TFun(paramTypes, bodyType, expr.params.map { it.name })
    }

    private fun checkLambda(
        expr: Lambda,
        expected: Type,
        env: TypeEnv,
    ) {
        if (expected !is TFun) {
            // No inward rule for a lambda against a non-function — fall back to subsumption.
            synthAndCheckSubtype(expr, expected, env)
            return
        }
        if (expr.params.size != expected.params.size) {
            recordError(TypeError.CallArityMismatch(expected.params.size, expr.params.size, expr.span))
            return
        }
        val bodyEnv = env.child()
        expr.params.zip(expected.params).forEach { (param, expectedParamType) ->
            val paramType =
                if (param.typeAnnotation != null) {
                    val annotated = resolveType(param.typeAnnotation, bodyEnv)
                    if (!subtyping.isSubtype(expectedParamType, annotated, env)) {
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

    private fun synthFunDef(
        funDef: FunDef,
        env: TypeEnv,
    ) {
        val sigEnv = env.child()
        introduceTypeVars(funDef.params.mapNotNull { it.typeAnnotation } + listOfNotNull(funDef.returnType), sigEnv)
        val paramTypes =
            funDef.params.map { param ->
                if (param.typeAnnotation != null) {
                    resolveType(param.typeAnnotation, sigEnv)
                } else {
                    recordError(TypeError.MissingParamAnnotation(param.name, param.span))
                }
            }
        val paramNames = funDef.params.map { it.name }

        val bodyEnv = sigEnv.child()
        funDef.params.zip(paramTypes).forEach { (param, type) -> bodyEnv.bind(param.name, type) }

        if (funDef.returnType != null) {
            val declared = resolveType(funDef.returnType, sigEnv)
            env.bind(funDef.name, quantify(sigEnv.localTypeVars(), TFun(paramTypes, declared, paramNames)))
            check(
                funDef.body,
                declared,
                bodyEnv,
                listOf(ExpectedType(declared, ExpectedTypeSource.Return(funDef.name, funDef.span))),
            )
        } else {
            val inferred = synth(funDef.body, bodyEnv)
            env.bind(funDef.name, quantify(sigEnv.localTypeVars(), TFun(paramTypes, inferred, paramNames)))
        }
    }

    private fun synthApply(
        expr: Apply,
        env: TypeEnv,
    ): Type = inferApply(expr, null, env) // null = synth mode: no result demand, minimize R

    private fun checkApply(
        expr: Apply,
        expected: Type,
        env: TypeEnv,
    ) {
        inferApply(expr, expected, env)
    }

    /**
     * Type-checks a function application `f(a₁ … aₙ)` and returns the result type R.
     *
     * The callee's type is `∀T̄. (P₁ … Pₙ) → R`. Its type parameters `T̄` are what we solve for — empty
     * for a monomorphic callee. Checking the call is one subtyping obligation, between the callee's
     * function type and the shape the call demands:
     *
     *     (P₁ … Pₙ) → R   <:   (A₁ … Aₙ) → D
     *
     * where each `Aᵢ` is the type of the argument we supply and `D` is the demanded result type.
     *
     * The solver then finds concrete types for `T̄` that make `R` minimal, i.e. as informative as the
     * call allows. That instantiated `R` is the result.
     *
     * TODO(doc): write up the mainline procedure and the rest. Cover: check vs synth mode and how
     *  `D = Top` nullifies the `R <: D` constraint; the per-parameter "check ground parameters"
     *  optimization; and the bare-lambda limitation.
     */
    private fun inferApply(
        expr: Apply,
        expected: Type?,
        env: TypeEnv,
    ): Type {
        val callee = synth(expr.callee, env)
        val scheme = callee as? TForall ?: TForall(emptySet(), callee)
        val body = scheme.body
        return when {
            body is TBottom -> { // S-App-Bot and C-App-Bot
                expr.args.forEach { synth(it, env) }
                TBottom
            }
            body !is TFun ->
                recordError(TypeError.NotAFunction(body.toLegacy(), expr.span))
            body.params.size != expr.args.size ->
                recordError(TypeError.CallArityMismatch(body.params.size, expr.args.size, expr.span))
            else -> {
                val unknowns = scheme.params
                val calleeName =
                    when (val callSite = expr.callee) {
                        is Ident -> callSite.name
                        is FieldAccess -> callSite.field
                        else -> null
                    }
                val argTypes =
                    body.params.mapIndexed { i, param ->
                        if (isGround(param, unknowns)) {
                            check(
                                expr.args[i],
                                param,
                                env,
                                listOf(
                                    ExpectedType(
                                        param,
                                        ExpectedTypeSource.Param(calleeName, body.paramNames.getOrNull(i), expr.args[i].span),
                                    ),
                                ),
                            )
                            param
                        } else {
                            synth(expr.args[i], env)
                        }
                    }
                // The type to minimize while solving constraints. This will always be the return type of the function.
                val target = if (expected == null) body.result else TTop
                val (instantiated, errors) = constraints.solveQuantified(scheme, TFun(argTypes, expected ?: TTop), target, env)
                errors.forEach { recordError(TypeError.TypeMismatch(it.lower.toLegacy(), it.upper.toLegacy(), expr.span)) }
                if (errors.isEmpty()) (instantiated as TFun).result else TBottom
            }
        }
    }

    private fun synthIfThenElse(
        expr: IfThenElse,
        env: TypeEnv,
    ): Type {
        check(expr.condition, TBool, env)
        val thenBranchType = synth(expr.thenBranch, env)

        if (expr.elseBranch == null) {
            return TOptional(thenBranchType)
        } else {
            val elseBranchType = synth(expr.elseBranch, env)
            // TODO: this should be the least upper bound of the branches (subtyping.lub), not just
            //  "accept one when it's a subtype of the other". As written, branches with a real common
            //  supertype but no direct subtype relation (e.g. { x, y } and { x, z }, LUB { x }) are
            //  rejected. The LUB must also handle polymorphic branch values: a ∀-typed branch trips
            //  the isSubtype/lub `!is TForall` guard today (crash). How to LUB two ∀s is open — α-equal
            //  → that scheme, otherwise reject? See IfThenElseLubTest for the red targets.
            if (subtyping.isSubtype(thenBranchType, elseBranchType, env)) {
                return elseBranchType
            } else if (subtyping.isSubtype(elseBranchType, thenBranchType, env)) {
                return thenBranchType
            } else {
                return recordError(TypeError.Misc("Branches of if-else need to be of the same type", expr.span))
            }
        }
    }

    private fun checkIfThenElse(
        expr: IfThenElse,
        expected: Type,
        env: TypeEnv,
        expectedSource: List<ExpectedType>,
    ) {
        if (expr.elseBranch != null) {
            check(expr.condition, TBool, env)
            check(expr.thenBranch, expected, env, expectedSource)
            check(expr.elseBranch, expected, env, expectedSource)
        } else {
            // No else means the result is Optional(then); synth builds it (checking the condition
            // and yielding the Optional), then subsume verifies it against expected.
            synthAndCheckSubtype(expr, expected, env)
        }
    }

    private fun synthIdent(
        expr: Ident,
        env: TypeEnv,
    ): Type = env.lookup(expr.name) ?: recordError(TypeError.UnboundVariable(expr.name, expr.span))

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
                val fieldType = resolveType(field.typeAnnotation, env)
                check(
                    field.value,
                    fieldType,
                    env,
                    listOf(ExpectedType(fieldType, ExpectedTypeSource.RecordField(field.name, field.value.span))),
                )
                fields[field.name] = fieldType
            } else {
                fields[field.name] = synth(field.value, env)
            }
        }
        return TRecord(fields)
    }

    private fun checkRecordLiteral(
        expr: RecordLiteral,
        expected: Type,
        env: TypeEnv,
        expectedSource: List<ExpectedType>,
    ) {
        if (expected !is TRecord) {
            synthAndCheckSubtype(expr, expected, env)
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
        val type = resolveType(expr.type, env)
        check(expr.expr, type, env)
        return type
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

    private fun synthAndCheckSubtype(
        expr: Expr,
        expected: Type,
        env: TypeEnv,
    ) {
        val synthesized = synth(expr, env)
        if (synthesized is TForall) {
            // A polymorphic value meets the demand iff some instantiation fits — the solver decides.
            constraints
                .solveQuantified(synthesized, expected, expected, env)
                .errors
                .forEach { recordError(TypeError.TypeMismatch(it.lower.toLegacy(), it.upper.toLegacy(), expr.span)) }
        } else if (!subtyping.isSubtype(synthesized, expected, env)) {
            recordError(TypeError.TypeMismatch(synthesized.toLegacy(), expected.toLegacy(), expr.span))
        }
    }

    private fun resolveType(
        typeExpr: TypeExpr,
        env: TypeEnv,
    ): Type =
        when (typeExpr) {
            is TypeName ->
                when (typeExpr.name) {
                    "Num" -> TNum
                    "String" -> TStr
                    "Bool" -> TBool
                    "Unit" -> TUnit
                    "Any" -> TTop
                    "Nothing" -> TBottom
                    else ->
                        if (env.lookupTypeDef(typeExpr.name) != null) {
                            TRef(typeExpr.name, emptyList())
                        } else {
                            recordError(TypeError.UnboundVariable(typeExpr.name, typeExpr.span))
                        }
                }
            is FunctionTypeExpr ->
                TFun(typeExpr.paramTypes.map { resolveType(it, env) }, resolveType(typeExpr.returnType, env))
            is RecordTypeExpr ->
                TRecord(typeExpr.fields.associate { (name, t) -> name to resolveType(t, env) })
            is TupleTypeExpr ->
                if (typeExpr.elements.isEmpty()) {
                    TUnit
                } else {
                    TRecord(typeExpr.elements.mapIndexed { i, t -> "_${i + 1}" to resolveType(t, env) }.toMap())
                }
            is TypeVar ->
                env.lookupTypeVar(typeExpr.name)
                    ?: recordError(TypeError.UnboundVariable(typeExpr.name, typeExpr.span))
            is AppliedTypeExpr -> {
                val info = env.lookupTypeDef(typeExpr.name)
                val args = typeExpr.args.map { resolveType(it, env) }
                when {
                    info == null -> recordError(TypeError.UnboundVariable(typeExpr.name, typeExpr.span))
                    info.typeParams.size != args.size -> {
                        recordError(TypeError.TypeArityMismatch(typeExpr.name, info.typeParams.size, args.size, typeExpr.span))
                        TRef(typeExpr.name, args)
                    }
                    else -> TRef(typeExpr.name, args)
                }
            }
            is UnionTypeExpr ->
                recordError(
                    TypeError.Misc("Anonymous union types ('A | B') aren't supported — define a nominal type", typeExpr.span),
                )
            is IntersectionTypeExpr ->
                recordError(TypeError.Misc("Anonymous intersection types ('A & B') aren't supported yet", typeExpr.span))
        }

    /** Introduce each not-yet-in-scope type variable in [annotations] as a fresh skolem at [sigEnv] —
     *  the binder owns where its `'T`s are quantified; [resolveType] only ever *references* them. */
    private fun introduceTypeVars(
        annotations: List<TypeExpr>,
        sigEnv: TypeEnv,
    ) {
        annotations.forEach { annotation ->
            collectTypeVarNames(annotation).forEach { name ->
                if (sigEnv.lookupTypeVar(name) == null) sigEnv.bindTypeVar(name, freshSkolem(name))
            }
        }
    }

    /** `∀params. body`, or just `body` when there's nothing to quantify. */
    private fun quantify(
        params: Set<TSkolem>,
        body: Type,
    ): Type = if (params.isEmpty()) body else TForall(params, body)

    /** Whether [type] mentions none of the [unknowns] — concrete enough to check an argument against. */
    private fun isGround(
        type: Type,
        unknowns: Set<TSkolem>,
    ): Boolean =
        when (type) {
            is TSkolem -> type !in unknowns
            is TFun -> type.params.all { isGround(it, unknowns) } && isGround(type.result, unknowns)
            is TRecord -> type.fields.values.all { isGround(it, unknowns) }
            is TOptional -> isGround(type.type, unknowns)
            is TRef -> type.typeArgs.all { isGround(it, unknowns) }
            is TForall -> isGround(type.body, unknowns - type.params)
            TNum, TStr, TBool, TUnit, TNull, TTop, TBottom -> true
        }

    private fun recordError(err: TypeError): Type {
        errors.add(err)
        return TBottom
    }
}
