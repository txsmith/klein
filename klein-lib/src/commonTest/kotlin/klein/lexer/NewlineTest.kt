@file:Suppress("ktlint")

package klein.lexer

import klein.TokenKind.*
import kotlin.test.Test

class NewlineTest {
    @Test
    fun simpleBinding() {
        assertTokens("x = 1", ident("x"), sym('='), num("1"), eof)
    }

    @Test
    fun twoBindingsOnSeparateLines() {
        val program = """
            x = 1
            y = 2
        """.trimIndent()
        assertTokens(
            program,
            ident("x"), sym('='), num("1"), stmtEnd,
            ident("y"), sym('='), num("2"), eof
        )
    }

    @Test
    fun noNewlineAfterOperator() {
        val program = """
            x = 1 +
            2
        """.trimIndent()
        assertTokens(
            program,
            ident("x"), sym('='), num("1"), sym('+'), num("2"), eof
        )
    }

    @Test
    fun noIndentInExprPosition() {
        val program = """
            foo(
                a,
                b
            )
        """.trimIndent()
        assertTokens(program, ident("foo"), sym('('), ident("a"), sym(','), ident("b"), sym(')'), eof)
    }

    @Test
    fun noNewlineAfterComma() {
        val program = """
            x = foo(a,
                b)
        """.trimIndent()
        assertTokens(program,
            ident("x"), sym('='), ident("foo"), sym('('), ident("a"), sym(','),
            ident("b"), sym(')'), eof
        )
    }

    @Test
    fun multipleNewlinesCollapsed() {
        val program = """
            x = 1


            y = 2
        """.trimIndent()
        assertTokens(program,
            ident("x"), sym('='), num("1"), stmtEnd,
            ident("y"), sym('='), num("2"), eof
        )
    }

    @Test
    fun newlineAfterBoolLiteral() {
        val program = """
            x = true
            y = false
        """.trimIndent()
        assertTokens(program,
            ident("x"), sym('='), kw(TRUE), stmtEnd,
            ident("y"), sym('='), kw(FALSE), eof
        )
    }

    @Test
    fun noNewlineAfterComment() {
        val program = """
            x = foo(a, # )
                b)
        """.trimIndent()
        assertTokens(program, ident("x"), sym('='), ident("foo"), sym('('), ident("a"), sym(','), ident("b"), sym(')'), eof)
    }

    @Test
    fun noNewlineAfterEquals() {
        val program = """
            x =
            1
        """.trimIndent()
        assertTokens(program, ident("x"), sym('='), num("1"), eof)
    }
}
