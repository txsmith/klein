package klein.check.contract

import klein.KleinError
import klein.SourceSpan
import klein.surface.*

/**
 * An answer that names a capability of the release it was asked under. Answers may use the
 * release's types, so answering a prompt never triggers more prompts.
 */
class CapabilityInAnswer(
    val name: String,
) : KleinError {
    override val message = "'$name' is a capability; an answer may use the release's types but not its capabilities"
    override val span = SourceSpan.zero
}

internal fun usedCapabilities(
    program: Program,
    exposed: Set<String>,
): Set<String> {
    val walk =
        object {
            fun mention(
                name: String,
                bound: Set<String>,
            ): Set<String> = if (name in exposed && name !in bound) setOf(name) else emptySet()

            fun typeExpr(
                t: TypeExpr<Nothing?>?,
                bound: Set<String>,
            ): Set<String> =
                when (t) {
                    null, is TypeVar -> emptySet()
                    is TypeName -> mention(t.name, bound)
                    is AppliedTypeExpr -> mention(t.name, bound) + t.args.flatMapTo(mutableSetOf()) { typeExpr(it, bound) }
                    is FunctionTypeExpr ->
                        t.paramTypes.flatMapTo(mutableSetOf()) { typeExpr(it, bound) } + typeExpr(t.returnType, bound)
                    is TupleTypeExpr -> t.elements.flatMapTo(mutableSetOf()) { typeExpr(it, bound) }
                    is RecordTypeExpr -> t.fields.flatMapTo(mutableSetOf()) { (_, fieldType) -> typeExpr(fieldType, bound) }
                    is OptionalTypeExpr -> typeExpr(t.inner, bound)
                }

            fun pattern(
                p: Pattern,
                bound: Set<String>,
            ): Set<String> = if (p is DataPattern && p.tag != null) mention(p.tag, bound) else emptySet()

            fun typeDef(
                d: TypeDef<Nothing?>,
                bound: Set<String>,
            ): Set<String> =
                d.constructors.flatMapTo(mutableSetOf()) { ctor ->
                    ctor.fields.flatMapTo(mutableSetOf()) { typeExpr(it.type, bound) }
                }

            fun params(
                params: List<Param<Nothing?>>,
                body: Expr,
                bound: Set<String>,
            ): Set<String> =
                params.flatMapTo(mutableSetOf()) { typeExpr(it.typeAnnotation, bound) } +
                    expr(body, bound + params.map { it.name })

            fun matchArm(
                arm: MatchArm,
                bound: Set<String>,
            ): Set<String> {
                val armBound = bound + arm.pattern.boundNames
                return pattern(arm.pattern, bound) +
                    (arm.guard?.let { expr(it, armBound) } ?: emptySet()) +
                    expr(arm.body, armBound)
            }

            fun expr(
                e: Expr,
                bound: Set<String>,
            ): Set<String> =
                when (e) {
                    is Ident -> mention(e.name, bound)
                    is IntLiteral, is DoubleLiteral, is StringLiteral, is BoolLiteral, is NullLiteral, is ImplicitParam -> emptySet()
                    is Lambda -> params(e.params, e.body, bound)
                    is Block -> stmts(e.stmts, bound)
                    is Match -> expr(e.scrutinee, bound) + e.arms.flatMapTo(mutableSetOf()) { matchArm(it, bound) }
                    is RecordLiteral ->
                        e.fields.flatMapTo(mutableSetOf()) { typeExpr(it.typeAnnotation, bound) + expr(it.value, bound) }
                    is Ascription -> expr(e.expr, bound) + typeExpr(e.type, bound)
                    is BinaryOp, is UnaryOp, is Apply, is IfThenElse, is FieldAccess, is SafeFieldAccess, is SafeApply ->
                        e.children.flatMapTo(mutableSetOf()) { expr(it, bound) }
                }

            fun stmt(
                s: Stmt,
                bound: Set<String>,
            ): Set<String> =
                when (s) {
                    is Val -> typeExpr(s.typeAnnotation, bound) + expr(s.value, bound)
                    is PatternVal -> pattern(s.pattern, bound) + expr(s.value, bound)
                    is FunDef -> typeExpr(s.returnType, bound) + params(s.params, s.body, bound)
                    is TypeDefStmt -> typeDef(s.typeDef, bound)
                    is Expr -> expr(s, bound)
                }

            fun stmts(
                stmts: List<Stmt>,
                outer: Set<String>,
            ): Set<String> {
                val bound = outer + stmts.flatMap { it.binders }
                return stmts.flatMapTo(mutableSetOf()) { stmt(it, bound) }
            }
        }

    return walk.stmts(program.stmts, emptySet())
}

private val Stmt.binders: List<String>
    get() =
        when (this) {
            is Val -> listOf(name)
            is PatternVal -> pattern.boundNames
            is FunDef -> listOf(name)
            is TypeDefStmt -> listOf(typeDef.name) + typeDef.constructors.map { it.name }
            is Expr -> emptyList()
        }
