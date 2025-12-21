package klein.parser

import klein.ParseError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FunTest {
    @Test
    fun simpleFunctionDefinition() {
        assertStmtEquals(
            parseStmt("fun double(x) = x * 2"),
            funDef("double", "x", body = mul(id("x"), int(2))),
        )
    }

    @Test
    fun functionWithMultipleParams() {
        assertStmtEquals(
            parseStmt("fun add(x, y) = x + y"),
            funDef("add", "x", "y", body = add(id("x"), id("y"))),
        )
    }

    @Test
    fun functionWithNoParams() {
        assertStmtEquals(
            parseStmt("fun thunk() = 42"),
            funDef("thunk", body = int(42)),
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
        assertStmtEquals(
            parseStmt(program),
            funDef(
                "calculate",
                "a",
                "b",
                body =
                    block(
                        valStmt("temp", mul(id("a"), int(2))),
                        expr = add(id("temp"), id("b")),
                    ),
            ),
        )
    }

    @Test
    fun functionWithRecordBody() {
        assertStmtEquals(
            parseStmt("fun makePerson(name) = { name }"),
            funDef("makePerson", "name", body = record("name" to id("name"))),
        )
    }

    @Test
    fun functionWithEmptyRecordBody() {
        assertStmtEquals(
            parseStmt("fun empty() = {}"),
            funDef("empty", body = record()),
        )
    }

    @Test
    fun functionWithLambdaBody() {
        assertStmtEquals(
            parseStmt("fun curry(f) = |x -> |y -> f(x, y)||"),
            funDef(
                "curry",
                "f",
                body = lambda("x", body = lambda("y", body = call(id("f"), id("x"), id("y")))),
            ),
        )
    }

    @Test
    fun functionWithIfBody() {
        assertStmtEquals(
            parseStmt("fun abs(x) = if x < 0 then -x else x"),
            funDef(
                "abs",
                "x",
                body = ifThenElse(lt(id("x"), int(0)), neg(id("x")), id("x")),
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
        assertFailsWith<ParseError> { parseStmt("fun (x) = x") }
    }

    @Test
    fun missingParens() {
        assertFailsWith<ParseError> { parseStmt("fun double x = x * 2") }
    }

    @Test
    fun missingEquals() {
        assertFailsWith<ParseError> { parseStmt("fun double(x) x * 2") }
    }

    @Test
    fun missingBody() {
        assertFailsWith<ParseError> { parseStmt("fun double(x) =") }
    }

    @Test
    fun funWithKeywordAsName() {
        assertFailsWith<ParseError> { parseStmt("fun if(x) = x") }
        assertFailsWith<ParseError> { parseStmt("fun then(x) = x") }
        assertFailsWith<ParseError> { parseStmt("fun else(x) = x") }
        assertFailsWith<ParseError> { parseStmt("fun true(x) = x") }
        assertFailsWith<ParseError> { parseStmt("fun false(x) = x") }
        assertFailsWith<ParseError> { parseStmt("fun and(x) = x") }
        assertFailsWith<ParseError> { parseStmt("fun or(x) = x") }
        assertFailsWith<ParseError> { parseStmt("fun not(x) = x") }
        assertFailsWith<ParseError> { parseStmt("fun fun(x) = x") }
    }

    @Test
    fun funWithSymbolAsName() {
        assertFailsWith<ParseError> { parseStmt("fun +(x) = x") }
        assertFailsWith<ParseError> { parseStmt("fun .(x) = x") }
        assertFailsWith<ParseError> { parseStmt("fun |(x) = x") }
    }

    @Test
    fun funInsideLambda() {
        val error = assertFailsWith<ParseError> { parseStmt("|x -> fun inner(y) = y|") }
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
        val error = assertFailsWith<ParseError> { parseStmt("{ f = fun nested(x) = x }") }
        assertEquals("Function definitions are only allowed at the top level", error.message)
    }
}
