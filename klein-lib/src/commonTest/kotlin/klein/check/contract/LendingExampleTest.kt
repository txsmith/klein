package klein.check.contract

import klein.Klein
import klein.KleinException
import klein.ReleaseNumber
import klein.check.Type
import klein.check.TypeError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** The rule the example follows: `eligibility-standard`, whose whole source is one comparison. */
private const val STANDARD = "creditScore(customer) >= 620"

private fun check(
    contract: String,
    rule: String,
    release: Int,
) = Klein.checkContract(contract.trimIndent()).check(rule, ReleaseNumber(release))

/**
 * `host-integration.md` §"Evolution, concretely" — one environment followed through a year: a new
 * capability, a meaning change, a shape change, an in-place edit, and a removal. One test per step,
 * checking rules against each release as it goes.
 */
class LendingExampleTest {
    // ── 1. A new capability ──────────────────────────────────────────────────

    private val dayOne =
        """
        type Customer = Customer { id: Num, name: String }

        customer: Customer
        fun creditScore(c: Customer): Num

        release 1
          Customer
          customer
          creditScore
        """

    @Test
    fun theDayOneContractServesItsRule() {
        assertEquals(Type.TBool, check(dayOne, STANDARD, release = 1))
    }

    // ── 2. The meaning changes ───────────────────────────────────────────────

    // The types do not move, so no checker can see the change: a new revision, in a new release.
    private val newBureau =
        """
        type Customer = Customer { id: Num, name: String }

        customer: Customer
        fun creditScore(c: Customer): Num
        fun creditScore/2(c: Customer): Num

        release 1
          Customer
          customer
          creditScore

        release 2
          creditScore/2
        """

    @Test
    fun aNewReleaseCarriesTheNamesItDidNotMention() {
        assertEquals(Type.TBool, check(newBureau, "creditScore(customer) >= 640", release = 2))
        assertEquals(Type.TBool, check(newBureau, STANDARD, release = 1))
    }

    // ── 3. The shape changes ─────────────────────────────────────────────────

    // A type edit requires a new revision, and the revision cascades through every signature that
    // mentions it. The bureau is unchanged, so release 2 is re-pointed rather than superseded.
    private val newShape =
        """
        type Customer = Customer { id: Num, name: String }
        type Customer/2 = Customer { id: Num, tier: String }

        customer: Customer/1
        customer/2: Customer/2
        fun creditScore(c: Customer/1): Num
        fun creditScore/2(c: Customer/1): Num
        fun creditScore/3(c: Customer/2): Num

        release 1
          Customer
          customer
          creditScore

        release 2
          Customer/2
          customer/2
          creditScore/3
        """

    @Test
    fun theRuleOnTheRepointedReleasePicksUpTheNewShape() {
        assertEquals(Type.TBool, check(newShape, STANDARD, release = 2))
        assertEquals(Type.TStr, check(newShape, "customer.tier", release = 2))
    }

    // `eligibility-premium` reads `customer.name`, and sits on release 1 where it still exists.
    @Test
    fun theRuleOnTheUntouchedReleaseIsUnaffected() {
        assertEquals(Type.TStr, check(newShape, "customer.name", release = 1))
        val error = assertFailsWith<KleinException> { check(newShape, "customer.name", release = 2) }
        assertIs<TypeError.MissingField>(error.errors.single())
    }

    // Re-pointing `Customer` without re-pointing the capabilities that take it is what
    // self-containment refuses: release 2 would expose a capability taking a shape it cannot name.
    @Test
    fun repointingATypeAloneIsRefused() {
        val error =
            assertFailsWith<KleinException> {
                Klein.checkContract(newShape.trimIndent().replace("  creditScore/3", "  creditScore/2"))
            }
        assertEquals("Customer/1", assertIs<TypeError.ReleaseNotSelfContained>(error.errors.single()).unreachable)
    }

    // ── 4. An edit in place ──────────────────────────────────────────────────

    // The scorer stops needing the whole customer, so the parameter widens with no revision: any
    // argument the old signature accepted, the new one accepts.
    @Test
    fun aCompatibleSignatureEditNeedsNoRevision() {
        val widened = newShape.trimIndent().replace("fun creditScore/3(c: Customer/2)", "fun creditScore/3(c: { id: Num, tier: String })")
        assertEquals(Type.TBool, check(widened, "creditScore(customer) >= 640", release = 2))
    }

    // ── 5. Removal ───────────────────────────────────────────────────────────

    // Retiring release 1 folds it into release 2 and deletes its block. Release 2 already stated
    // all three names, so the fold adds nothing and the file simply loses its first block.
    private val retired =
        """
        type Customer/2 = Customer { id: Num, tier: String }

        customer/2: Customer/2
        fun creditScore/3(c: Customer/2): Num

        release 2
          Customer/2
          customer/2
          creditScore/3
        """

    @Test
    fun retiringTheOldestReleaseLeavesItsSuccessorMeaningTheSame() {
        val everyName = "{ score = creditScore(customer), tier = customer.tier }"
        assertEquals(check(newShape, everyName, release = 2), check(retired, everyName, release = 2))
        assertEquals("{ score: Num, tier: String }", Type.print(check(retired, everyName, release = 2)))
    }

    @Test
    fun aRetiredReleaseCannotBeCompiledAgainstAgain() {
        val contract = Klein.checkContract(retired.trimIndent())
        assertEquals(listOf(ReleaseNumber(2)), contract.releases)
        assertEquals(ReleaseNumber(1), assertFailsWith<UnknownRelease> { contract.check(STANDARD, ReleaseNumber(1)) }.number)
    }
}
