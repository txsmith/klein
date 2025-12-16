@file:Suppress("ktlint")

package klein.lexer

import klein.Lexer
import klein.LexerError
import klein.TokenKind.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BlockTest {
    @Test
    fun blockStartedBlockAfterEquals() {
        val program = """
            result =
                x = 1
                y = 2
                y
        """.trimIndent()
        assertTokens(
            program,
            ident("result"), sym('='), blockStart,
            ident("x"), sym('='), num("1"), stmtEnd,
            ident("y"), sym('='), num("2"), stmtEnd,
            ident("y"), eof,
        )
    }

    @Test
    fun noNewlineInsideParensInsideIndentedBlock() {
        val program = """
            result =
                foo(
                    a,
                    b
                )
        """.trimIndent()
        assertTokens(
            program,
            ident("result"), sym('='),
            blockStart, ident("foo"), sym('('), ident("a"), sym(','), ident("b"), sym(')'),
            eof,
        )
    }

    @Test
    fun nestedIndentation() {
        val program = """
            outer =
                inner =
                    x = 1
                    x
                inner
        """.trimIndent()
        assertTokens(
            program,
            ident("outer"), sym('='), blockStart,
            ident("inner"), sym('='), blockStart,
            ident("x"), sym('='), num("1"), stmtEnd, ident("x"), blockEnd,
            ident("inner"), eof,
        )
    }

    @Test
    fun multipleDedentsAtOnce() {
        val program = """
            a =
                b =
                    c =
                        1
            d = 2
        """.trimIndent()
        assertTokens(
            program,
            ident("a"), sym('='), blockStart,
            ident("b"), sym('='), blockStart,
            ident("c"), sym('='), blockStart,
            num("1"), blockEnd, blockEnd, blockEnd,
            ident("d"), sym('='), num("2"), eof,
        )
    }

    @Test
    fun blockStarterFollowedBySameLevelExpr() {
        val program = """
            x =
            1
        """.trimIndent()
        assertTokens(program, ident("x"), sym('='), num("1"), eof)
    }

    @Test
    fun expressionContinuationInBlock() {
        val program = """
            if true then
                q = 1
                + 2
            else
                x
        """.trimIndent()
        assertTokens(
            program,
            kw(IF), kw(TRUE), kw(THEN), blockStart,
            ident("q"), sym('='), num("1"), stmtEnd,
            sym('+'), num("2"), blockEnd,
            kw(ELSE), blockStart, ident("x"), eof,
        )
    }

    @Test
    fun expressionContinuationWithIndent() {
        val program = """
            if true then
                q = 1
                    + 2
            else
                x
        """.trimIndent()
        assertTokens(
            program,
            kw(IF), kw(TRUE), kw(THEN), blockStart,
            ident("q"), sym('='), num("1"), sym('+'), num("2"), blockEnd,
            kw(ELSE), blockStart,
            ident("x"), eof,
        )
    }

    @Test
    fun deeperIndentWithinBlockIsContinuation() {
        val program = """
            if true then
                s = 1
                    y = 3
                    println(s)
                println(y)
        """.trimIndent()
        assertTokens(
            program,
            kw(IF), kw(TRUE), kw(THEN), blockStart,
            ident("s"), sym('='), num("1"),
            ident("y"), sym('='), num("3"),
            ident("println"), sym('('), ident("s"), sym(')'), stmtEnd,
            ident("println"), sym('('), ident("y"), sym(')'), eof,
        )
    }

    @Test
    fun blockEndMidExpression() {
        val program = """
            if true then
                q = 1
            + 2
            else
                x
        """.trimIndent()
        assertTokens(
            program,
            kw(IF), kw(TRUE), kw(THEN),
            blockStart, ident("q"), sym('='), num("1"),
            blockEnd, sym('+'), num("2"),
            kw(ELSE),
            blockStart, ident("x"),
            eof,
        )
    }

    @Test
    fun blockStartWithSpaces() {
        val program = """
            x =
                1
        """.trimIndent()
        assertTokens(program, ident("x"), sym('='), blockStart, num("1"), eof)
    }

    @Test
    fun tabsInIndentationThrows() {
        val program = "x =\n\t1"
        val error = assertFailsWith<LexerError> { Lexer(program).tokenize().toList() }
        assertEquals("Tabs are not allowed for indentation", error.message)
    }

    @Test
    fun tabsAfterSpacesInIndentationThrows() {
        val program = "x =\n    1\n\t2"
        val error = assertFailsWith<LexerError> { Lexer(program).tokenize().toList() }
        assertEquals("Tabs are not allowed for indentation", error.message)
    }

    @Test
    fun tabsInIndentationInsideParensThrows() {
        val program = "foo(\n    a,\n\tb)"
        val error = assertFailsWith<LexerError> { Lexer(program).tokenize().toList() }
        assertEquals("Tabs are not allowed for indentation", error.message)
    }

    @Test
    fun mixedIndentation2And4Spaces() {
        val program = """
            outer =
              inner =
                  42
              inner
        """.trimIndent()
        assertTokens(
            program,
            ident("outer"), sym('='),
            blockStart, ident("inner"), sym('='),
            blockStart, num("42"),
            blockEnd, ident("inner"),
            eof,
        )
    }

    @Test
    fun mixedIndentation4And2Spaces() {
        val program = """
            outer =
                inner =
                  42
                inner
        """.trimIndent()
        assertTokens(
            program,
            ident("outer"), sym('='),
            blockStart, ident("inner"), sym('='),
            blockStart, num("42"),
            blockEnd, ident("inner"),
            eof,
        )
    }

    @Test
    fun mixedIndentationMultipleLevels() {
        val program = """
            a =
              b =
                  c =
                     x
                  c
              b
        """.trimIndent()
        assertTokens(
            program,
            ident("a"), sym('='),
            blockStart, ident("b"), sym('='),
            blockStart, ident("c"), sym('='),
            blockStart, ident("x"),
            blockEnd, ident("c"),
            blockEnd, ident("b"),
            eof,
        )
    }

    @Test
    fun noIndentInsideParensAfterBlockStarter() {
        val program = """
            x = (
                a,
                b
            )
        """.trimIndent()
        assertTokens(program, ident("x"), sym('='), sym('('), ident("a"), sym(','), ident("b"), sym(')'), eof)
    }

    @Test
    fun commentAtStartOfIndentedBlock() {
        val program = """
            x =
              # comment
              42
        """.trimIndent()
        assertTokens(program, ident("x"), sym('='), blockStart, num("42"), eof)
    }

    @Test
    fun commentBetweenIndentedStatements() {
        val program = """
            x =
              y = 1
              # comment here
              y + 2
        """.trimIndent()
        assertTokens(
            program,
            ident("x"), sym('='), blockStart,
            ident("y"), sym('='), num("1"), stmtEnd,
            ident("y"), sym('+'), num("2"),
            eof,
        )
    }

    @Test
    fun commentWithDeeperIndentation() {
        val program = """
            x =
              y = 1
                # deeply indented comment
              y + 2
        """.trimIndent()
        assertTokens(
            program,
            ident("x"), sym('='), blockStart,
            ident("y"), sym('='), num("1"), stmtEnd,
            ident("y"), sym('+'), num("2"),
            eof,
        )
    }

    @Test
    fun weirdIndentInsideParensButOutsidePipe() {
        val program = """
            foo(
            a,
                    b,
              c
            )
        """.trimIndent()
        assertTokens(program, ident("foo"), sym('('), ident("a"), sym(','), ident("b"), sym(','), ident("c"), sym(')'), eof)
    }

    @Test
    fun unmatchedCloseParen() {
        assertTokens(")", sym(')'), eof)
    }

    @Test
    fun unmatchedCloseBracket() {
        assertTokens("]", sym(']'), eof)
    }

    @Test
    fun unmatchedClosePipe() {
        assertTokens("x|", ident("x"), sym('|'), eof)
    }

    @Test
    fun closeParenThroughBracket() {
        assertTokens("([)", sym('('), sym('['), sym(')'), eof)
    }

    @Test
    fun closeBracketThroughParen() {
        assertTokens("[(]", sym('['), sym('('), sym(']'), eof)
    }

    @Test
    fun closeWithWrongDelimiterNotOnStack() {
        assertTokens("(]", sym('('), sym(']'), eof)
    }

    @Test
    fun closeWithWrongDelimiterNotOnStackReversed() {
        assertTokens("[)", sym('['), sym(')'), eof)
    }

    @Test
    fun deepStackCloseWithWrongDelimiter() {
        assertTokens(
            "((((((]",
            sym('('), sym('('), sym('('), sym('('), sym('('), sym('('),
            sym(']'),
            eof,
        )
    }

    @Test
    fun indentationBetweenBlockLevels() {
        val program = """
            x =
                y = 1
              z
        """.trimIndent()
        assertTokens(
            program,
            ident("x"), sym('='),
            blockStart, ident("y"), sym('='), num("1"),
            blockEnd, ident("z"),
            eof,
        )
    }
}
