package klein.check.contract

import klein.Klein
import klein.KleinException
import klein.ReleaseNumber
import klein.RevisionNumber
import klein.check.TypeError
import klein.orFail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val CREDIT_RULE = "creditScore(customer) >= 620"

private val CONTRACT =
    """
    type Customer = Customer { id: Num, tier: String }
    type Customer/2 = Customer { id: Num, name: String, tier: String }
    type Shape/2 = Circle { area: Num } | Square { area: Num }

    customer: Customer
    customer/2: Customer/2
    fun creditScore(c: Customer): Num
    fun creditScore/2(c: Customer/2): Num
    fun riskBand/2(c: Customer/2, score: Num): String

    release 1
      Customer
      customer
      creditScore

    release 2
      Customer/2
      customer/2
      Shape/2
      creditScore/2
      riskBand/2
    """.trimIndent()

private val contract = Klein.checkContract(CONTRACT)

private fun pins(vararg pins: Pair<String, Int>): Map<String, RevisionNumber> = pins.associate { (name, revision) -> name to RevisionNumber(revision) }

private fun assertSameEdition(
    expected: Edition,
    actual: Edition,
) {
    assertEquals(expected.core, actual.core)
    assertEquals(expected.pins, actual.pins)
    assertEquals(expected.source, actual.source)
}

class CompileAgainstPinsTest {
    @Test
    fun compilingAgainstAnEditionsOwnPinsYieldsTheSameEdition() {
        val fromRelease = contract.compileRule(CREDIT_RULE, ReleaseNumber(1)).orFail()
        assertSameEdition(fromRelease, contract.compile(CREDIT_RULE, fromRelease.pins).orFail())
    }

    @Test
    fun aReleaseRemovedFromTheContractLeavesTheEditionCompilable() {
        val fromRelease = contract.compileRule(CREDIT_RULE, ReleaseNumber(1)).orFail()
        val withoutRelease1 =
            Klein.checkContract(
                """
                type Customer = Customer { id: Num, tier: String }

                customer: Customer
                fun creditScore(c: Customer): Num
                """.trimIndent(),
            )
        assertEquals(emptyList(), withoutRelease1.releases)
        assertSameEdition(fromRelease, withoutRelease1.compile(CREDIT_RULE, fromRelease.pins).orFail())
    }

    @Test
    fun aRevisionRemovedFromTheContractIsAnUnknownPinPerPin() {
        val fromRelease = contract.compileRule(CREDIT_RULE, ReleaseNumber(1)).orFail()
        val withoutRevision1 =
            Klein.checkContract(
                """
                type Customer = Customer { id: Num, tier: String }
                type Customer/2 = Customer { id: Num, name: String, tier: String }

                customer/2: Customer/2
                fun creditScore/2(c: Customer/2): Num

                release 2
                  Customer/2
                  customer/2
                  creditScore/2
                """.trimIndent(),
            )
        val errors = assertFailsWith<KleinException> { withoutRevision1.compile(CREDIT_RULE, fromRelease.pins) }.errors
        assertEquals(
            setOf("creditScore" to RevisionNumber(1), "customer" to RevisionNumber(1)),
            errors.map { assertIs<UnknownPin>(it) }.map { it.name to it.revision }.toSet(),
        )
    }

    @Test
    fun aDeclarationEditedInPlaceComesBackAsDiagnostics() {
        val fromRelease = contract.compileRule(CREDIT_RULE, ReleaseNumber(1)).orFail()
        val edited =
            Klein.checkContract(
                """
                type Customer = Customer { id: Num, tier: String }

                customer: Customer
                fun creditScore(c: Customer): String

                release 1
                  Customer
                  customer
                  creditScore
                """.trimIndent(),
            )
        val checked = edited.compile(CREDIT_RULE, fromRelease.pins)
        assertNull(checked.output)
        assertTrue(checked.diagnostics.isNotEmpty())
    }

    @Test
    fun aPinTheSourceDoesNotUseIsDropped() {
        val edition = contract.compile("customer.tier", pins("customer" to 1, "creditScore" to 1)).orFail()
        assertEquals(pins("customer" to 1), edition.pins)
    }

    @Test
    fun aWholeReleaseSurfaceAsPinsYieldsTheReleasesEdition() {
        val rule = "riskBand(customer, creditScore(customer) + Circle(2).area)"
        val release2 = pins("Customer" to 2, "customer" to 2, "Shape" to 2, "creditScore" to 2, "riskBand" to 2)
        assertSameEdition(contract.compileRule(rule, ReleaseNumber(2)).orFail(), contract.compile(rule, release2).orFail())
    }

    @Test
    fun aNameThePinsLackIsUnbound() {
        val errors = contract.compile("riskBand(customer, 1)", pins("customer" to 2)).diagnostics
        assertEquals("riskBand", assertIs<TypeError.UnboundVariable>(errors.single()).name)
    }

    @Test
    fun aSyntaxErrorComesBackAsADiagnostic() {
        val checked = contract.compile("creditScore(", pins("creditScore" to 1))
        assertNull(checked.output)
        assertEquals(1, checked.diagnostics.size)
    }
}
