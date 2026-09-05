package klein.surface

import klein.KleinError
import klein.SourceSpan

data class SyntaxError(
    override val message: String,
    override val span: SourceSpan,
) : KleinError

internal class Abort(
    val diagnostic: SyntaxError,
) : Exception() {
    override val message: String get() = diagnostic.message
}

internal fun syntaxError(
    message: String,
    span: SourceSpan,
): Nothing = throw Abort(SyntaxError(message, span))
