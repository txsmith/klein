package klein

import kotlinx.serialization.Serializable

@Serializable
data class SourceSpan(
    val start: Int,
    val end: Int,
) {
    companion object {
        val zero: SourceSpan = SourceSpan(0, 0)

        fun pos(pos: Int): SourceSpan = SourceSpan(pos, pos)
    }
}

@Serializable
sealed class Expr {
    abstract val span: SourceSpan
}

data class IntLiteral(
    val value: Long,
    override val span: SourceSpan,
) : Expr()

data class DoubleLiteral(
    val value: Double,
    override val span: SourceSpan,
) : Expr()

data class BinaryOp(
    val left: Expr,
    val op: Op,
    val right: Expr,
    override val span: SourceSpan,
) : Expr()

@Serializable
enum class Op {
    Add,
    Sub,
    Mul,
    Div,
}
