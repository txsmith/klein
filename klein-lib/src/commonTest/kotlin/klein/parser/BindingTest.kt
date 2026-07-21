package klein.parser

import klein.ParseError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BindingTest {
    @Test
    fun simpleBinding() {
        val stmt = parseStmt("x = 1")
        assertStmtEquals(stmt, valStmt("x", int(1)))
    }

    @Test
    fun bindingWithExpression() {
        val stmt = parseStmt("sum = 1 + 2")
        assertStmtEquals(stmt, valStmt("sum", add(int(1), int(2))))
    }

    @Test
    fun bindingWithComplexExpression() {
        val stmt = parseStmt("result = a * b + c")
        assertStmtEquals(stmt, valStmt("result", add(mul(id("a"), id("b")), id("c"))))
    }

    @Test
    fun bindingWithLambda() {
        val stmt = parseStmt("f = |x -> x + 1|")
        assertStmtEquals(stmt, valStmt("f", lambda("x", body = add(id("x"), int(1)))))
    }

    @Test
    fun bindingWithThunk() {
        val stmt = parseStmt("lazy = |42|")
        assertStmtEquals(stmt, valStmt("lazy", lambda(body = int(42))))
    }

    @Test
    fun bindingWithCall() {
        val stmt = parseStmt("y = f(x)")
        assertStmtEquals(stmt, valStmt("y", call(id("f"), id("x"))))
    }

    @Test
    fun bindingWithBoolean() {
        val stmt = parseStmt("flag = true")
        assertStmtEquals(stmt, valStmt("flag", bool(true)))
    }

    @Test
    fun bindingWithLogicalExpr() {
        val stmt = parseStmt("check = a and b or c")
        assertStmtEquals(stmt, valStmt("check", or(and(id("a"), id("b")), id("c"))))
    }

    @Test
    fun bindingWithComparison() {
        val stmt = parseStmt("isPositive = x > 0")
        assertStmtEquals(stmt, valStmt("isPositive", gt(id("x"), int(0))))
    }

    @Test
    fun bindingWithString() {
        val stmt = parseStmt("name = \"hello\"")
        assertStmtEquals(stmt, valStmt("name", string("hello")))
    }

    @Test
    fun bindingWithDouble() {
        val stmt = parseStmt("pi = 3.14")
        assertStmtEquals(stmt, valStmt("pi", double(3.14)))
    }

    @Test
    fun bindingWithNegation() {
        val stmt = parseStmt("neg = -x")
        assertStmtEquals(stmt, valStmt("neg", neg(id("x"))))
    }

    @Test
    fun bindingWithNot() {
        val stmt = parseStmt("inverted = not flag")
        assertStmtEquals(stmt, valStmt("inverted", not(id("flag"))))
    }

    @Test
    fun bindingWithParenthesized() {
        val stmt = parseStmt("grouped = (1 + 2) * 3")
        assertStmtEquals(stmt, valStmt("grouped", mul(add(int(1), int(2)), int(3))))
    }

    @Test
    fun bindingWithNestedCalls() {
        val stmt = parseStmt("composed = f(g(x))")
        assertStmtEquals(stmt, valStmt("composed", call(id("f"), call(id("g"), id("x")))))
    }

    @Test
    fun bindingWithCurriedLambda() {
        val stmt = parseStmt("add = |x -> |y -> x + y||")
        assertStmtEquals(stmt, valStmt("add", lambda("x", body = lambda("y", body = add(id("x"), id("y"))))))
    }

    @Test
    fun underscoreInName() {
        val stmt = parseStmt("my_var = 1")
        assertStmtEquals(stmt, valStmt("my_var", int(1)))
    }

    @Test
    fun missingValue() {
        val error = assertFailsWith<ParseError> { parseStmt("x =") }
        assertEquals("Expected expression, got Eof", error.message)
    }

    @Test
    fun keywordAsName() {
        val error = assertFailsWith<ParseError> { parseStmt("true = 1") }
        assertEquals("Expected identifier, got keyword 'true'", error.message)
    }

    @Test
    fun destructuringBinding() {
        val stmt = parseStmt("{ name, age } = person")
        assertStmtEquals(stmt, patternVal(recordP(fieldP("name"), fieldP("age")), id("person")))
    }

    @Test
    fun destructuringWithRenameAndWildcard() {
        val stmt = parseStmt("{ name = n, age = _ } = person")
        assertStmtEquals(stmt, patternVal(recordP(fieldP("name", "n"), fieldP("age", null)), id("person")))
    }

    @Test
    fun destructuringInAFunctionBody() {
        val stmt =
            parseTopLevel(
                """
                fun f(p: { a: Num }): Num =
                  { a } = p
                  a
                """.trimIndent(),
            )
        assertStmtEquals(
            stmt,
            funDef(
                "f",
                listOf(param("p", recordType("a" to typeName("Num")))),
                block(patternVal(recordP(fieldP("a")), id("p")), id("a")),
                typeName("Num"),
            ),
        )
    }

    @Test
    fun recordComparisonIsNotADestructuring() {
        val stmt = parseStmt("x = { a } == b")
        assertStmtEquals(stmt, valStmt("x", eq(record("a" to id("a")), id("b"))))
    }

    @Test
    fun bareRecordLiteralStatementStillParses() {
        val stmt = parseStmt("{ a = 1 }")
        assertStmtEquals(stmt, record("a" to int(1)))
    }

    @Test
    fun annotatedDestructuringIsRejected() {
        assertFailsWith<ParseError> { parseStmt("{ name }: Person = x") }
    }

    @Test
    fun emptyDestructuringPatternIsRejected() {
        assertFailsWith<ParseError> { parseStmt("{} = x") }
    }

    @Test
    fun constructorDestructuringBinding() {
        val stmt = parseStmt("Person { name } = someone")
        assertStmtEquals(stmt, patternVal(ctorP("Person", fieldP("name")), id("someone")))
    }

    @Test
    fun constructorBinderBinding() {
        val stmt = parseStmt("Circle c = circle")
        assertStmtEquals(stmt, patternVal(ctorBindP("Circle", "c"), id("circle")))
    }

    @Test
    fun bareConstructorIsNotABinding() {
        assertFailsWith<ParseError> { parseStmt("Circle = x") }
    }

    @Test
    fun literalPatternsAreNotBindings() {
        assertFailsWith<ParseError> { parseStmt("42 = x") }
        assertFailsWith<ParseError> { parseStmt("-1 = x") }
        assertFailsWith<ParseError> { parseStmt("\"a\" = x") }
        assertFailsWith<ParseError> { parseStmt("true = x") }
        assertFailsWith<ParseError> { parseStmt("null = x") }
    }

    @Test
    fun literalInDestructuringFieldIsRejected() {
        assertFailsWith<ParseError> { parseStmt("{ a = 1 } = p") }
    }

    @Test
    fun numberAsName() {
        val error = assertFailsWith<ParseError> { parseStmt("123 = 1") }
        assertTrue(error.message!!.startsWith("Expected newline but got '='"))
    }

    @Test
    fun nestedBlockFollowedByBinding() {
        val program =
            """
            x =
              y = 1
              y
            z = 2
            """.trimIndent()
        assertProgramEquals(
            parseProgram(program),
            listOf(
                valStmt("x", block(valStmt("y", int(1)), id("y"))),
                valStmt("z", int(2)),
            ),
        )
    }

    @Test
    fun blockEndingInBinding() {
        val program =
            """
            x =
              y = 1
            z = 2
            """.trimIndent()
        assertProgramEquals(
            parseProgram(program),
            listOf(
                valStmt("x", block(valStmt("y", int(1)))),
                valStmt("z", int(2)),
            ),
        )
    }

    @Test
    fun deeperIndentStillInBlock() {
        val program =
            """
            main =
              foo()
                q = 1
              y = 1
            """.trimIndent()
        assertProgramEquals(
            parseProgram(program),
            listOf(
                valStmt(
                    "main",
                    block(call(id("foo")), valStmt("q", int(1)), valStmt("y", int(1))),
                ),
            ),
        )
    }
}
