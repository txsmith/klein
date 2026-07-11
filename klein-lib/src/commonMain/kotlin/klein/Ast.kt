package klein

import kotlinx.serialization.Serializable

data class Program(
    val stmts: List<Stmt>,
    val span: SourceSpan,
)

sealed class Stmt {
    abstract val span: SourceSpan
}

data class Val(
    val name: String,
    val value: Expr,
    override val span: SourceSpan,
    val typeAnnotation: TypeExpr? = null,
) : Stmt()

data class Param(
    val name: String,
    val typeAnnotation: TypeExpr? = null,
    val span: SourceSpan = SourceSpan.zero,
)

data class FunDef(
    val name: String,
    val params: List<Param>,
    val body: Expr,
    override val span: SourceSpan,
    val returnType: TypeExpr? = null,
) : Stmt()

data class TypeDef(
    val name: String,
    val typeParams: List<String>,
    val constructors: List<Constructor>,
    override val span: SourceSpan,
) : Stmt()

data class Constructor(
    val name: String,
    val fields: List<FieldDecl>,
    val span: SourceSpan,
)

data class FieldDecl(
    val name: String,
    val type: TypeExpr,
    val span: SourceSpan,
)

sealed class TypeExpr {
    abstract val span: SourceSpan
}

data class TypeName(
    val name: String,
    override val span: SourceSpan,
) : TypeExpr()

data class AppliedTypeExpr(
    val name: String,
    val args: List<TypeExpr>,
    override val span: SourceSpan,
) : TypeExpr()

data class TypeVar(
    val name: String,
    override val span: SourceSpan,
) : TypeExpr()

data class FunctionTypeExpr(
    val paramTypes: List<TypeExpr>,
    val returnType: TypeExpr,
    override val span: SourceSpan,
) : TypeExpr()

data class TupleTypeExpr(
    val elements: List<TypeExpr>,
    override val span: SourceSpan,
) : TypeExpr()

data class RecordTypeExpr(
    val fields: List<Pair<String, TypeExpr>>,
    override val span: SourceSpan,
) : TypeExpr()

data class OptionalTypeExpr(
    val inner: TypeExpr,
    override val span: SourceSpan,
) : TypeExpr()

data class UnionTypeExpr(
    val left: TypeExpr,
    val right: TypeExpr,
    override val span: SourceSpan,
) : TypeExpr()

data class IntersectionTypeExpr(
    val left: TypeExpr,
    val right: TypeExpr,
    override val span: SourceSpan,
) : TypeExpr()

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

data class NullLiteral(
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
    val params: List<Param>,
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

data class SafeFieldAccess(
    val target: Expr,
    val field: String,
    override val span: SourceSpan,
) : Expr()

data class ImplicitParam(
    override val span: SourceSpan,
) : Expr()

val Expr.usesImplicitParam: Boolean
    get() =
        when (this) {
            is ImplicitParam -> true
            is IntLiteral, is DoubleLiteral, is StringLiteral, is BoolLiteral, is NullLiteral, is Ident -> false
            is BinaryOp -> left.usesImplicitParam || right.usesImplicitParam
            is UnaryOp -> operand.usesImplicitParam
            is Lambda -> false
            is Apply -> callee.usesImplicitParam || args.any { it.usesImplicitParam }
            is RecordLiteral -> fields.any { it.value.usesImplicitParam }
            is Ascription -> expr.usesImplicitParam
            is FieldAccess -> target.usesImplicitParam
            is SafeFieldAccess -> target.usesImplicitParam
            is IfThenElse ->
                condition.usesImplicitParam ||
                    thenBranch.usesImplicitParam ||
                    (elseBranch?.usesImplicitParam ?: false)
            is Block ->
                stmts.any { stmt ->
                    when (stmt) {
                        is Expr -> stmt.usesImplicitParam
                        is Val -> stmt.value.usesImplicitParam
                        is FunDef -> false
                        is TypeDef -> false
                    }
                }
        }

val Expr.children: List<Expr>
    get() =
        when (this) {
            is Block -> emptyList()
            is ImplicitParam, is IntLiteral, is DoubleLiteral, is StringLiteral, is BoolLiteral, is NullLiteral, is Ident -> emptyList()
            is BinaryOp -> listOf(left, right)
            is UnaryOp -> listOf(operand)
            is Lambda -> listOf(body)
            is Apply -> listOf(callee) + args
            is RecordLiteral -> fields.map { it.value }
            is Ascription -> listOf(expr)
            is FieldAccess -> listOf(target)
            is SafeFieldAccess -> listOf(target)
            is IfThenElse -> listOfNotNull(condition, thenBranch, elseBranch)
        }

data class RecordField(
    val name: String,
    val value: Expr,
    val typeAnnotation: TypeExpr? = null,
)
data class RecordLiteral(
    val fields: List<RecordField>,
    override val span: SourceSpan,
) : Expr()

data class Ascription(
    val expr: Expr,
    val type: TypeExpr,
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
