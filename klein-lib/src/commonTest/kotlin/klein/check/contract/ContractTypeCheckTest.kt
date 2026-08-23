package klein.check.contract

import klein.Klein
import klein.KleinError
import klein.KleinException
import klein.ReleaseNumber
import klein.RevisionNumber
import klein.check.RuleEnv
import klein.check.Type
import klein.check.Type.*
import klein.check.TypeEnv
import klein.check.TypeError
import klein.check.infer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The diagnostics a bad contract throws. A contract that checks returns instead, so the positive
 *  cases below simply call [Klein.checkContract] and let it speak for itself. */
private fun contractErrors(src: String): List<KleinError> =
    assertFailsWith<KleinException> { Klein.checkContract(src) }.errors

/** Check [rule] against [src]'s release [release], answering its type. */
private fun ruleAgainst(
    src: String,
    rule: String,
    release: Int = 1,
): Type<Nothing?> = Klein.checkContract(src).check(rule, ReleaseNumber(release))

/**
 * Checking a capability contract, through what a caller can observe: whether [Klein.checkContract]
 * returns or throws, what the artifact reports, and what a rule checked against a release can do.
 * Wrong-mode forms are refused by the contract parser and never reach here; see
 * `klein.parser.ContractTest` for the language split.
 */
class ContractTypeCheckTest {
    @Test
    fun wellFormedContractChecks() {
        Klein.checkContract(
            """
            type Customer = Customer { id: Num, name: String }

            fun creditCheck(c: Customer): Num
            maxRetries: Num
            """.trimIndent(),
        )
    }

    @Test
    fun typeDefinitionOnlyContractIsFine() {
        Klein.checkContract("type Customer = Customer { id: Num }")
    }

    @Test
    fun emptyContractIsFine() {
        Klein.checkContract("")
    }

    @Test
    fun aDeclaredCapabilityIsCallableFromARuleOnItsRelease() {
        val contract =
            """
            type Customer = Customer { id: Num, name: String }

            fun creditCheck(c: Customer): Num
            maxRetries: Num

            release 1
              Customer
              creditCheck
              maxRetries
            """.trimIndent()
        assertEquals("(Customer) -> Num", Type.print(ruleAgainst(contract, "creditCheck")))
        assertEquals(TNum, ruleAgainst(contract, "maxRetries"))
        assertEquals(TNum, ruleAgainst(contract, """creditCheck(Customer(1, "ada")) + maxRetries"""))
    }

    @Test
    fun declarationsComeBackInFileOrder() {
        val contract =
            Klein.checkContract(
                """
                fun creditCheck(c: Num): Num
                maxRetries: Num
                fun riskScore(c: Num): Num
                """.trimIndent(),
            )
        assertEquals(listOf("creditCheck", "maxRetries", "riskScore"), contract.declarations.map { it.name })
    }

    @Test
    fun contractCheckReturnsStructuredDeclarations() {
        val contract =
            Klein.checkContract(
                """
                type Customer = Customer { id: Num }
                type Customer/2 = Customer { id: Num, tier: String }

                fun creditScore(c: Customer): Num
                fun creditScore/2(c: Customer/2): Num
                maxRetries: Num
                """.trimIndent(),
            )
        assertEquals(
            listOf(
                Triple("creditScore", RevisionNumber(1), DeclarationKind.Function),
                Triple("creditScore", RevisionNumber(2), DeclarationKind.Function),
                Triple("maxRetries", RevisionNumber(1), DeclarationKind.Value),
            ),
            contract.declarations.map { Triple(it.name, it.revision, it.kind) },
        )
        assertEquals("(Customer/2) -> Num", Type.print(contract.declarations[1].type))
    }

    // ── signature scrutiny ───────────────────────────────────────────────────

    @Test
    fun declarationWithAnUnannotatedParamIsRejected() {
        assertIs<TypeError.MissingParamAnnotation>(contractErrors("fun creditCheck(c): Num").single())
    }

    @Test
    fun declarationNamingAnUnknownTypeIsRejected() {
        val error = assertIs<TypeError.UnboundVariable>(contractErrors("fun creditCheck(c: Nope): Num").single())
        assertEquals("Nope", error.name)
    }

    @Test
    fun constructorFieldNamingAnUnknownTypeIsRejected() {
        val error = assertIs<TypeError.UnboundVariable>(contractErrors("type Bad = Bad { x: Nope }").single())
        assertEquals("Nope", error.name)
    }

    @Test
    fun everyBadSignatureIsReported() {
        val errors =
            contractErrors(
                """
                fun a(x: Nope): Num
                fun b(y: AlsoNope): Num
                """.trimIndent(),
            )
        assertEquals(2, errors.size, "$errors")
        assertTrue(errors.all { it is TypeError.UnboundVariable }, "$errors")
    }

