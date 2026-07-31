package klein.parser

import klein.surface.ParseError
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
    fun bindingWithLongName() {
        val prog = parseProgram("myVariable = 42")
        assertProgramEquals(prog, listOf(valStmt("myVariable", int(42))))
    }

    @Test
    fun bindingWithUnderscores() {
        val prog = parseProgram("my_variable_name = 123")
        assertProgramEquals(prog, listOf(valStmt("my_variable_name", int(123))))
    }

    @Test
    fun bindingWithNumbersInName() {
        val prog = parseProgram("value2 = x1 + y2")
        assertProgramEquals(prog, listOf(valStmt("value2", add(id("x1"), id("y2")))))
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

    @Test
    fun bindingWithBlock() {
        val program =
            """
            result =
              x = 1
              y = 2
              x + y
            """.trimIndent()
        assertProgramEquals(
            parseProgram(program),
            listOf(valStmt("result", block(valStmt("x", int(1)), valStmt("y", int(2)), add(id("x"), id("y"))))),
        )
    }

    @Test
    fun bindingWithBlockFollowedByBinding() {
        val program =
            """
            a =
              x = 1
              x
            b = 2
            """.trimIndent()
        assertProgramEquals(
            parseProgram(program),
            listOf(
                valStmt("a", block(valStmt("x", int(1)), id("x"))),
                valStmt("b", int(2)),
            ),
        )
    }

    @Test
    fun nestedBlocks() {
        val program =
            """
            outer =
              inner =
                x = 1
                x
              inner + 1
            """.trimIndent()
        assertProgramEquals(
            parseProgram(program),
            listOf(
                valStmt(
                    "outer",
                    block(
                        valStmt("inner", block(valStmt("x", int(1)), id("x"))),
                        add(id("inner"), int(1)),
                    ),
                ),
            ),
        )
    }

    @Test
    fun blockWithIfThenElse() {
        val program =
            """
            result =
              x = getValue()
              if x > 0 then
                x
              else
                0
            """.trimIndent()
        assertProgramEquals(
            parseProgram(program),
            listOf(
                valStmt(
                    "result",
                    block(
                        valStmt("x", call(id("getValue"))),
                        ifThenElse(gt(id("x"), int(0)), block(id("x")), block(int(0))),
                    ),
                ),
            ),
        )
    }

    @Test
    fun blockWithIfThenNoElse() {
        val program =
            """
            main =
              if ready() then
                doWork()
              cleanup()
            """.trimIndent()
        assertProgramEquals(
            parseProgram(program),
            listOf(
                valStmt(
                    "main",
                    block(
                        ifThenElse(call(id("ready")), block(call(id("doWork")))),
                        call(id("cleanup")),
                    ),
                ),
            ),
        )
    }

    @Test
    fun negationAsStatement() {
        val program =
            """
            x = 1
            -a
            """.trimIndent()
        assertProgramEquals(
            parseProgram(program),
            listOf(valStmt("x", int(1)), neg(id("a"))),
        )
    }
}
