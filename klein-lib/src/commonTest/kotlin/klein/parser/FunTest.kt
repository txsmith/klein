package klein.parser

import klein.ParseError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FunTest {
    @Test
    fun simpleFunctionDefinition() {
        assertProgramEquals(
            parseProgram("fun double(x) = x * 2"),
            listOf(funDef("double", "x", body = mul(id("x"), int(2)))),
        )
    }

    @Test
    fun functionWithMultipleParams() {
        assertProgramEquals(
            parseProgram("fun add(x, y) = x + y"),
            listOf(funDef("add", "x", "y", body = add(id("x"), id("y")))),
        )
    }

    @Test
    fun functionWithNoParams() {
        assertProgramEquals(
            parseProgram("fun thunk() = 42"),
            listOf(funDef("thunk", body = int(42))),
        )
    }

    @Test
    fun functionWithBlockBody() {
        val program =
            """
            fun calculate(a, b) =
              temp = a * 2
              temp + b
            """.trimIndent()
        assertProgramEquals(
            parseProgram(program),
            listOf(
                funDef(
                    "calculate",
                    "a",
                    "b",
                    body =
                        block(
                            valStmt("temp", mul(id("a"), int(2))),
                            add(id("temp"), id("b")),
                        ),
                ),
            ),
        )
    }

    @Test
    fun functionWithRecordBody() {
        assertProgramEquals(
            parseProgram("fun makePerson(name) = { name }"),
            listOf(funDef("makePerson", "name", body = record("name" to id("name")))),
        )
    }

    @Test
    fun functionWithEmptyRecordBody() {
        assertProgramEquals(
            parseProgram("fun empty() = {}"),
            listOf(funDef("empty", body = record())),
        )
    }

    @Test
    fun functionWithLambdaBody() {
        assertProgramEquals(
            parseProgram("fun curry(f) = |x -> |y -> f(x, y)||"),
            listOf(
                funDef(
                    "curry",
                    "f",
                    body = lambda("x", body = lambda("y", body = call(id("f"), id("x"), id("y")))),
                ),
            ),
        )
    }

    @Test
    fun functionWithIfBody() {
        assertProgramEquals(
            parseProgram("fun abs(x) = if x < 0 then -x else x"),
            listOf(
                funDef(
                    "abs",
                    "x",
                    body = ifThenElse(lt(id("x"), int(0)), neg(id("x")), id("x")),
                ),
            ),
        )
    }

    @Test
    fun multipleFunctionsInProgram() {
        val program =
            """
            fun add(x, y) = x + y
            fun mul(x, y) = x * y
            """.trimIndent()
        assertProgramEquals(
            parseProgram(program),
            listOf(
                funDef("add", "x", "y", body = add(id("x"), id("y"))),
                funDef("mul", "x", "y", body = mul(id("x"), id("y"))),
            ),
        )
    }

    @Test
    fun functionAndValMixed() {
        val program =
            """
            fun double(x) = x * 2
            result = double(21)
            """.trimIndent()
        assertProgramEquals(
            parseProgram(program),
            listOf(
                funDef("double", "x", body = mul(id("x"), int(2))),
                valStmt("result", call(id("double"), int(21))),
            ),
        )
    }

    @Test
    fun missingFunctionName() {
        assertFailsWith<ParseError> { parseProgram("fun (x) = x") }
    }

    @Test
    fun missingParens() {
        assertFailsWith<ParseError> { parseProgram("fun double x = x * 2") }
    }

    @Test
    fun missingEquals() {
        assertFailsWith<ParseError> { parseProgram("fun double(x) x * 2") }
    }

    @Test
    fun missingBody() {
        assertFailsWith<ParseError> { parseProgram("fun double(x) =") }
    }

    @Test
    fun funWithKeywordAsName() {
        assertFailsWith<ParseError> { parseProgram("fun if(x) = x") }
        assertFailsWith<ParseError> { parseProgram("fun then(x) = x") }
        assertFailsWith<ParseError> { parseProgram("fun else(x) = x") }
        assertFailsWith<ParseError> { parseProgram("fun true(x) = x") }
        assertFailsWith<ParseError> { parseProgram("fun false(x) = x") }
        assertFailsWith<ParseError> { parseProgram("fun and(x) = x") }
        assertFailsWith<ParseError> { parseProgram("fun or(x) = x") }
        assertFailsWith<ParseError> { parseProgram("fun not(x) = x") }
        assertFailsWith<ParseError> { parseProgram("fun fun(x) = x") }
    }

    @Test
    fun funWithSymbolAsName() {
        assertFailsWith<ParseError> { parseProgram("fun +(x) = x") }
        assertFailsWith<ParseError> { parseProgram("fun .(x) = x") }
        assertFailsWith<ParseError> { parseProgram("fun |(x) = x") }
    }

    @Test
    fun funInsideLambda() {
        val error = assertFailsWith<ParseError> { parse("|x -> fun inner(y) = y|") }
        assertEquals("Function definitions are only allowed at the top level", error.message)
    }

    @Test
    fun funInsideBlock() {
        val program =
            """
            result =
              fun nested(x) = x
              nested(1)
            """.trimIndent()
        val error = assertFailsWith<ParseError> { parseProgram(program) }
        assertEquals("Function definitions are only allowed at the top level", error.message)
    }

    @Test
    fun funInsideFun() {
        val program =
            """
            fun outer(x) =
              fun inner(y) = y
              inner(x)
            """.trimIndent()
        val error = assertFailsWith<ParseError> { parseProgram(program) }
        assertEquals("Function definitions are only allowed at the top level", error.message)
    }

    @Test
    fun funInsideRecord() {
        val error = assertFailsWith<ParseError> { parse("{ f = fun nested(x) = x }") }
        assertEquals("Function definitions are only allowed at the top level", error.message)
    }
}
