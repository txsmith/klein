package klein

import klein.Type.*

data class InferResult(
    val type: Type,
    val errors: List<TypeError>,
) {
    val hasErrors: Boolean get() = errors.isNotEmpty()

    fun withType(t: Type) = copy(type = t)

    fun withError(e: TypeError) = copy(errors = errors + e)

    companion object {
        fun ok(type: Type) = InferResult(type, emptyList())

        fun err(
            type: Type,
            error: TypeError,
        ) = InferResult(type, listOf(error))
    }
}

object TypeGen {
    fun infer(
        expr: Expr,
        env: TypeEnv,
    ): InferResult =
        when (expr) {
            is IntLiteral -> InferResult.ok(TInt)
            is DoubleLiteral -> InferResult.ok(TDouble)
            is StringLiteral -> InferResult.ok(TString)
            is BoolLiteral -> InferResult.ok(TBool)
            is Ident -> inferIdent(expr, env)
            is BinaryOp -> TODO("Phase 5: Operators")
            is UnaryOp -> TODO("Phase 5: Operators")
            is Lambda -> TODO("Phase 6: Functions")
            is Apply -> TODO("Phase 6: Functions")
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
}
