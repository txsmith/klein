package klein.check.contract

import klein.ReleaseNumber
import klein.Revision
import klein.check.ContractType
import klein.check.TRef
import klein.check.Type
import klein.check.Type.*
import klein.check.Variance
import klein.check.checkContract
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Projection: `strip`, and the environment a release exposes.
 *
 * The revision witness already proves two things about `strip`, so nothing here re-tests them: no
 * revision can survive it (its result is a `RuleType`), and the recursion is total (a
 * `Type<Revision>` child cannot sit inside a `Type<Nothing?>` parent, so a missed branch would not
 * compile). What the witness says nothing about is whether the rewrite *preserves* the type — a
 * function returning `Any` for every input satisfies the signature perfectly — and that is what
 * these are for.
 */
class ProjectionTest {
    private fun ref(
        name: String,
        revision: Int,
        vararg args: ContractType,
    ): ContractType = TRef(name, args.toList(), Revision(revision))

    // --- every constructor round-trips, minus the revision ---

    @Test
    fun aFunctionKeepsItsParametersResultAndParameterNames() {
        val stripped = strip(TFun(listOf(ref("Customer", 2), TStr), ref("Order", 3), listOf("c", "note")))
        assertEquals(
            TFun(listOf(TRef("Customer"), TStr), TRef("Order"), listOf("c", "note")),
            stripped,
        )
    }

    @Test
    fun aRecordKeepsItsFieldNamesAndTheirOrder() {
        val stripped = strip(TRecord(mapOf("zip" to TStr, "id" to ref("Customer", 2), "age" to TNum)))
        assertEquals(listOf("zip", "id", "age"), (stripped as TRecord).fields.keys.toList())
        assertEquals(TRef("Customer"), stripped.fields.getValue("id"))
    }

    @Test
    fun anOptionalKeepsItsCore() {
        assertEquals(TOptional(TRef("Customer")), strip(TOptional(ref("Customer", 2))))
    }

    @Test
    fun aReferenceKeepsItsNameAndItsTypeArgumentsInOrder() {
        val stripped = strip(ref("Pair", 2, ref("Customer", 3), TNum))
        assertEquals(TRef("Pair", listOf(TRef("Customer"), TNum)), stripped)
    }

    @Test
    fun aForallKeepsItsBindersAndTheirIdentities() {
        val a = TSkolem("A", 7)
        val stripped = strip(TForall(setOf(a), TFun(listOf(a), ref("Box", 2, a))))
        assertEquals(setOf(a), (stripped as TForall).params)
        assertEquals(TFun(listOf(a), TRef("Box", listOf(a))), stripped.body)
    }

    @Test
    fun everyGroundTypeReturnsItself() {
        listOf(TNum, TStr, TBool, TUnit, TNull, TTop, TBottom, TSkolem("A", 1)).forEach {
            assertEquals(it, strip(it), "${Type.print(it)} should strip to itself")
        }
    }

    // --- depth, recursion, and the no-op case ---

    @Test
    fun everyDepthIsStrippedNotJustTheRoot() {
        val stripped = strip(TFun(listOf(ref("Customer", 2)), ref("Box", 1, ref("Order", 3))))
        assertEquals("(Customer) -> Box<Order>", Type.print(stripped))
    }

    /** `strip` never follows a name into the environment, so a self-referential type is a reference
     *  that terminates rather than an expansion that cannot. */
    @Test
    fun aRecursiveTypeStaysAReference() {
        val contract =
            checkContract(
                """
                type Tree/2 = Tree { value: Num, left: Tree/2?, right: Tree/2? }
                """.trimIndent(),
            )
        val iface = contract.env.getTypeDef("Tree", Revision(2)).iface
        val stripped = strip(iface) as TRecord
        assertEquals(TOptional(TRef("Tree")), stripped.fields.getValue("left"))
        assertEquals(TNum, stripped.fields.getValue("value"))
    }

    @Test
    fun aTypeWithNoRevisionsAnywhereIsUnchanged() {
        val type: ContractType = TFun(listOf(TNum, TStr), TOptional(TBool))
        assertEquals<Type<*>>(type, strip(type))
    }

    // --- the environment a release exposes ---

    private val TWO_REVISIONS =
        """
        type Customer = Customer { id: Num }
        type Customer/2 = Customer { id: Num, tier: String }

        fun creditScore(c: Customer): Num
        fun creditScore/2(c: Customer/2): Num
        maxRetries: Num
        """.trimIndent()

    private fun release(vararg entries: Pair<String, Int>): Release =
        Release(ReleaseNumber(2), entries.associate { (name, revision) -> name to Revision(revision) })

    @Test
    fun aDeclarationArrivesUnderItsPlainNameAtTheRevisionTheReleaseNames() {
        val env = environmentFor(release("Customer" to 2, "creditScore" to 2), checkContract(TWO_REVISIONS).env)
        assertEquals("(Customer) -> Num", Type.print(env.lookup("creditScore")!!))
        assertNull(env.lookup("creditScore/2"), "a rule can spell no such name, so nothing should answer to it")
    }

    @Test
    fun theRevisionTheReleaseNamesIsTheOneThatArrives() {
        val contract = checkContract(TWO_REVISIONS).env
        assertEquals(
            setOf("id"),
            environmentFor(release("Customer" to 1), contract).getTypeDef("Customer", null).iface.fields.keys,
        )
        assertEquals(
            setOf("id", "tier"),
            environmentFor(release("Customer" to 2), contract).getTypeDef("Customer", null).iface.fields.keys,
        )
    }

    @Test
    fun aNameTheReleaseDoesNotExposeIsAbsent() {
        val env = environmentFor(release("creditScore" to 1), checkContract(TWO_REVISIONS).env)
        assertNull(env.lookup("maxRetries"))
        assertNull(env.lookupTypeDef("Customer"))
        assertNull(env.lookupConstructor("Customer", null))
    }

    @Test
    fun constructorsTravelWithTheirType() {
        val contract =
            checkContract(
                """
                type Shape/2 = Circle { radius: Num } | Square { side: Num }
                """.trimIndent(),
            )
        val env = environmentFor(release("Shape" to 2), contract.env)

        assertEquals("(Num) -> Circle", Type.print(env.lookup("Circle")!!))
        assertEquals("(Num) -> Square", Type.print(env.lookup("Square")!!))
        assertEquals(listOf("radius"), env.lookupConstructor("Circle", null)!!.fields.map { it.name })
        assertEquals("Shape", env.lookupConstructor("Square", null)!!.parentType)
        assertNotNull(env.lookupTypeDef("Circle"), "a constructor's own type definition travels too")
        // Under plain names and nothing else — `Circle/2` is a key no rule could spell anyway, and
        // after projection there is no revision left to build one out of.
        assertEquals(setOf("Circle", "Square"), env.allConstructors().map { it.name }.toSet())
    }

    @Test
    fun aGenericTypeKeepsItsArityAndItsParametersVariance() {
        val contract =
            checkContract(
                """
                type Box/2<'A> = Box { value: 'A }
                type Consumer/2<'A> = Consumer { accept: ('A) -> Unit }
                """.trimIndent(),
            )
        val env = environmentFor(release("Box" to 2, "Consumer" to 2), contract.env)
        assertEquals(
            contract.env.getTypeDef("Box", Revision(2)).typeParams,
            env.getTypeDef("Box", null).typeParams,
        )
        assertEquals(Variance.Covariant, env.getTypeDef("Box", null).typeParams.single().variance)
        assertEquals(Variance.Contravariant, env.getTypeDef("Consumer", null).typeParams.single().variance)
    }
}
