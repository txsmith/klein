package klein.jsbinding

import klein.Klein
import kotlin.js.collections.JsReadonlyArray

@JsExport
fun checkContract(source: String): EnvironmentContract = EnvironmentContract(Klein.checkContract(source))

@JsExport
fun tokenize(source: String): Checked<JsReadonlyArray<Token>> = toJs(Klein.tokenize(source)) { tokens -> tokens.map(::Token).toJs() }

@JsExport
class Token internal constructor(
    token: klein.surface.Token,
) {
    val kind: String = token.kind.name
    val start: Int = token.span.start
    val end: Int = token.span.end
    val text: String? = token.text
    val indent: Int? = token.indent
}
