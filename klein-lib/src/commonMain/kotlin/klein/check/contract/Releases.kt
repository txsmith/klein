package klein.check.contract

import klein.ReleaseNumber
import klein.Revision
import klein.SourceSpan

/**
 * One release: what each plain name means to the rules checked against it.
 *
 * [surface] is *absolute* — deltas already folded — so nothing downstream re-derives it. Inside one
 * release a name means exactly one revision, which is what makes dropping the revision on the way
 * to a rule lossless.
 *
 * Nothing parses or resolves a release yet; callers build one by hand and hand it to
 * [environmentFor]. Resolution from written `release N` blocks arrives with the syntax.
 */
data class Release(
    val number: ReleaseNumber,
    val surface: Map<String, Revision>,
    val span: SourceSpan = SourceSpan.zero,
)
