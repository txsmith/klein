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
        subtyping = Subtyping(env)
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
        if (typeDefs.isEmpty()) return

        // Register placeholders for all types and constructors
        // This ensures all type names are known, and enables the following passes to work for (mutually) recursive types
        registerPlaceholders(typeDefs, env)

        // Validate all type references exist and have correct arity
        resolveNames(env)

        // Infer variance for all type parameters
        computeVariance(typeDefs, env)

        // Compute the TRecord type of each constructor
        buildCtorIfaces(env)

        // Derive the shared fields for each sum type
        buildParentIfaces(typeDefs, env)

        // Expose ctors as function values
        bindConstructors(env)
    }

    private fun registerPlaceholders(
        typeDefs: List<TypeDef>,
        env: TypeEnv,
    ) {
        val polyEnv = env.enterBindingScope() // Ensure we create lvl-1 TVars so they get generalized
        for (typeDef in typeDefs) {
            val typeParams = typeDef.typeParams.map { TypeParamInfo(it, Variance.Bivariant, polyEnv.freshVar()) }

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
                val usedTypeVars = constructor.fields.flatMap { collectTypeVars(it.type) }.toSet()
                val declaredTypeParams = typeDef.typeParams.toSet()

                // Check for undeclared type params
                for (typeVar in usedTypeVars) {
                    if (typeVar !in declaredTypeParams) {
                        errors.add(TypeError.UndeclaredTypeParam(typeVar, typeDef.name, constructor.span))
                    }
                }

                val ctorTypeParams =
                    typeParams
                        .filter { it.name in usedTypeVars }
                        .map { it.copy(tvar = polyEnv.freshVar()) }

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

    private fun resolveNames(env: TypeEnv) {
        for (ctor in env.allConstructors()) {
            for (field in ctor.fields) {
                resolveTypeExpr(field.type, env)
            }
        }
    }

    private fun resolveTypeExpr(
        typeExpr: TypeExpr,
        env: TypeEnv,
    ) {
        when (typeExpr) {
            is TypeVar -> {} // TVars are already checked during registerPlaceholders
            is TypeName -> {
                val builtinTypes = setOf("Num", "String", "Bool", "Unit")
                if (typeExpr.name !in builtinTypes && env.lookupTypeDef(typeExpr.name) == null) {
                    errors.add(TypeError.UnboundVariable(typeExpr.name, typeExpr.span))
                }
            }
            is AppliedTypeExpr -> {
                val typeDef = env.lookupTypeDef(typeExpr.name)
                if (typeDef == null) {
                    errors.add(TypeError.UnboundVariable(typeExpr.name, typeExpr.span))
                } else if (typeExpr.args.size != typeDef.typeParams.size) {
                    errors.add(TypeError.ArityMismatch(typeDef.typeParams.size, typeExpr.args.size, typeExpr.span))
                }
                for (arg in typeExpr.args) {
                    resolveTypeExpr(arg, env)
                }
            }
            is FunctionTypeExpr -> {
                for (param in typeExpr.paramTypes) resolveTypeExpr(param, env)
                resolveTypeExpr(typeExpr.returnType, env)
            }
            is TupleTypeExpr -> {
                for (element in typeExpr.elements) {
                    resolveTypeExpr(element, env)
                }
            }
            is RecordTypeExpr -> {
                for ((_, fieldType) in typeExpr.fields) {
                    resolveTypeExpr(fieldType, env)
                }
            }
        }
    }

    private fun collectTypeVars(typeExpr: TypeExpr): Set<String> =
        when (typeExpr) {
            is TypeVar -> setOf(typeExpr.name)
            is TypeName -> emptySet()
            is AppliedTypeExpr -> typeExpr.args.flatMap { collectTypeVars(it) }.toSet()
            is FunctionTypeExpr -> typeExpr.paramTypes.flatMap { collectTypeVars(it) }.toSet() + collectTypeVars(typeExpr.returnType)
            is TupleTypeExpr -> typeExpr.elements.flatMap { collectTypeVars(it) }.toSet()
            is RecordTypeExpr -> typeExpr.fields.flatMap { collectTypeVars(it.second) }.toSet()
        }

    private fun computeVariance(
        typeDefs: List<TypeDef>,
        env: TypeEnv,
    ) {
        if (typeDefs.isEmpty()) return

        val allTypeDefs = env.allTypeDefs()
        val allConstructors = env.allConstructors()

        val variances = mutableMapOf<Pair<String, String>, Variance>()

        for (typeDefInfo in allTypeDefs) {
            for (param in typeDefInfo.typeParams) {
                variances[typeDefInfo.name to param.name] = Variance.Bivariant
            }
        }

        fun update(
            typeExpr: TypeExpr,
            ownerName: String,
            polarity: Variance,
        ): Boolean =
            when (typeExpr) {
                is TypeVar -> {
                    val current =
                        variances[ownerName to typeExpr.name]
                            ?: error("TypeVar '${typeExpr.name}' not found in variances for $ownerName")
                    val newVariance = current.meet(polarity)
                    if (newVariance != current) {
                        variances[ownerName to typeExpr.name] = newVariance
                        true
                    } else {
                        false
                    }
                }

                is TypeName -> false

                is AppliedTypeExpr -> {
                    val refInfo = env.getTypeDef(typeExpr.name)
                    var changed = false
                    for ((i, arg) in typeExpr.args.withIndex()) {
                        val paramName =
                            refInfo.typeParams.getOrNull(i)?.name
                                ?: error("Type '${typeExpr.name}' has ${refInfo.typeParams.size} params but got arg at index $i")
                        val paramVariance =
                            variances[typeExpr.name to paramName]
                                ?: error("Variance not found for ${typeExpr.name}.$paramName")
                        val argPolarity =
                            when (paramVariance) {
                                Variance.Bivariant -> polarity
                                Variance.Covariant -> polarity
                                Variance.Contravariant -> polarity.flip()
                                Variance.Invariant -> Variance.Invariant
                            }
                        changed = update(arg, ownerName, argPolarity) || changed
                    }
                    changed
                }

                is FunctionTypeExpr -> {
                    var changed = false
                    for (param in typeExpr.paramTypes) {
                        changed = update(param, ownerName, polarity.flip()) || changed
                    }
                    changed = update(typeExpr.returnType, ownerName, polarity) || changed
                    changed
                }

                is TupleTypeExpr -> {
                    var changed = false
                    for (element in typeExpr.elements) {
                        changed = update(element, ownerName, polarity) || changed
                    }
                    changed
                }

                is RecordTypeExpr -> {
                    var changed = false
                    for ((_, fieldType) in typeExpr.fields) {
                        changed = update(fieldType, ownerName, polarity) || changed
                    }
                    changed
                }
            }

        var changed = true
        while (changed) {
            changed = false
            for (ctor in allConstructors) {
                for (field in ctor.fields) {
                    changed = update(field.type, ctor.name, Variance.Covariant) || changed
                }
            }
        }

        for (ctor in allConstructors) {
            for (param in ctor.typeParams) {
                val parentKey = ctor.parentType to param
                val ctorKey = ctor.name to param
                val parentVar = variances[parentKey] ?: Variance.Bivariant
                val ctorVar = variances[ctorKey] ?: Variance.Bivariant
                variances[parentKey] = parentVar.meet(ctorVar)
            }
        }

        for ((key, variance) in variances.toMap()) {
            if (variance == Variance.Bivariant) {
                variances[key] = Variance.Invariant
            }
        }

        for (typeDefInfo in allTypeDefs) {
            val updatedParams =
                typeDefInfo.typeParams.map { param ->
                    val v = variances[typeDefInfo.name to param.name] ?: Variance.Invariant
                    param.copy(variance = v)
                }
            env.updateTypeDef(typeDefInfo.copy(typeParams = updatedParams))
        }
    }

    private fun buildCtorIfaces(env: TypeEnv) {
        for (ctor in env.allConstructors()) {
            val iface = inferCtorIface(ctor, env)
            env.updateTypeDef(env.getTypeDef(ctor.name).copy(iface = iface))
        }
    }

    private fun buildParentIfaces(
        typeDefs: List<TypeDef>,
        env: TypeEnv,
    ) {
        for (typeDef in typeDefs) {
            val ctorIfaces = typeDef.constructors.map { env.getTypeDef(it.name).iface }
            val parentIface = intersectIfaces(ctorIfaces)
            env.updateTypeDef(env.getTypeDef(typeDef.name).copy(iface = parentIface))
        }
    }

    private fun bindConstructors(env: TypeEnv) {
        for (ctor in env.allConstructors()) {
            val ctorTypeDef = env.getTypeDef(ctor.name)
            val tvars = ctorTypeDef.typeParams.map { it.tvar }
            val resultType = TRef(ctor.name, tvars, ctor.span)

            val ctorType =
                if (ctor.fields.isEmpty()) {
                    resultType
                } else {
                    val fieldTypes = ctor.fields.map { ctorTypeDef.iface.fields[it.name]!! }
                    TFun(fieldTypes, resultType)
                }

            env.bindPolymorphic(ctor.name, ctorType)
        }
    }

    private fun intersectIfaces(ifaces: List<TRecord>): TRecord {
        if (ifaces.isEmpty()) return TRecord(emptyMap())
        return ifaces.reduce { acc, record ->
            TRecord(acc.fields.filter { (name, type) -> record.fields[name] == type })
        }
    }

    private fun inferCtorIface(
        ctor: ConstructorInfo,
        env: TypeEnv,
    ): TRecord {
        val ctorTypeDef = env.getTypeDef(ctor.name)
        val typeVarMap: Map<String, TVar> = ctorTypeDef.typeParams.associate { it.name to it.tvar }

        fun convertToSimpleType(typeExpr: TypeExpr): SimpleType =
            when (typeExpr) {
                is TypeVar ->
                    typeVarMap[typeExpr.name]
                        ?: error("Type variable '${typeExpr.name}' not in scope")

                is TypeName ->
                    when (typeExpr.name) {
                        "Num" -> TNum
                        "String" -> TString
                        "Bool" -> TBool
                        "Unit" -> TUnit
                        else -> TRef(typeExpr.name, emptyList(), typeExpr.span)
                    }

                is AppliedTypeExpr -> {
                    val args = typeExpr.args.map { convertToSimpleType(it) }
                    TRef(typeExpr.name, args, typeExpr.span)
                }

                is FunctionTypeExpr ->
                    TFun(
                        typeExpr.paramTypes.map { convertToSimpleType(it) },
                        convertToSimpleType(typeExpr.returnType),
                    )

                is TupleTypeExpr -> {
                    if (typeExpr.elements.isEmpty()) {
                        TUnit
                    } else {
                        val fields =
                            typeExpr.elements
                                .mapIndexed { i, elem ->
                                    "_$i" to convertToSimpleType(elem)
                                }.toMap()
                        TRecord(fields)
                    }
                }

                is RecordTypeExpr ->
                    TRecord(
                        typeExpr.fields.associate { (name, type) ->
                            name to convertToSimpleType(type)
                        },
                    )
            }

        val fields = ctor.fields.associate { it.name to convertToSimpleType(it.type) }

        return TRecord(fields)
    }

    companion object {
        fun infer(
            program: Program,
            env: TypeEnv = TypeEnv.empty(),
        ): ProgramResult = Typer().infer(program, env)
    }
}
