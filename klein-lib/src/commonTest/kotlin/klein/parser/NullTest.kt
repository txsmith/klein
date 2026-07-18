@file:Suppress("ktlint")

package klein.parser

import klein.ParseError
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Parser tests for the `null` literal.
 *
 * These tests verify that the parser correctly parses `null` as a NullLiteral
 * AST node and handles various contexts where null can appear.
 */
class NullTest {

    // =========================================================================
    // SECTION 1: Basic Null Parsing
    // =========================================================================

    @Test
    fun nullLiteral() {
        val expr = parse("null")
        assertExprEquals(expr, nullLit())
    }

    @Test
    fun nullInParens() {
        val expr = parse("(null)")
        assertExprEquals(expr, nullLit())
    }

    @Test
    fun nullInNestedParens() {
        val expr = parse("((null))")
        assertExprEquals(expr, nullLit())
    }

    // =========================================================================
    // SECTION 2: Null in If-Then-Else
    // =========================================================================

    @Test
    fun nullInElseBranch() {
        val expr = parse("if true then 42 else null")
        assertExprEquals(expr, ifThenElse(bool(true), int(42), nullLit()))
    }

    @Test
    fun nullInThenBranch() {
        val expr = parse("if false then null else 42")
        assertExprEquals(expr, ifThenElse(bool(false), nullLit(), int(42)))
    }

    @Test
    fun nullInBothBranches() {
        val expr = parse("if b then null else null")
        assertExprEquals(expr, ifThenElse(id("b"), nullLit(), nullLit()))
    }

    @Test
    fun nullInNestedIfElse_inner() {
        val expr = parse("if a then if b then 1 else null else 2")
        assertExprEquals(
            expr,
            ifThenElse(
                id("a"),
                ifThenElse(id("b"), int(1), nullLit()),
                int(2)
            )
        )
    }

    @Test
    fun nullInNestedIfElse_outer() {
        val expr = parse("if a then 1 else if b then 2 else null")
        assertExprEquals(
            expr,
            ifThenElse(
                id("a"),
                int(1),
                ifThenElse(id("b"), int(2), nullLit())
            )
        )
    }

    @Test
    fun nullInCondition_parses() {
        // This will be a type error, but should parse successfully
        val expr = parse("if null then 1 else 2")
        assertExprEquals(expr, ifThenElse(nullLit(), int(1), int(2)))
    }

    // =========================================================================
    // SECTION 3: Null in Bindings
    // =========================================================================

    @Test
    fun nullAssignment() {
        val stmt = parseStmt("x = null")
        assertStmtEquals(stmt, valStmt("x", nullLit()))
    }

    @Test
    fun nullAssignment_withIfElse() {
        val stmt = parseStmt("maybeNum = if b then 42 else null")
        assertStmtEquals(
            stmt,
            valStmt("maybeNum", ifThenElse(id("b"), int(42), nullLit()))
        )
    }

    @Test
    fun nullInMultipleBindings() {
        val program = parseProgram("""
            x = null
            y = null
            z = if true then x else y
        """.trimIndent())
        assertProgramEquals(
            program,
            listOf(
                valStmt("x", nullLit()),
                valStmt("y", nullLit()),
                valStmt("z", ifThenElse(bool(true), id("x"), id("y")))
            )
        )
    }

    // =========================================================================
    // SECTION 4: Null in Function Calls
    // =========================================================================

    @Test
    fun nullAsArgument() {
        val expr = parse("f(null)")
        assertExprEquals(expr, call(id("f"), nullLit()))
    }

    @Test
    fun nullAsFirstArgument() {
        val expr = parse("f(null, 42)")
        assertExprEquals(expr, call(id("f"), nullLit(), int(42)))
    }

    @Test
    fun nullAsSecondArgument() {
        val expr = parse("f(42, null)")
        assertExprEquals(expr, call(id("f"), int(42), nullLit()))
    }

