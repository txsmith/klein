package klein

import kotlinx.serialization.Serializable

@Serializable
data class SourceSpan(
    val start: Int,
    val end: Int,
) {
    operator fun plus(other: SourceSpan): SourceSpan = SourceSpan(start, other.end)

    companion object {
        val zero: SourceSpan = SourceSpan(0, 0)

        fun pos(pos: Int): SourceSpan = SourceSpan(pos, pos)
    }

    fun formatInSource(
        source: String,
        contextLines: Int = 2,
        message: String? = null,
    ): String =
        buildString {
            val lines = source.lines()
            val errorLineStart = findLineNumber(source, start)
            val errorLineEnd = findLineNumber(source, end)
            val isMultiLine = errorLineStart != errorLineEnd

            val colStart = start - findLineStart(source, start)
            val colEnd =
                if (isMultiLine) {
                    lines.getOrNull(errorLineEnd - 1)?.length ?: 0
                } else {
                    minOf(end - findLineStart(source, start), lines.getOrNull(errorLineStart - 1)?.length ?: 0)
                }

            val startLine = maxOf(1, errorLineStart - contextLines)
            val endLine = minOf(lines.size, errorLineEnd + contextLines)
            val maxLineNum = endLine
            val gutterWidth = maxLineNum.toString().length

            fun gutter(n: Int?) = if (n != null) n.toString().padStart(gutterWidth) else " ".repeat(gutterWidth)

            fun StringBuilder.appendMessageLines(msg: String?, gutterStr: String) {
                appendLine()
                if (msg != null) {
                    for (line in msg.lines()) {
                        appendLine("$gutterStr  |   $line")
                    }
                }
            }

            appendLine("${gutter(null)}  |")
            for (lineNum in startLine..endLine) {
                val line = lines.getOrElse(lineNum - 1) { "" }
                val marker = if (lineNum in errorLineStart..errorLineEnd) ">" else " "
                appendLine("${gutter(lineNum)} $marker| $line")

                if (isMultiLine && lineNum == errorLineEnd) {
                    append("${gutter(null)}  | ")
                    append("^".repeat(maxOf(1, colEnd)))
                    appendMessageLines(message, gutter(null))
                } else if (!isMultiLine && lineNum == errorLineStart) {
                    append("${gutter(null)}  | ")
                    append(" ".repeat(colStart))
                    append("^".repeat(maxOf(1, colEnd - colStart)))
                    appendMessageLines(message, gutter(null))
                }
            }
        }

    private fun findLineNumber(
        source: String,
        pos: Int,
    ): Int {
        var lineNumber = 1
        for (i in 0 until minOf(pos, source.length)) {
            if (source[i] == '\n') lineNumber++
        }
        return lineNumber
    }

    private fun findLineStart(
        source: String,
        pos: Int,
    ): Int {
        var lineStart = 0
        for (i in 0 until minOf(pos, source.length)) {
            if (source[i] == '\n') lineStart = i + 1
        }
        return lineStart
    }
}
