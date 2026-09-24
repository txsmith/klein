package klein.check.contract

import klein.Klein
import klein.KleinException
import klein.ReleaseNumber
import klein.RevisionNumber
import klein.UnknownRelease
import klein.check.TypeError
import klein.core.Bind
import klein.core.EnterScope
import klein.core.assertRuleLowersTo
import klein.interp.Value
import klein.assertRejected
import klein.orFail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

private val CONTRACT =
    """
    environment acme

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

private fun pins(vararg pins: Pair<String, Int>): Map<String, RevisionNumber> = pins.associate { (name, revision) -> name to RevisionNumber(revision) }

private fun preludeNames(edition: Edition): List<String> = (edition.core as EnterScope).stmts.map { (it as Bind).name }

class CompileRuleTest {
    @Test
    fun theSameRuleAgainstTwoReleasesYieldsDifferentPins() {
        val rule = "creditScore(customer)"
        assertEquals(pins("creditScore" to 1, "customer" to 1, "Customer" to 1), compile(rule, release = 1).pins)
        assertEquals(pins("creditScore" to 2, "customer" to 1, "Customer" to 1), compile(rule, release = 2).pins)
    }

    @Test
    fun theEditionRecordsTheSourceItWasCompiledFromVerbatim() {
        // We expect whitespace to be preserved
        val source = "  customer.tier  "
        assertEquals(source, contract.compileRule(source, ReleaseNumber(2)).orFail().source)
    }

    @Test
    fun pinsCoverOnlyWhatTheRuleReaches() {
        assertEquals(setOf("customer", "Customer"), compile("customer.tier").pins.keys)
    }

    @Test
    fun usingAConstructorPinsItsTypeNotTheConstructor() {
        assertEquals(pins("Shape" to 2), compile("Circle(9).area").pins)
    }

    @Test
    fun onlyTheUsedConstructorEntersThePrelude() {
        assertEquals(listOf("Circle"), preludeNames(compile("Circle(9).area")))
    }

    @Test
    fun annotatingWithAContractTypePinsIt() {
        val edition = compile("fun process(s: Shape): Num = s.area")
        assertEquals(pins("Shape" to 2), edition.pins)
    }

    @Test
    fun annotatingWithAContractTypeBindsNothingInThePrelude() {
        assertEquals(emptyList(), preludeNames(compile("fun process(s: Shape): Num = s.area")))
    }

    @Test
    fun theSameTypeOnlyRuleAgainstTheRepointedReleasePinsTheNewRevision() {
        assertEquals(pins("Shape" to 3), compile("fun process(s: Shape): Num = s.area", release = 2).pins)
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
        assertEquals(pins("Shape" to 2), edition.pins)
    }

    @Test
    fun aConstructorOnlyEditionExecutesToAValue() {
        val edition = compile("""Customer(1, "gold").tier == "gold"""")
        assertEquals(Value.VBool(true), Klein.execute(edition.core).orFail())
    }

    @Test
    fun anUnexposedNameIsStillUnbound() {
        val errors = contract.compileRule("riskBand(customer)", ReleaseNumber(1)).assertRejected()
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
