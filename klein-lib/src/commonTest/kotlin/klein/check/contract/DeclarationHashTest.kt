package klein.check.contract

import klein.Klein
import klein.RevisionNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

private fun hashOf(
    contract: String,
    name: String,
    revision: Int = 1,
): Long? = Klein.checkContract(contract.trimIndent()).hashOf(name, RevisionNumber(revision))

private fun assertSameHash(
    name: String,
    first: String,
    second: String,
) = assertEquals(hashOf(first, name), hashOf(second, name), "'$name' should hash the same in both contracts")

private fun assertDifferentHash(
    name: String,
    first: String,
    second: String,
) = assertNotEquals(hashOf(first, name), hashOf(second, name), "'$name' should hash differently in the two contracts")

class DeclarationHashTest {
    @Test
    fun theSameContractTextHashesTheSameInTwoInstances() {
        val contract =
            """
            type Customer = Customer { id: Num, tier: String }
            type Box<'A> = Box { value: 'A }
            customer: Customer
            fun creditScore(c: Customer): Num
            fun pick(x: 'A, y: 'A): 'A
            """
        listOf("Customer", "Box", "customer", "creditScore", "pick").forEach { assertSameHash(it, contract, contract) }
    }

    @Test
    fun anUnknownNameOrRevisionHasNoHash() {
        assertNull(hashOf("fun creditScore(c: Num): Num", "creditScore", revision = 2))
        assertNull(hashOf("fun creditScore(c: Num): Num", "nobody"))
    }

    @Test
    fun aCapabilityAndATypeHashDifferently() {
        val contract = "type Customer = Customer { id: Num }\ncustomer: Customer\nfun creditScore(c: Customer): Num"
        val hashes = listOf("Customer", "customer", "creditScore").map { hashOf(contract, it) }
        assertEquals(3, hashes.toSet().size)
    }

    @Test
    fun recordFieldOrderDoesNotMatter() {
        assertSameHash("f", "fun f(c: { a: Num, b: String }): Num", "fun f(c: { b: String, a: Num }): Num")
    }

    @Test
    fun aParameterRenameChangesTheHash() {
        assertDifferentHash("f", "type Customer = Customer { id: Num }\nfun f(c: Customer): Num", "type Customer = Customer { id: Num }\nfun f(customer: Customer): Num")
    }

    @Test
    fun aParameterTypeChangeChangesTheHash() {
        assertDifferentHash("f", "fun f(c: Num): Num", "fun f(c: String): Num")
    }

    @Test
    fun aResultTypeChangeChangesTheHash() {
        assertDifferentHash("f", "fun f(c: Num): Num", "fun f(c: Num): String")
    }

    @Test
    fun aTypeVariableRenameDoesNotMatter() {
        assertSameHash("pick", "fun pick(x: 'A, y: 'A): 'A", "fun pick(x: 'T, y: 'T): 'T")
    }

    @Test
    fun whichVariableIsWhichMatters() {
        assertDifferentHash("pick", "fun pick(x: 'A, y: 'B): 'A", "fun pick(x: 'A, y: 'B): 'B")
    }

    @Test
    fun aReachedRevisionIsPartOfTheHash() {
        val two = "type Customer = Customer { id: Num }\ntype Customer/2 = Customer { id: Num, tier: String }\n"
        assertDifferentHash("f", two + "fun f(c: Customer): Num", two + "fun f(c: Customer/2): Num")
    }

    @Test
    fun theDeclarationsOwnRevisionIsNotPartOfTheHash() {
        val first = Klein.checkContract("fun f(c: Num): Num").hashOf("f", RevisionNumber(1))
        val second = Klein.checkContract("fun f/2(c: Num): Num").hashOf("f", RevisionNumber(2))
        assertEquals(first, second)
    }

    @Test
    fun aValueCapabilityHashesItsWholeType() {
        assertDifferentHash("limit", "limit: Num", "limit: Num?")
    }

    @Test
    fun aTypeParameterRenameDoesNotMatter() {
        assertSameHash("Box", "type Box<'A> = Box { value: 'A }", "type Box<'T> = Box { value: 'T }")
    }

    @Test
    fun constructorOrderDoesNotMatter() {
        assertSameHash("Shape", "type Shape = Circle { area: Num } | Square { area: Num }", "type Shape = Square { area: Num } | Circle { area: Num }")
    }

    @Test
    fun aFieldAddedToOneConstructorChangesTheTypesHash() {
        assertDifferentHash(
            "Shape",
            "type Shape = Circle { area: Num } | Square { area: Num }",
            "type Shape = Circle { area: Num } | Square { area: Num, side: Num }",
        )
    }

    @Test
    fun aFieldTypeChangeInAConstructorChangesTheTypesHash() {
        assertDifferentHash("Customer", "type Customer = Customer { id: Num }", "type Customer = Customer { id: String }")
    }

    @Test
    fun aConstructorRenameChangesTheTypesHash() {
        assertDifferentHash("Shape", "type Shape = Circle { area: Num } | Square { area: Num }", "type Shape = Circle { area: Num } | Box { area: Num }")
    }

    @Test
    fun aConstructorAddedChangesTheTypesHash() {
        assertDifferentHash("Shape", "type Shape = Circle { area: Num } | Square { area: Num }", "type Shape = Circle { area: Num } | Square { area: Num } | Dot")
    }

    @Test
    fun aTypeParameterAddedChangesTheTypesHash() {
        assertDifferentHash("Box", "type Box = Box { value: Num }", "type Box<'A> = Box { value: Num }")
    }

    @Test
    fun aConstructorNameHashesAsItsType() {
        val contract = Klein.checkContract("type Shape = Circle { area: Num } | Square { area: Num }")
        assertEquals(contract.hashOf("Shape", RevisionNumber(1)), contract.hashOf("Circle", RevisionNumber(1)))
    }

    @Test
    fun aTypeReachedByAFieldIsNotPartOfTheReachingTypesHash() {
        val before = "type Address = Address { city: String }\ntype Account = Account { address: Address }"
        val after = "type Address = Address { city: String, zip: String }\ntype Account = Account { address: Address }"
        assertSameHash("Account", before, after)
        assertDifferentHash("Address", before, after)
    }

    @Test
    fun theHashOfEachDeclarationFormIsPinnedToItsRecordedValue() {
        val contract =
            Klein.checkContract(
                """
                type Point = Point { x: Num, y: Num }
                type Point/2 = Point { x: Num, y: Num, z: Num }
                type Shape<'A> = Square { corner: Point/2, side: Num } | Circle { area: 'A }
                customer: { id: Num, tier: String? }
                fun creditScore(c: { id: Num }, at: Point): Num
                fun pick(x: 'A, y: 'B): 'A?
                """.trimIndent(),
            )
        fun hex(name: String, revision: Int = 1): String = contract.hashOf(name, RevisionNumber(revision))!!.toULong().toString(16).padStart(16, '0')
        assertEquals("c00c9f64e0a8aa23", hex("Point"))
        assertEquals("5bb9b07d75b02487", hex("Point", 2))
        assertEquals("6cf10602fc1c1c1e", hex("Shape"))
        assertEquals("f63b5e55b0226168", hex("customer"))
        assertEquals("9e7b4d1cbd3b1822", hex("creditScore"))
        assertEquals("25501ec55932220d", hex("pick"))
    }
}
