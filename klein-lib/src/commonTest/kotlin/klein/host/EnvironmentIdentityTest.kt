package klein.host

import klein.Klein
import klein.KleinException
import klein.ReleaseNumber
import klein.WrongEnvironment
import klein.check.contract.Edition
import klein.check.contract.EnvironmentContract
import klein.host.codec.decodeEditionJson
import klein.host.codec.encodeEditionJson
import klein.interp.Value
import klein.orFail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val CREDIT_RULE = "creditScore(customer) >= 620"

private fun contractUnder(environment: String): String =
    """
    environment $environment

    type Customer = Customer { id: Num, tier: String }

    customer: Customer
    fun creditScore(c: Customer): Num

    release 1
      Customer
      customer
      creditScore
    """.trimIndent()

private val acme: EnvironmentContract = Klein.checkContract(contractUnder("acme"))

private val globex: EnvironmentContract = Klein.checkContract(contractUnder("globex"))

private val gold = Value.VStruct("Customer", mapOf("id" to Value.VNum(1.0), "tier" to Value.VStr("gold")))

private fun assertCreditCompiles(contract: EnvironmentContract = acme): Edition = contract.compileRule(CREDIT_RULE, ReleaseNumber(1)).orFail()

private fun assertWrongEnvironment(
    edition: String,
    environment: String,
    block: () -> Unit,
): WrongEnvironment {
    val error = assertIs<WrongEnvironment>(assertFailsWith<KleinException> { block() }.errors.single())
    assertEquals(edition, error.edition)
    assertEquals(environment, error.environment)
    assertTrue(edition in error.message && environment in error.message, error.message)
    return error
}

class EnvironmentIdentityTest {
    @Test
    fun aContractRecordsTheEnvironmentItsHeaderNames() {
        assertEquals("acme", acme.environment)
    }

    @Test
    fun aQuotedEnvironmentNameIsRecordedWithoutItsQuotes() {
        assertEquals("Acme EU", Klein.checkContract(contractUnder("\"Acme EU\"")).environment)
    }

    @Test
    fun aCompiledEditionRecordsTheEnvironmentItWasCompiledIn() {
        assertEquals("acme", assertCreditCompiles().environment)
    }

    @Test
    fun theArtifactCarriesTheEnvironmentAndDecodingRestoresIt() {
        val text = encodeEditionJson(assertCreditCompiles())
        assertTrue("\"environment\":\"acme\"" in text, text)
        assertEquals("acme", assertIs<DecodedEdition.Intact>(acme.decodeEditionJson(text)).edition.environment)
    }

    @Test
    fun decodingUnderAnotherEnvironmentIsWrongEnvironmentNamingBoth() {
        val text = encodeEditionJson(assertCreditCompiles())
        assertWrongEnvironment(edition = "acme", environment = "globex") { globex.decodeEditionJson(text) }
    }

    @Test
    fun theEnvironmentIsComparedBeforeTheChecksum() {
        val damaged = encodeEditionJson(assertCreditCompiles()).replace(">= 620", ">= 621")
        assertIs<DecodedEdition.Stale>(acme.decodeEditionJson(damaged))
        assertWrongEnvironment(edition = "acme", environment = "globex") { globex.decodeEditionJson(damaged) }
    }

    @Test
    fun theChecksumDoesNotCoverTheEnvironment() {
        val renamed = encodeEditionJson(assertCreditCompiles()).replace("\"environment\":\"acme\"", "\"environment\":\"globex\"")
        assertEquals("globex", assertIs<DecodedEdition.Intact>(globex.decodeEditionJson(renamed)).edition.environment)
    }

    @Test
    fun runningUnderAnotherEnvironmentIsWrongEnvironmentBeforeTheFirstEffect() {
        var asked = false
        val host =
            globex.implement(
                immediate("customer") { asked = true; gold },
                immediate("creditScore") { asked = true; Value.VNum(700.0) },
            )
        assertWrongEnvironment(edition = "acme", environment = "globex") { host.run(assertCreditCompiles()) }
        assertFalse(asked, "no capability may be asked under the wrong environment")
    }

    @Test
    fun runningUnderItsOwnEnvironmentIsUnaffected() {
        val host = acme.implement(immediate("customer") { gold }, immediate("creditScore") { Value.VNum(700.0) })
        assertEquals(Value.VBool(true), assertIs<RunOutcome.Completed>(host.run(assertCreditCompiles())).value)
    }
}
