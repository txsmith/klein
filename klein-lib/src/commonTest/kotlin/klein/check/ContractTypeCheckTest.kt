package klein.check

import klein.check.Type.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Checking a capability contract. The wrong-mode forms — a body, a value, a bare expression — no
 * longer reach here at all: the contract parser refuses them, so what is left for the checker is
 * what a declaration *means*. See `klein.parser.ContractTest` for the language split itself.
 */
class ContractTypeCheckTest {
    @Test
    fun wellFormedContractHasNoErrors() {
        val result =
            checkContract(
                """
                type Customer = Customer { id: Num, name: String }

                fun creditCheck(c: Customer): Num
                maxRetries: Num
                """.trimIndent(),
            )
        assertTrue(result.errors.isEmpty(), "unexpected errors: ${result.errors}")
    }

    @Test
    fun typeDefinitionOnlyContractIsFine() {
        val result = checkContract("type Customer = Customer { id: Num }")
        assertTrue(result.errors.isEmpty(), "unexpected errors: ${result.errors}")
    }

    @Test
    fun emptyContractIsFine() {
        assertTrue(checkContract("").errors.isEmpty())
    }

    @Test
    fun declaredCapabilitiesLandInTheEnv() {
        val result =
            checkContract(
                """
                type Customer = Customer { id: Num, name: String }

                fun creditCheck(c: Customer): Num
                maxRetries: Num
                """.trimIndent(),
            )
        assertEquals("(Customer) -> Num", Type.print(result.env.lookup("creditCheck")!!))
        assertEquals(TNum, result.env.lookup("maxRetries"))
    }

    @Test
    fun duplicateDeclarationIsRejected() {
        val result =
            checkContract(
                """
                fun creditCheck(c: Num): Num
                fun creditCheck(c: String): Num
                """.trimIndent(),
            )
        assertIs<TypeError.DuplicateBinding>(result.errors.single())
    }

    @Test
    fun declarationAndValueOfTheSameNameCollide() {
        val result =
            checkContract(
                """
                fun creditCheck(c: Num): Num
                creditCheck: Num
                """.trimIndent(),
            )
        assertTrue(result.errors.any { it is TypeError.DuplicateBinding }, "${result.errors}")
    }

    // A contract's signatures are still real signatures — they get the same scrutiny.

    @Test
    fun declarationWithAnUnannotatedParamIsRejected() {
        val error = checkContract("fun creditCheck(c): Num").errors.single()
        assertIs<TypeError.MissingParamAnnotation>(error)
    }

    @Test
    fun declarationNamingAnUnknownTypeIsRejected() {
        val error = checkContract("fun creditCheck(c: Nope): Num").errors.single()
        assertIs<TypeError.UnboundVariable>(error)
        assertEquals("Nope", error.name)
    }

    @Test
    fun everyBadSignatureIsReported() {
        val result =
            checkContract(
                """
                fun a(x: Nope): Num
                fun b(y: AlsoNope): Num
                """.trimIndent(),
            )
        assertEquals(2, result.errors.size, "${result.errors}")
        assertTrue(result.errors.all { it is TypeError.UnboundVariable }, "${result.errors}")
    }

    // --- the checker does not mutate what it is given ---

    @Test
    fun checkingAContractDoesNotBindIntoTheGivenEnvironment() {
        val env = TypeEnv.empty()
        val checked = checkContract("fun creditCheck(c: Num): Num", env)
        assertEquals(TNum, (checked.env.lookup("creditCheck") as Type.TFun).result)
        assertNull(env.lookup("creditCheck"), "the caller's environment should be untouched")
    }

    // The case a child environment would not have isolated: child() shares its parent's type-def map.
    @Test
    fun checkingAContractDoesNotRegisterTypesIntoTheGivenEnvironment() {
        val env = TypeEnv.empty()
        val checked = checkContract("type Customer = Customer { id: Num }", env)
        assertTrue(checked.env.lookupTypeDef("Customer") != null)
        assertNull(env.lookupTypeDef("Customer"), "the caller's environment should be untouched")
    }

    @Test
    fun checkingAProgramDoesNotBindIntoTheGivenEnvironment() {
        val env = TypeEnv.empty()
        infer("x = 1", env)
        assertNull(env.lookup("x"), "the caller's environment should be untouched")
    }

