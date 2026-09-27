package klein.jsbinding

import klein.SourceSpan
import kotlin.js.collections.JsReadonlyArray
import kotlin.js.collections.toList

@JsExport
class Checked<out T> internal constructor(
    val value: T?,
    val diagnostics: JsReadonlyArray<Diagnostic>?,
)

@JsExport
class Diagnostic(
    val message: String,
    val start: Int,
    val end: Int,
) {
    internal var diagnostic: klein.Diagnostic? = null
}

internal fun <T, R> toJs(
    checked: klein.Checked<T>,
    transform: (T) -> R,
): Checked<R> =
    when (checked) {
        is klein.Checked.Accepted -> Checked(transform(checked.value), null)
        is klein.Checked.Rejected -> Checked(null, toJs(checked.diagnostics))
    }

internal fun toJs(diagnostics: List<klein.Diagnostic>): JsReadonlyArray<Diagnostic> = diagnostics.map(::toJs).toJs()

internal fun toJs(diagnostic: klein.Diagnostic): Diagnostic =
    Diagnostic(diagnostic.message, diagnostic.span.start, diagnostic.span.end).also { it.diagnostic = diagnostic }

internal fun fromJs(diagnostics: JsReadonlyArray<Diagnostic>): List<klein.Diagnostic> = diagnostics.toList().map(::fromJs)

private fun fromJs(diagnostic: Diagnostic): klein.Diagnostic =
    diagnostic.diagnostic ?: object : klein.Diagnostic {
        override val message = diagnostic.message
        override val span = SourceSpan(diagnostic.start, diagnostic.end)
    }

internal fun <T> List<T>.toJs(): JsReadonlyArray<T> = toTypedArray().unsafeCast<JsReadonlyArray<T>>()
