package klein

import kotlin.jvm.JvmInline

/**
 * The `/N` half of a declaration's key. `contracts.md` §Revisions has one rule — *the key of a
 * declaration is (name, revision), and a bare declaration is revision 1* — and this type is what
 * makes that rule legible in the signatures instead of in a comment.
 *
 * A revision is contract-side data: it identifies a declaration, permanently. It is not a version
 * number a person orders migrations by; that is `ReleaseNumber`, and the two are different types
 * precisely so one can never be passed for the other.
 *
 * It lives in the root package for the same reason [SourceSpan] does: `surface`, `check` and
 * `host` all need it, and none of them may depend on another.
 */
@JvmInline
value class RevisionNumber(
    val value: Int,
) {
    override fun toString(): String = value.toString()
}

/**
 * The number of a release — the unit a person migrates rules by. A release decides which [RevisionNumber]
 * each plain name means for the rules checked against it.
 *
 * A release number orders migrations; a revision identifies a declaration. They are different types
 * so that one can never be passed where the other is wanted, which as bare `Int`s nothing would
 * have stopped.
 */
@JvmInline
value class ReleaseNumber(
    val value: Int,
) {
    override fun toString(): String = value.toString()
}
