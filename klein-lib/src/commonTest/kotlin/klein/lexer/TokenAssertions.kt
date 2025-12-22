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
    abstract val indent: Int?

    fun matches(other: ExpectedToken): Boolean {
        if (this::class != other::class) return false
        if (span != null && span != other.span) return false
        if (indent != null && indent != other.indent) return false

        return when (this) {
            is Ident -> other is Ident && name == other.name
            is Number -> other is Number && text == other.text
            is Str -> other is Str && value == other.value
            is Symbol -> other is Symbol && text == other.text && (kind == null || kind == other.kind)
            is Keyword -> other is Keyword && kind == other.kind
            is Pipe -> other is Pipe
            is Eof -> other is Eof
            is Error -> other is Error && expectedMessage == other.expectedMessage
        }
    }

    fun display(): String {
        val indentSuffix = if (indent != null) "@$indent" else ""
        return when (this) {
            is Ident -> "ident($name)$indentSuffix"
            is Number -> "num($text)$indentSuffix"
            is Str -> "str($value)$indentSuffix"
            is Symbol -> (if (text.length == 1) "sym('$text')" else "sym(\"$text\")") + indentSuffix
            is Keyword -> "kw(${kind.name})$indentSuffix"
            is Pipe -> "pipe$indentSuffix"
            is Eof -> "eof$indentSuffix"
            is Error -> "error($expectedMessage)"
        }
    }

    data class Ident(
        val name: String,
        override val span: klein.SourceSpan? = null,
        override val indent: Int? = null,
    ) : ExpectedToken()

    data class Number(
        val text: String,
        override val span: klein.SourceSpan? = null,
        override val indent: Int? = null,
    ) : ExpectedToken()

    data class Str(
        val value: String,
        override val span: klein.SourceSpan? = null,
        override val indent: Int? = null,
    ) : ExpectedToken()

    data class Symbol(
        val text: String,
        val kind: TokenKind? = null,
        override val span: klein.SourceSpan? = null,
        override val indent: Int? = null,
    ) : ExpectedToken()

    data class Keyword(
        val kind: TokenKind,
        override val span: klein.SourceSpan? = null,
        override val indent: Int? = null,
    ) : ExpectedToken()

    data class Pipe(
        override val span: klein.SourceSpan? = null,
        override val indent: Int? = null,
    ) : ExpectedToken()

    data class Eof(
        override val span: klein.SourceSpan? = null,
        override val indent: Int? = null,
    ) : ExpectedToken()

    data class Error(
        val expectedMessage: String,
    ) : ExpectedToken() {
        override val span: klein.SourceSpan? = null
        override val indent: Int? = null
    }
}

fun span(
    start: Int,
    end: Int,
) = klein.SourceSpan(start, end)

fun ident(
    name: String,
    span: klein.SourceSpan? = null,
    indent: Int? = null,
) = ExpectedToken.Ident(name, span, indent)

fun num(
    text: String,
    span: klein.SourceSpan? = null,
    indent: Int? = null,
) = ExpectedToken.Number(text, span, indent)

fun str(
    value: String,
    span: klein.SourceSpan? = null,
    indent: Int? = null,
) = ExpectedToken.Str(value, span, indent)

fun sym(
    char: Char,
    span: klein.SourceSpan? = null,
    kind: TokenKind? = null,
    indent: Int? = null,
) = ExpectedToken.Symbol(char.toString(), kind, span, indent)

fun sym(
    text: String,
    span: klein.SourceSpan? = null,
    kind: TokenKind? = null,
    indent: Int? = null,
) = ExpectedToken.Symbol(text, kind, span, indent)

fun kw(
    kind: TokenKind,
    span: klein.SourceSpan? = null,
    indent: Int? = null,
) = ExpectedToken.Keyword(kind, span, indent)

fun error(message: String) = ExpectedToken.Error(message)

val pipe get() = ExpectedToken.Pipe()
val eof get() = ExpectedToken.Eof()

fun pipe(indent: Int) = ExpectedToken.Pipe(indent = indent)

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
            "Token count before error mismatch.\nExpected: ${tokensBeforeError.map { it.display() }}\nActual:   ${actualTokens.map {
                it
                    .toExpected()
                    .display()
            }}",
        )

        tokensBeforeError.zip(actualTokens).forEachIndexed { i, (exp, act) ->
            val actualExpected = act.toExpected()
            if (!exp.matches(actualExpected)) {
                fail("Token mismatch at index $i.\nExpected: ${exp.display()}\nActual:   ${actualExpected.display()}")
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
            "Token count mismatch.\nExpected: ${expected.map {
                it.display()
            }}\nActual:   ${actualTokens.map { it.toExpected().display() }}",
        )

        expected.zip(actualTokens).forEachIndexed { i, (exp, act) ->
            val actualExpected = act.toExpected()
            if (!exp.matches(actualExpected)) {
                fail("Token mismatch at index $i.\nExpected: ${exp.display()}\nActual:   ${actualExpected.display()}")
            }
        }
    }
}

private fun Token.toExpected(): ExpectedToken =
    when (kind) {
        IDENT -> ExpectedToken.Ident(text!!, span, indent)
        INT, DOUBLE -> ExpectedToken.Number(text!!, span, indent)
        STRING -> ExpectedToken.Str(text!!, span, indent)
        PIPE -> ExpectedToken.Pipe(span, indent)
        EOF -> ExpectedToken.Eof(span, indent)
        else ->
            if (kind.keyword != null) {
                ExpectedToken.Keyword(kind, span, indent)
            } else {
                ExpectedToken.Symbol(kind.symbol!!, kind, span, indent)
            }
    }