    @Test
    fun genericDeclarationQuantifiesItsTypeVariables() {
        assertEquals(
            "(A) -> A",
            Type.print(ruleAgainst("fun identity(x: 'A): 'A\n\nrelease 1\n  identity", "identity")),
        )
    }

    // ── no functions cross the boundary ──────────────────────────────────────

    @Test
    fun declarationTakingAFunctionIsRejected() {
        assertIs<TypeError.FunctionTypeInCapability>(
            contractErrors("fun sortBy(xs: Num, key: (Num) -> Num): Num").single(),
        )
    }

    @Test
    fun declarationReturningAFunctionIsRejected() {
        assertIs<TypeError.FunctionTypeInCapability>(contractErrors("fun adder(n: Num): (Num) -> Num").single())
    }

    @Test
    fun functionTypeNestedInsideACapabilityTypeIsRejected() {
        assertIs<TypeError.FunctionTypeInCapability>(
            contractErrors(
                """
                type Handler = Handler { run: (Num) -> Num }

                fun register(h: Handler): Num
                """.trimIndent(),
            ).single(),
        )
    }

    /** The walk follows a type into its constructors' fields, so a sum hiding a function in one arm
     *  does not evade it — the parent's interface holds only what every arm shares. */
    @Test
    fun functionTypeInOneConstructorOfASumIsRejected() {
        assertIs<TypeError.FunctionTypeInCapability>(
            contractErrors(
                """
                type Handler = Direct { run: (Num) -> Num } | Named { name: String }

                fun register(h: Handler): Num
                """.trimIndent(),
            ).single(),
        )
    }

    /** The walk expands each name once, so a cycle terminates rather than hiding what it holds. */
    @Test
    fun aFunctionInsideAMutuallyRecursiveTypeIsRejected() {
        assertIs<TypeError.FunctionTypeInCapability>(
            contractErrors(
                """
                type Node = Node { edge: Edge? }
                type Edge = Edge { to: Node, weigh: (Num) -> Num }

                fun walk(n: Node): Num
                """.trimIndent(),
            ).single(),
        )
    }

    @Test
    fun aMutuallyRecursiveTypeCarryingNoFunctionIsFine() {
        Klein.checkContract(
            """
            type Node = Node { edge: Edge? }
            type Edge = Edge { to: Node }

            fun walk(n: Node): Num
            """.trimIndent(),
        )
    }

    @Test
    fun valueCapabilityOfFunctionTypeIsRejected() {
        assertIs<TypeError.FunctionTypeInCapability>(contractErrors("callback: (Num) -> Num").single())
    }

    // ── built-in type names ──────────────────────────────────────────────────

    @Test
    fun redefiningABuiltinTypeIsRejected() {
        val error =
            contractErrors("type Num = Zero | Succ { n: Num }").filterIsInstance<TypeError.ShadowsBuiltinType>().single()
        assertEquals("Num", error.name)
    }

    @Test
    fun aConstructorNamedAfterABuiltinTypeIsRejected() {
        contractErrors("type Wrapper = String { value: Num }").filterIsInstance<TypeError.ShadowsBuiltinType>().single()
    }

    @Test
    fun aRevisionDoesNotMakeABuiltinTypeDefinable() {
        val error =
            contractErrors("type Num/2 = Zero | Succ { n: Num }")
                .filterIsInstance<TypeError.ShadowsBuiltinType>()
                .single()
        assertEquals("Num/2", error.name)
    }

    // ── forward references ───────────────────────────────────────────────────

    @Test
    fun aDeclarationMayNameATypeDefinedBelowIt() {
        val contract =
            """
            fun creditCheck(c: Customer): Num

            type Customer = Customer { id: Num }

            release 1
              Customer
              creditCheck
            """.trimIndent()
        assertEquals("(Customer) -> Num", Type.print(ruleAgainst(contract, "creditCheck")))
    }

    @Test
    fun aTypeMayNameATypeDefinedBelowIt() {
        val contract =
            """
            type Order = Order { customer: Customer }
            type Customer = Customer { id: Num }

            release 1
              Order
              Customer
            """.trimIndent()
        assertEquals(
            "(Order) -> Customer",
            Type.print(ruleAgainst(contract, "fun who(o: Order): Customer = o.customer\nwho")),
        )
    }

    @Test
    fun mutuallyReferentialTypesResolve() {
        val contract =
            """
            type Node = Node { edge: Edge? }
            type Edge = Edge { to: Node }

            release 1
              Node
              Edge
            """.trimIndent()
        assertEquals("Edge?", Type.print(ruleAgainst(contract, "fun hop(n: Node): Edge? = n.edge\nhop(Node(null))")))
    }

    // ── what collides ────────────────────────────────────────────────────────

