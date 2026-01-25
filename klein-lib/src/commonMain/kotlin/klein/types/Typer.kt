package klein.types

import klein.*
import klein.types.SimpleType.*

data class ProgramResult(
    val type: SimpleType,
    val env: TypeEnv,
    val errors: List<TypeError>,
    val exprTypes: Map<SourceSpan, SimpleType> = emptyMap(),
) {
    val hasErrors: Boolean
        get() = errors.isNotEmpty()
}

class Typer {
    private val errors = mutableListOf<TypeError>()
    private val subtyping = Subtyping()

    fun getErrors(): List<TypeError> = errors + subtyping.getErrors()

    fun infer(
        program: Program,
        env: TypeEnv = TypeEnv.empty(),
    ): ProgramResult {
        val (type, exprTypes) = inferTopLevelStmts(program.stmts, env)
        return ProgramResult(type, env, getErrors(), exprTypes)
    }

    private fun inferTopLevelStmts(
        stmts: List<Stmt>,
        env: TypeEnv,
    ): Pair<SimpleType, Map<SourceSpan, SimpleType>> {
        processTypeDefs(stmts.filterIsInstance<TypeDef>(), env)
        processFunDefs(stmts.filterIsInstance<FunDef>(), env)
        return inferBlockStmts(stmts, env)
    }

    private fun processFunDefs(
        funDefs: List<FunDef>,
        env: TypeEnv,
    ) {
        if (funDefs.isEmpty()) return

        val scopeEnv = env.enterBindingScope()
        val funDefBindings = mutableMapOf<String, Pair<FunDef, SimpleType>>()

        // Pass 1: Bind all with TVars (enables mutual recursion)
        for (funDef in funDefs) {
            if (env.contains(funDef.name)) {
                errors.add(TypeError.DuplicateBinding(funDef.name, funDef.span))
            } else {
                val typeVar = scopeEnv.freshVar()
                funDefBindings[funDef.name] = funDef to typeVar
                env.bind(funDef.name, typeVar)
            }
        }

        // Pass 2: Infer types
        for ((funDef, typeVar) in funDefBindings.values) {
            val rhsEnv = env.enterBindingScope()
            rhsEnv.bind(funDef.name, typeVar)
            val type = inferFunction(funDef.params, funDef.body, funDef.span, rhsEnv, functionName = funDef.name)
            subtyping.constrain(type, typeVar, funDef.span)
        }

        // Pass 3: Generalize to polymorphic bindings
        for ((name, pair) in funDefBindings) {
            env.bindPolymorphic(name, pair.second)
        }
    }

    fun infer(
        expr: Expr,
        env: TypeEnv,
    ): SimpleType =
        when (expr) {
            is IntLiteral -> TNum
            is DoubleLiteral -> TNum
            is StringLiteral -> TString
            is BoolLiteral -> TBool
            is NullLiteral -> TNull
            is Ident -> inferIdent(expr, env)
            is BinaryOp -> inferBinaryOp(expr, env)
            is UnaryOp -> inferUnaryOp(expr, env)
            is Lambda -> inferFunction(expr.params, expr.body, expr.span, env)
            is Apply -> inferApply(expr, env)
            is RecordLiteral -> inferRecordLiteral(expr, env)
            is FieldAccess -> inferFieldAccess(expr, env)
            is SafeFieldAccess -> inferSafeFieldAccess(expr, env)
            is IfThenElse -> inferIfThenElse(expr, env)
            is ImplicitParam -> inferImplicitParam(expr, env)
            is Block -> inferBlockStmts(expr.stmts, env.child()).first
        }

    private fun inferBlockStmts(
        stmts: List<Stmt>,
        env: TypeEnv,
    ): Pair<SimpleType, Map<SourceSpan, SimpleType>> {
        var lastType: SimpleType = TUnit
        val exprTypes = mutableMapOf<SourceSpan, SimpleType>()
        for (stmt in stmts) {
            lastType =
                when (stmt) {
                    is Val -> {
                        if (env.contains(stmt.name)) {
                            errors.add(TypeError.DuplicateBinding(stmt.name, stmt.span))
                        }
                        val rhsEnv = env.enterBindingScope()
                        val type = infer(stmt.value, rhsEnv)
                        env.bindPolymorphic(stmt.name, type)
                        TUnit
                    }
                    is FunDef -> TUnit
                    is Expr -> {
                        val type = infer(stmt, env)
                        exprTypes[stmt.span] = type
                        type
                    }
                    is TypeDef -> TUnit
                }
        }
        return Pair(lastType, exprTypes)
    }

