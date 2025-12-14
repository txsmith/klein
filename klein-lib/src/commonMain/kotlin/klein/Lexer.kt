package klein

sealed class Token {
    abstract val span: SourceSpan

    data class Number(
        val text: String,
        override val span: SourceSpan,
    ) : Token()

    data class Ident(
        val name: String,
        override val span: SourceSpan,
    ) : Token()

    data class Str(
        val value: String,
        override val span: SourceSpan,
    ) : Token()

    data class Symbol(
        val char: Char,
        override val span: SourceSpan,
    ) : Token()

    data class Keyword(
        val kind: KeywordKind,
        override val span: SourceSpan,
    ) : Token()

    data class StatementEnd(
        override val span: SourceSpan,
    ) : Token()

    data class Eof(
        override val span: SourceSpan,
    ) : Token()
}

enum class KeywordKind {
    If,
    Then,
    Else,
    Fun,
}

/**
 * Determines how newlines are treated. In 'statement' mode (at top level, in blocks and lambdas),
 * newline signifies a new statement. In expr mode, newlines can be freely introduced and parsed as
 * part of the expression.
 */
sealed class WhitespaceContext {
    abstract val span: SourceSpan

    val isStatement: Boolean = this is TopLevel || this is Brace || this is Pipe

    val isExpr: Boolean = !isStatement

    data class TopLevel(
        override val span: SourceSpan,
    ) : WhitespaceContext() {
        companion object {
            val zero: TopLevel = TopLevel(SourceSpan.zero)
        }
    }

    data class Brace(
        override val span: SourceSpan,
    ) : WhitespaceContext()

    data class Pipe(
        override val span: SourceSpan,
    ) : WhitespaceContext()

    data class Paren(
        override val span: SourceSpan,
    ) : WhitespaceContext()

    data class Bracket(
        override val span: SourceSpan,
    ) : WhitespaceContext()
}

class Lexer(
    private val source: String,
) {
    private var pos: Int = 0
    val tokens = mutableListOf<Token>()

    private val whitespaceContext: ArrayDeque<WhitespaceContext> =
        ArrayDeque<WhitespaceContext>().apply { addFirst(WhitespaceContext.TopLevel.zero) }

    private fun isInPositionStatement(): Boolean = whitespaceContext.first().isStatement

    private fun canStatementEndHere(): Boolean {
        val last = tokens.lastOrNull() ?: return false
        return when (last) {
            is Token.Ident -> true
            is Token.Number -> true
            is Token.Str -> true
            is Token.Symbol -> last.char in ")]}"
            is Token.Keyword -> false
            is Token.StatementEnd -> false
            is Token.Eof -> false
        }
    }

    fun tokenize(): List<Token> {
        while (true) {
            val token = next()
            tokens.add(token)
            if (token is Token.Eof) break
        }
        return tokens
    }

    private fun next(): Token {
        if (pos >= source.length) {
            return Token.Eof(SourceSpan.pos(pos))
        }

        // Drop irrelevant whitespace
        if (isInPositionStatement()) {
            consumeWhile { it.isWhitespace() && it != '\n' }
        } else {
            consumeWhile { it.isWhitespace() }
        }

        val start = pos
        val c = source[pos]

        return when {
            c == '\n' && isInPositionStatement() -> {
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
                Token.Symbol(c, SourceSpan(start, pos))
            }
            else -> throw LexerError("Unexpected character: '$c'", SourceSpan(start, start + 1))
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

    private fun peekIf(
        chars: String,
        offset: Int = 0,
    ): Boolean = peekIf(offset) { it in chars }

    private fun peekIfNot(
        chars: String,
        offset: Int = 0,
    ): Boolean = peekIf(offset) { it !in chars }

    private inline fun peekIf(
        offset: Int = 0,
        predicate: (Char) -> Boolean,
    ): Boolean {
        val c = peek(offset)
        return c != null && predicate(c)
    }

    private fun peek(offset: Int = 0): Char? {
        val index = pos + offset
        return if (index < source.length) source[index] else null
    }
}

class LexerError(
    message: String,
    val span: SourceSpan,
) : Exception(message)
