package klein.host

internal class MalformedJson(
    override val message: String,
) : Exception(message)

private fun malformed(message: String): Nothing = throw MalformedJson(message)

internal sealed interface Json {
    data class JStr(val value: String) : Json

    data class JNum(val value: Double) : Json

    data class JBool(val value: Boolean) : Json

    data object JNull : Json

    data class JArr(val items: List<Json>) : Json

    data class JObj(val fields: LinkedHashMap<String, Json>) : Json
}

internal fun Json.JObj.expectField(name: String, owner: String): Json {
    val value = fields[name]
    if (value == null) malformed("$owner is missing its \"$name\" field")
    return value
}

internal fun Json.JObj.expectOnly(owner: String, vararg names: String) {
    val allowed = names.toSet()
    val unexpected = fields.keys.firstOrNull { it !in allowed }
    if (unexpected != null) malformed("unexpected field \"$unexpected\" in $owner")
}

internal fun StringBuilder.writeNumber(value: Double) {
    if (value.isFinite()) {
        // Kotlin/JS prints -0.0 as "0"; spell it out so the sign survives on every platform.
        if (value == 0.0 && value.toRawBits() != 0L) append("-0.0") else append(value)
    } else {
        val bits = value.toRawBits().toULong().toString(16).padStart(16, '0')
        append("{\"bits\":\"")
        append(bits)
        append("\"}")
    }
}

internal fun StringBuilder.writeText(value: String) {
    append('"')
    for (character in value) {
        when {
            character == '"' -> append("\\\"")
            character == '\\' -> append("\\\\")
            character == '\n' -> append("\\n")
            character == '\r' -> append("\\r")
            character == '\t' -> append("\\t")
            character < ' ' -> {
                append("\\u")
                append(character.code.toString(16).padStart(4, '0'))
            }
            else -> append(character)
        }
    }
    append('"')
}

internal class JsonReader(
    private val text: String,
) {
    private var position = 0

    fun readDocument(): Json {
        val value = readValue()
        skipWhitespace()
        if (position != text.length) malformed("unexpected trailing characters after the document, starting at offset $position")
        return value
    }

    private fun readValue(): Json {
        skipWhitespace()
        val character = peek()
        return when {
            character == '{' -> readObject()
            character == '[' -> readArray()
            character == '"' -> Json.JStr(readString())
            character == 't' || character == 'f' || character == 'n' -> readKeyword()
            character == '-' || character in '0'..'9' -> readNumber()
            else -> malformed("not valid JSON: unexpected character '$character' at offset $position")
        }
    }

    private fun readObject(): Json {
        position++
        val fields = LinkedHashMap<String, Json>()
        skipWhitespace()
        if (peek() == '}') {
            position++
            return Json.JObj(fields)
        }
        while (true) {
            skipWhitespace()
            val key = readString()
            if (fields.containsKey(key)) malformed("duplicate field \"$key\" in an object")
            skipWhitespace()
            if (peek() != ':') malformed("expected ':' after an object key at offset $position")
            position++
            fields[key] = readValue()
            skipWhitespace()
            when (peek()) {
                ',' -> position++
                '}' -> {
                    position++
                    return Json.JObj(fields)
                }
                else -> malformed("expected ',' or '}' in an object at offset $position")
            }
        }
    }

    private fun readArray(): Json {
        position++
        val items = mutableListOf<Json>()
        skipWhitespace()
        if (peek() == ']') {
            position++
            return Json.JArr(items)
        }
        while (true) {
            items.add(readValue())
            skipWhitespace()
            when (peek()) {
                ',' -> position++
                ']' -> {
                    position++
                    return Json.JArr(items)
                }
                else -> malformed("expected ',' or ']' in an array at offset $position")
            }
        }
    }

    private fun readString(): String {
        if (peek() != '"') malformed("expected a string at offset $position")
        position++
        val out = StringBuilder()
        while (true) {
            if (position >= text.length) malformed("the text ends early inside a string")
            val character = text[position++]
            when {
                character == '"' -> return out.toString()
                character == '\\' -> out.append(readEscape())
                character < ' ' -> malformed("a raw control character inside a string at offset ${position - 1}")
                else -> out.append(character)
            }
        }
    }

    private fun readEscape(): Char {
        if (position >= text.length) malformed("the text ends early inside a string escape")
        return when (val character = text[position++]) {
            '"' -> '"'
            '\\' -> '\\'
            '/' -> '/'
            'b' -> '\b'
            'f' -> 12.toChar()
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> readUnicodeEscape()
            else -> malformed("unknown escape '\\$character' in a string at offset ${position - 2}")
        }
    }

    private fun readUnicodeEscape(): Char {
        if (position + 4 > text.length) malformed("the text ends early inside a string escape")
        val hex = text.substring(position, position + 4)
        val code = hex.toIntOrNull(16)
        if (code == null) malformed("a \\u escape needs four hex digits, found \"$hex\"")
        position += 4
        return code.toChar()
    }

    private fun readKeyword(): Json =
        when {
            skipsOver("true") -> Json.JBool(true)
            skipsOver("false") -> Json.JBool(false)
            skipsOver("null") -> Json.JNull
            else -> malformed("not valid JSON: unexpected character '${text[position]}' at offset $position")
        }

    private fun skipsOver(keyword: String): Boolean {
        if (!text.startsWith(keyword, position)) return false
        position += keyword.length
        return true
    }

    private fun readNumber(): Json {
        val start = position
        if (peek() == '-') position++
        if (peek() == '0') position++ else readDigits()
        if (position < text.length && text[position] == '.') {
            position++
            readDigits()
        }
        if (position < text.length && (text[position] == 'e' || text[position] == 'E')) {
            position++
            if (position < text.length && (text[position] == '+' || text[position] == '-')) position++
            readDigits()
        }
        val token = text.substring(start, position)
        val value = token.toDoubleOrNull()
        if (value == null) malformed("not valid JSON: unreadable number \"$token\" at offset $start")
        return Json.JNum(value)
    }

    private fun readDigits() {
        if (peek() !in '0'..'9') malformed("expected a digit at offset $position")
        while (position < text.length && text[position] in '0'..'9') position++
    }

    private fun peek(): Char {
        if (position >= text.length) malformed("the text ends early at offset $position")
        return text[position]
    }

    private fun skipWhitespace() {
        while (position < text.length && (text[position] == ' ' || text[position] == '\t' || text[position] == '\n' || text[position] == '\r')) {
            position++
        }
    }
}
