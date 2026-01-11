package klein.types

import klein.*
import klein.types.SimpleType.*

data class InferResult(
    val type: SimpleType,
    val errors: List<TypeError>,
) {
    val hasErrors: Boolean get() = errors.isNotEmpty()

    fun withType(t: SimpleType) = copy(type = t)

    fun withError(e: TypeError) = copy(errors = errors + e)

    companion object {
        fun ok(type: SimpleType) = InferResult(type, emptyList())

        fun err(
            type: SimpleType,
            error: TypeError,
        ) = InferResult(type, listOf(error))
    }
}

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
)

object Typer {
    fun infer(
        program: Program,
        env: TypeEnv = TypeEnv.empty(),
    ): ProgramResult {
        val typedStmts = mutableListOf<TypedStmt>()
        val errors = mutableListOf<TypeError>()

        for (stmt in program.stmts) {
            when (stmt) {
                is Val -> {
                    val result = infer(stmt.value, env)
                    errors.addAll(result.errors)
                    env.bind(stmt.name, result.type)
                    typedStmts.add(TypedStmt.TypedVal(stmt.name, result.type))
                }
                is FunDef -> {
                    val result = inferFunction(stmt.params, stmt.body, env)
                    errors.addAll(result.errors)
                    env.bind(stmt.name, result.type)
                    typedStmts.add(TypedStmt.TypedFunDef(stmt.name, result.type))
                }
                is Expr -> {
                    val result = infer(stmt, env)
                    errors.addAll(result.errors)
                    typedStmts.add(TypedStmt.TypedExpr(result.type, stmt.span))
                }
            }
        }

        return ProgramResult(typedStmts, env, errors)
    }

    fun infer(
        expr: Expr,
        env: TypeEnv,
    ): InferResult =
        when (expr) {
            is IntLiteral -> InferResult.ok(TNum)
            is DoubleLiteral -> InferResult.ok(TNum)
            is StringLiteral -> InferResult.ok(TString)
            is BoolLiteral -> InferResult.ok(TBool)
            is Ident -> inferIdent(expr, env)
            is BinaryOp -> inferBinaryOp(expr, env)
            is UnaryOp -> inferUnaryOp(expr, env)
            is Lambda -> inferLambda(expr, env)
            is Apply -> inferApply(expr, env)
            is Block -> TODO("Phase 8: Blocks")
            is IfThenElse -> TODO("Phase 8: Control flow")
            is FieldAccess -> TODO("Phase 7: Records")
            is ImplicitParam -> TODO("Phase 6: Functions")
            is RecordLiteral -> TODO("Phase 7: Records")
        }

    private fun inferIdent(
        expr: Ident,
        env: TypeEnv,
    ): InferResult =
        env
            .lookup(expr.name)
            ?.let { InferResult.ok(it) }
            ?: InferResult.err(TVar(), TypeError.UnboundVariable(expr.name, expr.span))

    private fun inferLambda(
        expr: Lambda,
        env: TypeEnv,
    ): InferResult = inferFunction(expr.params, expr.body, env)

    private fun inferFunction(
        params: List<String>,
        body: Expr,
        env: TypeEnv,
    ): InferResult {
        val paramTypes = params.map { TVar() }
        val childEnv = env.child()
        params.zip(paramTypes).forEach { (name, type) ->
            childEnv.bind(name, type)
        }
        val bodyResult = infer(body, childEnv)
        return bodyResult.withType(TFun(paramTypes, bodyResult.type))
    }

    private fun inferApply(
        expr: Apply,
        env: TypeEnv,
    ): InferResult {
        val calleeResult = infer(expr.callee, env)
        val argResults = expr.args.map { infer(it, env) }
        val argTypes = argResults.map { it.type }
        val resultType = TVar()

        val subtyping = Subtyping()
        subtyping.constrain(
            calleeResult.type,
            TFun(argTypes, resultType),
            expr.span,
        )

        val allErrors =
            calleeResult.errors +
                argResults.flatMap { it.errors } +
                subtyping.getErrors()

        return InferResult(resultType, allErrors)
    }

    private fun inferBinaryOp(
        expr: BinaryOp,
        env: TypeEnv,
    ): InferResult {
        val leftResult = infer(expr.left, env)
        val rightResult = infer(expr.right, env)
        val subtyping = Subtyping()

        val resultType =
            when (expr.op) {
                Operator.Add, Operator.Sub, Operator.Mul, Operator.Div, Operator.Mod -> {
                    subtyping.constrain(leftResult.type, TNum, expr.left.span)
                    subtyping.constrain(rightResult.type, TNum, expr.right.span)
                    TNum
                }
                Operator.Lt, Operator.LtEq, Operator.Gt, Operator.GtEq -> {
                    subtyping.constrain(leftResult.type, TNum, expr.left.span)
                    subtyping.constrain(rightResult.type, TNum, expr.right.span)
                    TBool
                }
                Operator.Eq, Operator.NotEq -> {
                    TBool
                }
                Operator.And, Operator.Or -> {
                    subtyping.constrain(leftResult.type, TBool, expr.left.span)
                    subtyping.constrain(rightResult.type, TBool, expr.right.span)
                    TBool
                }
            }

        val allErrors = leftResult.errors + rightResult.errors + subtyping.getErrors()
        return InferResult(resultType, allErrors)
    }

    private fun inferUnaryOp(
        expr: UnaryOp,
        env: TypeEnv,
    ): InferResult {
        val operandResult = infer(expr.operand, env)
        val subtyping = Subtyping()

        val resultType =
            when (expr.op) {
                UnaryOperator.Neg -> {
                    subtyping.constrain(operandResult.type, TNum, expr.operand.span)
                    TNum
                }
                UnaryOperator.Not -> {
                    subtyping.constrain(operandResult.type, TBool, expr.operand.span)
                    TBool
                }
            }

        val allErrors = operandResult.errors + subtyping.getErrors()
        return InferResult(resultType, allErrors)
    }
}
