package klein.surface

import klein.Revision
import klein.SourceSpan

/**
 * The root of a capability contract — the other language the parser reads.
 *
 * A contract and a rule share a lexer and a type grammar and nothing else, so they share no root:
 * a [Declaration] is not a [Stmt], and a contract holds no expressions because it has nothing to
 * evaluate. [TypeDef] appears here *and* stays a [Stmt], because a rule may define its own types —
 * that one is shared for real.
 */
data class ContractExpr(
    val types: List<TypeDef<Revision?>>,
    val declarations: List<Declaration>,
    val span: SourceSpan,
)

/** A capability: a name, a signature, and the revision that — with the name — identifies it. */
sealed class Declaration {
    abstract val name: String
    abstract val revision: Revision?
    abstract val span: SourceSpan
}

data class FunDecl(
    override val name: String,
    val params: List<Param<Revision?>>,
    val returnType: TypeExpr<Revision?>,
    override val span: SourceSpan,
    override val revision: Revision? = null,
) : Declaration()

data class ValDecl(
    override val name: String,
    val type: TypeExpr<Revision?>,
    override val span: SourceSpan,
    override val revision: Revision? = null,
) : Declaration()
