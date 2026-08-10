package klein.check.contract

import klein.Klein
import klein.KleinError
import klein.KleinException
import klein.ReleaseNumber
import klein.check.Type
import klein.check.Type.TNum
import klein.check.TypeError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val DECLARATIONS =
    """
    type Customer = Customer { id: Num }
    type Customer/2 = Customer { id: Num, tier: String }

    fun creditScore(c: Customer): Num
    fun creditScore/2(c: Customer/2): Num
    maxRetries: Num
    """.trimIndent()

/** [DECLARATIONS] followed by [releases], so each test writes only the blocks it is about. */
private fun contractWith(releases: String) = "$DECLARATIONS\n\n${releases.trimIndent()}"

private fun contractErrors(src: String): List<KleinError> =
    assertFailsWith<KleinException> { Klein.checkContract(src) }.errors

/**
 * `contracts.md` §Releases as the checker enforces it: an entry points at a declaration, and what a
 * release exposes is what its entries name — observed through what a rule on that release can spell.
 */
class ReleaseTypeCheckTest {
    // ── What a release exposes ───────────────────────────────────────────────

    // What a release exposes at the revision it names is `RuleAgainstReleaseTest`'s subject.

    @Test
    fun anEntryWithoutARevisionMeansRevisionOne() {
        val contract =
            Klein.checkContract(
                contractWith(
                    """
                    release 1
                      Customer
                      maxRetries
                    """,
                ),
            )
        assertEquals(TNum, contract.check("maxRetries", ReleaseNumber(1)))
        assertEquals(TNum, contract.check("fun id(c: Customer): Num = c.id\nid(Customer(1))", ReleaseNumber(1)))
    }

    @Test
    fun aBlockKeepsItsNumber() {
        val contract =
            Klein.checkContract(
                contractWith(
                    """
                    release 7
                      Customer
                    """,
                ),
            )
        assertEquals(listOf(ReleaseNumber(7)), contract.releases)
    }

    @Test
    fun anEmptyBlockIsLegalAndExposesNothing() {
        val contract = Klein.checkContract(contractWith("release 2"))
        assertEquals(listOf(ReleaseNumber(2)), contract.releases)
        val error = assertFailsWith<KleinException> { contract.check("maxRetries", ReleaseNumber(2)) }
        assertIs<TypeError.UnboundVariable>(error.errors.single())
    }

    // A capability may be declared and implemented ahead of the release that exposes it.
    @Test
    fun aDeclarationNoReleaseReachesIsLegal() {
        Klein.checkContract(
            contractWith(
                """
                release 1
                  Customer
                  creditScore
                """,
            ),
        )
    }

    @Test
    fun aContractWithNoBlockHasNoReleases() {
        assertTrue(Klein.checkContract(DECLARATIONS).releases.isEmpty())
    }

    @Test
    fun theContractReportsItsReleaseNumbers() {
        val contract =
            Klein.checkContract(
                contractWith(
                    """
                    release 1
                      Customer

                    release 2
                      Customer/2
                    """,
                ),
            )
        assertEquals(listOf(ReleaseNumber(1), ReleaseNumber(2)), contract.releases)
    }

    // ── What the checker rejects ─────────────────────────────────────────────

    @Test
    fun pointingAtARevisionThatIsNotDeclaredIsRejected() {
        val errors =
            contractErrors(
                contractWith(
                    """
                    release 2
                      Customer/9
                    """,
                ),
            )
        val error = assertIs<TypeError.UnknownReleaseTarget>(errors.single())
        assertEquals("Customer/9", error.name)
        assertEquals(ReleaseNumber(2), error.release)
    }

    @Test
    fun pointingAtANameNothingDeclaresIsRejected() {
        val errors =
            contractErrors(
                contractWith(
                    """
                    release 2
                      nope
                    """,
                ),
            )
        assertEquals("nope", assertIs<TypeError.UnknownReleaseTarget>(errors.single()).name)
    }

    // Constructors travel with their type and are never pointed at individually, so an entry
    // naming one resolves against nothing.
    @Test
    fun pointingAtAConstructorIsRejected() {
        val errors =
            contractErrors(
                """
                type Shape/2 = Circle { radius: Num } | Square { side: Num }

                release 2
                  Circle/2
                """.trimIndent(),
            )
        assertEquals("Circle/2", assertIs<TypeError.UnknownReleaseTarget>(errors.single()).name)
    }

    @Test
    fun theSameNameTwiceInOneBlockIsRejected() {
        val errors =
            contractErrors(
                contractWith(
                    """
                    release 2
                      Customer
                      Customer/2
                    """,
                ),
            )
        val error = assertIs<TypeError.DuplicateReleaseEntry>(errors.single())
        assertEquals("Customer", error.name)
        assertEquals(ReleaseNumber(2), error.release)
    }

    /** Resolution reports every bad entry rather than stopping at the first. */
    @Test
    fun aRejectedEntryDoesNotStopTheRest() {
        val errors =
            contractErrors(
                contractWith(
                    """
                    release 2
                      Customer/9
                      alsoNope
                    """,
                ),
            )
        assertEquals(2, errors.size, "$errors")
        assertEquals(listOf("Customer/9", "alsoNope"), errors.map { (it as TypeError.UnknownReleaseTarget).name })
    }
}
