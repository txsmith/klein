package klein

import kotlinx.serialization.Serializable

sealed class Stmt {
    abstract val span: SourceSpan
}

data class Val(
    val name: String,
    val value: Expr,
    override val span: SourceSpan,
) : Stmt()

@Serializable
sealed class Expr : Stmt() {
    abstract override val span: SourceSpan
}

data class IntLiteral(
    val value: Long,
    override val span: SourceSpan,
) : Expr()

data class DoubleLiteral(
    val value: Double,
    override val span: SourceSpan,
) : Expr()

data class StringLiteral(
    val value: String,
    override val span: SourceSpan,
) : Expr()

data class BoolLiteral(
    val value: Boolean,
    override val span: SourceSpan,
) : Expr()

data class Ident(
    val name: String,
    override val span: SourceSpan,
) : Expr()

data class BinaryOp(
    val left: Expr,
    val op: Operator,
    val right: Expr,
    override val span: SourceSpan,
) : Expr()

data class UnaryOp(
    val op: UnaryOperator,
    val operand: Expr,
    override val span: SourceSpan,
) : Expr()

data class Lambda(
    val params: List<String>,
    val body: Expr,
    override val span: SourceSpan,
) : Expr()

data class Apply(
    val callee: Expr,
    val args: List<Expr>,
    override val span: SourceSpan,
) : Expr()

data class Block(
    val stmts: List<Stmt>,
    val expr: Expr,
    override val span: SourceSpan,
) : Expr()

@Serializable
enum class UnaryOperator {
    Neg,
    Not,
}

@Serializable
enum class Operator(
    val precedence: Int,
) {
    Or(1),
    And(2),
    Eq(3),
    NotEq(3),
    Lt(4),
    LtEq(4),
    Gt(4),
    GtEq(4),
    Add(5),
    Sub(5),
    Mul(6),
    Div(6),
    Mod(6),
    ;

    companion object {
        private val bySymbol =
            mapOf(
                "+" to Add,
                "-" to Sub,
                "*" to Mul,
                "/" to Div,
                "%" to Mod,
                "==" to Eq,
                "!=" to NotEq,
                "<" to Lt,
                "<=" to LtEq,
                ">" to Gt,
                ">=" to GtEq,
            )

        fun fromSymbol(text: String): Operator? = bySymbol[text]

        fun fromKeyword(kind: KeywordKind): Operator? =
            when (kind) {
                KeywordKind.And -> And
                KeywordKind.Or -> Or
                else -> null
            }
    }
}

@Serializable
data class SourceSpan(
    val start: Int,
    val end: Int,
) {
    operator fun plus(other: SourceSpan): SourceSpan = SourceSpan(start, other.end)

    companion object {
        val zero: SourceSpan = SourceSpan(0, 0)

        fun pos(pos: Int): SourceSpan = SourceSpan(pos, pos)
    }
}
