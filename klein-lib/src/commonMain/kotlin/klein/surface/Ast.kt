package klein.surface

import klein.Revision
import klein.SourceSpan

import kotlinx.serialization.Serializable

data class Program(
    val stmts: List<Stmt>,
    val span: SourceSpan,
)

/**
 * A statement of the rule language: something that can run. Declarations without bodies belong to
 * the contract language and live in [ContractExpr], so no pass that walks statements carries a
 * branch for a form that never executes.
 */
sealed class Stmt {
    abstract val span: SourceSpan
}

data class Val(
    val name: String,
    val value: Expr,
    override val span: SourceSpan,
    val typeAnnotation: TypeExpr<Nothing?>? = null,
) : Stmt()

data class PatternVal(
    val pattern: Pattern,
    val value: Expr,
    override val span: SourceSpan,
) : Stmt()

data class Param<out R : Revision?>(
    val name: String,
    val typeAnnotation: TypeExpr<R>? = null,
    val span: SourceSpan = SourceSpan.zero,
) {
    val isDiscard: Boolean get() = name == "_"
}

data class FunDef(
    val name: String,
    val params: List<Param<Nothing?>>,
    val body: Expr,
    override val span: SourceSpan,
    val returnType: TypeExpr<Nothing?>? = null,
) : Stmt()

/**
 * A type definition. Shared for real rather than incidentally: a rule may define its own types, and
 * a contract may too — only the contract parser ever fills [revision] in.
 */
data class TypeDef<out R : Revision?>(
    val name: String,
    val typeParams: List<String>,
    val constructors: List<Constructor<R>>,
    val span: SourceSpan,
    val revision: R,
)

/** A type definition as it appears in a rule: unrevisioned, because [TypeDef]`<Nothing?>` is all
 *  this can hold. Unparameterised itself, so `filterIsInstance<TypeDefStmt>()` is erasure-safe. */
data class TypeDefStmt(
    val typeDef: TypeDef<Nothing?>,
) : Stmt() {
    override val span: SourceSpan get() = typeDef.span
}

fun revisionedName(
    name: String,
    revision: Revision?,
): String = if (revision == null || revision.value == 1) name else "$name/${revision.value}"

data class Constructor<out R : Revision?>(
    val name: String,
    val fields: List<FieldDecl<R>>,
    val span: SourceSpan,
)

data class FieldDecl<out R : Revision?>(
    val name: String,
    val type: TypeExpr<R>,
    val span: SourceSpan,
)

/**
 * A type expression, carrying a witness for whether a revision could have been written in it.
 * `TypeExpr<Nothing?>` is a rule's type, and `Nothing?` has exactly one inhabitant — `null`. So "a
 * rule wrote a revision" is not a rule the checker enforces but a state that cannot be constructed.
 */
sealed class TypeExpr<out R : Revision?> {
    abstract val span: SourceSpan
}

data class TypeName<out R : Revision?>(
    val name: String,
    override val span: SourceSpan,
    val revision: R,
) : TypeExpr<R>()

data class AppliedTypeExpr<out R : Revision?>(
    val name: String,
    val args: List<TypeExpr<R>>,
    override val span: SourceSpan,
    val revision: R,
) : TypeExpr<R>()

/** A type variable names a quantifier, never a declaration, so it has no revision slot in either
 *  language — hence `Nothing`, which fits wherever a `TypeExpr<R>` is wanted. */
data class TypeVar(
    val name: String,
    override val span: SourceSpan,
) : TypeExpr<Nothing>()

data class FunctionTypeExpr<out R : Revision?>(
    val paramTypes: List<TypeExpr<R>>,
    val returnType: TypeExpr<R>,
    override val span: SourceSpan,
) : TypeExpr<R>()

data class TupleTypeExpr<out R : Revision?>(
    val elements: List<TypeExpr<R>>,
    override val span: SourceSpan,
) : TypeExpr<R>()

data class RecordTypeExpr<out R : Revision?>(
    val fields: List<Pair<String, TypeExpr<R>>>,
    override val span: SourceSpan,
) : TypeExpr<R>()

