package klein.check

import klein.ReleaseNumber
import klein.Revision
import klein.check.Type.*
import klein.check.contract.DeclarationKind
import klein.check.contract.Release
import klein.check.contract.environmentFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Checking a capability contract. Wrong-mode forms are refused by the contract parser and never
 * reach here; see `klein.parser.ContractTest` for the language split.
 */
class ContractTypeCheckTest {
    @Test
    fun wellFormedContractHasNoErrors() {
        val result =
            checkContract(
                """
                type Customer = Customer { id: Num, name: String }

                fun creditCheck(c: Customer): Num
                maxRetries: Num
                """.trimIndent(),
            )
        assertTrue(result.errors.isEmpty(), "unexpected errors: ${result.errors}")
    }

    @Test
    fun typeDefinitionOnlyContractIsFine() {
        val result = checkContract("type Customer = Customer { id: Num }")
        assertTrue(result.errors.isEmpty(), "unexpected errors: ${result.errors}")
    }

    @Test
    fun emptyContractIsFine() {
        assertTrue(checkContract("").errors.isEmpty())
    }

    @Test
    fun declaredCapabilitiesLandInTheEnv() {
        val result =
            checkContract(
                """
                type Customer = Customer { id: Num, name: String }

                fun creditCheck(c: Customer): Num
                maxRetries: Num
                """.trimIndent(),
            )
        assertEquals("(Customer) -> Num", Type.print(result.env.lookup("creditCheck")!!))
        assertEquals(TNum, result.env.lookup("maxRetries"))
    }

    @Test
    fun declarationsComeBackInFileOrder() {
        val result =
            checkContract(
                """
                fun creditCheck(c: Num): Num
                maxRetries: Num
                fun riskScore(c: Num): Num
                """.trimIndent(),
            )
        assertEquals(listOf("creditCheck", "maxRetries", "riskScore"), result.declarations.map { it.name })
    }

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

    // --- signature scrutiny ---

    @Test
    fun declarationWithAnUnannotatedParamIsRejected() {
        val error = checkContract("fun creditCheck(c): Num").errors.single()
        assertIs<TypeError.MissingParamAnnotation>(error)
    }

    @Test
    fun declarationNamingAnUnknownTypeIsRejected() {
        val error = checkContract("fun creditCheck(c: Nope): Num").errors.single()
        assertIs<TypeError.UnboundVariable>(error)
        assertEquals("Nope", error.name)
    }

    @Test
    fun constructorFieldNamingAnUnknownTypeIsRejected() {
        val error = checkContract("type Bad = Bad { x: Nope }").errors.single()
        assertIs<TypeError.UnboundVariable>(error)
        assertEquals("Nope", error.name)
    }

    @Test
    fun everyBadSignatureIsReported() {
        val result =
            checkContract(
                """
                fun a(x: Nope): Num
                fun b(y: AlsoNope): Num
                """.trimIndent(),
            )
        assertEquals(2, result.errors.size, "${result.errors}")
        assertTrue(result.errors.all { it is TypeError.UnboundVariable }, "${result.errors}")
    }

    @Test
    fun genericDeclarationQuantifiesItsTypeVariables() {
        val result = checkContract("fun identity(x: 'A): 'A")
        assertTrue(result.errors.isEmpty(), "unexpected errors: ${result.errors}")
    }

    // --- no functions cross the boundary ---

    @Test
    fun declarationTakingAFunctionIsRejected() {
        val error = checkContract("fun sortBy(xs: Num, key: (Num) -> Num): Num").errors.single()
        assertIs<TypeError.FunctionTypeInCapability>(error)
    }

    @Test
    fun declarationReturningAFunctionIsRejected() {
        val error = checkContract("fun adder(n: Num): (Num) -> Num").errors.single()
        assertIs<TypeError.FunctionTypeInCapability>(error)
    }