    @Test
    fun nullAsAllArguments() {
        val expr = parse("f(null, null, null)")
        assertExprEquals(expr, call(id("f"), nullLit(), nullLit(), nullLit()))
    }

    @Test
    fun nullInNestedCall() {
        val expr = parse("f(g(null))")
        assertExprEquals(expr, call(id("f"), call(id("g"), nullLit())))
    }

    // =========================================================================
    // SECTION 5: Null in Lambdas
    // =========================================================================

    @Test
    fun lambdaReturningNull() {
        val expr = parse("|x -> null|")
        assertExprEquals(expr, lambda("x", body = nullLit()))
    }

    @Test
    fun lambdaReturningNullConditionally() {
        val expr = parse("|x -> if x then 42 else null|")
        assertExprEquals(
            expr,
            lambda("x", body = ifThenElse(id("x"), int(42), nullLit()))
        )
    }

    @Test
    fun lambdaWithMultipleParams_returningNull() {
        val expr = parse("|x, y -> null|")
        assertExprEquals(expr, lambda("x", "y", body = nullLit()))
    }

    @Test
    fun nestedLambdaReturningNull() {
        val expr = parse("|x -> |y -> null||")
        assertExprEquals(
            expr,
            lambda("x", body = lambda("y", body = nullLit()))
        )
    }

    @Test
    fun lambdaWithImplicitParam_returningNull() {
        val expr = parse("|if . then 42 else null|")
        assertExprEquals(
            expr,
            lambda(body = ifThenElse(implicitParam(), int(42), nullLit()))
        )
    }

    // =========================================================================
    // SECTION 6: Null in Records
    // =========================================================================

    @Test
    fun nullAsRecordFieldValue() {
        val expr = parse("{ x = null }")
        assertExprEquals(expr, record("x" to nullLit()))
    }

    @Test
    fun nullInMultipleRecordFields() {
        val expr = parse("{ x = null, y = null }")
        assertExprEquals(expr, record("x" to nullLit(), "y" to nullLit()))
    }

    @Test
    fun nullInMixedRecordFields() {
        val expr = parse("{ name = \"Alice\", age = 30, spouse = null }")
        assertExprEquals(
            expr,
            record(
                "name" to string("Alice"),
                "age" to int(30),
                "spouse" to nullLit()
            )
        )
    }

    @Test
    fun nullInNestedRecord() {
        val expr = parse("{ outer = { inner = null } }")
        assertExprEquals(
            expr,
            record("outer" to record("inner" to nullLit()))
        )
    }

    @Test
    fun nullConditionalInRecordField() {
        val expr = parse("{ value = if b then 42 else null }")
        assertExprEquals(
            expr,
            record("value" to ifThenElse(id("b"), int(42), nullLit()))
        )
    }

    // =========================================================================
    // SECTION 7: Null with Operators (Type Errors, but Should Parse)
    // =========================================================================

    @Test
    fun nullWithAddition_parses() {
        // This is a type error, but should parse
        val expr = parse("null + 1")
        assertExprEquals(expr, add(nullLit(), int(1)))
    }

    @Test
    fun nullWithSubtraction_parses() {
        val expr = parse("42 - null")
        assertExprEquals(expr, sub(int(42), nullLit()))
    }

    @Test
    fun nullWithMultiplication_parses() {
        val expr = parse("null * null")
        assertExprEquals(expr, mul(nullLit(), nullLit()))
    }

    @Test
    fun nullWithDivision_parses() {
        val expr = parse("null / 2")
        assertExprEquals(expr, div(nullLit(), int(2)))
    }

    @Test
    fun nullWithModulo_parses() {
        val expr = parse("null % 3")
        assertExprEquals(expr, mod(nullLit(), int(3)))
    }

    @Test
    fun nullWithNegation_parses() {
        val expr = parse("-null")
        assertExprEquals(expr, neg(nullLit()))
    }

    @Test
    fun nullWithNot_parses() {
        val expr = parse("not null")
        assertExprEquals(expr, not(nullLit()))
    }

