package klein.parser

import kotlin.test.Test

class CommentTest {
    @Test
    fun bindingsWithComments() {
        val prog =
            parseProgram(
                """
                # comment
                x = 1
                # another
                y = 2
                """.trimIndent(),
            )
        assertProgramEquals(
            prog,
            listOf(
                valStmt("x", int(1)),
                valStmt("y", int(2)),
            ),
        )
    }

    @Test
    fun onlyComments() {
        val prog =
            parseProgram(
                """
                # just a comment
                # and another
                """.trimIndent(),
            )
        assertProgramEquals(prog, emptyList())
    }

    @Test
    fun commentAfterExpression() {
        val expr = parse("1 + 2 # add them")
        assertExprEquals(expr, add(int(1), int(2)))
    }

    @Test
    fun commentInMultilineExpression() {
        val expr =
            parse(
                """
                1 + # first operand
                2   # second operand
                """.trimIndent(),
            )
        assertExprEquals(expr, add(int(1), int(2)))
    }

    @Test
    fun commentAfterBinding() {
        val prog = parseProgram("x = 1 # assign x")
        assertProgramEquals(prog, listOf(valStmt("x", int(1))))
    }

    @Test
    fun commentBetweenOperatorAndOperand() {
        val expr =
            parse(
                """
                1 * # multiply
                2 + # then add
                3
                """.trimIndent(),
            )
        assertExprEquals(expr, add(mul(int(1), int(2)), int(3)))
    }

    @Test
    fun commentInFunctionCall() {
        val expr =
            parse(
                """
                foo(
                    a, # first arg
                    b  # second arg
                )
                """.trimIndent(),
            )
        assertExprEquals(expr, call(id("foo"), id("a"), id("b")))
    }
}
