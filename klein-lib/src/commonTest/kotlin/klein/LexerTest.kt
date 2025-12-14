package klein

import kotlin.test.Test
import kotlin.test.assertEquals

class LexerTest {
    @Test
    fun singleNumber() {
        Lexer("42").assertTokens(num("42"), eof)
    }

    @Test
    fun decimal() {
        Lexer("3.14").assertTokens(num("3.14"), eof)
    }

    @Test
    fun largerNumber() {
        Lexer("1122334455.5").assertTokens(num("1122334455.5"), eof)
    }

    @Test
    fun operators() {
        Lexer("+ - * / %").assertTokens(sym('+'), sym('-'), sym('*'), sym('/'), sym('%'), eof)
    }

    @Test
    fun expression() {
        Lexer("1 + 2 * 3").assertTokens(num("1"), sym('+'), num("2"), sym('*'), num("3"), eof)
    }

    @Test
    fun parens() {
        Lexer("(1 + 2)").assertTokens(sym('('), num("1"), sym('+'), num("2"), sym(')'), eof)
    }

    @Test
    fun spans() {
        val tokens = Lexer("12 + 3").tokenize()
        assertEquals(SourceSpan(0, 2), tokens[0].span)
        assertEquals(SourceSpan(3, 4), tokens[1].span)
        assertEquals(SourceSpan(5, 6), tokens[2].span)
    }

    @Test
    fun ident() {
        Lexer("foo").assertTokens(ident("foo"), eof)
    }

    @Test
    fun identWithUnderscore() {
        Lexer("foo_bar").assertTokens(ident("foo_bar"), eof)
    }

    @Test
    fun identWithDigits() {
        Lexer("x2 _2 2_").assertTokens(ident("x2"), ident("_2"), num("2"), ident("_"), eof)
    }

    @Test
    fun identStartingWithUnderscore() {
        Lexer("_foo").assertTokens(ident("_foo"), eof)
    }

    @Test
    fun keywords() {
        Lexer("if then else fun")
            .assertTokens(
                kw(KeywordKind.If),
                kw(KeywordKind.Then),
                kw(KeywordKind.Else),
                kw(KeywordKind.Fun),
                eof,
            )
    }

    @Test
    fun keywordLikeIdent() {
        Lexer("iffy elsewhere").assertTokens(ident("iffy"), ident("elsewhere"), eof)
    }

    @Test
    fun spanForKeyword() {
        val tokens = Lexer("if else").tokenize()
        assertEquals(SourceSpan(0, 2), tokens[0].span)
        assertEquals(SourceSpan(3, 7), tokens[1].span)
    }

    @Test
    fun spanForDecimal() {
        val tokens = Lexer("12.34").tokenize()
        assertEquals(SourceSpan(0, 5), tokens[0].span)
    }

    @Test
    fun spanForEof() {
        val tokens = Lexer("x").tokenize()
        assertEquals(SourceSpan(1, 1), tokens[1].span)
    }

    @Test
    fun commentAtEndOfLine() {
        Lexer("x + 1 # add one").assertTokens(ident("x"), sym('+'), num("1"), eof)
    }

    @Test
    fun commentOnOwnLine() {
        Lexer(
                """
            # this is a comment
            x
            """
                    .trimIndent())
            .assertTokens(ident("x"), eof)
    }

    @Test
    fun commentBetweenStatements() {
        Lexer(
                """
            x = 1
            # comment
            y = 2
            """
                    .trimIndent())
            .assertTokens(
                ident("x"), sym('='), num("1"), stmtEnd, ident("y"), sym('='), num("2"), eof)
    }

    @Test
    fun commentAfterStatement() {
        Lexer(
                """
            x = 1 # comment
            y = 2
            """
                    .trimIndent())
            .assertTokens(
                ident("x"), sym('='), num("1"), stmtEnd, ident("y"), sym('='), num("2"), eof)
    }

    @Test
    fun doubleEquals() {
        Lexer("a == b").assertTokens(ident("a"), sym("=="), ident("b"), eof)
    }

    @Test
    fun notEquals() {
        Lexer("a != b").assertTokens(ident("a"), sym("!="), ident("b"), eof)
    }

    @Test
    fun lessThanOrEqual() {
        Lexer("a <= b").assertTokens(ident("a"), sym("<="), ident("b"), eof)
    }

    @Test
    fun greaterThanOrEqual() {
        Lexer("a >= b").assertTokens(ident("a"), sym(">="), ident("b"), eof)
    }

    @Test
    fun arrow() {
        Lexer("x -> y").assertTokens(ident("x"), sym("->"), ident("y"), eof)
    }

    @Test
    fun range() {
        Lexer("1..10").assertTokens(num("1"), sym(".."), num("10"), eof)
    }

    @Test
    fun singleEqualsStillWorks() {
        Lexer("x = 1").assertTokens(ident("x"), sym("="), num("1"), eof)
    }

    @Test
    fun lessThanStillWorks() {
        Lexer("a < b").assertTokens(ident("a"), sym("<"), ident("b"), eof)
    }
}