    private fun inferFunction(
        params: List<String>,
        body: Expr,
        span: SourceSpan,
        env: TypeEnv,
        functionName: String? = null,
    ): SimpleType {
        val seen = mutableSetOf<String>()
        for (name in params) {
            if (name in seen) {
                errors.add(TypeError.DuplicateParameter(name, span))
            }
            seen.add(name)
        }

        val isLambda = functionName == null
        return if (params.isEmpty() && isLambda) {
            val implicitType = env.freshVar()
            val childEnv = env.child(ImplicitParamContext.Available(implicitType))
            val bodyType = infer(body, childEnv)
            if (body.usesImplicitParam) {
                TFun(listOf(implicitType), bodyType)
            } else {
                TFun(emptyList(), bodyType)
            }
        } else {
            val blockedContext =
                if (functionName != null) {
                    ImplicitParamContext.BlockedByNamedFunction
                } else {
                    ImplicitParamContext.BlockedByExplicitParams(params)
                }
            val paramTypes = params.map { env.freshVar() }
            val childEnv = env.child(blockedContext)
            params.zip(paramTypes).forEach { (name, type) ->
                childEnv.bind(name, type)
            }
            val bodyType = infer(body, childEnv)
            TFun(paramTypes, bodyType)
        }
    }

    private fun inferApply(
        expr: Apply,
        env: TypeEnv,
    ): SimpleType {
        if (expr.callee is SafeFieldAccess) {
            return inferSafeMethodCall(expr.callee, expr.args, expr.span, env)
        }

        val calleeType = infer(expr.callee, env)
        val argTypes = expr.args.map { infer(it, env) }
        val resultType = env.freshVar()

        subtyping.constrain(
            calleeType,
            TFun(argTypes, resultType),
            expr.span,
        )

        return resultType
    }

    private fun inferSafeMethodCall(
        callee: SafeFieldAccess,
        args: List<Expr>,
        span: SourceSpan,
        env: TypeEnv,
    ): SimpleType {
        val targetType = infer(callee.target, env)
        val argTypes = args.map { infer(it, env) }
        val returnType = env.freshVar()

        val funType = TFun(argTypes, returnType)
        subtyping.constrain(targetType, TOptional(TRecord(mapOf(callee.field to funType))), span)

        return TOptional(returnType)
    }

    private fun inferIdent(
        expr: Ident,
        env: TypeEnv,
    ): SimpleType =
        env.lookupAndInstantiate(expr.name) ?: run {
            errors.add(TypeError.UnboundVariable(expr.name, expr.span))
            env.freshVar()
        }

    private fun inferImplicitParam(
        expr: ImplicitParam,
        env: TypeEnv,
    ): SimpleType =
        when (val ctx = env.implicitParam) {
            is ImplicitParamContext.Available -> ctx.type
            is ImplicitParamContext.BlockedByNamedFunction -> {
                errors.add(TypeError.ImplicitParamInNamedFunction(expr.span))
                env.freshVar()
            }
            is ImplicitParamContext.BlockedByExplicitParams -> {
                errors.add(TypeError.ImplicitParamWithExplicitParams(ctx.params, expr.span))
                env.freshVar()
            }
            is ImplicitParamContext.None -> {
                errors.add(TypeError.ImplicitParamOutsideLambda(expr.span))
                env.freshVar()
            }
        }

    private fun inferBinaryOp(
        expr: BinaryOp,
        env: TypeEnv,
    ): SimpleType {
        val leftType = infer(expr.left, env)
        val rightType = infer(expr.right, env)

        return when (expr.op) {
            Operator.Add, Operator.Sub, Operator.Mul, Operator.Div, Operator.Mod -> {
                subtyping.constrain(leftType, TNum, expr.left.span)
                subtyping.constrain(rightType, TNum, expr.right.span)
                TNum
            }
            Operator.Lt, Operator.LtEq, Operator.Gt, Operator.GtEq -> {
                subtyping.constrain(leftType, TNum, expr.left.span)
                subtyping.constrain(rightType, TNum, expr.right.span)
                TBool
            }
            Operator.Eq, Operator.NotEq -> {
                subtyping.constrain(leftType, rightType, expr.left.span)
                subtyping.constrain(rightType, leftType, expr.right.span)
                TBool
            }
            Operator.And, Operator.Or -> {
                subtyping.constrain(leftType, TBool, expr.left.span)
                subtyping.constrain(rightType, TBool, expr.right.span)
                TBool
            }
        }
    }

    private fun inferUnaryOp(
        expr: UnaryOp,
        env: TypeEnv,
    ): SimpleType {
        val operandType = infer(expr.operand, env)

        return when (expr.op) {
            UnaryOperator.Neg -> {
                subtyping.constrain(operandType, TNum, expr.operand.span)
                TNum
            }
            UnaryOperator.Not -> {
                subtyping.constrain(operandType, TBool, expr.operand.span)
                TBool
            }
        }
    }

    private fun inferRecordLiteral(
        expr: RecordLiteral,
        env: TypeEnv,
    ): SimpleType {
        val fieldTypes = mutableMapOf<String, SimpleType>()
        for ((name, value) in expr.fields) {
            if (name in fieldTypes) {
                errors.add(TypeError.DuplicateField(name, expr.span))
            }
            fieldTypes[name] = infer(value, env)
        }
        return TRecord(fieldTypes)
    }

