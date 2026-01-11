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
                    typedStmts.add(TypedStmt.TypedFunDef(stmt.name, TVar()))
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
