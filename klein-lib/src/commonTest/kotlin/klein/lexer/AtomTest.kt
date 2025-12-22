@file:Suppress("ktlint")

package klein.lexer

import klein.TokenKind.*
import kotlin.test.Test

class AtomTest {
    @Test
    fun singleNumber() {
        assertTokens("42", num("42"), eof)
    }

    @Test
    fun decimal() {
        assertTokens("3.14", num("3.14"), eof)
    }

    @Test
    fun largerNumber() {
        assertTokens("1122334455.5", num("1122334455.5"), eof)
    }

    @Test
    fun operators() {
        assertTokens("+ - * / %", sym('+'), sym('-'), sym('*'), sym('/'), sym('%'), eof)
    }

    @Test
    fun expression() {
        assertTokens("1 + 2 * 3", num("1"), sym('+'), num("2"), sym('*'), num("3"), eof)
    }

    @Test
    fun parens() {
        assertTokens("(1 + 2)", sym('('), num("1"), sym('+'), num("2"), sym(')'), eof)
    }

    @Test
    fun spans() {
        assertTokens(
            "12 + 3",
            num("12", span(0, 2)),
            sym('+', span(3, 4)),
            num("3", span(5, 6)),
            eof
        )
    }

    @Test
    fun ident() {
        assertTokens("foo", ident("foo"), eof)
    }

    @Test
    fun identWithUnderscore() {
        assertTokens("foo_bar", ident("foo_bar"), eof)
    }

    @Test
    fun identWithDigits() {
        assertTokens("x2 _2 2_", ident("x2"), ident("_2"), num("2"), ident("_"), eof)
    }

    @Test
    fun identStartingWithUnderscore() {
        assertTokens("_foo", ident("_foo"), eof)
    }

    @Test
    fun spanForKeyword() {
        assertTokens(
            "if else",
            kw(IF, span(0, 2)),
            kw(ELSE, span(3, 7)),
            eof
        )
    }

    @Test
    fun spanForDecimal() {
        assertTokens(
            "12.34",
            num("12.34", span(0, 5)),
            eof
        )
    }

    @Test
    fun spanForEof() {
        assertTokens(
            "x",
            ident("x"),
            eof(span(1, 1))
        )
    }

    @Test
    fun commentAtEndOfLine() {
        assertTokens("x + 1 # add one", ident("x"), sym('+'), num("1"), eof)
    }

    @Test
    fun commentOnOwnLine() {
        val program = """
            # this is a comment
            x
        """.trimIndent()
        assertTokens(program, ident("x"), eof)
    }

    @Test
    fun doubleEquals() {
        assertTokens("a == b", ident("a"), sym("=="), ident("b"), eof)
    }

    @Test
    fun notEquals() {
        assertTokens("a != b", ident("a"), sym("!="), ident("b"), eof)
    }

    @Test
    fun lessThanOrEqual() {
        assertTokens("a <= b", ident("a"), sym("<="), ident("b"), eof)
    }

    @Test
    fun greaterThanOrEqual() {
        assertTokens("a >= b", ident("a"), sym(">="), ident("b"), eof)
    }

    @Test
    fun arrow() {
        assertTokens("x -> y", ident("x"), sym("->"), ident("y"), eof)
    }

    @Test
    fun range() {
        assertTokens("1..10", num("1"), sym(".."), num("10"), eof)
    }

    @Test
    fun singleEqualsStillWorks() {
        assertTokens("x = 1", ident("x"), sym("="), num("1"), eof)
    }

    @Test
    fun lessThanStillWorks() {
        assertTokens("a < b", ident("a"), sym("<"), ident("b"), eof)
    }

    @Test
    fun minusTightVsLoose() {
        // No space after - means MINUS_TIGHT (unary)
        assertTokens("-2", sym("-", kind = MINUS_TIGHT), num("2"), eof)
        // Space after - means MINUS (binary)
        assertTokens("- 2", sym("-", kind = MINUS), num("2"), eof)
    }

    @Test
    fun minusInExpression() {
        // In expressions, space determines the kind
        assertTokens("a -b", ident("a"), sym("-", kind = MINUS_TIGHT), ident("b"), eof)
        assertTokens("a - b", ident("a"), sym("-", kind = MINUS), ident("b"), eof)
    }
}