    @Test
    fun nullWithAnd_parses() {
        val expr = parse("true and null")
        assertExprEquals(expr, and(bool(true), nullLit()))
    }

    @Test
    fun nullWithOr_parses() {
        val expr = parse("null or false")
        assertExprEquals(expr, or(nullLit(), bool(false)))
    }

    // =========================================================================
    // SECTION 8: Null in Comparisons
    // =========================================================================

    @Test
    fun nullEqualsNull() {
        val expr = parse("null == null")
        assertExprEquals(expr, eq(nullLit(), nullLit()))
    }

    @Test
    fun nullNotEqualsNull() {
        val expr = parse("null != null")
        assertExprEquals(expr, neq(nullLit(), nullLit()))
    }

    @Test
    fun valueEqualsNull() {
        val expr = parse("x == null")
        assertExprEquals(expr, eq(id("x"), nullLit()))
    }

    @Test
    fun nullEqualsValue() {
        val expr = parse("null == x")
        assertExprEquals(expr, eq(nullLit(), id("x")))
    }

    @Test
    fun valueNotEqualsNull() {
        val expr = parse("x != null")
        assertExprEquals(expr, neq(id("x"), nullLit()))
    }

    @Test
    fun nullLessThan_parses() {
        // Type error, but should parse
        val expr = parse("null < 5")
        assertExprEquals(expr, lt(nullLit(), int(5)))
    }

    @Test
    fun nullGreaterThan_parses() {
        val expr = parse("null > 5")
        assertExprEquals(expr, gt(nullLit(), int(5)))
    }

    // =========================================================================
    // SECTION 9: Null in Function Definitions
    // =========================================================================

    @Test
    fun functionReturningNull() {
        val stmt = parseTopLevel("fun nothing(x) = null")
        assertStmtEquals(stmt, funDef("nothing", "x", body = nullLit()))
    }

    @Test
    fun functionReturningNullConditionally() {
        val stmt = parseTopLevel("fun maybeDouble(x, b) = if b then x * 2 else null")
        assertStmtEquals(
            stmt,
            funDef(
                "maybeDouble", "x", "b",
                body = ifThenElse(id("b"), mul(id("x"), int(2)), nullLit())
            )
        )
    }

    @Test
    fun functionWithNullInBody() {
        val program = parseProgram("""
            fun process(data) =
                result = if data.valid then data.value else null
                { output = result }
        """.trimIndent())
        assertProgramEquals(
            program,
            listOf(
                funDef(
                    "process", "data",
                    body = block(
                        valStmt(
                            "result",
                            ifThenElse(
                                fieldAccess(id("data"), "valid"),
                                fieldAccess(id("data"), "value"),
                                nullLit()
                            )
                        ),
                        record("output" to id("result"))
                    )
                )
            )
        )
    }

    // =========================================================================
    // SECTION 10: Null in Complex Expressions
    // =========================================================================

    @Test
    fun nullInChainedIfElse() {
        val expr = parse("if a then 1 else if b then 2 else if c then 3 else null")
        assertExprEquals(
            expr,
            ifThenElse(
                id("a"),
                int(1),
                ifThenElse(
                    id("b"),
                    int(2),
                    ifThenElse(id("c"), int(3), nullLit())
                )
            )
        )
    }

    @Test
    fun nullInComplexExpression() {
        val expr = parse("if f(x) == null then g(null) else null")
        assertExprEquals(
            expr,
            ifThenElse(
                eq(call(id("f"), id("x")), nullLit()),
                call(id("g"), nullLit()),
                nullLit()
            )
        )
    }

    @Test
    fun nullInRecordAccessChain_parses() {
        // This will be a type error (field access on potential null)
        // but should parse successfully
        val program = parseProgram("""
            r = { inner = null }
            r.inner
        """.trimIndent())
        assertProgramEquals(
            program,
            listOf(
                valStmt("r", record("inner" to nullLit())),
                fieldAccess(id("r"), "inner")
            )
        )
    }

