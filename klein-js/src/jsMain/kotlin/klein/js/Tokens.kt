package klein.js

import klein.Klein
import kotlin.js.collections.JsReadonlyArray

@JsExport
class Token internal constructor(
    val kind: String,
    val start: Int,
    val end: Int,
)

@JsExport
fun tokenize(source: String): Checked<JsReadonlyArray<Token>> =
    toJs(Klein.tokenize(source)) { tokens -> tokens.map { Token(it.kind.name, it.span.start, it.span.end) }.frozen() }
