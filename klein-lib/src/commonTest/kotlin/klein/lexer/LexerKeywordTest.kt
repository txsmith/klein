package klein.lexer

import klein.KeywordKind
import klein.Lexer
import kotlin.test.Test

class LexerKeywordTest {
    @Test
    fun ifKeyword() {
        Lexer("if").assertTokens(kw(KeywordKind.If), eof)
    }

    @Test
    fun thenKeyword() {
        Lexer("then").assertTokens(kw(KeywordKind.Then), eof)
    }

    @Test
    fun elseKeyword() {
        Lexer("else").assertTokens(kw(KeywordKind.Else), eof)
    }

    @Test
    fun funKeyword() {
        Lexer("fun").assertTokens(kw(KeywordKind.Fun), eof)
    }

    @Test
    fun trueKeyword() {
        Lexer("true").assertTokens(kw(KeywordKind.True), eof)
    }

    @Test
    fun falseKeyword() {
        Lexer("false").assertTokens(kw(KeywordKind.False), eof)
    }

    @Test
    fun andKeyword() {
        Lexer("and").assertTokens(kw(KeywordKind.And), eof)
    }

    @Test
    fun orKeyword() {
        Lexer("or").assertTokens(kw(KeywordKind.Or), eof)
    }

    @Test
    fun notKeyword() {
        Lexer("not").assertTokens(kw(KeywordKind.Not), eof)
    }

    @Test
    fun keywordLikePrefixIsIdent() {
        Lexer("iffy trueish falsey andy oracle funky notty").assertTokens(
            ident("iffy"),
            ident("trueish"),
            ident("falsey"),
            ident("andy"),
            ident("oracle"),
            ident("funky"),
            ident("notty"),
            eof,
        )
    }
}