data class OptionalTypeExpr<out R : Revision?>(
    val inner: TypeExpr<R>,
    override val span: SourceSpan,
) : TypeExpr<R>()

@Serializable
sealed class Expr : Stmt() {
    abstract override val span: SourceSpan
}

data class IntLiteral(
    val value: Long,
    override val span: SourceSpan,
    // The literal as written in source. Authoritative for numeric models that outrange
    // Long/Double (exact rationals, decimals); `value` is the eager double-world convenience.
    val text: String,
) : Expr()

data class DoubleLiteral(
    val value: Double,
    override val span: SourceSpan,
    // The literal as written in source, before IEEE rounding; see IntLiteral.text.
    val text: String,
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
    val params: List<Param<Nothing?>>,
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

data class Match(
    val scrutinee: Expr,
    val arms: List<MatchArm>,
    override val span: SourceSpan,
) : Expr()

data class MatchArm(
    val pattern: Pattern,
    val guard: Expr?,
    val body: Expr,
    val span: SourceSpan,
)

sealed class Pattern {
    abstract val span: SourceSpan
}

val Pattern.boundNames: List<String>
    get() =
        when (this) {
            is DataPattern -> listOfNotNull(binder) + fields.mapNotNull { it.binder }
            is VariablePattern -> listOf(name)
            is WildcardPattern, is LiteralPattern -> emptyList()
        }

data class WildcardPattern(
    override val span: SourceSpan,
) : Pattern()

/** `42`, `"yes"`, `true`, `null` — [literal] is one of the literal Expr nodes. */
data class LiteralPattern(
    val literal: Expr,
    override val span: SourceSpan,
) : Pattern()

data class VariablePattern(
    val name: String,
    override val span: SourceSpan,
) : Pattern()

/**
 * A tagged-record pattern — the dual of core `MakeData`. [tag] is the constructor name (null for a
 * structural record), [binder] optionally names the whole value, and [fields] destructures. The
 * three are independent: `Circle`, `Circle c`, `Circle { radius }`, `Circle c { radius }`,
 * `{ name }`, `r { name }`.
 */
data class DataPattern(
    val tag: String?,
    val binder: String?,
    val fields: List<FieldPattern>,
    override val span: SourceSpan,
) : Pattern()

/** `name` (pun for `name = name`), `name = n` (rename), or `name = _` (test only: binder = null). */
data class FieldPattern(
    val field: String,
    val binder: String?,
    val span: SourceSpan,
)

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

data class SafeApply(
    val target: Expr,
    val method: String,
    val args: List<Expr>,
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
            is SafeApply -> target.usesImplicitParam || args.any { it.usesImplicitParam }
            is IfThenElse ->
                condition.usesImplicitParam ||
                    thenBranch.usesImplicitParam ||
                    (elseBranch?.usesImplicitParam ?: false)
            is Match ->
                scrutinee.usesImplicitParam ||
                    arms.any { (it.guard?.usesImplicitParam ?: false) || it.body.usesImplicitParam }
            is Block ->
                stmts.any { stmt ->
                    when (stmt) {
                        is Expr -> stmt.usesImplicitParam
                        is Val -> stmt.value.usesImplicitParam
                        is PatternVal -> stmt.value.usesImplicitParam
                        is FunDef -> false
                        is TypeDefStmt -> false
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
            is SafeApply -> listOf(target) + args
            is IfThenElse -> listOfNotNull(condition, thenBranch, elseBranch)
            is Match -> listOf(scrutinee) + arms.flatMap { listOfNotNull(it.guard, it.body) }
        }

data class RecordField(
    val name: String,
    val value: Expr,
    val typeAnnotation: TypeExpr<Nothing?>? = null,
)
data class RecordLiteral(
    val fields: List<RecordField>,
    override val span: SourceSpan,
) : Expr()

data class Ascription(
    val expr: Expr,
    val type: TypeExpr<Nothing?>,
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
