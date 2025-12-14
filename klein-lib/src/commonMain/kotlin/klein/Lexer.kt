package klein

sealed class Token {
    abstract val span: SourceSpan

    data class Number(val text: String, override val span: SourceSpan) : Token()

    data class Ident(val name: String, override val span: SourceSpan) : Token()

    data class Str(val value: String, override val span: SourceSpan) : Token()

    data class Symbol(val char: Char, override val span: SourceSpan) : Token()

    data class Keyword(val kind: KeywordKind, override val span: SourceSpan) : Token()

    data class StatementEnd(override val span: SourceSpan) : Token()

    data class Eof(override val span: SourceSpan) : Token()
}

enum class KeywordKind {
    If,
    Then,
    Else,
    Fun,
}

class Lexer(private val source: String) {
    private var pos: Int = 0
    val tokens = mutableListOf<Token>()
    private val whitespaceContext = WhitespaceContext()

    fun tokenize(): List<Token> {
        while (true) {
            val token = next()
            tokens.add(token)
            if (token is Token.Eof) break
        }
        return tokens
    }

    private fun next(): Token {
        skipWhitespaceAndComments()

        if (pos >= source.length) {
            return Token.Eof(SourceSpan.pos(pos))
        }

        val start = pos
        val c = source[pos]

        return when {
            c == '\n' && whitespaceContext.isStatement -> {
                pos++
                if (canStatementEndHere()) {
                    Token.StatementEnd(SourceSpan(start, pos))
                } else {
                    next()
                }
            }
            c.isDigit() -> number()
            c.isLetter() || c == '_' -> ident()
            c == '\'' -> string()
            c in "+-*/%()=<>!&|,.;:{}[]@" -> {
                pos++
                whitespaceContext.handleChar(c, pos)
                Token.Symbol(c, SourceSpan(start, pos))
            }
            else -> throw LexerError("Unexpected character: '$c'", SourceSpan(start, start + 1))
        }
    }

    private fun skipWhitespaceAndComments() {
        while (true) {
            // Drop irrelevant whitespace
            if (whitespaceContext.isStatement) {
                // In statements, newlines are significant for determining where it ends
                consumeWhile { it.isWhitespace() && it != '\n' }
            } else {
                // In expressions, newlines aren't significant
                consumeWhile { it.isWhitespace() }
            }
            // Skip comments (but not the newline after them)
            if (peek() == '#') {
                consumeWhile { it != '\n' }
            } else {
                break
            }
        }
    }

    private fun canStatementEndHere(): Boolean {
        val last = tokens.lastOrNull() ?: return false
        return when (last) {
            is Token.Ident -> true
            is Token.Number -> true
            is Token.Str -> true
            is Token.Symbol -> whitespaceContext.lastClosed == last.char
            is Token.Keyword -> false
            is Token.StatementEnd -> false
            is Token.Eof -> false
        }
    }

    private fun number(): Token {
        val start = pos
        consumeWhile { it.isDigit() }
        if (consumeChar { it == '.' }) {
            consumeWhile { it.isDigit() }
        }
        return Token.Number(source.substring(start, pos), SourceSpan(start, pos))
    }

    private fun ident(): Token {
        val start = pos
        val text = consumeWhile { it.isLetterOrDigit() || it == '_' }
        val span = SourceSpan(start, pos)
        return when (text) {
            "if" -> Token.Keyword(KeywordKind.If, span)
            "then" -> Token.Keyword(KeywordKind.Then, span)
            "else" -> Token.Keyword(KeywordKind.Else, span)
            "fun" -> Token.Keyword(KeywordKind.Fun, span)
            else -> Token.Ident(text, span)
        }
    }

    private fun string(): Token {
        val start = pos
        pos++ // consume opening quote
        val content = StringBuilder()
        while (peekIfNot("'")) {
            content.append(consumeWhileNot("'\\"))
            if (consumeChar("\\") != null) {
                content.append(escapeChar())
            }
        }
        if (consumeChar("'") == null) {
            throw LexerError("Unterminated string", SourceSpan(start, pos))
        }
        return Token.Str(content.toString(), SourceSpan(start, pos))
    }