    @Test
    fun functionTypeNestedInsideACapabilityTypeIsRejected() {
        val error =
            checkContract(
                """
                type Handler = Handler { run: (Num) -> Num }

                fun register(h: Handler): Num
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.FunctionTypeInCapability>(error)
    }

    @Test
    fun valueCapabilityOfFunctionTypeIsRejected() {
        val error = checkContract("callback: (Num) -> Num").errors.single()
        assertIs<TypeError.FunctionTypeInCapability>(error)
    }

    // --- built-in type names ---

    @Test
    fun redefiningABuiltinTypeIsRejected() {
        val error = checkContract("type Num = Zero | Succ { n: Num }").errors.filterIsInstance<TypeError.ShadowsBuiltinType>().single()
        assertEquals("Num", error.name)
    }

    @Test
    fun aConstructorNamedAfterABuiltinTypeIsRejected() {
        checkContract("type Wrapper = String { value: Num }").errors.filterIsInstance<TypeError.ShadowsBuiltinType>().single()
    }

    @Test
    fun aRevisionDoesNotMakeABuiltinTypeDefinable() {
        val error = checkContract("type Num/2 = Zero | Succ { n: Num }").errors.filterIsInstance<TypeError.ShadowsBuiltinType>().single()
        assertEquals("Num/2", error.name)
    }

    // --- forward references ---

    @Test
    fun aDeclarationMayNameATypeDefinedBelowIt() {
        val result =
            checkContract(
                """
                fun creditCheck(c: Customer): Num

                type Customer = Customer { id: Num }
                """.trimIndent(),
            )
        assertTrue(result.errors.isEmpty(), "unexpected errors: ${result.errors}")
        assertEquals("(Customer) -> Num", Type.print(result.env.lookup("creditCheck")!!))
    }

    @Test
    fun aTypeMayNameATypeDefinedBelowIt() {
        val result =
            checkContract(
                """
                type Order = Order { customer: Customer }
                type Customer = Customer { id: Num }
                """.trimIndent(),
            )
        assertTrue(result.errors.isEmpty(), "unexpected errors: ${result.errors}")
        assertEquals("Customer", Type.print(result.env.getTypeDef("Order", Revision(1)).iface.fields.getValue("customer")))
    }

    @Test
    fun mutuallyReferentialTypesResolve() {
        val result =
            checkContract(
                """
                type Node = Node { edge: Edge? }
                type Edge = Edge { to: Node }
                """.trimIndent(),
            )
        assertTrue(result.errors.isEmpty(), "unexpected errors: ${result.errors}")
    }

    // --- what collides ---

    @Test
    fun duplicateDeclarationIsRejected() {
        val result =
            checkContract(
                """
                fun creditCheck(c: Num): Num
                fun creditCheck(c: String): Num
                """.trimIndent(),
            )
        assertIs<TypeError.DuplicateBinding>(result.errors.single())
    }

    @Test
    fun declarationAndValueOfTheSameNameCollide() {
        val result =
            checkContract(
                """
                fun creditCheck(c: Num): Num
                creditCheck: Num
                """.trimIndent(),
            )
        assertTrue(result.errors.any { it is TypeError.DuplicateBinding }, "${result.errors}")
    }

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

    // --- two revisions in one file ---

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
    fun aRevisionDoesNotBindTheBareName() {
        val contract = checkContract("maxRetries/2: Num")
        assertNull(contract.env.lookup("maxRetries"))
        assertEquals(TNum, contract.env.lookup("maxRetries/2"))
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
        val rev1 = TRef("Customer", emptyList(), Revision(1))
        val rev2 = TRef("Customer", emptyList(), Revision(2))
        assertTrue(!subtyping.isSubtype(rev1, rev2, result.env))
        assertTrue(!subtyping.isSubtype(rev2, rev1, result.env))
        assertTrue(subtyping.isSubtype(rev2, rev2, result.env))
    }

    // --- revisions on built-in types ---

    @Test
    fun aRevisionOnABuiltinTypeIsRejected() {
        val error = checkContract("maxRetries: Num/2").errors.single()
        assertIs<TypeError.RevisionOnPrimitive>(error)
        assertEquals("Num", error.typeName)
        assertEquals(Revision(2), error.revision)
    }

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

    // --- a rule checks against a release ---

    @Test
    fun programChecksAgainstTheContractEnvironment() {
        val contract =
            checkContract(
                """
                type Customer = Customer { id: Num, name: String }

                fun creditCheck(c: Customer): Num
                maxRetries: Num
                """.trimIndent(),
            )
        val release =
            Release(
                ReleaseNumber(1),
                mapOf("Customer" to Revision(1), "creditCheck" to Revision(1), "maxRetries" to Revision(1)),
            )
        val program = infer("creditCheck(Customer(1, \"ada\")) + maxRetries", environmentFor(release, contract.env))
        assertTrue(program.errors.isEmpty(), "unexpected errors: ${program.errors}")
        assertEquals(TNum, program.type)
    }

    @Test
    fun programMisusingACapabilityIsRejected() {
        val contract = checkContract("fun creditCheck(c: Num): Num")
        val release = Release(ReleaseNumber(1), mapOf("creditCheck" to Revision(1)))
        val program = infer("creditCheck(\"nope\")", environmentFor(release, contract.env))
        assertIs<TypeError.TypeMismatch>(program.errors.single())
    }

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
        val release =
            Release(ReleaseNumber(1), mapOf("Customer" to Revision(1), "creditScore" to Revision(1)))
        val program = infer("creditScore(Customer(1))", environmentFor(release, contract.env))
        assertTrue(program.errors.isEmpty(), "unexpected errors: ${program.errors}")
        assertEquals(TNum, program.type)
    }

    // --- environment isolation ---

    @Test
    fun checkingAProgramDoesNotBindIntoTheGivenEnvironment() {
        val env: RuleEnv = TypeEnv.empty()
        infer("x = 1", env)
        assertNull(env.lookup("x"), "the caller's environment should be untouched")
    }

    @Test
    fun contractStageCarriesItsEnvironmentAndErrors() {
        val declared =
            klein.Klein
                .tokenize("maxRetries: Num")
                .andThen(klein.Klein::parseContract)
                .andThen { klein.Klein.checkContract(it) }
        assertTrue(declared.errors.isEmpty(), "unexpected errors: ${declared.errors}")
        assertEquals(TNum, declared.output!!.lookup("maxRetries"))

        val rejected =
            klein.Klein
                .tokenize("fun creditCheck(c: Nope): Num")
                .andThen(klein.Klein::parseContract)
                .andThen { klein.Klein.checkContract(it) }
        assertIs<TypeError.UnboundVariable>(rejected.errors.single())
    }
}
