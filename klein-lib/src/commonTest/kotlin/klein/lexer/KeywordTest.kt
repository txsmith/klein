@file:Suppress("ktlint")

package klein.lexer

import klein.TokenKind.*
import kotlin.test.Test

class KeywordTest {
    @Test
    fun ifKeyword() {
        assertTokens("if", kw(IF), eof)
    }

    @Test
    fun thenKeyword() {
        assertTokens("then", kw(THEN), eof)
    }

    @Test
    fun elseKeyword() {
        assertTokens("else", kw(ELSE), eof)
    }

    @Test
    fun funKeyword() {
        assertTokens("fun", kw(FUN), eof)
    }

    @Test
    fun trueKeyword() {
        assertTokens("true", kw(TRUE), eof)
    }

    @Test
    fun falseKeyword() {
        assertTokens("false", kw(FALSE), eof)
    }

    @Test
    fun andKeyword() {
        assertTokens("and", kw(AND), eof)
    }

    @Test
    fun orKeyword() {
        assertTokens("or", kw(OR), eof)
    }

    @Test
    fun notKeyword() {
        assertTokens("not", kw(NOT), eof)
    }

    @Test
    fun keywordLikePrefixIsIdent() {
        assertTokens(
            "iffy trueish falsey andy oracle funky notty",
            ident("iffy"), ident("trueish"), ident("falsey"), ident("andy"), ident("oracle"), ident("funky"), ident("notty"), eof,
        )
    }
}