    @Test
    fun nullWithFieldAccess_parses() {
        // null.x is a type error but should parse
        val expr = parse("null.x")
        assertExprEquals(expr, fieldAccess(nullLit(), "x"))
    }

    // =========================================================================
    // SECTION 11: Null in Multiline Expressions
    // =========================================================================

    @Test
    fun nullInMultilineIfElse() {
        val expr = parse("""
            if true
            then 42
            else null
        """.trimIndent())
        assertExprEquals(expr, ifThenElse(bool(true), int(42), nullLit()))
    }

    @Test
    fun nullInMultilineBlock() {
        val expr = parse("""
            |
              x = null
              y = if true then 1 else null
              y
            |
        """.trimIndent())
        assertExprEquals(
            expr,
            lambda(body = block(
                valStmt("x", nullLit()),
                valStmt("y", ifThenElse(bool(true), int(1), nullLit())),
                id("y")
            ))
        )
    }

    @Test
    fun nullInMultilineRecord() {
        val expr = parse("""
            {
                name = "Alice",
                spouse = null,
                age = 30
            }
        """.trimIndent())
        assertExprEquals(
            expr,
            record(
                "name" to string("Alice"),
                "spouse" to nullLit(),
                "age" to int(30)
            )
        )
    }

    @Test
    fun nullInMultilineLambda() {
        val expr = parse("""
            |x ->
                result = if x then 42 else null
                result|
        """.trimIndent())
        assertExprEquals(
            expr,
            lambda(
                "x",
                body = block(
                    valStmt("result", ifThenElse(id("x"), int(42), nullLit())),
                    id("result")
                )
            )
        )
    }

    // =========================================================================
    // SECTION 12: Null Cannot Be Used as Identifier
    // =========================================================================
    // These tests verify that using `null` as an identifier causes parse errors.

    @Test
    fun nullAsVariableName_fails() {
        val error = assertFailsWith<ParseError> { parseStmt("null = 42") }
        // Expects identifier for binding, got keyword
        assertTrue(error.message?.contains("null") == true || error.message?.contains("identifier") == true)
    }

    @Test
    fun nullAsFunctionName_fails() {
        val error = assertFailsWith<ParseError> { parseTopLevel("fun null(x) = x") }
        // Expects identifier for function name, got keyword
        assertTrue(error.message?.contains("null") == true || error.message?.contains("identifier") == true)
    }

    @Test
    fun nullAsParameterName_fails() {
        val error = assertFailsWith<ParseError> { parse("|null -> 42|") }
        // Expects identifier for parameter, got keyword
        assertTrue(error.message?.contains("null") == true || error.message?.contains("identifier") == true)
    }

    @Test
    fun nullAsMultipleParameterName_fails() {
        val error = assertFailsWith<ParseError> { parse("|x, null -> 42|") }
        // Expects identifier for parameter, got keyword
        assertTrue(error.message?.contains("null") == true || error.message?.contains("identifier") == true)
    }

    @Test
    fun nullAsFunctionParameter_fails() {
        val error = assertFailsWith<ParseError> { parseTopLevel("fun f(null) = 42") }
        // Expects identifier for parameter, got keyword
        assertTrue(error.message?.contains("null") == true || error.message?.contains("identifier") == true)
    }

    @Test
    fun nullAsRecordFieldName_fails() {
        val error = assertFailsWith<ParseError> { parse("{ null = 42 }") }
        // Expects identifier for field name, got keyword
        assertTrue(error.message?.contains("null") == true || error.message?.contains("identifier") == true)
    }

    @Test
    fun nullInFieldAccess_fails() {
        val error = assertFailsWith<ParseError> { parse("x.null") }
        // Expects identifier after '.', got keyword
        assertTrue(error.message?.contains("null") == true || error.message?.contains("identifier") == true)
    }
}
