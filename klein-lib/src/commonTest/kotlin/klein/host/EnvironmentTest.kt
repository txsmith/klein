package klein.host

import klein.Klein
import klein.KleinException
import klein.ReleaseNumber
import klein.Revision
import klein.check.Type
import klein.check.TypeError
import klein.check.contract.DeclarationKind
import klein.interp.Value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private fun registerAll(registry: Registry) =
    registry.declarations.forEach { registry.immediate(it.name, it.revision) { Value.VUnit } }

private fun load(
    source: String,
    register: Registry.() -> Unit = {},
): Environment = Klein.checkContract(source).implement(register)

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
        assertEquals(DeclarationKind.Function, env.capabilities.first { it.name == "creditCheck" }.kind)
        assertEquals(DeclarationKind.Value, env.capabilities.first { it.name == "maxRetries" }.kind)
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
        assertTrue(load(CONTRACT, ::registerAll).capabilities.all { it.revision == Revision(1) })
    }

    @Test
    fun registrationAttachesAnImplementation() {
        val env = load(CONTRACT, ::registerAll)
        env.capabilities.forEach { assertTrue(env[it.id] != null, "${it.name} should have an implementation") }
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
                immediate("creditCheck", revision = Revision(3)) { Value.VNum(1.0) }
                immediate("maxRetries") { Value.VNum(3.0) }
            }
        assertEquals(Revision(3), env.capabilities.first { it.name == "creditCheck" }.revision)
        assertEquals(Revision(1), env.capabilities.first { it.name == "maxRetries" }.revision)
    }

    @Test
    fun deferredRegistrationIsAlsoAnImplementation() {
        val env =
            load(CONTRACT) {
                deferred("creditCheck") { }
                immediate("maxRetries") { Value.VNum(3.0) }
            }
        assertTrue(env[env.capabilities.first { it.name == "creditCheck" }.id] is Implementation.Deferred)
    }

    @Test
    fun registeringAnUndeclaredNameFails() {
        val error = assertFailsWith<KleinException> { load(CONTRACT) { immediate("nope") { Value.VUnit } } }
        assertTrue(error.message!!.contains("nope"), "message should name the capability: ${error.message}")
    }

    @Test
    fun registeringTheSameNameAndRevisionTwiceFails() {
        assertFailsWith<KleinException> {
            load(CONTRACT) {
                immediate("creditCheck") { Value.VNum(1.0) }
                deferred("creditCheck") { }
                immediate("maxRetries") { Value.VNum(3.0) }
            }
        }
    }

    // Revisions are declared in the contract; registration names one that exists.
    @Test
    fun aDeclaredRevisionIsACapability() {
        val env =
            load("fun creditScore/2(c: Num): Num") {
                immediate("creditScore", revision = Revision(2)) { Value.VNum(1.0) }
            }
        val capability = env.capabilities.single()
        assertEquals("creditScore", capability.name)
        assertEquals(Revision(2), capability.revision)
        assertTrue(env[capability.id] != null)
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
                immediate("creditScore", revision = Revision(2)) { Value.VNum(2.0) }
            }
        assertEquals(listOf(Revision(1), Revision(2)), env.capabilities.map { it.revision })
        assertNotEquals(env.capabilities[0].id, env.capabilities[1].id)
    }

    @Test
    fun registeringAnUndeclaredRevisionFails() {
        val error =
            assertFailsWith<KleinException> {
                load("fun creditScore(c: Num): Num") {
                    immediate("creditScore") { Value.VNum(1.0) }
                    immediate("creditScore", revision = Revision(2)) { Value.VNum(2.0) }
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
                immediate("creditCheck", revision = Revision(2)) { Value.VNum(2.0) }
            }
        val both = env.capabilities.filter { it.name == "creditCheck" }
        assertEquals(listOf(Revision(1), Revision(2)), both.map { it.revision })
        assertNotEquals(both[0].type, both[1].type)
        both.forEach { assertTrue(env[it.id] != null, "revision ${it.revision} should be implemented") }
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
        assertEquals(2, error.errors.size, "expected both unknown types: ${error.errors}")
        assertTrue(error.errors.all { it is TypeError.UnboundVariable })
    }

    @Test
    fun registrationErrorsAreNotReportedWhenTheContractItselfFailed() {
        val error = assertFailsWith<KleinException> { load("fun a(x: Nope): Num") { immediate("ghost") { Value.VUnit } } }
        assertEquals(1, error.errors.size, "contract errors should short-circuit: ${error.errors}")
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
            contract.check("""creditCheck(Customer(1, "ada")) + maxRetries""", ReleaseNumber(1)),
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
        val unbound =
            assertFailsWith<KleinException> { Klein.checkContract(PARTLY_EXPOSED).check("maxRetries", ReleaseNumber(1)) }
        assertEquals("maxRetries", assertIs<TypeError.UnboundVariable>(unbound.errors.single()).name)
    }

    // --- identity ---

    @Test
    fun identityIsStableForTheSameDeclarationAndRevision() {
        val a = load(CONTRACT, ::registerAll).capabilities.first { it.name == "creditCheck" }.id
        val b = load(CONTRACT, ::registerAll).capabilities.first { it.name == "creditCheck" }.id
        assertEquals(a, b)
    }

    @Test
    fun identityChangesWithTheRevision() {
        val one = load("fun creditCheck(c: Num): Num", ::registerAll).capabilities.single().id
        val two = load("fun creditCheck/2(c: Num): Num", ::registerAll).capabilities.single().id
        assertNotEquals(one, two)
    }

    // The property `name@rev` identity would lose: a signature change that skips a revision bump.
    @Test
    fun identityChangesWithTheSignatureEvenAtTheSameRevision() {
        val num = load("fun f(x: Num): Num", ::registerAll).capabilities.single().id
        val str = load("fun f(x: String): Num", ::registerAll).capabilities.single().id
        assertNotEquals(num, str)
    }

    @Test
    fun identityChangesWithTheName() {
        val f = load("fun f(x: Num): Num", ::registerAll).capabilities.single().id
        val g = load("fun g(x: Num): Num", ::registerAll).capabilities.single().id
        assertNotEquals(f, g)
    }
}
