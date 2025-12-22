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

data class FunDef(
    val name: String,
    val params: List<String>,
    val body: Expr,
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
    override val span: SourceSpan,
) : Expr()

data class IfThenElse(
    val condition: Expr,
    val thenBranch: Expr,
    val elseBranch: Expr?,
    override val span: SourceSpan,
) : Expr()

data class FieldAccess(
    val target: Expr,
    val field: String,
    override val span: SourceSpan,
) : Expr()

data class ImplicitParam(
    override val span: SourceSpan,
) : Expr()

data class RecordLiteral(
    val fields: List<Pair<String, Expr>>,
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
        fun fromTokenKind(kind: TokenKind): Operator? =
            when (kind) {
                TokenKind.PLUS -> Add
                TokenKind.MINUS -> Sub
                TokenKind.MINUS_TIGHT -> Sub
                TokenKind.STAR -> Mul
                TokenKind.SLASH -> Div
                TokenKind.PERCENT -> Mod
                TokenKind.EQEQ -> Eq
                TokenKind.NEQ -> NotEq
                TokenKind.LT -> Lt
                TokenKind.LTEQ -> LtEq
                TokenKind.GT -> Gt
                TokenKind.GTEQ -> GtEq
                TokenKind.AND -> And
                TokenKind.OR -> Or
                else -> null
            }
    }
}
