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
        assertTrue(errors.isNotEmpty(), "Cannot construct Account with structural record instead of Money")
    }
}
