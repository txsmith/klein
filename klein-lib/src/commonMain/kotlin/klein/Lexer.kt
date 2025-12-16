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
    private var lastToken: Token? = null
    private val nesting = NestingContext()

    private var atLineStart: Boolean = true
    private var indent: Int = 0 // Column position of first non-whitespace char on current line

    fun tokenize(): Sequence<Token> =
        sequence {
            while (true) {
                val batch = next()
                if (batch.isEmpty()) continue
                for (token in batch) {
                    lastToken = token
                    yield(token)
                }
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
            return listOf(Token(EOF, SourceSpan.pos(pos)))
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
                val kind = nesting.determinePipeKind(lastToken?.kind)
                val token = Token(kind, SourceSpan(start, pos))
                val blockEnds = nesting.updateNesting(token, indent)
                blockEnds + listOf(token)
            }

            c in "()[]" -> {
                val (kind, length) =
                    TokenKind.matchSymbol(source, pos)
                        ?: throw LexerError("Unknown symbol", SourceSpan(start, start + 1), nesting.describeStack())
                advance(length)
                val token = Token(kind, SourceSpan(start, pos))
                val blockEnds = nesting.updateNesting(token, indent)
                blockEnds + listOf(token)
            }

            c in "+-*/%=<>!&,.;:{}@" -> {
                val (kind, length) =
                    TokenKind.matchSymbol(source, pos)
                        ?: throw LexerError("Unknown symbol", SourceSpan(start, start + 1), nesting.describeStack())
                advance(length)
                listOf(Token(kind, SourceSpan(start, pos)))
            }

            else -> {
                throw LexerError("Unexpected character: '$c'", SourceSpan(start, start + 1), nesting.describeStack())
            }
        }
    }

    private fun handleIndentation(): List<Token> {
        val indentStart = pos
        indent = consumeIndentation()
        atLineStart = false

        // Track indentation outside of parens or if we're inside a statement
        if (nesting.isIndentTrackingEnabled) {
            val span = SourceSpan(indentStart, pos)
            if (indent > nesting.currentBlockIndent && lastTokenWasBlockStarter) {
                return listOf(nesting.pushBlock(indent, span))
            } else if (indent < nesting.currentBlockIndent) {
                val blockEnds = mutableListOf<Token>()
                while (nesting.currentBlockIndent > indent) {
                    val token = nesting.popBlock(span) ?: break
                    blockEnds.add(token)
                }
                if (blockEnds.isNotEmpty()) {
                    return blockEnds
                }
            } else if (indent == nesting.currentBlockIndent) {
                // Same level: emit StatementEnd if last token allows it
                // But not if next token is a closing delimiter or continuation keyword
                val nextChar = peekNonWhitespace()
                if (lastTokenAllowsStatementEnd && !isClosingDelimiter(nextChar) && !peekIsContinuationKeyword()) {
                    return listOf(Token(STMT_END, statementEndSpan()))
                }
            }
            // else: More indented without block starter = expression continuation, no StatementEnd
        } else {
            // Inside brackets (expression context): may still need StatementEnd
            if (lastTokenAllowsStatementEnd) {
                return listOf(Token(STMT_END, statementEndSpan()))
            }
        }

        return emptyList()
    }

    private fun consumeWhitespaceAndComments() {
        while (true) {
            // Drop irrelevant whitespace
            if (nesting.isIndentTrackingEnabled) {
                // In statements, newlines are significant for determining where it ends
                consumeWhile { it.isWhitespace() && it != '\n' }
            } else {
                // In expressions, newlines aren't significant,
                // but keep tracking line indent for when a statement starts
                while (peek()?.isWhitespace() == true) {
                    if (consumeIf('\n')) {
                        indent = consumeIndentation()
                    } else {
                        advance()
                    }
                }
            }
            // Skip comments (but not the newline after them)
            if (peek() == '#') {
                consumeWhile { it != '\n' }
            } else {
                break
            }
        }
    }

    private val lastTokenAllowsStatementEnd: Boolean
        get() {
            val last = lastToken ?: return false
            return when (last.kind) {
                IDENT, INT, DOUBLE, STRING, TRUE, FALSE -> true
                RPAREN, RBRACKET, PIPE_CLOSE -> nesting.isCurrentContextBlock
                else -> false
            }
        }

    private val lastTokenWasBlockStarter: Boolean
        get() {
            val last = lastToken ?: return false
            return when (last.kind) {
                THEN, ELSE, EQ, ARROW -> true
                PIPE_OPEN -> nesting.isCurrentContextPipe
                else -> false
            }
        }

    private fun isClosingDelimiter(char: Char?): Boolean = char == '|' || char == ')' || char == ']'

    private fun statementEndSpan(): SourceSpan {
        val lastSpan = lastToken?.span
        return if (lastSpan != null) SourceSpan(lastSpan.end, lastSpan.end) else SourceSpan.pos(pos)
    }

    private fun symbol(start: Int): Token {
        val (kind, length) =
            TokenKind.matchSymbol(source, pos)
                ?: throw LexerError("Unknown symbol", SourceSpan(start, start + 1), nesting.describeStack())
        pos += length
        return Token(kind, SourceSpan(start, pos))
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
        return Token(kind, SourceSpan(start, pos), text)
    }

    private fun identOrKeyword(): Token {
        val start = pos
        val text = consumeWhile { it.isLetterOrDigit() || it == '_' }
        val span = SourceSpan(start, pos)
        val kind = TokenKind.fromKeyword(text) ?: IDENT
        return Token(kind, span, text)
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
            throw LexerError("Unterminated string", SourceSpan(start, pos), nesting.describeStack())
        }
        return Token(STRING, SourceSpan(start, pos), content.toString())
    }

    private val escapes = mapOf('\'' to '\'', '\\' to '\\', 'n' to '\n', 't' to '\t')

    private fun escapeChar(): Char {
        val c = consumeChar(escapes.keys.joinToString(""))
        if (c != null) return escapes[c]!!
        val next = peek()
        if (next == null) {
            throw LexerError("Invalid escape sequence", SourceSpan(pos - 1, pos), nesting.describeStack())
        } else {
            throw LexerError("Invalid escape sequence: \\$next", SourceSpan(pos - 1, pos + 1), nesting.describeStack())
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
            throw LexerError("Unexpected '$actual', expected '$expected'", SourceSpan(pos - 1, pos), nesting.describeStack())
        }
    }

    private fun consumeIf(char: Char): Boolean {
        if (peek() == char) {
            advance()
            return true
        }
        return false
    }

    private fun peekNonWhitespace(): Char? {
        var i = pos
        while (i < source.length && source[i].isWhitespace()) {
            i++
        }
        return if (i < source.length) source[i] else null
    }

    private fun peekIsContinuationKeyword(): Boolean {
        var i = pos
        while (i < source.length && source[i].isWhitespace()) {
            i++
        }
        if (i >= source.length || !source[i].isLetter()) return false

        val start = i
        while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_')) {
            i++
        }
        val word = source.substring(start, i)
        return TokenKind.fromKeyword(word)?.continuesExpression == true
    }

    private fun consumeIndentation(): Int {
        var col = 0
        while (pos < source.length && source[pos] == ' ') {
            col++
            advance()
        }
        if (pos < source.length && source[pos] == '\t') {
            throw LexerError("Tabs are not allowed for indentation", SourceSpan(pos, pos + 1), nesting.describeStack())
        }
        return col
    }
}

