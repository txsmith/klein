package klein.host

import klein.Klein
import klein.KleinException
import klein.ReleaseNumber
import klein.RevisionNumber
import klein.check.Type
import klein.check.TypeError
import klein.check.contract.ContractDeclaration
import klein.check.contract.InvalidContract
import klein.interp.Value
import klein.orFail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private fun registerAll(registry: HandlerRegistry) =
    registry.declarations.forEach { registry.immediate("${it.name}/${it.revision.value}") { Value.VUnit } }

private fun load(
    source: String,
    register: HandlerRegistry.() -> Unit = {},
): Environment = Klein.checkContract(source).implement(register = register)

private val CONTRACT =
    """
    type Customer = Customer { id: Num, name: String }

    fun creditCheck(c: Customer): Num
    maxRetries: Num

    release 1
      Customer
      creditCheck
      maxRetries
    """.trimIndent()

class EnvironmentTest {
    @Test
    fun declarationsBecomeCapabilities() {
        val env = load(CONTRACT, ::registerAll)
        assertEquals(listOf("creditCheck", "maxRetries"), env.capabilities.map { it.name })
        assertIs<ContractDeclaration.Function>(env.capabilities.first { it.name == "creditCheck" })
        assertIs<ContractDeclaration.Value>(env.capabilities.first { it.name == "maxRetries" })
    }

    @Test
    fun everyDeclarationMustBeRegistered() {
        val error = assertFailsWith<KleinException> { load(CONTRACT) { immediate("creditCheck") { Value.VNum(1.0) } } }
        assertTrue(error.message!!.contains("maxRetries"), "message should name what is missing: ${error.message}")
    }

    @Test
    fun anEmptyBuilderFailsWhenAnythingIsDeclared() {
        assertFailsWith<KleinException> { load(CONTRACT) }
    }

    @Test
    fun anEmptyContractNeedsNoRegistrations() {
        assertEquals(emptyList(), load("type Customer = Customer { id: Num }").capabilities)
    }

    @Test
    fun registrationsDefaultToRevisionOne() {
        assertTrue(load(CONTRACT, ::registerAll).capabilities.all { it.revision == RevisionNumber(1) })
    }

    @Test
    fun registrationAttachesAnImplementation() {
        val env = load(CONTRACT, ::registerAll)
        env.capabilities.forEach { assertTrue(env.getHandler(it.name, it.revision) != null, "${it.name} should have an implementation") }
    }

    @Test
    fun revisionsComeFromTheDeclaration() {
        val env =
            load(
                """
                type Customer = Customer { id: Num, name: String }

                fun creditCheck/3(c: Customer): Num
                maxRetries: Num
                """.trimIndent(),
            ) {
                immediate("creditCheck/3") { Value.VNum(1.0) }
                immediate("maxRetries") { Value.VNum(3.0) }
            }
        assertEquals(RevisionNumber(3), env.capabilities.first { it.name == "creditCheck" }.revision)
        assertEquals(RevisionNumber(1), env.capabilities.first { it.name == "maxRetries" }.revision)
    }

    @Test
    fun registeringAnUndeclaredNameFails() {
        val error = assertFailsWith<KleinException> { load(CONTRACT) { immediate("nope") { Value.VUnit } } }
        assertTrue(error.message!!.contains("nope"), "message should name the capability: ${error.message}")
    }

    @Test
    fun aMalformedRevisionSuffixIsARegistrationError() {
        val error = assertFailsWith<KleinException> { load(CONTRACT) { immediate("creditCheck/x") { Value.VNum(1.0) } } }
        assertTrue(error.message!!.contains("revision suffix"), "message should explain the suffix form: ${error.message}")
    }

    @Test
    fun anEmptyNameBeforeTheSlashIsTheMalformedSuffixErrorAlone() {
        val error = assertFailsWith<KleinException> { load(CONTRACT) { registerAll(this); immediate("/2") { Value.VNum(1.0) } } }
        assertEquals(1, error.errors.size, "expected only the malformed-suffix error: ${error.errors}")
        assertTrue(error.message!!.contains("revision suffix"), "message should explain the suffix form: ${error.message}")
    }

    @Test
    fun aRevisionSuffixBelowOneIsARegistrationError() {
        val zero = assertFailsWith<KleinException> { load(CONTRACT) { registerAll(this); immediate("creditCheck/0") { Value.VNum(1.0) } } }
        assertTrue(zero.message!!.contains("revision suffix"), "message should explain the suffix form: ${zero.message}")
        val negative =
            assertFailsWith<KleinException> { load(CONTRACT) { registerAll(this); immediate("creditCheck/-1") { Value.VNum(1.0) } } }
        assertTrue(negative.message!!.contains("revision suffix"), "message should explain the suffix form: ${negative.message}")
    }