    @Test
    fun duplicateDeclarationIsRejected() {
        assertIs<TypeError.DuplicateBinding>(
            contractErrors(
                """
                fun creditCheck(c: Num): Num
                fun creditCheck(c: String): Num
                """.trimIndent(),
            ).single(),
        )
    }

    @Test
    fun declarationAndValueOfTheSameNameCollide() {
        val errors =
            contractErrors(
                """
                fun creditCheck(c: Num): Num
                creditCheck: Num
                """.trimIndent(),
            )
        assertTrue(errors.any { it is TypeError.DuplicateBinding }, "$errors")
    }

    @Test
    fun theSameRevisionDeclaredTwiceCollides() {
        val error =
            assertIs<TypeError.DuplicateBinding>(
                contractErrors(
                    """
                    fun creditScore/2(c: Num): Num
                    fun creditScore/2(c: String): Num
                    """.trimIndent(),
                ).single(),
            )
        assertEquals("creditScore/2", error.name)
    }

    @Test
    fun revisionOneCollidesWithTheBareName() {
        assertIs<TypeError.DuplicateBinding>(
            contractErrors(
                """
                fun creditScore(c: Num): Num
                fun creditScore/1(c: String): Num
                """.trimIndent(),
            ).single(),
        )
    }

    @Test
    fun aTypeRevisionDeclaredTwiceCollides() {
        val errors =
            contractErrors(
                """
                type Customer/2 = Customer { id: Num }
                type Customer/2 = Customer { id: Num, tier: String }
                """.trimIndent(),
            )
        assertTrue(errors.any { it is TypeError.DuplicateBinding }, "$errors")
    }

    @Test
    fun revisionOneOfATypeCollidesWithTheBareName() {
        val errors =
            contractErrors(
                """
                type Customer = Customer { id: Num }
                type Customer/1 = Customer { id: Num, tier: String }
                """.trimIndent(),
            )
        assertTrue(errors.any { it is TypeError.DuplicateBinding }, "$errors")
    }

    @Test
    fun aDeclarationAndAValueOfTheSameRevisionCollide() {
        val errors =
            contractErrors(
                """
                fun creditScore/2(c: Num): Num
                creditScore/2: Num
                """.trimIndent(),
            )
        assertTrue(errors.any { it is TypeError.DuplicateBinding }, "$errors")
    }

    @Test
    fun aDeclarationAndAValueOfDifferentRevisionsDoNotCollide() {
        Klein.checkContract(
            """
            fun creditScore(c: Num): Num
            creditScore/2: Num
            """.trimIndent(),
        )
    }

    // ── two revisions in one file ────────────────────────────────────────────

    private val twoRevisions =
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
        """.trimIndent()

    @Test
    fun eachReleaseSeesItsOwnRevisionOfAType() {
        assertEquals(TNum, ruleAgainst(twoRevisions, "fun id(c: Customer): Num = c.id\nid(Customer(1))", release = 1))
        assertEquals(
            TStr,
            ruleAgainst(twoRevisions, """fun tier(c: Customer): String = c.tier
tier(Customer(1, "gold"))""", release = 2),
        )
    }

    @Test
    fun aFieldFromAnotherRevisionIsNotThere() {
        val errors = assertFailsWith<KleinException> { ruleAgainst(twoRevisions, "fun t(c: Customer): String = c.tier") }
        assertIs<TypeError.MissingField>(errors.errors.single())
    }

    @Test
    fun aRevisedConstructorTakesItsOwnRevisionsFields() {
        assertEquals("(Num) -> Customer", Type.print(ruleAgainst(twoRevisions, "Customer", release = 1)))
        assertEquals("(Num, String) -> Customer", Type.print(ruleAgainst(twoRevisions, "Customer", release = 2)))
    }

    @Test
    fun twoRevisionsOfAValueCapabilityCoexist() {
        val contract =
            """
            maxRetries: Num
            maxRetries/2: String

            release 1
              maxRetries

            release 2
              maxRetries/2
            """.trimIndent()
        assertEquals(TNum, ruleAgainst(contract, "maxRetries", release = 1))
        assertEquals(TStr, ruleAgainst(contract, "maxRetries", release = 2))
    }

    @Test
    fun aRevisedSumTypeRevisesItsConstructors() {
        val contract =
            """
            type Shape/2 = Circle { radius: Num } | Square { side: Num }

            fun area(s: Shape/2): Num

