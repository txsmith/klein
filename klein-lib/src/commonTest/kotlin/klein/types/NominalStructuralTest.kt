package klein.types

import kotlin.test.Test
import kotlin.test.assertEquals

class NominalStructuralTest {
    @Test
    fun nominalSubtypesStructural_moneyFieldAccess() {
        assertType(
            "Num",
            infer(
                """
                type Money = Money { value: Num }

                fun getValue(r) = r.value
                getValue(Money(100))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun nominalSubtypesStructural_multipleFieldsAccess() {
        assertType(
            "{ x: Num, y: Num }",
            infer(
                """
                type Point = Point { x: Num, y: Num }

                fun toRecord(p) = { x = p.x, y = p.y }
                toRecord(Point(1, 2))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun nominalInRecordField_nestedNominalAccess() {
        assertType(
            "Num",
            infer(
                """
                type Money = Money { value: Num }
                type Account = Account { balance: Money, owner: String }

                fun getBalance(a) = a.balance.value
                getBalance(Account(Money(500), "Alice"))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun mixedTypesInList_infersUnionType() {
        assertType(
            "Cons<Num | String>",
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }

                Cons(1, Cons("two", Nil))
                """.trimIndent(),
            ),
            expectedLub = "Cons<Any>",
        )
    }

    @Test
    fun nominalInRecordField_structuralCannotReplaceNestedNominal() {
        val errors =
            inferErrors(
                """
                type Money = Money { value: Num }
                type Account = Account { balance: Money, owner: String }

                Account({ value = 100 }, "Bob")
                """.trimIndent(),
            )
        assertEquals(1, errors.size, "Cannot construct Account with structural record instead of Money")
        assertMismatch(errors[0], "{ value: Num }", "Money")
    }

    @Test
    fun genericStructuralCannotSubtypeGenericNominal() {
        val errors =
            inferErrors(
                """
                type Box<'A> = Box { content: 'A }
                type ForceBox = ForceBox { b: Box<Num> }

                ForceBox({ content = 42 })
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertMismatch(errors[0], "{ content: Num }", "Box<Num>")
    }

    @Test
    fun twoNominalTypesSameStructureCannotBeConfused() {
        val errors =
            inferErrors(
                """
                type Dollars = Dollars { amount: Num }
                type Euros = Euros { amount: Num }
                type ForceDollars = ForceDollars { d: Dollars }

                ForceDollars(Euros(50))
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertMismatch(errors[0], "Euros", "Dollars")
    }

    // ============================================================
    // Nested nominal subtyping
    // ============================================================

    @Test
    fun twoLevelNominal_structuralFunctionAccessesNestedField() {
        assertType(
            "String",
            infer(
                """
                type Address = Address { city: String, zip: Num }
                type Person = Person { name: String, address: Address }

                fun getCity(p) = p.address.city
                getCity(Person("Alice", Address("Nairobi", 10100)))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun nestedGenericNominals_typeParamsAtEachLevel() {
        assertType(
            "Num",
            infer(
                """
                type Wrapper<'A> = Wrapper { inner: 'A }
                type Box<'A> = Box { content: Wrapper<'A> }

                fun unwrapBox(b) = b.content.inner
                unwrapBox(Box(Wrapper(42)))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun mixedNominalStructural_nominalContainingStructuralContainingNominal() {
        assertType(
            "String",
            infer(
                """
                type Tag = Tag { label: String }

                fun getLabel(x) = x.meta.tag.label
                getLabel({ meta = { tag = Tag("important") } })
                """.trimIndent(),
            ),
        )
    }
}
