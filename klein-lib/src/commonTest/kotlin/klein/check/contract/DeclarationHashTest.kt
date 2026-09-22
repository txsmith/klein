package klein.check.contract

import klein.Klein
import klein.RevisionNumber
import klein.contractOf
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
            environment acme

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
        assertNull(hashOf(contractOf("fun creditScore(c: Num): Num"), "creditScore", revision = 2))
        assertNull(hashOf(contractOf("fun creditScore(c: Num): Num"), "nobody"))
    }

    @Test
    fun aCapabilityAndATypeHashDifferently() {
        val contract =
            """
            environment acme

            type Customer = Customer { id: Num }
            customer: Customer
            fun creditScore(c: Customer): Num
            """
        val hashes = listOf("Customer", "customer", "creditScore").map { hashOf(contract, it) }
        assertEquals(3, hashes.toSet().size)
    }

    @Test
    fun recordFieldOrderDoesNotMatter() {
        assertSameHash("f", contractOf("fun f(c: { a: Num, b: String }): Num"), contractOf("fun f(c: { b: String, a: Num }): Num"))
    }

    @Test
    fun aParameterRenameChangesTheHash() {
        val before =
            """
            environment acme

            type Customer = Customer { id: Num }
            fun f(c: Customer): Num
            """
        val after =
            """
            environment acme

            type Customer = Customer { id: Num }
            fun f(customer: Customer): Num
            """
        assertDifferentHash("f", before, after)
    }

    @Test
    fun aParameterTypeChangeChangesTheHash() {
        assertDifferentHash("f", contractOf("fun f(c: Num): Num"), contractOf("fun f(c: String): Num"))
    }

    @Test
    fun aResultTypeChangeChangesTheHash() {
        assertDifferentHash("f", contractOf("fun f(c: Num): Num"), contractOf("fun f(c: Num): String"))
    }

    @Test
    fun aTypeVariableRenameDoesNotMatter() {
        assertSameHash("pick", contractOf("fun pick(x: 'A, y: 'A): 'A"), contractOf("fun pick(x: 'T, y: 'T): 'T"))
    }

    @Test
    fun whichVariableIsWhichMatters() {
        assertDifferentHash("pick", contractOf("fun pick(x: 'A, y: 'B): 'A"), contractOf("fun pick(x: 'A, y: 'B): 'B"))
    }

    @Test
    fun aReachedRevisionIsPartOfTheHash() {
        val two =
            """
            environment acme

            type Customer = Customer { id: Num }
            type Customer/2 = Customer { id: Num, tier: String }
            """
        assertDifferentHash("f", two + "fun f(c: Customer): Num", two + "fun f(c: Customer/2): Num")
    }

    @Test
    fun theDeclarationsOwnRevisionIsNotPartOfTheHash() {
        val first = Klein.checkContract(contractOf("fun f(c: Num): Num")).hashOf("f", RevisionNumber(1))
        val second = Klein.checkContract(contractOf("fun f/2(c: Num): Num")).hashOf("f", RevisionNumber(2))
        assertEquals(first, second)
    }

    @Test
    fun aValueCapabilityHashesItsWholeType() {
        assertDifferentHash("limit", contractOf("limit: Num"), contractOf("limit: Num?"))
    }

    @Test
    fun aTypeParameterRenameDoesNotMatter() {
        assertSameHash("Box", contractOf("type Box<'A> = Box { value: 'A }"), contractOf("type Box<'T> = Box { value: 'T }"))
    }

    @Test
    fun constructorOrderDoesNotMatter() {
        assertSameHash(
            "Shape",
            contractOf("type Shape = Circle { area: Num } | Square { area: Num }"),
            contractOf("type Shape = Square { area: Num } | Circle { area: Num }"),
        )
    }

    @Test
    fun aFieldAddedToOneConstructorChangesTheTypesHash() {
        assertDifferentHash(
            "Shape",
            contractOf("type Shape = Circle { area: Num } | Square { area: Num }"),
            contractOf("type Shape = Circle { area: Num } | Square { area: Num, side: Num }"),
        )
    }

    @Test
    fun aFieldTypeChangeInAConstructorChangesTheTypesHash() {
        assertDifferentHash("Customer", contractOf("type Customer = Customer { id: Num }"), contractOf("type Customer = Customer { id: String }"))
    }

    @Test
    fun aConstructorRenameChangesTheTypesHash() {
        assertDifferentHash(
            "Shape",
            contractOf("type Shape = Circle { area: Num } | Square { area: Num }"),
            contractOf("type Shape = Circle { area: Num } | Box { area: Num }"),
        )
    }

    @Test
    fun aConstructorAddedChangesTheTypesHash() {
        assertDifferentHash(
            "Shape",
            contractOf("type Shape = Circle { area: Num } | Square { area: Num }"),
            contractOf("type Shape = Circle { area: Num } | Square { area: Num } | Dot"),
        )
    }

    @Test
    fun aTypeParameterAddedChangesTheTypesHash() {
        assertDifferentHash("Box", contractOf("type Box = Box { value: Num }"), contractOf("type Box<'A> = Box { value: Num }"))
    }

    @Test
    fun aConstructorNameHashesAsItsType() {
        val contract = Klein.checkContract(contractOf("type Shape = Circle { area: Num } | Square { area: Num }"))
        assertEquals(contract.hashOf("Shape", RevisionNumber(1)), contract.hashOf("Circle", RevisionNumber(1)))
    }

    @Test
    fun aTypeReachedByAFieldIsNotPartOfTheReachingTypesHash() {
        val before =
            """
            environment acme

            type Address = Address { city: String }
            type Account = Account { address: Address }
            """
        val after =
            """
            environment acme

            type Address = Address { city: String, zip: String }
            type Account = Account { address: Address }
            """
        assertSameHash("Account", before, after)
        assertDifferentHash("Address", before, after)
    }

    @Test
    fun theHashOfEachDeclarationFormIsPinnedToItsRecordedValue() {
        val contract =
            Klein.checkContract(
                """
                environment acme

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