            release 2
              Shape/2
              area
            """.trimIndent()
        assertEquals("(Shape) -> Num", Type.print(ruleAgainst(contract, "area", release = 2)))
        assertEquals(TNum, ruleAgainst(contract, "area(Circle(2))", release = 2))
    }

    @Test
    fun aRevisedTypeArgumentResolves() {
        val contract =
            """
            type Customer/2 = Customer { id: Num }
            type Box<'A> = Box { value: 'A }

            fun boxed(): Box<Customer/2>

            release 1
              Customer/2
              Box
              boxed
            """.trimIndent()
        assertEquals("() -> Box<Customer>", Type.print(ruleAgainst(contract, "boxed")))
    }

    @Test
    fun aRevisionThatIsNotDeclaredIsUnbound() {
        val error =
            assertIs<TypeError.UnboundVariable>(
                contractErrors(
                    """
                    type Customer = Customer { id: Num }

                    fun creditScore/2(c: Customer/3): Num
                    """.trimIndent(),
                ).single(),
            )
        assertEquals("Customer/3", error.name)
    }

    /** Declaring `/2` alone does not make the bare name mean anything, so a release cannot reach it. */
    @Test
    fun aRevisionDoesNotBindTheBareName() {
        val error =
            assertIs<TypeError.UnknownReleaseTarget>(
                contractErrors("maxRetries/2: Num\n\nrelease 1\n  maxRetries").single(),
            )
        assertEquals("maxRetries", error.name)
    }

    // ── revisions on built-in types ──────────────────────────────────────────

    @Test
    fun aRevisionOnABuiltinTypeIsRejected() {
        val error = assertIs<TypeError.RevisionOnPrimitive>(contractErrors("maxRetries: Num/2").single())
        assertEquals("Num", error.typeName)
        assertEquals(RevisionNumber(2), error.revision)
    }

    @Test
    fun revisionOneOnABuiltinTypeIsAlsoRejected() {
        val error = assertIs<TypeError.RevisionOnPrimitive>(contractErrors("maxRetries: Num/1").single())
        assertEquals(RevisionNumber(1), error.revision)
    }

    @Test
    fun aBareBuiltinTypeIsStillFine() {
        assertEquals(TNum, ruleAgainst("maxRetries: Num\n\nrelease 1\n  maxRetries", "maxRetries"))
    }

    @Test
    fun everyBuiltinTypeNameRejectsARevisionNumber() {
        for (name in listOf("Num", "String", "Bool", "Unit", "Any", "Nothing")) {
            val error =
                assertIs<TypeError.RevisionOnPrimitive>(
                    contractErrors("x: $name/2").single(),
                    "expected a rejection for '$name/2'",
                )
            assertEquals(name, error.typeName)
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
            val errors = contractErrors(source)
            assertTrue(
                errors.any { it is TypeError.RevisionOnPrimitive },
                "expected a RevisionOnPrimitive for '$source', got $errors",
            )
        }
    }

    @Test
    fun aRevisionOnABuiltinIsRejectedInsideATypeArgument() {
        val errors =
            contractErrors(
                """
                type Box<'A> = Box { value: 'A }

                fun boxed(): Box<Num/2>
                """.trimIndent(),
            )
        assertTrue(errors.any { it is TypeError.RevisionOnPrimitive }, "expected a RevisionOnPrimitive, got $errors")
    }

    @Test
    fun aRevisionOnADeclaredTypeIsStillFine() {
        Klein.checkContract(
            """
            type Customer = Customer { id: Num }
            type Customer/2 = Customer { id: Num, tier: String }

            fun creditScore(c: Customer/2): Num
            """.trimIndent(),
        )
    }

    // ── a rule checks against a release ──────────────────────────────────────

    @Test
    fun aRuleMisusingACapabilityIsRejected() {
        val errors =
            assertFailsWith<KleinException> {
                ruleAgainst("fun creditCheck(c: Num): Num\n\nrelease 1\n  creditCheck", """creditCheck("nope")""")
            }
        assertIs<TypeError.TypeMismatch>(errors.errors.single())
    }

    // ── environment isolation ────────────────────────────────────────────────

    @Test
    fun checkingAProgramDoesNotBindIntoTheGivenEnvironment() {
        val env: RuleEnv = TypeEnv.empty()
        infer("x = 1", env)
        assertNull(env.lookup("x"), "the caller's environment should be untouched")
    }

    // ── the public entry point ───────────────────────────────────────────────

    // Holding an EnvironmentContract means the contract checked: there is no errors field to read.
    @Test
    fun theEntryPointAnswersAnArtifactOrThrows() {
        val contract = Klein.checkContract("maxRetries: Num\n\nrelease 1\n  maxRetries")
        assertEquals(listOf("maxRetries"), contract.declarations.map { it.name })
        assertEquals(listOf(ReleaseNumber(1)), contract.releases)

        assertIs<TypeError.UnboundVariable>(contractErrors("fun creditCheck(c: Nope): Num").single())
    }

    @Test
    fun theEntryPointThrowsOnASyntaxErrorToo() {
        assertFailsWith<KleinException> { Klein.checkContract("maxRetries: Num = 3") }
    }
}
