package klein.parser

import klein.surface.FunDecl
import klein.surface.Abort
import klein.surface.ValDecl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A contract and a rule are two disjoint languages that share a lexer and a type grammar. They no
 * longer share a root or an entry point: `parseContract` reads the one that only declares,
 * `parseProgram` reads the one that only runs, and a form written into the wrong file is a parse
 * error at the place it is written.
 */
class ContractTest {
    // ── What a contract accepts ──────────────────────────────────────────────

    @Test
    fun contractTakesTypeDefinitionsAndBothDeclarationForms() {
        val contract =
            parseContract(
                """
                type Customer = Customer { id: Num, name: String }

                fun creditCheck(c: Customer): Num
                maxRetries: Num
                """.trimIndent(),
            )
        assertEquals(listOf("Customer"), contract.types.map { it.name })
        assertEquals(listOf("creditCheck", "maxRetries"), contract.declarations.map { it.name })
        assertIs<FunDecl>(contract.declarations[0])
        assertIs<ValDecl>(contract.declarations[1])
    }

    @Test
    fun anEmptyContractParses() {
        val contract = parseContract("")
        assertTrue(contract.types.isEmpty())
        assertTrue(contract.declarations.isEmpty())
    }

    @Test
    fun aTypeOnlyContractParses() {
        assertContractEquals(
            parseContract("type Customer = Customer { id: Num }"),
            types = listOf(typeDef("Customer", constructors = arrayOf(constructor("Customer", field("id", typeName("Num")))))),
        )
    }

    @Test
    fun funDeclarationWithoutBody() {
        assertContractEquals(
            parseContract("fun creditCheck(c: Customer): Num"),
            declarations = listOf(funDecl("creditCheck", listOf(param("c", typeName("Customer"))), typeName("Num"))),
        )
    }

    @Test
    fun funDeclarationWithNoParams() {
        assertContractEquals(
            parseContract("fun now(): Num"),
            declarations = listOf(funDecl("now", emptyList(), typeName("Num"))),
        )
    }

    @Test
    fun funDeclarationWithMultipleParams() {
        assertContractEquals(
            parseContract("fun rate(from: String, to: String): Num"),
            declarations =
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
        assertContractEquals(
            parseContract("fun adder(n: Num): (Num) -> Num"),
            declarations =
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
        assertContractEquals(
            parseContract("maxRetries: Num"),
            declarations = listOf(valDecl("maxRetries", typeName("Num"))),
        )
    }

    @Test
    fun valDeclarationWithFunctionType() {
        assertContractEquals(
            parseContract("classify: (Num) -> String"),
            declarations = listOf(valDecl("classify", functionType(typeName("Num"), typeName("String")))),
        )
    }

    // The indentation risk: a bodiless declaration must not swallow the lines that follow it.

    @Test
    fun aDeclarationDoesNotSwallowTheNextDeclaration() {
        assertContractEquals(
            parseContract(
                """
                fun creditCheck(c: Num): Num
                fun riskScore(c: Num): Num
                """.trimIndent(),
            ),
            declarations =
                listOf(
                    funDecl("creditCheck", listOf(param("c", typeName("Num"))), typeName("Num")),
                    funDecl("riskScore", listOf(param("c", typeName("Num"))), typeName("Num")),
                ),
        )
    }

    @Test
    fun aDeclarationDoesNotSwallowAFollowingTypeDefinition() {
        val contract =
            parseContract(
                """
                maxRetries: Num
                type Customer = Customer { id: Num }
                fun creditCheck(c: Customer): Num
                """.trimIndent(),
            )
        assertEquals(listOf("Customer"), contract.types.map { it.name })
        assertEquals(listOf("maxRetries", "creditCheck"), contract.declarations.map { it.name })
    }

    @Test
    fun aValDeclarationDoesNotSwallowTheNextStatement() {
        assertContractEquals(
            parseContract(
                """
                maxRetries: Num
                limit: Num
                """.trimIndent(),
            ),
            declarations = listOf(valDecl("maxRetries", typeName("Num")), valDecl("limit", typeName("Num"))),
        )
    }

    // The type of a declaration ends with its line: a '|' opening the next line would be a lambda,
    // and a lambda is not something a contract can hold at all.
    @Test
    fun aDeclarationTypeDoesNotRunOnIntoALambda() {
        val error =
            assertFailsWith<Abort> {
                parseContract(
                    """
                    handler: Num
                    |x -> x|
                    """.trimIndent(),
                )
            }
        assertTrue("contract" in error.message, error.message)
    }

    // ── What a contract rejects ──────────────────────────────────────────────

    @Test
    fun aFunctionWithABodyIsRejected() {
        val error = assertFailsWith<Abort> { parseContract("fun creditCheck(c: Num): Num = c") }
        assertTrue("creditCheck" in error.message, error.message)
        assertTrue("contract" in error.message, error.message)
    }

    @Test
    fun aBindingWithAValueIsRejected() {
        val error = assertFailsWith<Abort> { parseContract("maxRetries: Num = 3") }
        assertTrue("maxRetries" in error.message, error.message)
        assertTrue("contract" in error.message, error.message)
    }

    @Test
    fun anUnannotatedBindingIsRejected() {
        assertFailsWith<Abort> { parseContract("maxRetries = 3") }
    }

    @Test
    fun aBareExpressionIsRejected() {
        val error = assertFailsWith<Abort> { parseContract("1 + 2") }
        assertTrue("contract" in error.message, error.message)
    }

    @Test
    fun aDestructuringBindingIsRejected() {
        val error = assertFailsWith<Abort> { parseContract("{ name } = customer") }
        assertTrue("contract" in error.message, error.message)
    }

    @Test
    fun aFunWithNeitherReturnTypeNorBodyIsRejected() {
        assertFailsWith<Abort> { parseContract("fun mystery()") }
    }

    // ── What a program rejects ───────────────────────────────────────────────

    @Test
    fun aBodilessFunInAProgramIsAParseError() {
        val error = assertFailsWith<Abort> { parseProgram("fun creditCheck(c: Num): Num") }
        assertTrue("creditCheck" in error.message, error.message)
        assertTrue("contract" in error.message, error.message)
    }

    @Test
    fun aBodilessBindingInAProgramIsAParseError() {
        val error = assertFailsWith<Abort> { parseProgram("maxRetries: Num") }
        assertTrue("maxRetries" in error.message, error.message)
        assertTrue("contract" in error.message, error.message)
    }

    @Test
    fun aBodilessBindingInsideABlockIsAParseError() {
        assertFailsWith<Abort> {
            parseProgram(
                """
                result =
                    limit: Num
                    1
                """.trimIndent(),
            )
        }
    }

    @Test
    fun aBodilessFunIsRejectedEvenWhenSomethingFollowsIt() {
        assertFailsWith<Abort> {
            parseProgram(
                """
                fun creditCheck(c: Num): Num
                1 + 2
                """.trimIndent(),
            )
        }
    }

}
