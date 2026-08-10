package klein.check.contract

import klein.Revision
import klein.check.ContractType
import klein.check.TRef
import klein.check.Type
import klein.check.Type.*
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [strip], the one function whose signature changes the revision witness.
 *
 * The witness already proves no revision survives and that the recursion is total — a
 * `Type<Revision>` child cannot sit inside a `Type<Nothing?>` parent, so a missed branch would not
 * compile. It says nothing about whether the rewrite *preserves* the type, which is what these are
 * for. What a release exposes is asserted through rules, in `RuleAgainstReleaseTest`.
 */
class ProjectionTest {
    private fun ref(
        name: String,
        revision: Int,
        vararg args: ContractType,
    ): ContractType = TRef(name, args.toList(), Revision(revision))

    // ── every constructor round-trips, minus the revision ────────────────────

    @Test
    fun aFunctionKeepsItsParametersResultAndParameterNames() {
        val stripped = TFun(listOf(ref("Customer", 2), TStr), ref("Order", 3), listOf("c", "note")).strip()
        assertEquals(
            TFun(listOf(TRef("Customer"), TStr), TRef("Order"), listOf("c", "note")),
            stripped,
        )
    }

    @Test
    fun aRecordKeepsItsFieldNamesAndTheirOrder() {
        val stripped = TRecord(mapOf("zip" to TStr, "id" to ref("Customer", 2), "age" to TNum)).strip()
        assertEquals(listOf("zip", "id", "age"), (stripped as TRecord).fields.keys.toList())
        assertEquals(TRef("Customer"), stripped.fields.getValue("id"))
    }

    @Test
    fun anOptionalKeepsItsCore() {
        assertEquals(TOptional(TRef("Customer")), TOptional(ref("Customer", 2)).strip())
    }

    @Test
    fun aReferenceKeepsItsNameAndItsTypeArgumentsInOrder() {
        val stripped = ref("Pair", 2, ref("Customer", 3), TNum).strip()
        assertEquals(TRef("Pair", listOf(TRef("Customer"), TNum)), stripped)
    }

    @Test
    fun aForallKeepsItsBindersAndTheirIdentities() {
        val a = TSkolem("A", 7)
        val stripped = TForall(setOf(a), TFun(listOf(a), ref("Box", 2, a))).strip()
        assertEquals(setOf(a), (stripped as TForall).params)
        assertEquals(TFun(listOf(a), TRef("Box", listOf(a))), stripped.body)
    }

    @Test
    fun everyGroundTypeReturnsItself() {
        listOf(TNum, TStr, TBool, TUnit, TNull, TTop, TBottom, TSkolem("A", 1)).forEach {
            assertEquals(it, it.strip(), "${Type.print(it)} should strip to itself")
        }
    }

    // ── depth, recursion, and the no-op case ─────────────────────────────────

    @Test
    fun everyDepthIsStrippedNotJustTheRoot() {
        val stripped = TFun(listOf(ref("Customer", 2)), ref("Box", 1, ref("Order", 3))).strip()
        assertEquals("(Customer) -> Box<Order>", Type.print(stripped))
    }

    /** [strip] never follows a name into the environment, so a self-referential type is a reference
     *  that terminates rather than an expansion that cannot. */
    @Test
    fun aRecursiveTypeStaysAReference() {
        val tree = TRecord(mapOf("value" to TNum, "left" to TOptional(ref("Tree", 2))))
        val stripped = tree.strip() as TRecord
        assertEquals(TOptional(TRef("Tree")), stripped.fields.getValue("left"))
        assertEquals(TNum, stripped.fields.getValue("value"))
    }

    @Test
    fun aTypeWithNoRevisionsAnywhereIsUnchanged() {
        val type: ContractType = TFun(listOf(TNum, TStr), TOptional(TBool))
        assertEquals<Type<*>>(type, type.strip())
    }
}
