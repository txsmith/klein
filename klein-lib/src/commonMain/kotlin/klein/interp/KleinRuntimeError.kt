package klein.interp

import klein.KleinError
import klein.SourceSpan

/** A fail-fast evaluation error carrying the source location it arose at. */
class KleinRuntimeError(
    override val message: String,
    override val span: SourceSpan,
) : Exception(message),
    KleinError
