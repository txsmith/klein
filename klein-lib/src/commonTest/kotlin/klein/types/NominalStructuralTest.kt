package klein.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun nominalSubtypesStructural_consPartialFieldAccess() {
        assertType(
            "Num",
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }

                fun getHead(r) = r.head
                getHead(Cons(42, Nil))
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
    fun nominalInFunctionContext_acceptsNominalForStructural() {
        assertType(
            "Num",
            infer(
                """
                type Dollars = Dollars { amount: Num }

                fun getAmount(r) = r.amount
                getAmount(Dollars(50))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun nominalInFunctionContext_functionInferredToRequireStructuralAcceptsNominal() {
        assertType(
            "Num",
            infer(
                """
                type Point = Point { x: Num, y: Num }

                fun sumCoords(p) = p.x + p.y
                sumCoords(Point(3, 4))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun nominalInFunctionContext_genericFunctionAcceptsNominal() {
        assertType(
            "Num",
            infer(
                """
                type Box<'A> = Box { content: 'A }

                fun unbox(b) = b.content
                unbox(Box(42))
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

    // ============================================================
    // Structural records cannot subtype nominal types
    // ============================================================

    @Test
    fun recordLiteralCannotSubtypeNominalParam() {
        val errors =
            inferErrors(
                """
                type Money = Money { value: Num }
                type MoneyConsumer = MoneyConsumer { f: Money -> Num }
                mc = MoneyConsumer(|m -> m.value|)
                mc.f({ value = 100 })
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty(), "Structural record should not be usable where Money is expected")
    }

    @Test
    fun recordWithExactSameFieldsCannotSubtypeNominal() {
        val errors =
            inferErrors(
                """
                type Point = Point { x: Num, y: Num }
                type PointConsumer = PointConsumer { f: Point -> Num }
                pc = PointConsumer(|p -> p.x + p.y|)
                pc.f({ x = 3, y = 4 })
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty(), "Record with same fields as Point should not subtype Point")
    }

    @Test
    fun genericStructuralCannotSubtypeGenericNominal() {
        val errors =
            inferErrors(
                """
                type Box<'A> = Box { content: 'A }
                type BoxNumConsumer = BoxNumConsumer { f: Box<Num> -> Num }
                bc = BoxNumConsumer(|b -> b.content|)
                bc.f({ content = 42 })
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty(), "Structural record should not subtype generic nominal Box<Num>")
    }

    @Test
    fun twoNominalTypesSameStructureCannotBeConfused() {
        val errors =
            inferErrors(
                """
                type Dollars = Dollars { amount: Num }
                type Euros = Euros { amount: Num }
                type DollarConsumer = DollarConsumer { f: Dollars -> Num }
                dc = DollarConsumer(|d -> d.amount|)
                dc.f(Euros(50))
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty(), "Euros should not be usable where Dollars is expected")
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
    fun threeLevelNominal_deepFieldAccessThroughStructural() {
        assertType(
            "String",
            infer(
                """
                type Address = Address { city: String, zip: Num }
                type Person = Person { name: String, address: Address }
                type Company = Company { ceo: Person, revenue: Num }

                fun getCeoCity(c) = c.ceo.address.city
                getCeoCity(Company(Person("Bob", Address("Amsterdam", 1011)), 1000000))
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

    @Test
    fun nestedNominal_structuralCannotSubstituteForInnerNominal() {
        val errors =
            inferErrors(
                """
                type Address = Address { city: String, zip: Num }
                type Person = Person { name: String, address: Address }

                Person("Alice", { city = "Nairobi", zip = 10100 })
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty(), "Structural record should not be accepted where nominal Address is required")
    }
}