/**
 * Tracks nesting context for parens, brackets, pipes, and indentation blocks.
 *
 * Closing delimiters pop until matching opener, emitting BLOCK_END for any
 * indent blocks encountered. Unclosed intermediate delimiters are discarded.
 *
 * Newlines are significant in statement contexts (top-level, pipes, indent blocks)
 * but ignored in expression contexts (parens, brackets).
 */
class NestingContext {
    sealed class Item(
        val needsIndentTracking: Boolean,
    ) {
        data class BlockStart(
            val indent: Int,
        ) : Item(needsIndentTracking = true)

        data object Paren : Item(needsIndentTracking = false)

        data object Bracket : Item(needsIndentTracking = false)

        data class Pipe(
            val anchorIndent: Int?, // Source line indent where pipe opened (for pipes inside parens)
        ) : Item(needsIndentTracking = true)
    }

    private val stack: ArrayDeque<Item> = ArrayDeque<Item>()
    private var lastPipeWasOpen: Boolean = false

    init {
        stack.addFirst(Item.BlockStart(0))
    }

    /** True in statement contexts where newlines can end statements. */
    val isIndentTrackingEnabled: Boolean
        get() = stack.first().needsIndentTracking

    /** True if anywhere inside parens or brackets (expression context). */
    val inParenOrBracket: Boolean
        get() = stack.any { it is Item.Paren || it is Item.Bracket }

