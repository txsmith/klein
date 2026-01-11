package klein.types

import klein.*
import klein.types.SimpleType.*

sealed class TypedStmt {
    data class TypedVal(
        val name: String,
        val type: SimpleType,
    ) : TypedStmt()

    data class TypedFunDef(
        val name: String,
        val type: SimpleType,
    ) : TypedStmt()

    data class TypedExpr(
        val type: SimpleType,
        val span: SourceSpan,
    ) : TypedStmt()
}

data class ProgramResult(
    val stmts: List<TypedStmt>,
    val env: TypeEnv,
    val errors: List<TypeError>,
) {
    val type: SimpleType
        get() = (stmts.lastOrNull() as? TypedStmt.TypedExpr)?.type ?: TUnit

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
        val typedStmts = mutableListOf<TypedStmt>()

        for (stmt in program.stmts) {
            when (stmt) {
                is Val -> {
                    if (env.contains(stmt.name)) {
                        errors.add(TypeError.DuplicateBinding(stmt.name, stmt.span))
                    }
                    // Infer RHS at level+1, then generalize at current level
                    val rhsEnv = env.child(level = env.level + 1)
                    val type = infer(stmt.value, rhsEnv)
                    env.bindPolymorphic(stmt.name, type, generalizationLevel = env.level)
                    typedStmts.add(TypedStmt.TypedVal(stmt.name, type))
                }
                is FunDef -> {
                    if (env.contains(stmt.name)) {
                        errors.add(TypeError.DuplicateBinding(stmt.name, stmt.span))
                    }
                    // Infer function at level+1, then generalize at current level
                    val rhsEnv = env.child(level = env.level + 1)
                    val type = inferFunction(stmt.params, stmt.body, stmt.span, rhsEnv, functionName = stmt.name)
                    env.bindPolymorphic(stmt.name, type, generalizationLevel = env.level)
                    typedStmts.add(TypedStmt.TypedFunDef(stmt.name, type))
                }
                is Expr -> {
                    val type = infer(stmt, env)
                    typedStmts.add(TypedStmt.TypedExpr(type, stmt.span))
                }
            }
        }

        return ProgramResult(typedStmts, env, getErrors())
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
            is Ident -> inferIdent(expr, env)
            is BinaryOp -> inferBinaryOp(expr, env)
            is UnaryOp -> inferUnaryOp(expr, env)
            is Lambda -> inferLambda(expr, env)
            is Apply -> inferApply(expr, env)
            is RecordLiteral -> inferRecordLiteral(expr, env)
            is FieldAccess -> inferFieldAccess(expr, env)
            is IfThenElse -> inferIfThenElse(expr, env)
            is ImplicitParam -> inferImplicitParam(expr, env)
            is Block -> inferBlock(expr, env)
        }

    private fun inferIdent(
        expr: Ident,
        env: TypeEnv,
    ): SimpleType =
        env.lookup(expr.name, env.level) ?: run {
            errors.add(TypeError.UnboundVariable(expr.name, expr.span))
            TVar(env.level)
        }

    private fun inferImplicitParam(
        expr: ImplicitParam,
        env: TypeEnv,
    ): SimpleType =
        when (val ctx = env.implicitParam) {
            is ImplicitParamContext.Available -> ctx.type
            is ImplicitParamContext.BlockedByNamedFunction -> {
                errors.add(TypeError.ImplicitParamInNamedFunction(expr.span))
                TVar(env.level)
            }
            is ImplicitParamContext.BlockedByExplicitParams -> {
                errors.add(TypeError.ImplicitParamWithExplicitParams(ctx.params, expr.span))
                TVar(env.level)
            }
            is ImplicitParamContext.None -> {
                errors.add(TypeError.ImplicitParamOutsideLambda(expr.span))
                TVar(env.level)
            }
        }

    private fun inferLambda(
        expr: Lambda,
        env: TypeEnv,
    ): SimpleType = inferFunction(expr.params, expr.body, expr.span, env)

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
            val implicitType = SimpleType.TVar(env.level)
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
            val paramTypes = params.map { SimpleType.TVar(env.level) }
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
        val resultType = TVar(env.level)

        subtyping.constrain(
            calleeType,
            TFun(argTypes, resultType),
            expr.span,
        )

        return resultType
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
        val fieldType = TVar(env.level)
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
            val resultType = TVar(env.level)
            subtyping.constrain(thenType, resultType, expr.thenBranch.span)
            subtyping.constrain(elseType, resultType, expr.elseBranch.span)
            resultType
        } else {
            TUnit
        }
    }

    private fun inferBlock(
        expr: Block,
        env: TypeEnv,
    ): SimpleType {
        if (expr.stmts.isEmpty()) {
            return TUnit
        }

        val blockEnv = env.child()
        var lastType: SimpleType = TUnit

        for (stmt in expr.stmts) {
            when (stmt) {
                is Val -> {
                    if (blockEnv.contains(stmt.name)) {
                        errors.add(TypeError.DuplicateBinding(stmt.name, stmt.span))
                    }
                    // Infer RHS at level+1, then generalize at block level
                    val rhsEnv = blockEnv.child(level = blockEnv.level + 1)
                    val type = infer(stmt.value, rhsEnv)
                    blockEnv.bindPolymorphic(stmt.name, type, generalizationLevel = blockEnv.level)
                    lastType = TUnit
                }
                is FunDef -> {
                    if (blockEnv.contains(stmt.name)) {
                        errors.add(TypeError.DuplicateBinding(stmt.name, stmt.span))
                    }
                    // Infer function at level+1, then generalize at block level
                    val rhsEnv = blockEnv.child(level = blockEnv.level + 1)
                    val type = inferFunction(stmt.params, stmt.body, stmt.span, rhsEnv, functionName = stmt.name)
                    blockEnv.bindPolymorphic(stmt.name, type, generalizationLevel = blockEnv.level)
                    lastType = TUnit
                }
                is Expr -> {
                    lastType = infer(stmt, blockEnv)
                }
            }
        }

        return lastType
    }

    companion object {
        fun infer(
            program: Program,
            env: TypeEnv = TypeEnv.empty(),
        ): ProgramResult = Typer().infer(program, env)
    }
}
