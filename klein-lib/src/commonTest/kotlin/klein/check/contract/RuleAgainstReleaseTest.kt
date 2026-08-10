package klein.check.contract

import klein.Klein
import klein.KleinError
import klein.KleinException
import klein.ReleaseNumber
import klein.check.RuleType
import klein.check.Type
import klein.check.TypeError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val CONTRACT =
    """
    type Customer = Customer { id: Num }
    type Customer/2 = Customer { id: Num, tier: String }
    type Shape/2 = Circle { radius: Num } | Square { side: Num }
    type Order/3 = Order { total: Num }

    fun creditScore(c: Customer): Num
    fun creditScore/2(c: Customer/2): Num
    latest/2: Customer/2

    release 1
      Customer
      creditScore

    release 2
      Customer/2
      Shape/2
      creditScore/2
      latest/2
    """.trimIndent()

private val contract = Klein.checkContract(CONTRACT)

private fun check(
    rule: String,
    release: Int = 2,
): RuleType = contract.check(rule, ReleaseNumber(release))

private fun errorsFrom(
    rule: String,
    release: Int = 2,
): List<KleinError> = assertFailsWith<KleinException> { check(rule, release) }.errors

/** No rule-facing diagnostic may spell a revision, whatever channel it came out of. */
private fun assertNoRevision(errors: List<KleinError>) {
    val revision = Regex("""/\d""")
    errors.forEach { assertTrue(!revision.containsMatchIn(it.message), "revision leaked: ${it.message}") }
}

/**
 * A rule checked against one release. Inside it every name means exactly one revision, the types
 * carry none, and no `/N` reaches any diagnostic. The sweep asserts what a *user* observes, which
 * the witness alone does not state.
 */
class RuleAgainstReleaseTest {
    // ── One release, one meaning per name ────────────────────────────────────

    @Test
    fun aRuleSeesTheRevisionItsReleaseNames() {
        assertEquals("(Customer) -> Num", Type.print(check("creditScore")))
        assertEquals("(Customer) -> Num", Type.print(check("creditScore", release = 1)))
    }

    @Test
    fun theShapeIsTheOneTheReleaseNames() {
        assertEquals(Type.TStr, check("fun tier(c: Customer): String = c.tier\ntier(latest)"))
        assertIs<TypeError.MissingField>(errorsFrom("fun tier(c: Customer): String = c.tier", release = 1).single())
    }

    @Test
    fun aRuleCallsACapabilityThroughItsPlainName() {
        assertEquals(Type.TNum, check("""creditScore(Customer(1, "gold"))"""))
        assertEquals(Type.TNum, check("creditScore(Customer(1))", release = 1))
    }

    @Test
    fun aNameNoReleaseExposesIsUnbound() {
        val error = assertIs<TypeError.UnboundVariable>(errorsFrom("fun f(o: Order): Num = o.total").first())
        assertEquals("Order", error.name)
    }

    @Test
    fun aReleaseTheContractDoesNotHaveIsRefused() {
        val error = assertFailsWith<UnknownRelease> { check("1", release = 9) }
        assertEquals(ReleaseNumber(9), error.number)
        assertEquals(listOf(ReleaseNumber(1), ReleaseNumber(2)), error.available)
    }

    // Each rule gets its own scope, so checking one cannot bind into the release's environment.
    @Test
    fun oneRulesBindingsAreInvisibleToTheNext() {
        assertEquals(Type.TNum, check("x = 1\nx"))
        assertEquals("x", assertIs<TypeError.UnboundVariable>(errorsFrom("x").single()).name)
    }

    // ── Constructors travel with their type ──────────────────────────────────

    @Test
    fun constructorsArriveUnderTheirPlainNames() {
        val rule =
            """
            fun area(s: Shape): Num = match s
              Circle c -> c.radius
              Square sq -> sq.side
            area(Circle(2))
            """.trimIndent()
        assertEquals(Type.TNum, check(rule))
    }

    // ── what else travels across projection ──────────────────────────────────

    /** A generic type keeps its arity and its parameters' variance, so subtyping still works. */
    @Test
    fun aGenericTypeKeepsItsArityAndVariance() {
        val contract =
            Klein.checkContract(
                """
                type Shape/2 = Circle { radius: Num } | Square { side: Num }
                type Box/2<'A> = Box { value: 'A }

                fun widen(b: Box/2<Shape/2>): Num

                release 2
                  Shape/2
                  Box/2
                  widen
                """.trimIndent(),
            )
        assertEquals("(Box<Shape>) -> Num", Type.print(contract.check("widen", ReleaseNumber(2))))
        assertEquals(Type.TNum, contract.check("widen(Box(Circle(1)))", ReleaseNumber(2)))
    }

    /** A recursive type arrives as a reference, so a rule can walk it. */
    @Test
    fun aRecursiveTypeIsWalkableFromARule() {
        val contract =
            Klein.checkContract(
                """
                type Tree/2 = Tree { value: Num, left: Tree/2? }

                release 2
                  Tree/2
                """.trimIndent(),
            )
        assertEquals(
            "Tree?",
            Type.print(contract.check("fun left(t: Tree): Tree? = t.left\nleft(Tree(1, null))", ReleaseNumber(2))),
        )
    }

    @Test
    fun exhaustivenessNamesTheMissingConstructorPlainly() {
        val rule =
            """
            fun area(s: Shape): Num = match s
              Circle c -> c.radius
            """.trimIndent()
        val error = assertIs<TypeError.NonExhaustiveMatch>(errorsFrom(rule).single())
        assertEquals(listOf("Square"), error.missing)
    }

    // ── The leak sweep ───────────────────────────────────────────────────────

    @Test
    fun aTypeMismatchNamesThePlainType() {
        val errors = errorsFrom("creditScore(1)")
        val error = assertIs<TypeError.TypeMismatch>(errors.single())
        assertEquals("Customer", Type.print(error.supertype))
        assertNoRevision(errors)
    }

    @Test
    fun aMissingFieldNamesThePlainType() {
        val errors = errorsFrom("fun f(c: Customer): Num = c.nope")
        assertIs<TypeError.MissingField>(errors.single())
        assertNoRevision(errors)
    }

    @Test
    fun aNotAFunctionNamesThePlainType() {
        val errors = errorsFrom("latest(1)")
        val error = assertIs<TypeError.NotAFunction>(errors.first())
        assertEquals("Customer", Type.print(error.actual))
        assertNoRevision(errors)
    }

    @Test
    fun aBadConstructorPatternNamesThePlainType() {
        val rule =
            """
            fun area(s: Shape): Num = match s
              Triangle -> 1
              _ -> 0
            """.trimIndent()
        val errors = errorsFrom(rule)
        val error = assertIs<TypeError.NotAConstructorOf>(errors.first())
        assertEquals("Shape", Type.print(error.scrutinee))
        assertNoRevision(errors)
    }

    @Test
    fun anUnmatchableScrutineeNamesThePlainType() {
        val rule =
            """
            match creditScore
              _ -> 1
            """.trimIndent()
        val errors = errorsFrom(rule)
        val error = assertIs<TypeError.CannotMatchOn>(errors.single())
        assertEquals("(Customer) -> Num", Type.print(error.scrutinee))
        assertNoRevision(errors)
    }

    @Test
    fun anUnboundVariableNamesWhatWasWritten() {
        assertNoRevision(errorsFrom("nope"))
    }
}