    private fun inferFieldAccess(
        expr: FieldAccess,
        env: TypeEnv,
    ): SimpleType {
        val targetType = infer(expr.target, env)
        val fieldType = env.freshVar()
        subtyping.constrain(targetType, TRecord(mapOf(expr.field to fieldType)), expr.span)
        return fieldType
    }

    private fun inferSafeFieldAccess(
        expr: SafeFieldAccess,
        env: TypeEnv,
    ): SimpleType {
        val targetType = infer(expr.target, env)
        val fieldType = env.freshVar()
        subtyping.constrain(targetType, TOptional(TRecord(mapOf(expr.field to fieldType))), expr.span)
        return TOptional(fieldType)
    }

    private fun inferIfThenElse(
        expr: IfThenElse,
        env: TypeEnv,
    ): SimpleType {
        val condType = infer(expr.condition, env)
        subtyping.constrain(condType, TBool, expr.condition.span)

        val thenType = infer(expr.thenBranch, env)

        return if (expr.elseBranch != null) {
            val elseType = infer(expr.elseBranch, env)
            val resultType = env.freshVar()
            subtyping.constrain(thenType, resultType, expr.thenBranch.span)
            subtyping.constrain(elseType, resultType, expr.elseBranch.span)
            resultType
        } else {
            TUnit
        }
    }

    private fun processTypeDefs(
        typeDefs: List<TypeDef>,
        env: TypeEnv,
    ) {
        if (typeDefs.isEmpty()) return

        // Pass 1: Register placeholders for all types and constructors
        // This ensures all type names are known, and enables the following passes to work for (mutually) recursive types
        registerPlaceholders(typeDefs, env)

        // Pass 2: Infer variance for all type parameters
        inferVariance(typeDefs, env)

        // Pass 3: Finalize structures and bind constructors
        finalizeTypeDefs(typeDefs, env)
    }

    private fun registerPlaceholders(
        typeDefs: List<TypeDef>,
        env: TypeEnv,
    ) {
        for (typeDef in typeDefs) {
            val typeParams = typeDef.typeParams.map { TypeParamInfo(it, Variance.Bivariant) }

            // Register the sum type
            env.registerTypeDef(
                TypeDefInfo(
                    name = typeDef.name,
                    typeParams = typeParams,
                    iface = TRecord(emptyMap()),
                    span = typeDef.span,
                ),
            )

            // Register each constructor
            for (constructor in typeDef.constructors) {
                val usedTypeVars = collectTypeVarsFromFields(constructor.fields)
                val declaredTypeParams = typeDef.typeParams.toSet()

                // Check for undeclared type params
                for (typeVar in usedTypeVars) {
                    if (typeVar !in declaredTypeParams) {
                        errors.add(TypeError.UndeclaredTypeParam(typeVar, typeDef.name, constructor.span))
                    }
                }

                val ctorTypeParams = typeParams.filter { it.name in usedTypeVars }.map { it.copy() }

                env.registerConstructor(
                    ConstructorInfo(
                        name = constructor.name,
                        typeParams = ctorTypeParams.map { it.name },
                        fields = constructor.fields,
                        parentType = typeDef.name,
                        span = constructor.span,
                    ),
                )

                env.registerTypeDef(
                    TypeDefInfo(
                        name = constructor.name,
                        typeParams = ctorTypeParams,
                        iface = TRecord(emptyMap()),
                        span = constructor.span,
                    ),
                )
            }
        }
    }

    private fun collectTypeVars(typeExpr: TypeExpr): Set<String> =
        when (typeExpr) {
            is TypeVar -> setOf(typeExpr.name)
            is TypeName -> emptySet()
            is AppliedTypeExpr -> typeExpr.args.flatMap { collectTypeVars(it) }.toSet()
            is FunctionTypeExpr -> collectTypeVars(typeExpr.paramType) + collectTypeVars(typeExpr.returnType)
            is TupleTypeExpr -> typeExpr.elements.flatMap { collectTypeVars(it) }.toSet()
            is RecordTypeExpr -> typeExpr.fields.flatMap { collectTypeVars(it.second) }.toSet()
        }

    private fun collectTypeVarsFromFields(fields: List<FieldDecl>): Set<String> = fields.flatMap { collectTypeVars(it.type) }.toSet()

    private fun inferVariance(
        typeDefs: List<TypeDef>,
        env: TypeEnv,
    ) {
        // TODO: Fixed-point iteration over variance
    }

    private fun finalizeTypeDefs(
        typeDefs: List<TypeDef>,
        env: TypeEnv,
    ) {
        // TODO: Build structures, compute inferred interfaces, bind constructors
    }

    companion object {
        fun infer(
            program: Program,
            env: TypeEnv = TypeEnv.empty(),
        ): ProgramResult = Typer().infer(program, env)
    }
}
