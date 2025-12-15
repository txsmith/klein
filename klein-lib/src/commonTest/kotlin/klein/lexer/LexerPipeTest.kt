package klein.lexer

import klein.Lexer
import kotlin.test.Test
import kotlin.test.assertEquals

class LexerPipeTest {
    @Test
    fun doublePipe() {
        val tokens = Lexer("||").tokenize()
        assertEquals(3, tokens.size, "Expected 3 tokens, got: $tokens")
    }

    @Test
    fun pipeWithContent() {
        Lexer("|x|").assertTokens(sym('|'), ident("x"), sym('|'), eof)
    }

    @Test
    fun emptyPipePair() {
        Lexer("||").assertTokens(sym('|'), sym('|'), eof)
    }
}
