package klein.check

import klein.Revision
import klein.check.Type.*
import klein.check.contract.DeclarationKind
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

    // --- revisions on built-in types ---
    //
    // A revision names one version of declared vocabulary. A built-in is never declared — a contract
    // cannot write `type Num` at any revision (TypeDefPreprocessor rejects the name outright) — so
    // there is no number that could make `Num/N` mean something.

    @Test
    fun aRevisionOnABuiltinTypeIsRejected() {
        val error = checkContract("maxRetries: Num/2").errors.single()
        assertIs<TypeError.RevisionOnPrimitive>(error)
        assertEquals("Num", error.typeName)
        assertEquals(Revision(2), error.revision)
    }

    /** `/1` too: it is rejected for being *written*, not for the number it names. */
    @Test
    fun revisionOneOnABuiltinTypeIsAlsoRejected() {
        val error = checkContract("maxRetries: Num/1").errors.single()
        assertIs<TypeError.RevisionOnPrimitive>(error)
        assertEquals(Revision(1), error.revision)
    }

    @Test
    fun aBareBuiltinTypeIsStillFine() {
        val result = checkContract("maxRetries: Num")
        assertTrue(result.errors.isEmpty(), "unexpected errors: ${result.errors}")
        assertEquals("Num", Type.print(result.env.lookup("maxRetries")!!))
    }

    @Test
    fun everyBuiltinTypeNameRejectsARevision() {
        for (name in listOf("Num", "String", "Bool", "Unit", "Any", "Nothing")) {
            val error = checkContract("x: $name/2").errors.single()
            val rejection = assertIs<TypeError.RevisionOnPrimitive>(error, "expected a rejection for '$name/2'")
            assertEquals(name, rejection.typeName)
        }
    }

    @Test
    fun aRevisionOnABuiltinIsRejectedInEveryPosition() {
        val sources =
            listOf(
                "x: Num/2",
                "fun f(n: Num/2): Num",
                "fun f(n: Num): Num/2",
                "type Wrapper = Wrapper { value: Num/2 }",
                "x: { value: Num/2 }",
                "x: Num/2?",
                "x: (Num/2) -> Num",
                "x: (Num) -> Num/2",
            )
        for (source in sources) {
            val errors = checkContract(source).errors
            assertTrue(
                errors.any { it is TypeError.RevisionOnPrimitive },
                "expected a RevisionOnPrimitive for '$source', got $errors",
            )
        }
    }

    @Test
    fun aRevisionOnABuiltinIsRejectedInsideATypeArgument() {
        val errors =
            checkContract(
                """
                type Box<'A> = Box { value: 'A }

                fun boxed(): Box<Num/2>
                """.trimIndent(),
            ).errors
        assertTrue(
            errors.any { it is TypeError.RevisionOnPrimitive },
            "expected a RevisionOnPrimitive, got $errors",
        )
    }

    /** A declared type keeps working beside the rejection — this rejects built-ins, not revisions. */
    @Test
    fun aRevisionOnADeclaredTypeIsStillFine() {
        val result =
            checkContract(
                """
                type Customer = Customer { id: Num }
                type Customer/2 = Customer { id: Num, tier: String }

                fun creditScore(c: Customer/2): Num
                """.trimIndent(),
            )
        assertTrue(result.errors.isEmpty(), "unexpected errors: ${result.errors}")
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

    // --- structured identity ---

    @Test
    fun contractCheckReturnsStructuredDeclarations() {
        val result =
            checkContract(
                """
                type Customer = Customer { id: Num }
                type Customer/2 = Customer { id: Num, tier: String }

                fun creditScore(c: Customer): Num
                fun creditScore/2(c: Customer/2): Num
                maxRetries: Num
                """.trimIndent(),
            )
        assertTrue(result.errors.isEmpty(), "unexpected errors: ${result.errors}")
        assertEquals(
            listOf(
                Triple("creditScore", Revision(1), DeclarationKind.Function),
                Triple("creditScore", Revision(2), DeclarationKind.Function),
                Triple("maxRetries", Revision(1), DeclarationKind.Value),
            ),
            result.declarations.map { Triple(it.name, it.revision, it.kind) },
        )
        assertEquals("(Customer/2) -> Num", Type.print(result.declarations[1].type))
    }

    @Test
    fun twoRevisionsOfATypeAreUnrelatedNominalTypes() {
        val result =
            checkContract(
                """
                type Customer = Customer { id: Num }
                type Customer/2 = Customer { id: Num }
                """.trimIndent(),
            )
        val subtyping = Subtyping()
        val rev1 = TRef("Customer")
        val rev2 = TRef("Customer", emptyList(), Revision(2))
        assertTrue(!subtyping.isSubtype(rev1, rev2, result.env))
        assertTrue(!subtyping.isSubtype(rev2, rev1, result.env))
        assertTrue(subtyping.isSubtype(rev2, rev2, result.env))
    }
}
