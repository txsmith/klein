package klein

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LexerStatementTest {
    @Test
    fun simpleBinding() {
        val tokens = Lexer("x = 1").tokenize()
        assertEquals("x", (tokens[0] as Token.Ident).name)
        assertEquals('=', (tokens[1] as Token.Symbol).char)
        assertEquals("1", (tokens[2] as Token.Number).text)
        assertIs<Token.Eof>(tokens[3])
    }

    @Test
    fun twoBindingsOnSeparateLines() {
        val tokens =
            Lexer(
                """
                x = 1
                y = 2
                """.trimIndent(),
            ).tokenize()

        assertEquals("x", (tokens[0] as Token.Ident).name)
        assertEquals('=', (tokens[1] as Token.Symbol).char)
        assertEquals("1", (tokens[2] as Token.Number).text)
        assertIs<Token.StatementEnd>(tokens[3])
        assertEquals("y", (tokens[4] as Token.Ident).name)
        assertEquals('=', (tokens[5] as Token.Symbol).char)
        assertEquals("2", (tokens[6] as Token.Number).text)
        assertIs<Token.Eof>(tokens[7])
    }

    @Test
    fun noNewlineAfterOperator() {
        val tokens = Lexer("x = 1 +\n2").tokenize()
        assertEquals("x", (tokens[0] as Token.Ident).name)
        assertEquals('=', (tokens[1] as Token.Symbol).char)
        assertEquals("1", (tokens[2] as Token.Number).text)
        assertEquals('+', (tokens[3] as Token.Symbol).char)
        assertEquals("2", (tokens[4] as Token.Number).text)
        assertIs<Token.Eof>(tokens[5])
    }

    // @Test
    // fun noNewlineAfterOpenParen() {
    //     val tokens = Lexer("foo(\na,\nb\n)").tokenize()
    //     assertEquals("foo", (tokens[0] as Token.Ident).name)
    //     assertEquals('(', (tokens[1] as Token.Symbol).char)
    //     assertEquals("a", (tokens[2] as Token.Ident).name)
    //     assertEquals(',', (tokens[3] as Token.Symbol).char)
    //     assertEquals("b", (tokens[4] as Token.Ident).name)
    //     assertEquals(')', (tokens[5] as Token.Symbol).char)
    //     assertTrue(tokens[6] is Token.Eof)
    // }
    //
    // @Test
    // fun newlineInsideBlock() {
    //     val tokens = Lexer("{\nx = 1\ny = 2\n}").tokenize()
    //     assertEquals('{', (tokens[0] as Token.Symbol).char)
    //     assertEquals("x", (tokens[1] as Token.Ident).name)
    //     assertEquals('=', (tokens[2] as Token.Symbol).char)
    //     assertEquals("1", (tokens[3] as Token.Number).text)
    //     assertTrue(tokens[4] is Token.Newline)
    //     assertEquals("y", (tokens[5] as Token.Ident).name)
    //     assertEquals('=', (tokens[6] as Token.Symbol).char)
    //     assertEquals("2", (tokens[7] as Token.Number).text)
    //     assertTrue(tokens[8] is Token.Newline)
    //     assertEquals('}', (tokens[9] as Token.Symbol).char)
    //     assertTrue(tokens[10] is Token.Eof)
    // }
    //
    // @Test
    // fun noNewlineInsideParensInsideBlock() {
    //     val tokens = Lexer("{\nfoo(\na\n)\n}").tokenize()
    //     assertEquals('{', (tokens[0] as Token.Symbol).char)
    //     assertEquals("foo", (tokens[1] as Token.Ident).name)
    //     assertEquals('(', (tokens[2] as Token.Symbol).char)
    //     assertEquals("a", (tokens[3] as Token.Ident).name)
    //     assertEquals(')', (tokens[4] as Token.Symbol).char)
    //     assertTrue(tokens[5] is Token.Newline)
    //     assertEquals('}', (tokens[6] as Token.Symbol).char)
    //     assertTrue(tokens[7] is Token.Eof)
    // }
    //
    // @Test
    // fun newlineInsidePipeLambda() {
    //     val tokens = Lexer("|\nx = 1\ny\n|").tokenize()
    //     assertEquals('|', (tokens[0] as Token.Symbol).char)
    //     assertEquals("x", (tokens[1] as Token.Ident).name)
    //     assertEquals('=', (tokens[2] as Token.Symbol).char)
    //     assertEquals("1", (tokens[3] as Token.Number).text)
    //     assertTrue(tokens[4] is Token.Newline)
    //     assertEquals("y", (tokens[5] as Token.Ident).name)
    //     assertTrue(tokens[6] is Token.Newline)
    //     assertEquals('|', (tokens[7] as Token.Symbol).char)
    //     assertTrue(tokens[8] is Token.Eof)
    // }
    //
    // @Test
    // fun nestedLambdaInParens() {
    //     val tokens = Lexer("foo(|.x|)").tokenize()
    //     assertEquals("foo", (tokens[0] as Token.Ident).name)
    //     assertEquals('(', (tokens[1] as Token.Symbol).char)
    //     assertEquals('|', (tokens[2] as Token.Symbol).char)
    //     assertEquals('.', (tokens[3] as Token.Symbol).char)
    //     assertEquals("x", (tokens[4] as Token.Ident).name)
    //     assertEquals('|', (tokens[5] as Token.Symbol).char)
    //     assertEquals(')', (tokens[6] as Token.Symbol).char)
    //     assertTrue(tokens[7] is Token.Eof)
    // }
    //
    // @Test
    // fun noNewlineAfterEquals() {
    //     val tokens = Lexer("x =\n1").tokenize()
    //     assertEquals("x", (tokens[0] as Token.Ident).name)
    //     assertEquals('=', (tokens[1] as Token.Symbol).char)
    //     assertEquals("1", (tokens[2] as Token.Number).text)
    //     assertTrue(tokens[3] is Token.Eof)
    // }
    //
    // @Test
    // fun noNewlineAfterComma() {
    //     val tokens = Lexer("x = foo(a,\nb)").tokenize()
    //     assertEquals("x", (tokens[0] as Token.Ident).name)
    //     assertEquals('=', (tokens[1] as Token.Symbol).char)
    //     assertEquals("foo", (tokens[2] as Token.Ident).name)
    //     assertEquals('(', (tokens[3] as Token.Symbol).char)
    //     assertEquals("a", (tokens[4] as Token.Ident).name)
    //     assertEquals(',', (tokens[5] as Token.Symbol).char)
    //     assertEquals("b", (tokens[6] as Token.Ident).name)
    //     assertEquals(')', (tokens[7] as Token.Symbol).char)
    //     assertTrue(tokens[8] is Token.Eof)
    // }
    //
    // @Test
    // fun multipleNewlinesCollapsed() {
    //     val tokens = Lexer("x = 1\n\n\ny = 2").tokenize()
    //     assertEquals("x", (tokens[0] as Token.Ident).name)
    //     assertEquals('=', (tokens[1] as Token.Symbol).char)
    //     assertEquals("1", (tokens[2] as Token.Number).text)
    //     assertTrue(tokens[3] is Token.Newline)
    //     assertEquals("y", (tokens[4] as Token.Ident).name)
    //     assertEquals('=', (tokens[5] as Token.Symbol).char)
    //     assertEquals("2", (tokens[6] as Token.Number).text)
    //     assertTrue(tokens[7] is Token.Eof)
    // }
}
