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
    type Address = Address { city: String }
    type Account = Account { address: Address, balance: Num }
    type CardDetails = CardDetails { last4: String }
    type Payment/2 = Card { details: CardDetails } | Cash

    customer: Customer
    customer/2: Customer/2
    fun creditScore(c: Customer): Num
    fun creditScore/2(c: Customer/2): Num
    fun riskBand/2(c: Customer/2, score: Num): String
    fun account(id: Num): Account
    fun shapeOf/2(c: Customer/2): Shape/2
    fun payment/2(c: Customer/2): Payment/2

    release 1
      Customer
      Address
      Account
      customer
      creditScore
      account

    release 2
      Customer/2
      customer/2
      Shape/2
      CardDetails
      Payment/2
      creditScore/2
      riskBand/2
      shapeOf/2
      payment/2
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
        assertSameEdition(fromRelease, contract.compileRule(CREDIT_RULE, fromRelease.pins).orFail())
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
        assertSameEdition(fromRelease, withoutRelease1.compileRule(CREDIT_RULE, fromRelease.pins).orFail())
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
        val errors = assertFailsWith<KleinException> { withoutRevision1.compileRule(CREDIT_RULE, fromRelease.pins) }.errors
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
        val checked = edited.compileRule(CREDIT_RULE, fromRelease.pins)
        assertNull(checked.output)
        assertTrue(checked.diagnostics.isNotEmpty())
    }

    @Test
    fun anUnreachedPinIsDropped() {
        val edition = contract.compileRule("customer.tier", pins("customer" to 1, "creditScore" to 1)).orFail()
        assertEquals(pins("customer" to 1, "Customer" to 1), edition.pins)
    }

    @Test
    fun pinsBeyondTheClosureAreDropped() {
        val release2 = pins("Customer" to 2, "customer" to 2, "Shape" to 2, "creditScore" to 2, "riskBand" to 2, "shapeOf" to 2)
        val edition = contract.compileRule("creditScore(customer)", release2).orFail()
        assertEquals(pins("creditScore" to 2, "customer" to 2, "Customer" to 2), edition.pins)
    }

    @Test
    fun aWholeReleaseSurfaceAsPinsYieldsTheReleasesEdition() {
        val rule = "riskBand(customer, creditScore(customer) + Circle(2).area)"
        val release2 = pins("Customer" to 2, "customer" to 2, "Shape" to 2, "creditScore" to 2, "riskBand" to 2, "shapeOf" to 2)
        assertSameEdition(contract.compileRule(rule, ReleaseNumber(2)).orFail(), contract.compileRule(rule, release2).orFail())
    }

    @Test
    fun callingACapabilityPinsTheTypesInItsSignature() {
        val edition = contract.compileRule(CREDIT_RULE, ReleaseNumber(1)).orFail()
        assertEquals(pins("creditScore" to 1, "customer" to 1, "Customer" to 1), edition.pins)
    }

    @Test
    fun callingACapabilityPinsTheConstructorsOfTheTypesInItsSignature() {
        val edition = contract.compileRule("shapeOf(customer).area", ReleaseNumber(2)).orFail()
        assertEquals(
            pins("shapeOf" to 2, "customer" to 2, "Customer" to 2, "Shape" to 2, "Circle" to 2, "Square" to 2),
            edition.pins,
        )
    }

    @Test
    fun aTypeReachedOnlyThroughAnotherTypeIsPinned() {
        val edition = contract.compileRule("account(1).address.city", ReleaseNumber(1)).orFail()
        assertEquals(pins("account" to 1, "Account" to 1, "Address" to 1), edition.pins)
    }

    @Test
    fun aTypeReachedOnlyThroughAConstructorFieldIsPinned() {
        val edition = contract.compileRule("payment(customer)", ReleaseNumber(2)).orFail()
        assertEquals(
            pins("payment" to 2, "customer" to 2, "Customer" to 2, "Payment" to 2, "Card" to 2, "Cash" to 2, "CardDetails" to 1),
            edition.pins,
        )
    }

    @Test
    fun annotatingATypeOrInferringItGivesTheSamePins() {
        val annotated = contract.compileRule("fun f(c: Customer): Num = c.id\nf(customer)", ReleaseNumber(1)).orFail()
        val inferred = contract.compileRule("customer.id", ReleaseNumber(1)).orFail()
        assertEquals(pins("customer" to 1, "Customer" to 1), annotated.pins)
        assertEquals(annotated.pins, inferred.pins)
    }

    @Test
    fun pinsAloneAnswerWhichEditionsDependOnARevision() {
        val editions =
            mapOf(
                "credit on 1" to contract.compileRule(CREDIT_RULE, ReleaseNumber(1)).orFail(),
                "credit on 2" to contract.compileRule(CREDIT_RULE, ReleaseNumber(2)).orFail(),
                "risk on 2" to contract.compileRule("riskBand(customer, 1)", ReleaseNumber(2)).orFail(),
                "shape on 2" to contract.compileRule("Circle(2).area", ReleaseNumber(2)).orFail(),
            )
        val onCustomer2 = editions.filterValues { it.pins["Customer"] == RevisionNumber(2) }.keys
        assertEquals(setOf("credit on 2", "risk on 2"), onCustomer2)
    }

    @Test
    fun aNameThePinsLackIsUnbound() {
        val errors = contract.compileRule("riskBand(customer, 1)", pins("customer" to 2)).diagnostics
        assertEquals("riskBand", assertIs<TypeError.UnboundVariable>(errors.single()).name)
    }

    @Test
    fun aSyntaxErrorComesBackAsADiagnostic() {
        val checked = contract.compileRule("creditScore(", pins("creditScore" to 1))
        assertNull(checked.output)
        assertEquals(1, checked.diagnostics.size)
    }
}
