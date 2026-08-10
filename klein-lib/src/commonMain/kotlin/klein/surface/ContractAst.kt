package klein.surface

import klein.ReleaseNumber
import klein.Revision
import klein.SourceSpan

/**
 * The root of a capability contract — the other language the parser reads.
 *
 * A contract and a rule share a lexer and a type grammar and nothing else, so they share no root:
 * a [Declaration] is not a [Stmt], and a contract holds no expressions because it has nothing to
 * evaluate. [TypeDef] is shared for real, because a rule may define its own types — but it is a
 * [Stmt] in neither language: a rule reaches it through [TypeDefStmt], which admits `Nothing?`
 * alone, and a contract holds it here, where a revision is allowed.
 */
data class ContractExpr(
    val types: List<TypeDef<*>>,
    val declarations: List<CapabilityDeclaration>,
    val releases: List<ReleaseBlock>,
    val span: SourceSpan,
)

/** A capability: a name, a signature, and the revision that — with the name — identifies it. */
sealed class CapabilityDeclaration {
    abstract val name: String
    abstract val revision: Revision?
    abstract val span: SourceSpan
}

data class FunDecl(
    override val name: String,
    val params: List<Param<*>>,
    val returnType: TypeExpr<*>,
    override val span: SourceSpan,
    override val revision: Revision? = null,
) : CapabilityDeclaration()

data class ValDecl(
    override val name: String,
    val type: TypeExpr<*>,
    override val span: SourceSpan,
    override val revision: Revision? = null,
) : CapabilityDeclaration()

data class ReleaseBlock(
    val number: ReleaseNumber,
    val entries: List<ReleaseEntry>,
    val span: SourceSpan,
)

data class ReleaseEntry(
    val name: String,
    val revision: Revision?,
    val remove: Boolean,
    val span: SourceSpan,
)
