package klein.check.contract

import klein.Klein
import klein.ReleaseNumber
import klein.RevisionNumber
import klein.core.PreludeBinding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val CONTRACT =
    """
    type Customer = Customer { id: Num, name: String }
    type Customer/2 = Customer { id: Num, name: String, tier: String }
    type Shape/2 = Circle { radius: Num } | Square { side: Num }
    type Flag/2 = Off | On { since: Num }

    customer: Customer
    customer/2: Customer/2
    fun creditScore(c: Customer): Num
    fun creditScore/2(c: Customer/2): Num
    fun riskBand/2(c: Customer/2, score: Num): String

    release 1
      Customer
      customer
      creditScore

    release 2
      Customer/2
      customer/2
      Shape/2
      Flag/2
      creditScore/2
      riskBand/2
    """.trimIndent()

private val contract = Klein.checkContract(CONTRACT)

private fun resolve(release: Int = 2): ResolvedRelease = contract.resolve(ReleaseNumber(release))

class ResolvedReleaseTest {
    // ── bindingFor, one per kind ─────────────────────────────────────────────

    @Test
    fun aConstructorBindsAsCtorWithItsFieldNamesInDeclarationOrder() {
        val binding = assertIs<PreludeBinding.Ctor>(resolve().bindingFor("Customer"))
        assertEquals("Customer", binding.name)
        assertEquals(listOf("id", "name", "tier"), binding.fieldNames)
    }

    @Test
    fun aSumTypesConstructorBindsAsCtor() {
        val binding = assertIs<PreludeBinding.Ctor>(resolve().bindingFor("Circle"))
        assertEquals(listOf("radius"), binding.fieldNames)
    }

    @Test
    fun aFieldlessConstructorBindsAsCtorWithNoFields() {
        val binding = assertIs<PreludeBinding.Ctor>(resolve().bindingFor("Off"))
        assertEquals(emptyList(), binding.fieldNames)
    }

    @Test
    fun aCapabilityFunctionBindsAsFunctionWithItsArity() {
        val binding = assertIs<PreludeBinding.Function>(resolve().bindingFor("riskBand"))
        assertEquals("riskBand", binding.name)
        assertEquals(2, binding.arity)
        assertEquals(1, assertIs<PreludeBinding.Function>(resolve().bindingFor("creditScore")).arity)
    }

    @Test
    fun aCapabilityValueBindsAsValue() {
        val binding = assertIs<PreludeBinding.Value>(resolve().bindingFor("customer"))
        assertEquals("customer", binding.name)
    }

    @Test
    fun aTypeOnlyNameBindsNothing() {
        assertNull(resolve().bindingFor("Shape"))
    }

    @Test
    fun aNameTheReleaseDoesNotExposeBindsNothing() {
        assertNull(resolve(1).bindingFor("riskBand"))
        assertNull(resolve(1).bindingFor("Circle"))
    }

    // ── revisions ────────────────────────────────────────────────────────────

    @Test
    fun revisionsFollowTheReleaseSurface() {
        assertEquals(RevisionNumber(1), resolve(1).revisions["creditScore"])
        assertEquals(RevisionNumber(2), resolve().revisions["creditScore"])
        assertNull(resolve(1).revisions["riskBand"])
    }

    @Test
    fun aSumTypesConstructorsHaveRevisionsThoughNoReleaseEntryNamesThem() {
        val revisions = resolve().revisions
        assertEquals(RevisionNumber(2), revisions["Circle"])
        assertEquals(RevisionNumber(2), revisions["Square"])
        assertNull(resolve(1).revisions["Circle"])
    }

    @Test
    fun aTypeOnlyNameHasARevisionThoughItBindsNothing() {
        assertEquals(RevisionNumber(2), resolve().revisions["Shape"])
    }

    // ── the two halves agree ─────────────────────────────────────────────────

    @Test
    fun everyNameTypesBindsOrRegistersHasARevision() {
        for (release in contract.releases) {
            val (types, revisions) = resolve(release.value).let { it.types to it.revisions }
            contract.declarations.map { it.name }.distinct().forEach { name ->
                if (types.lookup(name) != null) assertNotNull(revisions[name], "$name bound without a revision")
            }
            types.allTypeDefs().forEach { assertNotNull(revisions[it.name], "${it.name} registered without a revision") }
            types.allConstructors().forEach { assertNotNull(revisions[it.name], "${it.name} registered without a revision") }
            revisions.keys.forEach { name ->
                val visible = types.lookup(name) != null || types.lookupTypeDef(name) != null || types.lookupConstructor(name, null) != null
                assertTrue(visible, "$name has a revision but nothing in types")
            }
        }
    }
}
