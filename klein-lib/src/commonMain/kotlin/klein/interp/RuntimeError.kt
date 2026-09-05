package klein.interp

import klein.Diagnostic
import klein.SourceSpan

data class RuntimeError(
    override val message: String,
    override val span: SourceSpan,
) : Diagnostic

internal class Abort(
    val diagnostic: RuntimeError,
) : Exception() {
    override val message: String get() = diagnostic.message
}

internal fun runtimeError(
    message: String,
    span: SourceSpan,
): Nothing = throw Abort(RuntimeError(message, span))