    /** True if the innermost context is an indent block. */
    val isCurrentContextBlock: Boolean
        get() = stack.first() is Item.BlockStart

    /** True if the innermost context is a pipe/lambda. */
    val isCurrentContextPipe: Boolean
        get() = stack.first() is Item.Pipe

    /** True if any pipe is open (used to determine if `|` opens or closes). */
    val hasPipeOnStack: Boolean
        get() = stack.any { it is Item.Pipe }

    /** Number of indent blocks currently open. */
    val blockDepth: Int
        get() = stack.count { it is Item.BlockStart }

    /**
     * The indent level that dedents are measured against.
     * For pipes inside parens, this is the line where the pipe opened.
     */
    val currentBlockIndent: Int
        get() {
            for (item in stack) {
                when (item) {
                    is Item.BlockStart -> return item.indent
                    is Item.Pipe -> item.anchorIndent?.let { return it }
                    else -> continue
                }
            }
            return 0
        }

    /** Opens a new indent block, returns BLOCK_START token. */
    fun pushBlock(
        indent: Int,
        span: SourceSpan,
    ): Token {
        stack.addFirst(Item.BlockStart(indent))
        return Token(BLOCK_START, span)
    }

    /** Pops an indent block if possible, returns BLOCK_END token or null. */
    fun popBlock(span: SourceSpan): Token? {
        val top = stack.firstOrNull()
        return if (top is Item.BlockStart && blockDepth > 1) {
            stack.removeFirst()
            Token(BLOCK_END, span)
        } else {
            null
        }
    }

    /**
     * Determines whether a `|` should be PIPE_OPEN or PIPE_CLOSE.
     * Opens if no pipe on stack or previous token can't end an expression.
     * Consecutive pipes (||) follow the same direction as the first.
     */
    fun determinePipeKind(lastTokenKind: TokenKind?): TokenKind {
        val lastTokenCanEndExpr =
            when (lastTokenKind) {
                IDENT, INT, DOUBLE, STRING, TRUE, FALSE, RPAREN, RBRACKET, BLOCK_END, STMT_END, PIPE_OPEN, PIPE_CLOSE -> true
                else -> false
            }
        val isOpeningPipe =
            when {
                lastTokenKind == PIPE_OPEN || lastTokenKind == PIPE_CLOSE -> lastPipeWasOpen
                hasPipeOnStack && lastTokenCanEndExpr -> false
                else -> true
            }
        lastPipeWasOpen = isOpeningPipe
        return if (isOpeningPipe) PIPE_OPEN else PIPE_CLOSE
    }

    /**
     * Updates nesting for delimiter tokens (parens, brackets, pipes).
     * Returns BLOCK_END tokens for any indent blocks closed by the delimiter.
     */
    fun updateNesting(
        token: Token,
        lineIndent: Int,
    ): List<Token> =
        when (token.kind) {
            LPAREN -> {
                stack.addFirst(Item.Paren)
                emptyList()
            }
            RPAREN -> popUntilMatching<Item.Paren>(token.span)
            LBRACKET -> {
                stack.addFirst(Item.Bracket)
                emptyList()
            }
            RBRACKET -> popUntilMatching<Item.Bracket>(token.span)
            PIPE_OPEN -> {
                val anchorIndent = if (inParenOrBracket) lineIndent else null
                stack.addFirst(Item.Pipe(anchorIndent))
                emptyList()
            }
            PIPE_CLOSE -> popUntilMatching<Item.Pipe>(token.span)
            else -> emptyList()
        }

    /** Returns a description of the nesting stack for error messages. */
    fun describeStack(): List<String> =
        stack.map { item ->
            when (item) {
                is Item.BlockStart -> "Block(indent=${item.indent})"
                is Item.Paren -> "Paren"
                is Item.Bracket -> "Bracket"
                is Item.Pipe -> "Pipe(anchor=${item.anchorIndent})"
            }
        }

    private inline fun <reified T : Item> popUntilMatching(span: SourceSpan): List<Token> {
        if (stack.none { it is T }) {
            return emptyList()
        }

        val blockEnds = mutableListOf<Token>()
        while (true) {
            when (val top = stack.removeFirst()) {
                is T -> return blockEnds
                is Item.BlockStart -> blockEnds.add(Token(BLOCK_END, span))
                is Item.Paren, is Item.Bracket, is Item.Pipe -> {}
            }
        }
    }
}