    @Test
    fun anExplicitRevisionOneSuffixNamesRevisionOne() {
        val env = load(CONTRACT) { immediate("creditCheck/1") { Value.VNum(1.0) }; immediate("maxRetries") { Value.VNum(3.0) } }
        assertTrue(env.capabilities.any { it.name == "creditCheck" })
    }

    @Test
    fun registeringTheSameNameAndRevisionTwiceFails() {
        assertFailsWith<KleinException> {
            load(CONTRACT) {
                immediate("creditCheck") { Value.VNum(1.0) }
                immediate("creditCheck") { Value.VNum(2.0) }
                immediate("maxRetries") { Value.VNum(3.0) }
            }
        }
    }

    // Revisions are declared in the contract; registration names one that exists.
    @Test
    fun aDeclaredRevisionIsACapability() {
        val env =
            load("fun creditScore/2(c: Num): Num") {
                immediate("creditScore/2") { Value.VNum(1.0) }
            }
        val capability = env.capabilities.single()
        assertEquals("creditScore", capability.name)
        assertEquals(RevisionNumber(2), capability.revision)
        assertTrue(env.getHandler(capability.name, capability.revision) != null)
    }

    @Test
    fun bothDeclaredRevisionsBecomeCapabilities() {
        val env =
            load(
                """
                fun creditScore(c: Num): Num
                fun creditScore/2(c: Num): Num
                """.trimIndent(),
            ) {
                immediate("creditScore") { Value.VNum(1.0) }
                immediate("creditScore/2") { Value.VNum(2.0) }
            }
        assertEquals(listOf(RevisionNumber(1), RevisionNumber(2)), env.capabilities.map { it.revision })
    }

    @Test
    fun registeringAnUndeclaredRevisionFails() {
        val error =
            assertFailsWith<KleinException> {
                load("fun creditScore(c: Num): Num") {
                    immediate("creditScore") { Value.VNum(1.0) }
                    immediate("creditScore/2") { Value.VNum(2.0) }
                }
            }
        assertTrue(error.message!!.contains("revision 2"), "message should name the revision: ${error.message}")
    }

    // A node mid-transition serves both revisions until runs pinned to the old one drain,
    // and the two may carry different shapes — that is the point of an incompatible revision.
    @Test
    fun revisionsMayCarryDifferentSignatures() {
        val env =
            load(
                """
                type Customer = Customer { id: Num }
                type Customer/2 = Customer { id: Num, tier: String }

                fun creditCheck(c: Customer): Num
                fun creditCheck/2(c: Customer/2): Num
                """.trimIndent(),
            ) {
                immediate("creditCheck") { Value.VNum(1.0) }
                immediate("creditCheck/2") { Value.VNum(2.0) }
            }
        val both = env.capabilities.filter { it.name == "creditCheck" }
        assertEquals(listOf(RevisionNumber(1), RevisionNumber(2)), both.map { it.revision })
        assertNotEquals(both[0].type, both[1].type)
        both.forEach { assertTrue(env.getHandler(it.name, it.revision) != null, "revision ${it.revision} should be implemented") }
    }

    @Test
    fun everyDeclaredRevisionNeedsItsOwnImplementation() {
        val error =
            assertFailsWith<KleinException> {
                load(
                    """
                    fun creditCheck(c: Num): Num
                    fun creditCheck/2(c: Num): Num
                    """.trimIndent(),
                ) { immediate("creditCheck") { Value.VNum(1.0) } }
            }
        assertTrue(error.message!!.contains("revision 2"), "message should name the revision: ${error.message}")
    }

    @Test
    fun aContractThatDoesNotCheckFailsWithEveryError() {
        val error =
            assertFailsWith<KleinException> {
                load(
                    """
                    fun a(x: Nope): Num
                    fun b(y: AlsoNope): Num
                    """.trimIndent(),
                )
            }
        val diagnostics = assertIs<InvalidContract>(error.errors.single()).diagnostics
        assertEquals(2, diagnostics.size, "expected both unknown types: $diagnostics")
        assertTrue(diagnostics.all { it is TypeError.UnboundVariable })
    }

