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

private fun contractErrors(src: String): List<KleinError> =
    assertFailsWith<KleinException> { Klein.checkContract(src.trimIndent()) }.errors

private fun notSelfContained(src: String): TypeError.ReleaseNotSelfContained =
    assertIs<TypeError.ReleaseNotSelfContained>(contractErrors(src).single())

/**
 * `contracts.md` §"A release must be self-contained": every type reachable from anything a release
 * exposes must itself be exposed at that same revision.
 *
 * This is what makes dropping the revision on the way to a rule lossless. Without it a release can
 * expose `Customer` at revision 1 beside a capability taking `Customer/2`, and a rule hands a
 * one-field record to a handler written against two.
 */
class SelfContainmentTest {
    @Test
    fun aCapabilityReachingAnotherRevisionOfAnExposedTypeIsRejected() {
        val error =
            notSelfContained(
                """
                type Customer = Customer { id: Num }
                type Customer/2 = Customer { id: Num, tier: String }

                fun creditScore/2(c: Customer/2): Num

                release 1
                  Customer
                  creditScore/2
                """,
            )
        assertEquals("Customer/2", error.unreachable)
        assertEquals(ReleaseNumber(1), error.release)
    }

    /** The spec's own example: release 2 states only `creditScore/2` and inherits `Customer` at
     *  revision 1, so self-containment is judged on the folded surface rather than on the block. */
    @Test
    fun anInheritedPointerIsWhatMakesALaterReleaseIncoherent() {
        val error =
            notSelfContained(
                """
                type Customer = Customer { id: Num }
                type Customer/2 = Customer { id: Num, tier: String }

                fun creditScore(c: Customer): Num
                fun creditScore/2(c: Customer/2): Num

                release 1
                  Customer
                  creditScore

                release 2
                  creditScore/2
                """,
            )
        assertEquals("Customer/2", error.unreachable)
        assertEquals(ReleaseNumber(2), error.release)
    }

    @Test
    fun namingTheTypeInTheBlockFixesIt() {
        val contract =
            Klein.checkContract(
                """
                type Customer = Customer { id: Num }
                type Customer/2 = Customer { id: Num, tier: String }

                fun creditScore(c: Customer): Num
                fun creditScore/2(c: Customer/2): Num

                release 1
                  Customer
                  creditScore

                release 2
                  Customer/2
                  creditScore/2
                """.trimIndent(),
            )
        assertEquals(TNum, contract.check("""creditScore(Customer(1, "gold"))""", ReleaseNumber(2)))
    }

    @Test
    fun aResultPositionReferenceCountsToo() {
        assertEquals(
            "Customer/2",
            notSelfContained(
                """
                type Customer/2 = Customer { id: Num }

                fun latest/2(): Customer/2

                release 2
                  latest/2
                """,
            ).unreachable,
        )
    }

    // ── the walk is transitive through constructor fields ────────────────────

    @Test
    fun aTypeReachableOnlyThroughAConstructorFieldMustBeExposed() {
        assertEquals(
            "Address/2",
            notSelfContained(
                """
                type Address/2 = Address { zip: String }
                type Customer/2 = Customer { id: Num, addr: Address/2 }

                fun creditScore/2(c: Customer/2): Num

                release 2
                  Customer/2
                  creditScore/2
                """,
            ).unreachable,
        )
    }

    @Test
    fun aFieldOfOneConstructorOfASumCountsToo() {
        assertEquals(
            "Colour/2",
            notSelfContained(
                """
                type Colour/2 = Colour { hex: String }
                type Shape/2 = Circle { radius: Num, colour: Colour/2 } | Square { side: Num }

                release 2
                  Shape/2
                """,
            ).unreachable,
        )
    }

    /**
     * A sum's interface types each shared field as the *lub* of its arms, so the interface can name
     * a type no constructor field does — here `x` is `lub(Circle/2, Square/2)`, which is `Shape/2`.
     * That is why the walk reads the interface as well as the constructors: neither contains the
     * other.
     */
    @Test
    fun aLubbedInterfaceFieldNamesATypeNoConstructorFieldDoes() {
        val errors =
            contractErrors(
                """
                type Shape/2 = Circle { r: Num } | Square { s: Num }
                type Wrapper/2 = P { x: Circle/2 } | Q { x: Square/2 }

                release 2
                  Wrapper/2
                """,
            )
        assertEquals(
            listOf("Circle/2", "Shape/2", "Square/2"),
            errors.map { assertIs<TypeError.ReleaseNotSelfContained>(it).unreachable }.sorted(),
        )
    }

