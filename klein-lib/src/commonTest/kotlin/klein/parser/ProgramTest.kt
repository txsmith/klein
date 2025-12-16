package klein.parser

import klein.ParseError
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ProgramTest {
    @Test
    fun emptyProgram() {
        val prog = parseProgram("")
        assertProgramEquals(prog, emptyList())
    }

    @Test
    fun singleBinding() {
        val prog = parseProgram("x = 1")
        assertProgramEquals(prog, listOf(valStmt("x", int(1))))
    }

    @Test
    fun twoBindings() {
        val program =
            """
            x = 1
            y = 2
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("x", int(1)), valStmt("y", int(2))))
    }

    @Test
    fun threeBindings() {
        val program =
            """
            a = 1
            b = 2
            c = 3
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("a", int(1)), valStmt("b", int(2)), valStmt("c", int(3))))
    }

    @Test
    fun bindingsWithExpressions() {
        val program =
            """
            x = 1 + 2
            y = x * 3
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("x", add(int(1), int(2))), valStmt("y", mul(id("x"), int(3)))))
    }

    @Test
    fun bindingsWithLambdas() {
        val program =
            """
            f = |x -> x + 1|
            g = |y -> f(y)|
            """.trimIndent()
        assertProgramEquals(
            parseProgram(program),
            listOf(valStmt("f", lambda("x", body = add(id("x"), int(1)))), valStmt("g", lambda("y", body = call(id("f"), id("y"))))),
        )
    }

    @Test
    fun bindingsOnSameLineIsError() {
        assertFailsWith<ParseError> {
            parseProgram("x = 1 y = 2")
        }
    }

    @Test
    fun bindingsWithExtraWhitespace() {
        val program =
            """
            x = 1

            y = 2
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("x", int(1)), valStmt("y", int(2))))
    }

    @Test
    fun expressionStatements() {
        val program =
            """
            x = 10
            ask(x)
            y = 20
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("x", int(10)), call(id("ask"), id("x")), valStmt("y", int(20))))
    }

    @Test
    fun multipleExpressionStatements() {
        val program =
            """
            foo()
            bar(1, 2)
            baz
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(call(id("foo")), call(id("bar"), int(1), int(2)), id("baz")))
    }
}