    @Test
    fun constructorFieldNamingAnUnknownTypeIsRejected() {
        val error = checkContract("type Bad = Bad { x: Nope }").errors.single()
        assertIs<TypeError.UnboundVariable>(error)
        assertEquals("Nope", error.name)
    }

    @Test
    fun declarationCanUseATypeTheContractDefines() {
        val result =
            checkContract(
                """
                type Shape = Circle { radius: Num } | Square { side: Num }

                fun area(s: Shape): Num
                """.trimIndent(),
            )
        assertTrue(result.errors.isEmpty(), "unexpected errors: ${result.errors}")
        assertEquals("(Shape) -> Num", Type.print(result.env.lookup("area")!!))
    }

    @Test
    fun declarationTakingAFunctionIsRejected() {
        val error = checkContract("fun sortBy(xs: Num, key: (Num) -> Num): Num").errors.single()
        assertIs<TypeError.FunctionTypeInCapability>(error)
    }

    @Test
    fun declarationReturningAFunctionIsRejected() {
        val error = checkContract("fun adder(n: Num): (Num) -> Num").errors.single()
        assertIs<TypeError.FunctionTypeInCapability>(error)
    }

    @Test
    fun functionTypeNestedInsideACapabilityTypeIsRejected() {
        val error =
            checkContract(
                """
                type Handler = Handler { run: (Num) -> Num }

                fun register(h: Handler): Num
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.FunctionTypeInCapability>(error)
    }

    @Test
    fun valueCapabilityOfFunctionTypeIsRejected() {
        val error = checkContract("callback: (Num) -> Num").errors.single()
        assertIs<TypeError.FunctionTypeInCapability>(error)
    }

    @Test
    fun genericDeclarationQuantifiesItsTypeVariables() {
        val result = checkContract("fun identity(x: 'A): 'A")
        assertTrue(result.errors.isEmpty(), "unexpected errors: ${result.errors}")
    }

    @Test
    fun declarationsComeBackInFileOrder() {
        val result =
            checkContract(
                """
                fun creditCheck(c: Num): Num
                maxRetries: Num
                fun riskScore(c: Num): Num
                """.trimIndent(),
            )
        assertEquals(listOf("creditCheck", "maxRetries", "riskScore"), result.declarations.map { it.name })
    }

    // The payoff: a program checked against the environment a contract declares.

    @Test
    fun programChecksAgainstTheContractEnvironment() {
        val contract =
            checkContract(
                """
                type Customer = Customer { id: Num, name: String }

                fun creditCheck(c: Customer): Num
                maxRetries: Num
                """.trimIndent(),
            )
        val program = infer("creditCheck(Customer(1, \"ada\")) + maxRetries", contract.env)
        assertTrue(program.errors.isEmpty(), "unexpected errors: ${program.errors}")
        assertEquals(TNum, program.type)
    }

    @Test
    fun programMisusingACapabilityIsRejected() {
        val contract = checkContract("fun creditCheck(c: Num): Num")
        val program = infer("creditCheck(\"nope\")", contract.env)
        assertIs<TypeError.TypeMismatch>(program.errors.single())
    }

    @Test
    fun contractStageCarriesItsEnvironmentAndErrors() {
        val declared =
            klein.Klein
                .tokenize("maxRetries: Num")
                .andThen(klein.Klein::parseContract)
                .andThen { klein.Klein.checkContract(it) }
        assertTrue(declared.errors.isEmpty(), "unexpected errors: ${declared.errors}")
        assertEquals(TNum, declared.output!!.lookup("maxRetries"))

        val rejected =
            klein.Klein
                .tokenize("fun creditCheck(c: Nope): Num")
                .andThen(klein.Klein::parseContract)
                .andThen { klein.Klein.checkContract(it) }
        assertIs<TypeError.UnboundVariable>(rejected.errors.single())
    }

    @Test
    fun aDefinitionNeverReachesTheContractStage() {
        val parsed =
            klein.Klein
                .tokenize("maxRetries: Num = 3")
                .andThen(klein.Klein::parseContract)
        assertIs<klein.surface.ParseError>(parsed.errors.single())
    }
}
