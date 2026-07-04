package klein.check

import klein.check.Type.*
import klein.types.TypeError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Nominal types — ported from the SimpleSub `NominalStructuralTest`. **All red targets:** the new
 * checker treats `type` declarations as a no-op (no constructors, no `TRef`, no nominal subtyping),
 * so every test here fails until nominal support lands.
 *
 * Ported with two Path G adjustments:
 *  - functions that inferred an unannotated parameter's type from usage (`fun getValue(r) = r.value`)
 *    were given explicit annotations — Path G requires them;
 *  - the SimpleSub test that inferred an anonymous union (`Cons(1, Cons("two", Nil)) : Cons<Num | String>`)
 *    was dropped — Path G has no anonymous unions.
 */
class NominalTypeCheckTest {
    /** Assert a program checks with no errors and yields [expected] — errors matter here because a
     *  `⊥`-recovering constructor (App-Bot on an unbound name) would otherwise let the type slip through. */
    private fun assertChecks(
        expected: Type,
        src: String,
    ) {
        val result = infer(src)
        assertTrue(result.errors.isEmpty(), "unexpected errors: ${result.errors}")
        assertEquals(expected, result.type)
    }

    // --- nominal <: structural (records-as-interfaces): a nominal value where a record is expected ---

    @Test
    fun nominalWhereStructuralRecordExpected_singleField() =
        assertChecks(
            TNum,
            """
            type Money = Money { value: Num }
            fun getValue(r: { value: Num }): Num = r.value
            getValue(Money(100))
            """.trimIndent(),
        )

    @Test
    fun nominalWhereStructuralRecordExpected_multipleFields() =
        assertChecks(
            TRecord(mapOf("x" to TNum, "y" to TNum)),
            """
            type Point = Point { x: Num, y: Num }
            fun toRecord(p: { x: Num, y: Num }): { x: Num, y: Num } = { x = p.x, y = p.y }
            toRecord(Point(1, 2))
            """.trimIndent(),
        )

    @Test
    fun nestedNominalFieldAccess() =
        assertChecks(
            TNum,
            """
            type Money = Money { value: Num }
            type Account = Account { balance: Money, owner: String }
            fun getBalance(a: Account): Num = a.balance.value
            getBalance(Account(Money(500), "Alice"))
            """.trimIndent(),
        )

    @Test
    fun twoLevelNominalFieldAccess() =
        assertChecks(
            TStr,
            """
            type Address = Address { city: String, zip: Num }
            type Person = Person { name: String, address: Address }
            fun getCity(p: Person): String = p.address.city
            getCity(Person("Alice", Address("Nairobi", 10100)))
            """.trimIndent(),
        )

    @Test
    fun nestedGenericNominals() =
        assertChecks(
            TNum,
            """
            type Wrapper<'A> = Wrapper { inner: 'A }
            type Box<'A> = Box { content: Wrapper<'A> }
            fun unwrapBox(b: Box<Num>): Num = b.content.inner
            unwrapBox(Box(Wrapper(42)))
            """.trimIndent(),
        )

    @Test
    fun nominalInsideStructuralInsideNominal() =
        assertChecks(
            TStr,
            """
            type Tag = Tag { label: String }
            fun getLabel(x: { meta: { tag: Tag } }): String = x.meta.tag.label
            getLabel({ meta = { tag = Tag("important") } })
            """.trimIndent(),
        )

    // --- nominal is NOT structural: a structural record can't stand in for a nominal type ---

    @Test
    fun structuralRecordCannotReplaceNominalField() {
        val errors =
            infer(
                """
                type Money = Money { value: Num }
                type Account = Account { balance: Money, owner: String }
                Account({ value = 100 }, "Bob")
                """.trimIndent(),
            ).errors
        assertTrue(errors.any { it is TypeError.TypeMismatch }, "expected a Money/record mismatch, got: $errors")
    }

    @Test
    fun structuralRecordCannotReplaceGenericNominalField() {
        val errors =
            infer(
                """
                type Box<'A> = Box { content: 'A }
                type ForceBox = ForceBox { b: Box<Num> }
                ForceBox({ content = 42 })
                """.trimIndent(),
            ).errors
        assertTrue(errors.any { it is TypeError.TypeMismatch }, "expected a Box<Num>/record mismatch, got: $errors")
    }

    @Test
    fun distinctNominalTypesWithSameStructureAreNotInterchangeable() {
        val errors =
            infer(
                """
                type Dollars = Dollars { amount: Num }
                type Euros = Euros { amount: Num }
                type ForceDollars = ForceDollars { d: Dollars }
                ForceDollars(Euros(50))
                """.trimIndent(),
            ).errors
        assertTrue(errors.any { it is TypeError.TypeMismatch }, "expected a Euros/Dollars mismatch, got: $errors")
    }
}