    @Test
    fun aTypeArgumentCountsToo() {
        assertEquals(
            "Customer/2",
            notSelfContained(
                """
                type Customer/2 = Customer { id: Num }
                type Box/2<'A> = Box { value: 'A }

                fun boxed/2(): Box/2<Customer/2>

                release 2
                  Box/2
                  boxed/2
                """,
            ).unreachable,
        )
    }

    /** One generic type, two arguments: the visited set stops the *expansion* of a name, never the
     *  arguments written at it, so the second `Box` is still a reference to follow. */
    @Test
    fun eachArgumentOfARepeatedGenericCounts() {
        assertEquals(
            "Address/2",
            notSelfContained(
                """
                type Order/2 = Order { total: Num }
                type Address/2 = Address { zip: String }
                type Box/2<'A> = Box { value: 'A }

                fun ship/2(o: Box/2<Order/2>, a: Box/2<Address/2>): Num

                release 2
                  Box/2
                  Order/2
                  ship/2
                """,
            ).unreachable,
        )
    }

    // ── the roots are everything the release exposes ─────────────────────────

    /** An exposed type is vocabulary in its own right: a rule can annotate with it, and read a
     *  field one level down, whether or not any capability mentions it. */
    @Test
    fun anExposedTypeIsARootEvenWhenNoCapabilityMentionsIt() {
        assertEquals(
            "Address/2",
            notSelfContained(
                """
                type Address/2 = Address { zip: String }
                type Customer/2 = Customer { id: Num, addr: Address/2 }

                release 2
                  Customer/2
                """,
            ).unreachable,
        )
    }

    @Test
    fun everyUnreachableNameIsReported() {
        val errors =
            contractErrors(
                """
                type Address/2 = Address { zip: String }
                type Order/2 = Order { total: Num }
                type Customer/2 = Customer { id: Num, addr: Address/2, order: Order/2 }

                release 2
                  Customer/2
                """,
            )
        assertEquals(
            listOf("Address/2", "Order/2"),
            errors.map { assertIs<TypeError.ReleaseNotSelfContained>(it).unreachable },
        )
    }

    // ── what is already reachable ────────────────────────────────────────────

    /** Constructors travel with their type, so a field naming one needs no entry of its own. */
    @Test
    fun aConstructorOfAnExposedTypeIsReachable() {
        val contract =
            Klein.checkContract(
                """
                type Shape/2 = Circle { radius: Num } | Square { side: Num }
                type Pick/2 = Pick { first: Circle/2 }

                release 2
                  Shape/2
                  Pick/2
                """.trimIndent(),
            )
        assertEquals(TNum, contract.check("fun r(p: Pick): Num = p.first.radius\nr(Pick(Circle(1)))", ReleaseNumber(2)))
    }

    /** The walk terminates on a visited set, so a self-referential type is self-contained. */
    @Test
    fun aRecursiveTypeIsSelfContained() {
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
            Type.print(contract.check("fun l(t: Tree): Tree? = t.left\nl(Tree(1, null))", ReleaseNumber(2))),
        )
    }

    @Test
    fun aReleaseExposingNothingIsSelfContained() {
        Klein.checkContract("type Customer/2 = Customer { id: Num }\n\nrelease 2")
    }

    @Test
    fun aBuiltInTypeNeedsNoEntry() {
        val contract = Klein.checkContract("maxRetries: Num\n\nrelease 1\n  maxRetries")
        assertEquals(TNum, contract.check("maxRetries", ReleaseNumber(1)))
    }

    /** A declaration no release exposes is not a root, so it may reach anything at all. */
    @Test
    fun anUnexposedDeclarationConstrainsNothing() {
        Klein.checkContract(
            """
            type Customer = Customer { id: Num }
            type Customer/2 = Customer { id: Num, tier: String }

            fun creditScore/2(c: Customer/2): Num

            release 1
              Customer
            """.trimIndent(),
        )
    }
}
