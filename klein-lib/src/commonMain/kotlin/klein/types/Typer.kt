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
        val (type, exprTypes) = inferStmts(program.stmts, env)
        return ProgramResult(type, env, getErrors(), exprTypes)
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
            is IfThenElse -> inferIfThenElse(expr, env)
            is ImplicitParam -> inferImplicitParam(expr, env)
            is Block -> inferStmts(expr.stmts, env.child()).first
        }

    private fun inferStmts(
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
                    is FunDef -> {
                        if (env.contains(stmt.name)) {
                            errors.add(TypeError.DuplicateBinding(stmt.name, stmt.span))
                        }
                        val rhsEnv = env.enterBindingScope()
                        val recVar = rhsEnv.freshVar()
                        rhsEnv.bind(stmt.name, recVar)
                        val type = inferFunction(stmt.params, stmt.body, stmt.span, rhsEnv, functionName = stmt.name)
                        subtyping.constrain(type, recVar, stmt.span)
                        env.bindPolymorphic(stmt.name, recVar)
                        TUnit
                    }
                    is Expr -> {
                        val type = infer(stmt, env)
                        exprTypes[stmt.span] = type
                        type
                    }
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

    companion object {
        fun infer(
            program: Program,
            env: TypeEnv = TypeEnv.empty(),
        ): ProgramResult = Typer().infer(program, env)
    }
}
