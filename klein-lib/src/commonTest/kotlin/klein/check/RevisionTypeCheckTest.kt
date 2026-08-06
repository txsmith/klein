package klein.check

import klein.check.Type.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Revisions in a contract. The declaration key is (name, revision), so two incompatible versions of
 * a capability or a type sit in one file while the old one drains. Revision 1 *is* the bare name.
 */
class RevisionTypeCheckTest {
    @Test
    fun twoRevisionsOfATypeCoexist() {
        val result =
            checkContract(
                """
                type Customer = Customer { id: Num }
                type Customer/2 = Customer { id: Num, tier: String }
                """.trimIndent(),
            )
        assertTrue(result.errors.isEmpty(), "unexpected errors: ${result.errors}")
        assertNotNull(result.env.lookupTypeDef("Customer"))
        assertNotNull(result.env.lookupTypeDef("Customer/2"))
    }

    @Test
    fun twoRevisionsOfACapabilityCoexist() {
        val result =
            checkContract(
                """
                type Customer = Customer { id: Num }
                type Customer/2 = Customer { id: Num, tier: String }

                fun creditScore(c: Customer): Num
                fun creditScore/2(c: Customer/2): Num
                """.trimIndent(),
            )
        assertTrue(result.errors.isEmpty(), "unexpected errors: ${result.errors}")
        assertEquals("(Customer) -> Num", Type.print(result.env.lookup("creditScore")!!))
        assertEquals("(Customer/2) -> Num", Type.print(result.env.lookup("creditScore/2")!!))
    }

    @Test
    fun twoRevisionsOfAValueCapabilityCoexist() {
        val result =
            checkContract(
                """
                maxRetries: Num
                maxRetries/2: String
                """.trimIndent(),
            )
        assertTrue(result.errors.isEmpty(), "unexpected errors: ${result.errors}")
        assertEquals(TNum, result.env.lookup("maxRetries"))
        assertEquals(TStr, result.env.lookup("maxRetries/2"))
    }

    @Test
    fun aRevisedConstructorIsBoundUnderItsRevision() {
        val result =
            checkContract(
                """
                type Customer = Customer { id: Num }
                type Customer/2 = Customer { id: Num, tier: String }
                """.trimIndent(),
            )
        assertEquals("(Num) -> Customer", Type.print(result.env.lookup("Customer")!!))
        assertEquals("(Num, String) -> Customer/2", Type.print(result.env.lookup("Customer/2")!!))
    }

    @Test
    fun aRevisedSumTypeRevisesItsConstructors() {
        val result =
            checkContract(
                """
                type Shape/2 = Circle { radius: Num } | Square { side: Num }

                fun area(s: Shape/2): Num
                fun unit(): Circle/2
                """.trimIndent(),
            )
        assertTrue(result.errors.isEmpty(), "unexpected errors: ${result.errors}")
        assertEquals("(Shape/2) -> Num", Type.print(result.env.lookup("area")!!))
        assertEquals("() -> Circle/2", Type.print(result.env.lookup("unit")!!))
    }

    // --- what still collides ---

    @Test
    fun theSameRevisionDeclaredTwiceCollides() {
        val result =
            checkContract(
                """
                fun creditScore/2(c: Num): Num
                fun creditScore/2(c: String): Num
                """.trimIndent(),
            )
        val error = assertIs<TypeError.DuplicateBinding>(result.errors.single())
        assertEquals("creditScore/2", error.name)
    }

    @Test
    fun aBareNameStillCollidesWithItself() {
        val result =
            checkContract(
                """
                fun creditScore(c: Num): Num
                fun creditScore(c: String): Num
                """.trimIndent(),
            )
        assertIs<TypeError.DuplicateBinding>(result.errors.single())
    }

    @Test
    fun revisionOneCollidesWithTheBareName() {
        val result =
            checkContract(
                """
                fun creditScore(c: Num): Num
                fun creditScore/1(c: String): Num
                """.trimIndent(),
            )
        assertIs<TypeError.DuplicateBinding>(result.errors.single())
    }

    @Test
    fun aTypeRevisionDeclaredTwiceCollides() {
        val result =
            checkContract(
                """
                type Customer/2 = Customer { id: Num }
                type Customer/2 = Customer { id: Num, tier: String }
                """.trimIndent(),
            )
        assertTrue(result.errors.any { it is TypeError.DuplicateBinding }, "${result.errors}")
    }

