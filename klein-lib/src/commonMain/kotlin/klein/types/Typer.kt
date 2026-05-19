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
    private lateinit var subtyping: Subtyping

    fun getErrors(): List<TypeError> = errors + subtyping.getErrors()

    fun infer(
        program: Program,
        env: TypeEnv = TypeEnv.empty(),
    ): ProgramResult {
        SimpleType.TVar.resetUidCounter()
        subtyping = Subtyping(env)
        val (type, exprTypes) = inferTopLevelStmts(program.stmts, env)
        return ProgramResult(type, env, getErrors(), exprTypes)
    }

    private fun inferTopLevelStmts(
        stmts: List<Stmt>,
        env: TypeEnv,
    ): Pair<SimpleType, Map<SourceSpan, SimpleType>> {
        processTypeDefs(stmts.filterIsInstance<TypeDef>(), env)
        bindTopLevel(stmts, env)
        return inferResultTypes(stmts, env)
    }

    /**
     * Bind every top-level value and function, processing strongly connected components in
     * dependency order so each binding is generalized before the bindings that reference it.
     *
     * Functions in a component are mutually recursive and bound together (the three-pass scheme).
     * A standalone value is inferred and bound on its own. A component that pulls a value into a
     * cycle is a use-before-initialization error.
     */
    private fun bindTopLevel(
        stmts: List<Stmt>,
        env: TypeEnv,
    ) {
        val scopeGraph = ScopeGraph.constructGraph(stmts)
        scopeGraph.duplicates.forEach { (name, span) -> errors.add(TypeError.DuplicateBinding(name, span)) }

        for (component in scopeGraph.graph.computeSCCs()) {
            val bindings = component.map { it.binding }
            when {
                bindings.all { it is FunDef } ->
                    processFunComponent(bindings.filterIsInstance<FunDef>(), env)

                bindings.size == 1 && bindings.single() is Val ->
                    bindVal(bindings.single() as Val, env)

                else ->
                    // A value reachable from itself (directly or through functions) can never be
                    // initialized; report it with the cycle that proves it.
                    component.filter { it.binding is Val }.forEach { node ->
                        val cycle = scopeGraph.graph.findCycle(node.name)
                        errors.add(TypeError.RecursiveVal(node.name, cycle, (node.binding as Val).span))
                    }
            }
        }
    }

    /** Bind one component of mutually-recursive functions, then generalize them. */
    private fun processFunComponent(
        funDefs: List<FunDef>,
        env: TypeEnv,
    ) {
        val scopeEnv = env.enterBindingScope()
        val bound = mutableListOf<Pair<FunDef, SimpleType>>()

        // Pass 1: bind every function to a fresh type variable (enables mutual recursion).
        for (funDef in funDefs) {
            val typeVar = scopeEnv.freshVar()
            env.bind(funDef.name, typeVar)
            env.registerFunDef(FunDefInfo(funDef.name, funDef.params.map { it.name }))
            bound.add(funDef to typeVar)
        }

        // Pass 2: infer each body against the shared placeholders.
        for ((funDef, typeVar) in bound) {
            val rhsEnv = env.enterBindingScope()
            rhsEnv.bind(funDef.name, typeVar)
            val type = inferFunDef(funDef, rhsEnv)
            subtyping.constrain(type, typeVar, funDef.span)
        }

        // Pass 3: generalize to polymorphic bindings.
        for ((funDef, typeVar) in bound) {
            env.bindPolymorphic(funDef.name, typeVar)
        }
    }

    /** Infer a standalone value's right-hand side and bind it polymorphically. */
    private fun bindVal(
        stmt: Val,
        env: TypeEnv,
    ) {
        val rhsEnv = env.enterBindingScope()
        val type = infer(stmt.value, rhsEnv)
        env.bindPolymorphic(stmt.name, type)
    }

    /**
     * Walk the statements in lexical order for the program's result type (the last expression)
     * and the span-to-type map. Bindings are already in scope from [bindTopLevel]; only bare
     * expressions are inferred here.
     */
    private fun inferResultTypes(
        stmts: List<Stmt>,
        env: TypeEnv,
    ): Pair<SimpleType, Map<SourceSpan, SimpleType>> {
        var lastType: SimpleType = TUnit
        val exprTypes = mutableMapOf<SourceSpan, SimpleType>()
        for (stmt in stmts) {
            lastType =
                if (stmt is Expr) {
                    val type = infer(stmt, env)
                    exprTypes[stmt.span] = type
                    type
                } else {
                    TUnit
                }
        }
        return lastType to exprTypes
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
            is Lambda -> inferLambda(expr, env)
            is Apply -> inferApply(expr, env)
            is RecordLiteral -> inferRecordLiteral(expr, env)
            is FieldAccess -> inferFieldAccess(expr, env)
            is SafeFieldAccess -> inferSafeFieldAccess(expr, env)
            is IfThenElse -> inferIfThenElse(expr, env)
            is ImplicitParam -> inferImplicitParam(expr, env)
            is Ascription -> inferAscription(expr, env)
            is Block -> inferBlockStmts(expr.stmts, env.child()).first
        }

    private fun inferBlockStmts(
        stmts: List<Stmt>,
        env: TypeEnv,
        isTopLevel: Boolean = false,
    ): Pair<SimpleType, Map<SourceSpan, SimpleType>> {
        var lastType: SimpleType = TUnit
        val exprTypes = mutableMapOf<SourceSpan, SimpleType>()
        for (stmt in stmts) {
            lastType =
                when (stmt) {
                    is Val -> inferVal(stmt, env, isTopLevel)
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

    private fun inferVal(stmt: Val, env: TypeEnv, isTopLevel: Boolean): SimpleType {
        if (env.contains(stmt.name)) {
            errors.add(TypeError.DuplicateBinding(stmt.name, stmt.span))
        }
        val rhsEnv = env.enterBindingScope()
        val inferredType = infer(stmt.value, rhsEnv)
        if (stmt.typeAnnotation != null) {
            val annotType =
                if (isTopLevel) resolveSignatureTypeExpr(stmt.typeAnnotation, env)
                else resolveBodyTypeExpr(stmt.typeAnnotation, env)
            subtyping.constrain(inferredType, annotType, stmt.span)
            env.bindPolymorphic(stmt.name, annotType)
        } else {
            env.bindPolymorphic(stmt.name, inferredType)
        }
        return TUnit
    }

    private fun inferAscription(expr: Ascription, env: TypeEnv): SimpleType {
        val exprType = infer(expr.expr, env)
        val annotType = resolveBodyTypeExpr(expr.type, env)
        subtyping.constrain(exprType, annotType, expr.span)
        return annotType
    }

    private fun inferFunDef(funDef: FunDef, env: TypeEnv): SimpleType {
        val childEnv = env.child(ImplicitParamContext.BlockedByNamedFunction)
        return inferFunction(funDef.params, funDef.body, funDef.span, childEnv, funDef.returnType)
    }

    private fun inferLambda(lambda: Lambda, env: TypeEnv): SimpleType =
        if (lambda.params.isEmpty()) {
            val implicitType = env.freshVar()
            val childEnv = env.child(ImplicitParamContext.Available(implicitType))
            val bodyType = infer(lambda.body, childEnv)
            if (lambda.body.usesImplicitParam) {
                TFun(listOf(implicitType), bodyType, listOf("."))
            } else {
                TFun(emptyList(), bodyType)
            }
        } else {
            val childEnv = env.child(ImplicitParamContext.BlockedByExplicitParams(lambda.params.map { it.name }))
            inferFunction(lambda.params, lambda.body, lambda.span, childEnv)
        }

    private fun inferFunction(
        params: List<Param>,
        body: Expr,
        span: SourceSpan,
        env: TypeEnv,
        annotRetTypeExpr: TypeExpr? = null,
    ): SimpleType {
        val paramNames = params.map { it.name }
        val seen = mutableSetOf<String>()
        for (name in paramNames) {
            if (name in seen) {
                errors.add(TypeError.DuplicateParameter(name, span))
            }
            seen.add(name)
        }
        // Signature resolution introduces skolems directly into env's local type-var scope.
        // Inner scopes shadow outer names because signature mode only checks local.
        val paramTypes = params.map { param ->
            if (param.typeAnnotation != null) {
                resolveSignatureTypeExpr(param.typeAnnotation, env, Variance.Contravariant)
            } else {
                env.freshVar()
            }
        }
        paramNames.zip(paramTypes).forEach { (name, type) ->
            env.bind(name, type)
        }
        val bodyType = infer(body, env)
        val returnType = if (annotRetTypeExpr != null) {
            val annotReturnType = resolveSignatureTypeExpr(annotRetTypeExpr, env)
            subtyping.constrain(bodyType, annotReturnType, span)
            annotReturnType
        } else {
            bodyType
        }
        return TFun(paramTypes, returnType, paramNames)
    }

    private fun inferApply(
        expr: Apply,
        env: TypeEnv,
    ): SimpleType {
        if (expr.callee is SafeFieldAccess) {
            return inferSafeMethodCall(expr.callee, expr.args, expr.span, env)
        }

        val calleeName =
            when (expr.callee) {
                is Ident -> expr.callee.name
                is FieldAccess -> expr.callee.field
                else -> null // TODO what if lambda?
            }

        val calleeType = infer(expr.callee, env)
        val argTypes = expr.args.map { infer(it, env) }
        val resultType = env.freshVar()

        val context = listOf(ConstraintContext.FunctionCall(calleeName?.let { env.lookupFunDef(it) }, expr.args.map { it.span }))
        subtyping.constrain(
            calleeType,
            TFun(argTypes, resultType),
            expr.span,
            context,
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
        for (field in expr.fields) {
            if (field.name in fieldTypes) {
                errors.add(TypeError.DuplicateField(field.name, expr.span))
            }
            val inferredType = infer(field.value, env)
            if (field.typeAnnotation != null) {
                val annotType = resolveBodyTypeExpr(field.typeAnnotation, env)
                subtyping.constrain(inferredType, annotType, expr.span)
                fieldTypes[field.name] = annotType
            } else {
                fieldTypes[field.name] = inferredType
            }
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
        val result = env.freshVar()
        subtyping.constrain(fieldType, result, expr.span)
        subtyping.constrain(TNull, result, expr.span)
        return result
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
        TypeDefPreprocessor(subtyping, errors).process(typeDefs, env)
    }

    /**
     * Resolve a type annotation at a top-level binding site (fun/lambda signature,
     * top-level val). Type variables not already in local scope are introduced as
     * skolems and bound into [env]'s local type-var scope.
     */
    private fun resolveSignatureTypeExpr(
        typeExpr: TypeExpr,
        env: TypeEnv,
        polarity: Variance = Variance.Covariant,
    ): SimpleType {
        val (type, resolveErrors) =
            SimpleType.fromTypeExpr(typeExpr, env, rigid = true, isEnvClosed = false, polarity = polarity)
        errors.addAll(resolveErrors)
        return type
    }

    /**
     * Resolve a type annotation nested inside a function body. Type variables must
     * already be visible via [TypeEnv.lookupTypeVar]; unknown names produce
     * [TypeError.UnboundTypeVar].
     */
    private fun resolveBodyTypeExpr(
        typeExpr: TypeExpr,
        env: TypeEnv,
        polarity: Variance = Variance.Covariant,
    ): SimpleType {
        val (type, resolveErrors) =
            SimpleType.fromTypeExpr(typeExpr, env, isEnvClosed = true, polarity = polarity)
        errors.addAll(resolveErrors)
        return type
    }

    companion object {
        fun infer(
            program: Program,
            env: TypeEnv = TypeEnv.empty(),
        ): ProgramResult = Typer().infer(program, env)
    }
}