    private val escapes = mapOf('\'' to '\'', '\\' to '\\', 'n' to '\n', 't' to '\t')

    private fun escapeChar(): Char {
        val c = consumeChar(escapes.keys.joinToString(""))
        if (c != null) return escapes[c]!!
        val next = peek()
        if (next == null) {
            throw LexerError("Invalid escape sequence", SourceSpan(pos - 1, pos))
        } else {
            throw LexerError("Invalid escape sequence: \\$next", SourceSpan(pos - 1, pos + 1))
        }
    }

    private fun consumeWhile(chars: String): String = consumeWhile { it in chars }

    private fun consumeWhileNot(chars: String): String = consumeWhile { it !in chars }

    private inline fun consumeWhile(predicate: (Char) -> Boolean): String {
        val start = pos
        while (pos < source.length && predicate(source[pos])) {
            pos++
        }
        return source.substring(start, pos)
    }

    private fun consumeChar(chars: String): Char? {
        val c = peek()
        if (c != null && c in chars) {
            pos++
            return c
        }
        return null
    }

    private inline fun consumeChar(predicate: (Char) -> Boolean): Boolean {
        if (pos < source.length && predicate(source[pos])) {
            pos++
            return true
        }
        return false
    }

    private fun peekIf(chars: String): Boolean = peekIf { it in chars }

    private fun peekIfNot(chars: String): Boolean = peekIf { it !in chars }

    private inline fun peekIf(predicate: (Char) -> Boolean): Boolean {
        val c = peek()
        return c != null && predicate(c)
    }

    private fun peek(): Char? {
        val index = pos
        return if (index < source.length) source[index] else null
    }
}

class LexerError(message: String, val span: SourceSpan) : Exception(message)

/**
 * Tracks nested bracket contexts to determine newline handling.
 *
 * In statement contexts (top-level, braces, pipes), newlines become StatementEnd tokens. In
 * expression contexts (parens, brackets), newlines are ignored as whitespace.
 *
 * Example: `foo(\n a,\n b\n)` - newlines inside parens are ignored Example: `{\n x = 1\n y =
 * 2\n}` - newlines inside braces become statement separators
 */
class WhitespaceContext() {
    private enum class Item(
        val openingChar: Char?,
        val closingChar: Char?,
        val isStatement: Boolean,
    ) {
        Brace('{', '}', true),
        Pipe('|', '|', true),
        Paren('(', ')', false),
        Bracket('[', ']', false),
    }

    private val stack: ArrayDeque<Item> = ArrayDeque<Item>()

    val isStatement: Boolean
        get() = stack.isEmpty() || stack.first().isStatement

    var lastClosed: Char? = null

    fun handleChar(char: Char, pos: Int) {
        if (char == '|') {
            if (stack.firstOrNull() == Item.Pipe) {
                lastClosed = stack.removeFirst().closingChar
            } else {
                stack.addFirst(Item.Pipe)
            }
            return
        }

        fromOpeningChar(char)?.let {
            stack.addFirst(it)
            return
        }

        fromClosingChar(char)?.let { expected ->
            val top = stack.firstOrNull()
            if (top == expected) {
                lastClosed = stack.removeFirst().closingChar
            } else {
                val expectedChar = top?.closingChar
                val msg =
                    if (expectedChar != null) {
                        "Unexpected '$char', expected '$expectedChar'"
                    } else {
                        "Unexpected '$char'"
                    }
                throw LexerError(msg, SourceSpan(pos - 1, pos))
            }
        }
    }

    private fun fromOpeningChar(char: Char): Item? = byOpeningChar[char]

    private fun fromClosingChar(char: Char): Item? = byClosingChar[char]

    companion object {
        private val byOpeningChar =
            Item.entries.filter { it.openingChar != null }.associateBy { it.openingChar }
        private val byClosingChar =
            Item.entries.filter { it.closingChar != null }.associateBy { it.closingChar }
        private val closingChars = byClosingChar.keys

        fun isClosingChar(char: Char): Boolean = char in closingChars
    }
}
