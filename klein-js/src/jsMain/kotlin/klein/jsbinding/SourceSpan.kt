package klein.jsbinding

import klein.SourceSpan

@JsExport
fun formatInSource(
    start: Int,
    end: Int,
    source: String,
    contextLines: Int,
    message: String?,
): String = SourceSpan(start, end).formatInSource(source, contextLines, message)