    @Test
    fun registrationErrorsAreNotReportedWhenTheContractItselfFailed() {
        val error = assertFailsWith<KleinException> { load("fun a(x: Nope): Num") { immediate("ghost") { Value.VUnit } } }
        val invalid = assertIs<InvalidContract>(error.errors.single())
        assertEquals(1, invalid.diagnostics.size, "contract errors should short-circuit: ${invalid.diagnostics}")
    }

    @Test
    fun declarationsAreVisibleToTheBuilderForTableDrivenRegistration() {
        val seen = mutableListOf<String>()
        load(CONTRACT) {
            declarations.forEach { seen.add(it.name) }
            registerAll(this)
        }
        assertEquals(listOf("creditCheck", "maxRetries"), seen)
    }

    // The split the API exists for: a CI job checks contracts and rules with no handlers anywhere.
    @Test
    fun checkingARuleNeedsNoImplementations() {
        val contract = Klein.checkContract(CONTRACT)
        assertEquals(
            Type.TNum,
            contract.check("""creditCheck(Customer(1, "ada")) + maxRetries""", ReleaseNumber(1)).orFail(),
        )
    }

    // --- what a release exposes and what a host implements are independent ---

    private val PARTLY_EXPOSED =
        """
        type Customer = Customer { id: Num }

        fun creditCheck(c: Customer): Num
        maxRetries: Num

        release 1
          Customer
          creditCheck
        """.trimIndent()

    // Staged before its release, or draining after it: either way the host still answers for it.
    @Test
    fun aDeclarationNoReleaseExposesStillNeedsAnImplementation() {
        val error = assertFailsWith<KleinException> { load(PARTLY_EXPOSED) { immediate("creditCheck") { Value.VUnit } } }
        assertTrue(error.message!!.contains("maxRetries"), "message should name what is missing: ${error.message}")
    }

    @Test
    fun capabilitiesComeFromDeclarationsNotFromTheReleaseSurface() {
        val env = load(PARTLY_EXPOSED, ::registerAll)
        assertEquals(listOf("creditCheck", "maxRetries"), env.capabilities.map { it.name })

        // The same contract, from a rule's side: `maxRetries` is not vocabulary release 1 gave it.
        val unbound = Klein.checkContract(PARTLY_EXPOSED).check("maxRetries", ReleaseNumber(1))
        assertEquals("maxRetries", assertIs<TypeError.UnboundVariable>(unbound.diagnostics.single()).name)
    }

    // --- per-run supply: the lambda-less marker (2a; a run supplying it is 2b) ---

    @Test
    fun aLambdaLessRegistrationSatisfiesCompleteness() {
        assertFailsWith<KleinException> { load(CONTRACT) { immediate("maxRetries") { Value.VNum(3.0) } } }
        val env =
            load(CONTRACT) {
                immediate("creditCheck")
                immediate("maxRetries") { Value.VNum(3.0) }
            }
        assertEquals(listOf("creditCheck", "maxRetries"), env.capabilities.map { it.name })
    }

    @Test
    fun aPerRunEntryHasNoImplementation() {
        val env =
            load(CONTRACT) {
                immediate("creditCheck")
                immediate("maxRetries") { Value.VNum(3.0) }
            }
        assertEquals(null, env.getHandler("creditCheck", RevisionNumber(1)))
        assertIs<Handler.Immediate>(env.getHandler("maxRetries", RevisionNumber(1)))
    }

    @Test
    fun aPerRunMarkerNamesADeclaredRevision() {
        val env = load("fun creditScore/2(c: Num): Num") { immediate("creditScore/2") }
        assertEquals(RevisionNumber(2), env.capabilities.single().revision)
        assertEquals(null, env.getHandler("creditScore", RevisionNumber(2)))
    }

    @Test
    fun aPerRunMarkerForAnUndeclaredNameFails() {
        val error = assertFailsWith<KleinException> { load(CONTRACT) { registerAll(this); immediate("nope") } }
        assertTrue(error.message!!.contains("nope"), "message should name the capability: ${error.message}")
    }

    @Test
    fun aPerRunMarkerCountsAsARegistrationForDuplicates() {
        assertFailsWith<KleinException> {
            load(CONTRACT) {
                immediate("creditCheck")
                immediate("creditCheck") { Value.VNum(1.0) }
                immediate("maxRetries") { Value.VNum(3.0) }
            }
        }
    }

    // --- the contract reference ---

    @Test
    fun theEnvironmentKeepsTheContractItImplements() {
        val contract = Klein.checkContract(CONTRACT)
        val env = contract.implement(register = ::registerAll)
        assertTrue(env.contract === contract)
    }
}
