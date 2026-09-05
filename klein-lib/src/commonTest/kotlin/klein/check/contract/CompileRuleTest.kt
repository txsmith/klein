package klein.check.contract

import klein.Klein
import klein.KleinException
import klein.ReleaseNumber
import klein.RevisionNumber
import klein.check.TypeError
import klein.core.Bind
import klein.core.EnterScope
import klein.core.assertRuleLowersTo
import klein.interp.Value
import klein.orFail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

private val CONTRACT =
    """
    type Customer = Customer { id: Num, tier: String }
    type Shape/2 = Circle { area: Num } | Square { area: Num }
    type Shape/3 = Circle { area: Num } | Square { area: Num } | Dot { area: Num }

    customer: Customer
    fun creditScore(c: Customer): Num
    fun creditScore/2(c: Customer): Num
    fun riskBand/2(c: Customer): String

    release 1
      Customer
      customer
      creditScore
      Shape/2

    release 2
      Shape/3
      creditScore/2
      riskBand/2
    """.trimIndent()

private val contract = Klein.checkContract(CONTRACT)

private fun compile(
    rule: String,
    release: Int = 1,
): Edition = contract.compileRule(rule.trimIndent(), ReleaseNumber(release)).orFail()

private fun preludeNames(edition: Edition): List<String> = (edition.core as EnterScope).stmts.map { (it as Bind).name }

class CompileRuleTest {
    @Test
    fun theSameRuleAgainstTwoReleasesYieldsDifferentPins() {
        val rule = "creditScore(customer)"
        assertEquals(mapOf("creditScore" to RevisionNumber(1), "customer" to RevisionNumber(1)), compile(rule, release = 1).pins)
        assertEquals(mapOf("creditScore" to RevisionNumber(2), "customer" to RevisionNumber(1)), compile(rule, release = 2).pins)
    }

    @Test
    fun theEditionRecordsTheReleaseItWasCompiledAgainst() {
        assertEquals(ReleaseNumber(2), compile("customer.tier", release = 2).release)
    }

    @Test
    fun pinsContainOnlyNamesTheRuleUsed() {
        assertEquals(setOf("customer"), compile("customer.tier").pins.keys)
    }

    @Test
    fun aSumTypesConstructorIsPinnedAndInThePreludeThoughNoReleaseEntryNamesIt() {
        val edition = compile("Circle(9).area")
        assertEquals(mapOf("Circle" to RevisionNumber(2)), edition.pins)
        assertEquals(listOf("Circle"), preludeNames(edition))
    }

    @Test
    fun aTypeOnlyUsePinsTheTypeAndAddsNothingToThePrelude() {
        val edition = compile("fun process(s: Shape): Num = s.area")
        assertEquals(mapOf("Shape" to RevisionNumber(2)), edition.pins)
        assertEquals(emptyList(), preludeNames(edition))
    }

    @Test
    fun theSameTypeOnlyRuleAgainstTheRepointedReleasePinsTheNewRevision() {
        assertEquals(mapOf("Shape" to RevisionNumber(3)), compile("fun process(s: Shape): Num = s.area", release = 2).pins)
    }

    @Test
    fun aFieldOfTheRulesOwnTypeDefinitionPinsTheContractType() {
        val edition =
            compile(
                """
                type Canvas = Canvas { top: Shape }
                1
                """,
            )
        assertEquals(mapOf("Shape" to RevisionNumber(2)), edition.pins)
    }

    @Test
    fun aConstructorOnlyEditionExecutesToAValue() {
        val edition = compile("""Customer(1, "gold").tier == "gold"""")
        val result = Klein.execute(edition.core)
        assertEquals(emptyList(), result.diagnostics)
        assertEquals(Value.VBool(true), result.output)
    }

    @Test
    fun anUnexposedNameIsStillUnbound() {
        val errors = contract.compileRule("riskBand(customer)", ReleaseNumber(1)).diagnostics
        assertEquals("riskBand", assertIs<TypeError.UnboundVariable>(errors.single()).name)
    }

    @Test
    fun unknownReleasePropagates() {
        assertIs<UnknownRelease>(assertFailsWith<KleinException> { compile("1", release = 9) }.errors.single())
    }

    // ── golden confirmations of the assembly ─────────────────────────────────

    @Test
    fun theStandardRuleLowersWithItsPrelude() =
        assertRuleLowersTo(
            contract,
            "creditScore(customer) >= 620",
            ReleaseNumber(1),
            """
            scope
              bind creditScore#0 = fun creditScore/1 -> host creditScore(_0[0;0])
              bind customer#1 = host customer()
              (creditScore[0;0](customer[0;1]) >= 620)
            """,
        )

    @Test
    fun aConstructorOnlyRuleLowersItsPreludeAndBody() =
        assertRuleLowersTo(
            contract,
            """
            c = Customer(1, "gold")
            c.tier
            """,
            ReleaseNumber(1),
            """
            scope
              bind Customer#0 = fun Customer/2 -> Customer{id: id[0;0], tier: tier[0;1]}
              scope
                bind c#0 = Customer[1;0](1, "gold")
                c[0;0].tier
            """,
        )
}
