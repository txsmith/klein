package klein

import klein.TokenKind.*

class LexerError(
    message: String,
    val span: SourceSpan,
    val nestingStack: List<String> = emptyList(),
) : Exception(message)

class Lexer(
    private val source: String,
) {
    private var pos: Int = 0
    private var atLineStart: Boolean = true
    private var indent: Int = 0 // Column position of first non-whitespace char on current line
    private var nextTokenIndent: Int? = 0 // Indent to stamp on next token (null = same line)

    private fun token(
        kind: TokenKind,
        span: SourceSpan,
        text: String? = null,
    ): Token {
        val token = Token(kind, span, text, nextTokenIndent)
        nextTokenIndent = null
        return token
    }

    fun tokenize(): Sequence<Token> =
        sequence {
            while (true) {
                val batch = next()
                if (batch.isEmpty()) continue
                yieldAll(batch)
                if (batch.last().kind == EOF) break
            }
        }

    private fun next(): List<Token> {
        if (atLineStart) {
            val indentTokens = handleIndentation()
            if (indentTokens.isNotEmpty()) {
                return indentTokens
            }
        }

        // Beyond all relevant indentation, drop whitespace
        consumeWhitespaceAndComments()

        if (pos >= source.length) {
            // EOF always gets indent=0 so it naturally terminates any block
            return listOf(Token(EOF, SourceSpan.pos(pos), indent = 0))
        }

        val start = pos
        val c = source[pos]

        return when {
            c == '\n' -> {
                advance()
                atLineStart = true
                emptyList()
            }

            c.isDigit() -> {
                listOf(number())
            }

            c.isLetter() || c == '_' -> {
                listOf(identOrKeyword())
            }

            c == '\'' -> {
                listOf(string())
            }

            c == '|' -> {
                advance()
                listOf(token(PIPE, SourceSpan(start, pos)))
            }

            c in "()[]{}" -> {
                val (kind, length) =
                    TokenKind.matchSymbol(source, pos)
                        ?: throw LexerError("Unknown symbol", SourceSpan(start, start + 1))
                advance(length)
                listOf(token(kind, SourceSpan(start, pos)))
            }

            c == '-' -> {
                advance()
                // Check if -> arrow
                if (peek() == '>') {
                    advance()
                    listOf(token(ARROW, SourceSpan(start, pos)))
                } else {
                    // MINUS_TIGHT if no space after, MINUS otherwise
                    val kind = if (peek()?.isWhitespace() != false) MINUS else MINUS_TIGHT
                    listOf(token(kind, SourceSpan(start, pos)))
                }
            }

            c in "+*/%=<>!&,.;:@" -> {
                val (kind, length) =
                    TokenKind.matchSymbol(source, pos)
                        ?: throw LexerError("Unknown symbol", SourceSpan(start, start + 1))
                advance(length)
                listOf(token(kind, SourceSpan(start, pos)))
            }

            else -> {
                throw LexerError("Unexpected character: '$c'", SourceSpan(start, start + 1))
            }
        }
    }

    private fun handleIndentation(): List<Token> {
        indent = consumeIndentation()
        atLineStart = false

        // Empty lines and comment-only lines should be skipped for indentation
        if (peek() == '\n' || peek() == '#') {
            return emptyList()
        }

        // Set indent for next token (first token on this line)
        nextTokenIndent = indent

        return emptyList()
    }

    private fun consumeWhitespaceAndComments() {
        while (true) {
            // Never consume newlines - they trigger handleIndentation via next()
            consumeWhile { it.isWhitespace() && it != '\n' }
            // Skip comments (but not the newline after them)
            if (peek() == '#') {
                consumeWhile { it != '\n' }
            } else {
                break
            }
        }
    }

    private fun number(): Token {
        val start = pos
        consumeWhile { it.isDigit() }
        // Only consume decimal point if followed by a digit (not for ranges like 1..10)
        val isDouble = peek() == '.' && peekAt(1)?.isDigit() == true
        if (isDouble) {
            advance()
            consumeWhile { it.isDigit() }
        }
        val text = source.substring(start, pos)
        val kind = if (isDouble) DOUBLE else INT
        return token(kind, SourceSpan(start, pos), text)
    }

    private fun identOrKeyword(): Token {
        val start = pos
        val text = consumeWhile { it.isLetterOrDigit() || it == '_' }
        val span = SourceSpan(start, pos)
        val kind = TokenKind.fromKeyword(text) ?: IDENT
        return token(kind, span, text)
    }

    private fun string(): Token {
        val start = pos
        advance('\'')
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
        return token(STRING, SourceSpan(start, pos), content.toString())
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
            advance()
        }
        return source.substring(start, pos)
    }

    private fun consumeChar(chars: String): Char? {
        val c = peek()
        if (c != null && c in chars) {
            advance()
            return c
        }
        return null
    }

    private inline fun consumeChar(predicate: (Char) -> Boolean): Boolean {
        if (pos < source.length && predicate(source[pos])) {
            advance()
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

    private fun peek(): Char? = peekAt(0)

    private fun peekAt(offset: Int): Char? {
        val index = pos + offset
        return if (index < source.length) source[index] else null
    }

    private fun advance(): Char? = if (pos < source.length) source[pos++] else null

    private fun advance(count: Int) {
        pos += count
    }

    private fun advance(expected: Char) {
        val actual = advance()
        if (actual !=
            expected
        ) {
            throw LexerError("Unexpected '$actual', expected '$expected'", SourceSpan(pos - 1, pos))
        }
    }

    private fun consumeIndentation(): Int {
        var col = 0
        while (pos < source.length && source[pos] == ' ') {
            col++
            advance()
        }
        if (pos < source.length && source[pos] == '\t') {
            throw LexerError("Tabs are not allowed for indentation", SourceSpan(pos, pos + 1))
        }
        return col
    }
}
