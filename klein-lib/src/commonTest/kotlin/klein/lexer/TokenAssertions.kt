package klein.lexer

import klein.Lexer
import klein.LexerError
import klein.Token
import klein.TokenKind
import klein.TokenKind.*
import kotlin.test.assertEquals
import kotlin.test.fail

private fun LexerError.formatWithSource(source: String): String = span.formatInSource(source, contextLines = 5, message = message)

sealed class ExpectedToken {
    abstract val span: klein.SourceSpan?

    fun matches(other: ExpectedToken): Boolean {
        if (this::class != other::class) return false
        if (span != null && span != other.span) return false

        return when (this) {
            is Ident -> other is Ident && name == other.name
            is Number -> other is Number && text == other.text
            is Str -> other is Str && value == other.value
            is Symbol -> other is Symbol && text == other.text
            is Keyword -> other is Keyword && kind == other.kind
            is StatementEnd -> other is StatementEnd
            is BlockStart -> other is BlockStart
            is BlockEnd -> other is BlockEnd
            is Eof -> other is Eof
            is Error -> other is Error && expectedMessage == other.expectedMessage
        }
    }

    data class Ident(
        val name: String,
        override val span: klein.SourceSpan? = null,
    ) : ExpectedToken()

    data class Number(
        val text: String,
        override val span: klein.SourceSpan? = null,
    ) : ExpectedToken()

    data class Str(
        val value: String,
        override val span: klein.SourceSpan? = null,
    ) : ExpectedToken()

    data class Symbol(
        val text: String,
        override val span: klein.SourceSpan? = null,
    ) : ExpectedToken()

    data class Keyword(
        val kind: TokenKind,
        override val span: klein.SourceSpan? = null,
    ) : ExpectedToken()

    data class StatementEnd(
        override val span: klein.SourceSpan? = null,
    ) : ExpectedToken()

    data class BlockStart(
        override val span: klein.SourceSpan? = null,
    ) : ExpectedToken()

    data class BlockEnd(
        override val span: klein.SourceSpan? = null,
    ) : ExpectedToken()

    data class Eof(
        override val span: klein.SourceSpan? = null,
    ) : ExpectedToken()

    data class Error(
        val expectedMessage: String,
    ) : ExpectedToken() {
        override val span: klein.SourceSpan? = null
    }
}

fun span(
    start: Int,
    end: Int,
) = klein.SourceSpan(start, end)

fun ident(
    name: String,
    span: klein.SourceSpan? = null,
) = ExpectedToken.Ident(name, span)

fun num(
    text: String,
    span: klein.SourceSpan? = null,
) = ExpectedToken.Number(text, span)

fun str(
    value: String,
    span: klein.SourceSpan? = null,
) = ExpectedToken.Str(value, span)

fun sym(
    char: Char,
    span: klein.SourceSpan? = null,
) = ExpectedToken.Symbol(char.toString(), span)

fun sym(
    text: String,
    span: klein.SourceSpan? = null,
) = ExpectedToken.Symbol(text, span)

fun kw(
    kind: TokenKind,
    span: klein.SourceSpan? = null,
) = ExpectedToken.Keyword(kind, span)

fun error(message: String) = ExpectedToken.Error(message)

val stmtEnd get() = ExpectedToken.StatementEnd()
val blockStart get() = ExpectedToken.BlockStart()
val blockEnd get() = ExpectedToken.BlockEnd()
val eof get() = ExpectedToken.Eof()

fun stmtEnd(span: klein.SourceSpan) = ExpectedToken.StatementEnd(span)

fun blockStart(span: klein.SourceSpan) = ExpectedToken.BlockStart(span)

fun blockEnd(span: klein.SourceSpan) = ExpectedToken.BlockEnd(span)

fun eof(span: klein.SourceSpan) = ExpectedToken.Eof(span)

fun assertTokens(
    source: String,
    vararg expected: ExpectedToken,
) {
    val result = runCatching { Lexer(source).tokenize().toList() }
    val actualTokens = result.getOrNull() ?: emptyList()
    val lexerError = result.exceptionOrNull() as? LexerError

    val errorIndex = expected.indexOfFirst { it is ExpectedToken.Error }

    if (errorIndex >= 0) {
        // We expected an error
        if (lexerError == null) {
            val expectedError = expected[errorIndex] as ExpectedToken.Error
            fail("Expected error '${expectedError.expectedMessage}' but lexing succeeded")
        }

        // Check tokens before the error
        val tokensBeforeError = expected.take(errorIndex)
        assertEquals(
            tokensBeforeError.size,
            actualTokens.size,
            "Token count before error mismatch.\nExpected: $tokensBeforeError\nActual:   ${actualTokens.map { it.toExpected() }}",
        )

        tokensBeforeError.zip(actualTokens).forEachIndexed { i, (exp, act) ->
            val actualExpected = act.toExpected()
            if (!exp.matches(actualExpected)) {
                fail("Token mismatch at index $i.\nExpected: $exp\nActual:   $actualExpected")
            }
        }

        // Check error message
        val expectedError = expected[errorIndex] as ExpectedToken.Error
        if (expectedError.expectedMessage !in (lexerError.message ?: "")) {
            fail("Expected error containing '${expectedError.expectedMessage}' but got:\n${lexerError.formatWithSource(source)}")
        }
    } else {
        // No error expected
        if (lexerError != null) {
            fail("Unexpected lexer error:\n${lexerError.formatWithSource(source)}")
        }

        assertEquals(
            expected.size,
            actualTokens.size,
            "Token count mismatch.\nExpected: ${expected.toList()}\nActual:   ${actualTokens.map { it.toExpected() }}",
        )

        expected.zip(actualTokens).forEachIndexed { i, (exp, act) ->
            val actualExpected = act.toExpected()
            if (!exp.matches(actualExpected)) {
                fail("Token mismatch at index $i.\nExpected: $exp\nActual:   $actualExpected")
            }
        }
    }
}

private fun Token.toExpected(): ExpectedToken =
    when (kind) {
        IDENT -> ExpectedToken.Ident(text!!, span)
        INT, DOUBLE -> ExpectedToken.Number(text!!, span)
        STRING -> ExpectedToken.Str(text!!, span)
        STMT_END -> ExpectedToken.StatementEnd(span)
        BLOCK_START -> ExpectedToken.BlockStart(span)
        BLOCK_END -> ExpectedToken.BlockEnd(span)
        PIPE_OPEN, PIPE_CLOSE -> ExpectedToken.Symbol("|", span)
        EOF -> ExpectedToken.Eof(span)
        else -> if (kind.keyword != null) ExpectedToken.Keyword(kind, span) else ExpectedToken.Symbol(kind.symbol!!, span)
    }