    @Test
    fun revisionOneOfATypeCollidesWithTheBareName() {
        val result =
            checkContract(
                """
                type Customer = Customer { id: Num }
                type Customer/1 = Customer { id: Num, tier: String }
                """.trimIndent(),
            )
        assertTrue(result.errors.any { it is TypeError.DuplicateBinding }, "${result.errors}")
    }

    @Test
    fun aDeclarationAndAValueOfTheSameRevisionCollide() {
        val result =
            checkContract(
                """
                fun creditScore/2(c: Num): Num
                creditScore/2: Num
                """.trimIndent(),
            )
        assertTrue(result.errors.any { it is TypeError.DuplicateBinding }, "${result.errors}")
    }

    @Test
    fun aDeclarationAndAValueOfDifferentRevisionsDoNotCollide() {
        val result =
            checkContract(
                """
                fun creditScore(c: Num): Num
                creditScore/2: Num
                """.trimIndent(),
            )
        assertTrue(result.errors.isEmpty(), "unexpected errors: ${result.errors}")
    }

    // --- a revised signature gets the same scrutiny as any other ---

    @Test
    fun aRevisionThatIsNotDeclaredIsUnbound() {
        val result =
            checkContract(
                """
                type Customer = Customer { id: Num }

                fun creditScore/2(c: Customer/3): Num
                """.trimIndent(),
            )
        val error = assertIs<TypeError.UnboundVariable>(result.errors.single())
        assertEquals("Customer/3", error.name)
    }

    @Test
    fun aRevisedTypesFieldsAreStillResolved() {
        val error = checkContract("type Bad/2 = Bad { x: Nope }").errors.single()
        assertIs<TypeError.UnboundVariable>(error)
        assertEquals("Nope", error.name)
    }

    @Test
    fun aRevisedCapabilityCarryingAFunctionIsStillRejected() {
        val error = checkContract("fun adder/2(n: Num): (Num) -> Num").errors.single()
        assertIs<TypeError.FunctionTypeInCapability>(error)
        assertEquals("adder/2", error.name)
    }

    @Test
    fun aRevisedTypeArgumentResolves() {
        val result =
            checkContract(
                """
                type Customer/2 = Customer { id: Num }
                type Box<'A> = Box { value: 'A }

                fun boxed(): Box<Customer/2>
                """.trimIndent(),
            )
        assertTrue(result.errors.isEmpty(), "unexpected errors: ${result.errors}")
        assertEquals("() -> Box<Customer/2>", Type.print(result.env.lookup("boxed")!!))
    }

    @Test
    fun aRevisionOnABuiltinTypeIsUnbound() {
        val error = checkContract("maxRetries: Num/2").errors.single()
        assertIs<TypeError.UnboundVariable>(error)
        assertEquals("Num/2", error.name)
    }

    // --- the review marker is carried, and changes no typing ---

    @Test
    fun theReviewMarkerDoesNotAffectChecking() {
        val result =
            checkContract(
                """
                type Application = Application { amount: Num }
                type Decision = Decision { approved: Bool }

                fun underwrite/2(a: Application): Decision review
                """.trimIndent(),
            )
        assertTrue(result.errors.isEmpty(), "unexpected errors: ${result.errors}")
        assertEquals("(Application) -> Decision", Type.print(result.env.lookup("underwrite/2")!!))
    }

    // --- the bare declaration is unchanged by the presence of a revision ---

    @Test
    fun aProgramStillSeesTheBareCapability() {
        val contract =
            checkContract(
                """
                type Customer = Customer { id: Num }
                type Customer/2 = Customer { id: Num, tier: String }

                fun creditScore(c: Customer): Num
                fun creditScore/2(c: Customer/2): Num
                """.trimIndent(),
            )
        val program = infer("creditScore(Customer(1))", contract.env)
        assertTrue(program.errors.isEmpty(), "unexpected errors: ${program.errors}")
        assertEquals(TNum, program.type)
    }

    @Test
    fun aRevisionDoesNotBindTheBareName() {
        val contract = checkContract("maxRetries/2: Num")
        assertNull(contract.env.lookup("maxRetries"))
        assertEquals(TNum, contract.env.lookup("maxRetries/2"))
    }
}
