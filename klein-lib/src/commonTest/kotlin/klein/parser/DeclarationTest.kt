package klein.parser

import klein.surface.Block
import klein.surface.FunDecl
import klein.surface.FunDef
import klein.surface.ParseError
import klein.surface.ValDecl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Declarations without definitions — the shape a capability contract file is written in.
 * `fun f(x: T): U` with no `= body`, and `name: T` with no `= value`.
 */
class DeclarationTest {
    @Test
    fun funDeclarationWithoutBody() {
        assertProgramEquals(
            parseProgram("fun creditCheck(c: Customer): Num"),
            listOf(funDecl("creditCheck", listOf(param("c", typeName("Customer"))), typeName("Num"))),
        )
    }

    @Test
    fun funDeclarationWithNoParams() {
        assertProgramEquals(
            parseProgram("fun now(): Num"),
            listOf(funDecl("now", emptyList(), typeName("Num"))),
        )
    }

    @Test
    fun funDeclarationWithMultipleParams() {
        assertProgramEquals(
            parseProgram("fun rate(from: String, to: String): Num"),
            listOf(
                funDecl(
                    "rate",
                    listOf(param("from", typeName("String")), param("to", typeName("String"))),
                    typeName("Num"),
                ),
            ),
        )
    }

    @Test
    fun funDeclarationWithFunctionReturnType() {
        assertProgramEquals(
            parseProgram("fun adder(n: Num): (Num) -> Num"),
            listOf(
                funDecl(
                    "adder",
                    listOf(param("n", typeName("Num"))),
                    functionType(typeName("Num"), typeName("Num")),
                ),
            ),
        )
    }

    @Test
    fun valDeclarationWithoutValue() {
        assertProgramEquals(
            parseProgram("maxRetries: Num"),
            listOf(valDecl("maxRetries", typeName("Num"))),
        )
    }

    @Test
    fun valDeclarationWithFunctionType() {
        assertProgramEquals(
            parseProgram("classify: (Num) -> String"),
            listOf(valDecl("classify", functionType(typeName("Num"), typeName("String")))),
        )
    }

    @Test
    fun contractFileParsesTypeDefsAndDeclarations() {
        val source =
            """
            type Customer = Customer { id: Num, name: String }

            fun creditCheck(c: Customer): Num
            maxRetries: Num
            """.trimIndent()
        val stmts = parseProgram(source).stmts
        assertEquals(3, stmts.size)
        assertIs<FunDecl>(stmts[1])
        assertIs<ValDecl>(stmts[2])
    }

    // The indentation risk: a bodiless `fun` must not swallow the lines that follow it.

    @Test
    fun funDeclarationDoesNotSwallowTheNextDeclaration() {
        val source =
            """
            fun creditCheck(c: Num): Num
            fun riskScore(c: Num): Num
            """.trimIndent()
        assertProgramEquals(
            parseProgram(source),
            listOf(
                funDecl("creditCheck", listOf(param("c", typeName("Num"))), typeName("Num")),
                funDecl("riskScore", listOf(param("c", typeName("Num"))), typeName("Num")),
            ),
        )
    }

    @Test
    fun funDeclarationDoesNotSwallowAFollowingIndentedBlockFunction() {
        val source =
            """
            fun creditCheck(c: Num): Num
            fun square(n: Num): Num =
                m = n * n
                m
            """.trimIndent()
        val stmts = parseProgram(source).stmts
        assertEquals(2, stmts.size)
        assertIs<FunDecl>(stmts[0])
        val def = assertIs<FunDef>(stmts[1])
        assertEquals("square", def.name)
        assertIs<Block>(def.body)
    }

    @Test
    fun funDeclarationDoesNotSwallowAFollowingExpression() {
        val source =
            """
            fun creditCheck(c: Num): Num
            1 + 2
            """.trimIndent()
        val stmts = parseProgram(source).stmts
        assertEquals(2, stmts.size)
        assertIs<FunDecl>(stmts[0])
        assertExprEquals(stmts[1] as klein.surface.Expr, add(int(1), int(2)))
    }

    @Test
    fun valDeclarationDoesNotSwallowTheNextStatement() {
        val source =
            """
            maxRetries: Num
            limit: Num
            """.trimIndent()
        assertProgramEquals(
            parseProgram(source),
            listOf(valDecl("maxRetries", typeName("Num")), valDecl("limit", typeName("Num"))),
        )
    }

    @Test
    fun valDeclarationDoesNotSwallowAFollowingBinding() {
        val source =
            """
            maxRetries: Num
            x = 1
            """.trimIndent()
        assertProgramEquals(
            parseProgram(source),
            listOf(valDecl("maxRetries", typeName("Num")), valStmt("x", int(1))),
        )
    }

    @Test
    fun valDeclarationDoesNotSwallowAFollowingIndentedBlockFunction() {
        val source =
            """
            maxRetries: Num
            fun square(n: Num): Num =
                m = n * n
                m
            """.trimIndent()
        val stmts = parseProgram(source).stmts
        assertEquals(2, stmts.size)
        assertIs<ValDecl>(stmts[0])
        val def = assertIs<FunDef>(stmts[1])
        assertIs<Block>(def.body)
    }

    // The type of a declaration ends with its line: a '|' opening the next line is a lambda, not
    // the continuation of a union type.
    @Test
    fun valDeclarationDoesNotSwallowAFollowingLambda() {
        val source =
            """
            handler: Num
            |x -> x|
            """.trimIndent()
        val stmts = parseProgram(source).stmts
        assertEquals(2, stmts.size)
        assertIs<ValDecl>(stmts[0])
        assertIs<klein.surface.Lambda>(stmts[1])
    }

    @Test
    fun valDeclarationIsAStatementInsideABlock() {
        val source =
            """
            result =
                limit: Num
                1
            """.trimIndent()
        val stmt = parseProgram(source).stmts.single()
        val binding = assertIs<klein.surface.Val>(stmt)
        val block = assertIs<Block>(binding.value)
        assertEquals(2, block.stmts.size)
        assertIs<ValDecl>(block.stmts[0])
    }

    // Definitions keep parsing as definitions.

    @Test
    fun funWithBodyIsStillADefinition() {
        assertProgramEquals(
            parseProgram("fun double(x: Num): Num = x * 2"),
            listOf(
                funDef("double", listOf(param("x", typeName("Num"))), mul(id("x"), int(2)), typeName("Num")),
            ),
        )
    }

    @Test
    fun annotatedBindingWithValueIsStillAVal() {
        assertProgramEquals(
            parseProgram("x: Num = 42"),
            listOf(valStmt("x", int(42), typeName("Num"))),
        )
    }

    @Test
    fun funWithNeitherReturnTypeNorBodyIsAParseError() {
        assertFailsWith<ParseError> { parseProgram("fun mystery()") }
    }
}
