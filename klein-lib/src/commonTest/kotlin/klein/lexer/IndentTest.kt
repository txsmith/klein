@file:Suppress("ktlint")

package klein.lexer

import klein.Lexer
import klein.LexerError
import klein.TokenKind.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IndentTest {
    @Test
    fun blockAfterEquals() {
        val program = """
            result =
                x = 1
                y = 2
                y
        """.trimIndent()
        assertTokens(
            program,
            ident("result", indent = 0), sym('='),
            ident("x", indent = 4), sym('='), num("1"),
            ident("y", indent = 4), sym('='), num("2"),
            ident("y", indent = 4), eof,
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
            ident("result", indent = 0), sym('='),
            ident("foo", indent = 4), sym('('),
            ident("a", indent = 8), sym(','),
            ident("b", indent = 8),
            sym(')', indent = 4),
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
            ident("outer", indent = 0), sym('='),
            ident("inner", indent = 4), sym('='),
            ident("x", indent = 8), sym('='), num("1"),
            ident("x", indent = 8),
            ident("inner", indent = 4),
            eof,
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
            ident("a", indent = 0), sym('='),
            ident("b", indent = 4), sym('='),
            ident("c", indent = 8), sym('='),
            num("1", indent = 12),
            ident("d", indent = 0), sym('='), num("2"), eof,
        )
    }

    @Test
    fun blockStarterFollowedBySameLevelExpr() {
        val program = """
            x =
            1
        """.trimIndent()
        assertTokens(program, ident("x", indent = 0), sym('='), num("1", indent = 0), eof)
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
            kw(IF, indent = 0), kw(TRUE), kw(THEN),
            ident("q", indent = 4), sym('='), num("1"),
            sym('+', indent = 4), num("2"),
            kw(ELSE, indent = 0), ident("x"), eof,
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
            kw(IF, indent = 0), kw(TRUE), kw(THEN),
            ident("q", indent = 4), sym('='), num("1"),
            sym('+', indent = 8), num("2"),
            kw(ELSE, indent = 0),
            ident("x", indent = 4), eof,
        )
    }

    @Test
    fun deeperIndentWithNonOperatorIsNewStatement() {
        val program = """
            if true then
                s = 1
                    y = 3
                    println(s)
                println(y)
        """.trimIndent()
        assertTokens(
            program,
            kw(IF, indent = 0), kw(TRUE), kw(THEN),
            ident("s", indent = 4), sym('='), num("1"),
            ident("y", indent = 8), sym('='), num("3"),
            ident("println", indent = 8), sym('('), ident("s"), sym(')'),
            ident("println", indent = 4), sym('('), ident("y"), sym(')'), eof,
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
            kw(IF, indent = 0), kw(TRUE), kw(THEN),
            ident("q", indent = 4), sym('='), num("1"),
            sym('+', indent = 0), num("2"),
            kw(ELSE, indent = 0),
            ident("x", indent = 4),
            eof,
        )
    }

    @Test
    fun blockStartWithSpaces() {
        val program = """
            x =
                1
        """.trimIndent()
        assertTokens(program, ident("x", indent = 0), sym('='), num("1", indent = 4), eof)
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
            ident("outer", indent = 0), sym('='),
            ident("inner", indent = 2), sym('='),
            num("42", indent = 6),
            ident("inner", indent = 2),
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
            ident("outer", indent = 0), sym('='),
            ident("inner", indent = 4), sym('='),
            num("42", indent = 6),
            ident("inner", indent = 4),
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
            ident("a", indent = 0), sym('='),
            ident("b", indent = 2), sym('='),
            ident("c", indent = 6), sym('='),
            ident("x", indent = 9),
            ident("c", indent = 6),
            ident("b", indent = 2),
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
        assertTokens(
            program,
            ident("x", indent = 0), sym('='), sym('('),
            ident("a", indent = 4), sym(','),
            ident("b", indent = 4),
            sym(')', indent = 0), eof,
        )
    }

    @Test
    fun commentAtStartOfIndentedBlock() {
        val program = """
            x =
              # comment
              42
        """.trimIndent()
        assertTokens(program, ident("x", indent = 0), sym('='), num("42", indent = 2), eof)
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
            ident("x", indent = 0), sym('='),
            ident("y", indent = 2), sym('='), num("1"),
            ident("y", indent = 2), sym('+'), num("2"),
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
            ident("x", indent = 0), sym('='),
            ident("y", indent = 2), sym('='), num("1"),
            ident("y", indent = 2), sym('+'), num("2"),
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
        assertTokens(
            program,
            ident("foo", indent = 0), sym('('),
            ident("a", indent = 0), sym(','),
            ident("b", indent = 8), sym(','),
            ident("c", indent = 2),
            sym(')', indent = 0), eof,
        )
    }

    @Test
    fun unmatchedCloseParen() {
        assertTokens(")", sym(')', indent = 0), eof)
    }

    @Test
    fun unmatchedCloseBracket() {
        assertTokens("]", sym(']', indent = 0), eof)
    }

    @Test
    fun unmatchedClosePipe() {
        assertTokens("x|", ident("x", indent = 0), pipe, eof)
    }

    @Test
    fun closeParenThroughBracket() {
        assertTokens("([)", sym('(', indent = 0), sym('['), sym(')'), eof)
    }

    @Test
    fun closeBracketThroughParen() {
        assertTokens("[(]", sym('[', indent = 0), sym('('), sym(']'), eof)
    }

    @Test
    fun closeWithWrongDelimiterNotOnStack() {
        assertTokens("(]", sym('(', indent = 0), sym(']'), eof)
    }

    @Test
    fun closeWithWrongDelimiterNotOnStackReversed() {
        assertTokens("[)", sym('[', indent = 0), sym(')'), eof)
    }

    @Test
    fun deepStackCloseWithWrongDelimiter() {
        assertTokens(
            "((((((]",
            sym('(', indent = 0), sym('('), sym('('), sym('('), sym('('), sym('('),
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
            ident("x", indent = 0), sym('='),
            ident("y", indent = 4), sym('='), num("1"),
            ident("z", indent = 2),
            eof,
        )
    }

    @Test
    fun emptyLinesWithinBlock() {
        val program = """
            x =
                y = 1

                y + 2
        """.trimIndent()
        assertTokens(
            program,
            ident("x", indent = 0), sym('='),
            ident("y", indent = 4), sym('='), num("1"),
            ident("y", indent = 4), sym('+'), num("2"),
            eof,
        )
    }

    @Test
    fun emptyLineWithSpacesWithinBlock() {
        val program = """
        x =
            y = 1
            # empty line with indentation
            y + 2
        """.trimIndent()
        assertTokens(
            program,
            ident("x", indent = 0), sym('='),
            ident("y", indent = 4), sym('='), num("1"),
            ident("y", indent = 4), sym('+'), num("2"),
            eof,
        )
    }

    @Test
    fun emptyLineWithCommentWithinBlock() {
        val program = """
        x =
            y = 1
        # empty line without indentation
            y + 2
        """.trimIndent()
        assertTokens(
            program,
            ident("x", indent = 0), sym('='),
            ident("y", indent = 4), sym('='), num("1"),
            ident("y", indent = 4), sym('+'), num("2"),
            eof,
        )
    }

    @Test
    fun emptyLineWithHalfIndentation() {
        val program =
            "x = \n" +
            "    y = 1\n" +
            "  \n" +
            "    y + 2"
        assertTokens(
            program,
            ident("x", indent = 0), sym('='),
            ident("y", indent = 4), sym('='), num("1"),
            ident("y", indent = 4), sym('+'), num("2"),
            eof,
        )
    }

    @Test
    fun topLevelIndentedBindingIsNewStatement() {
        val program =
            "x = 1\n" +
            "\n" +
            "  y = 2\n" +
            "\n" +
            "z = 3"
        assertTokens(
            program,
            ident("x", indent = 0), sym('='), num("1"),
            ident("y", indent = 2), sym('='), num("2"),
            ident("z", indent = 0), sym('='), num("3"), eof,
        )
    }

    @Test
    fun topLevelIndentedOperatorIsContinuation() {
        val program =
            "x = 1\n" +
            "  + 2"
        assertTokens(
            program,
            ident("x", indent = 0), sym('='), num("1"),
            sym('+', indent = 2), num("2"), eof,
        )
    }

    @Test
    fun topLevelIndentedOperatorAfterBlankLineIsContinuation() {
        val program = """
            x = 1

              + 2"""
        assertTokens(
            program,
            ident("x", indent = 12), sym('='), num("1"),
            sym('+', indent = 14), num("2"), eof,
        )
    }

    @Test
    fun indentedBindingInBlockIsNewStatement() {
        val program = """
            result =
              x = 1
                y = 2
              x + y"""
        assertTokens(
            program,
            ident("result", indent = 12), sym('='),
            ident("x", indent = 14), sym('='), num("1"),
            ident("y", indent = 16), sym('='), num("2"),
            ident("x", indent = 14), sym('+'), ident("y"), eof,
        )
    }

    @Test
    fun indentedOperatorInBlockIsContinuation() {
        val program = """
            result =
              x = 1
                + 2
              x"""
        assertTokens(
            program,
            ident("result", indent = 12), sym('='),
            ident("x", indent = 14), sym('='), num("1"),
            sym('+', indent = 16), num("2"),
            ident("x", indent = 14), eof,
        )
    }
}
