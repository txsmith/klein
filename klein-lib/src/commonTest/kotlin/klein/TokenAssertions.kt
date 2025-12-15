package klein

import kotlin.test.assertEquals
import kotlin.test.fail

sealed class ExpectedToken {
    data class Ident(
        val name: String,
    ) : ExpectedToken()

    data class Number(
        val text: String,
    ) : ExpectedToken()

    data class Str(
        val value: String,
    ) : ExpectedToken()

    data class Symbol(
        val text: String,
    ) : ExpectedToken()

    data class Keyword(
        val kind: KeywordKind,
    ) : ExpectedToken()

    data object StatementEnd : ExpectedToken()

    data object Eof : ExpectedToken()
}

fun ident(name: String) = ExpectedToken.Ident(name)

fun num(text: String) = ExpectedToken.Number(text)

fun str(value: String) = ExpectedToken.Str(value)

fun sym(char: Char) = ExpectedToken.Symbol(char.toString())

fun sym(text: String) = ExpectedToken.Symbol(text)

fun kw(kind: KeywordKind) = ExpectedToken.Keyword(kind)

val stmtEnd = ExpectedToken.StatementEnd
val eof = ExpectedToken.Eof

fun Lexer.assertTokens(vararg expected: ExpectedToken) {
    val actual = tokenize()
    assertEquals(
        expected.size,
        actual.size,
        "Token count mismatch.\nExpected: ${expected.toList()}\nActual:   ${actual.map { it.toExpected() }}",
    )
    expected.zip(actual).forEachIndexed { i, (exp, act) ->
        val actualExpected = act.toExpected()
        if (exp != actualExpected) {
            fail("Token mismatch at index $i.\nExpected: $exp\nActual:   $actualExpected")
        }
    }
}

private fun Token.toExpected(): ExpectedToken =
    when (this) {
        is Token.Ident -> ExpectedToken.Ident(name)
        is Token.Number -> ExpectedToken.Number(text)
        is Token.Str -> ExpectedToken.Str(value)
        is Token.Symbol -> ExpectedToken.Symbol(text)
        is Token.Keyword -> ExpectedToken.Keyword(kind)
        is Token.StatementEnd -> ExpectedToken.StatementEnd
        is Token.Eof -> ExpectedToken.Eof
    }
