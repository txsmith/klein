package klein

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LexerTest {
    @Test
    fun singleNumber() {
        val tokens = Lexer("42").tokenize()
        assertEquals(2, tokens.size)
        assertEquals("42", (tokens[0] as Token.Number).text)
    }

    @Test
    fun decimal() {
        val tokens = Lexer("3.14").tokenize()
        assertEquals("3.14", (tokens[0] as Token.Number).text)
    }

    @Test
    fun largerNumber() {
        val tokens = Lexer("1122334455.5").tokenize()
        assertEquals("1122334455.5", (tokens[0] as Token.Number).text)
    }

    @Test
    fun operators() {
        val tokens = Lexer("+ - * / %").tokenize()
        assertEquals('+', (tokens[0] as Token.Symbol).char)
        assertEquals('-', (tokens[1] as Token.Symbol).char)
        assertEquals('*', (tokens[2] as Token.Symbol).char)
        assertEquals('/', (tokens[3] as Token.Symbol).char)
        assertEquals('%', (tokens[4] as Token.Symbol).char)
    }

    @Test
    fun expression() {
        val tokens = Lexer("1 + 2 * 3").tokenize()
        assertEquals(6, tokens.size)
        assertEquals("1", (tokens[0] as Token.Number).text)
        assertEquals('+', (tokens[1] as Token.Symbol).char)
        assertEquals("2", (tokens[2] as Token.Number).text)
        assertEquals('*', (tokens[3] as Token.Symbol).char)
        assertEquals("3", (tokens[4] as Token.Number).text)
        assertIs<Token.Eof>(tokens[5])
    }

    @Test
    fun parens() {
        val tokens = Lexer("(1 + 2)").tokenize()
        assertEquals('(', (tokens[0] as Token.Symbol).char)
        assertEquals("1", (tokens[1] as Token.Number).text)
        assertEquals('+', (tokens[2] as Token.Symbol).char)
        assertEquals("2", (tokens[3] as Token.Number).text)
        assertEquals(')', (tokens[4] as Token.Symbol).char)
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
        val tokens = Lexer("foo").tokenize()
        assertEquals("foo", (tokens[0] as Token.Ident).name)
    }

    @Test
    fun identWithUnderscore() {
        val tokens = Lexer("foo_bar").tokenize()
        assertEquals("foo_bar", (tokens[0] as Token.Ident).name)
    }

    @Test
    fun identWithDigits() {
        val tokens = Lexer("x2 _2 2_").tokenize()
        assertEquals("x2", (tokens[0] as Token.Ident).name)
        assertEquals("_2", (tokens[1] as Token.Ident).name)
        assertEquals("2", (tokens[2] as Token.Number).text)
        assertEquals("_", (tokens[3] as Token.Ident).name)
    }

    @Test
    fun identStartingWithUnderscore() {
        val tokens = Lexer("_foo").tokenize()
        assertEquals("_foo", (tokens[0] as Token.Ident).name)
    }

    @Test
    fun keywords() {
        val tokens = Lexer("if then else fun").tokenize()
        assertEquals(KeywordKind.If, (tokens[0] as Token.Keyword).kind)
        assertEquals(KeywordKind.Then, (tokens[1] as Token.Keyword).kind)
        assertEquals(KeywordKind.Else, (tokens[2] as Token.Keyword).kind)
        assertEquals(KeywordKind.Fun, (tokens[3] as Token.Keyword).kind)
    }

    @Test
    fun keywordLikeIdent() {
        val tokens = Lexer("iffy elsewhere").tokenize()
        assertEquals("iffy", (tokens[0] as Token.Ident).name)
        assertEquals("elsewhere", (tokens[1] as Token.Ident).name)
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
}
