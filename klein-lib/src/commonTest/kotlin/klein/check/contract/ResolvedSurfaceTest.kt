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

private fun resolve(release: Int = 2): ResolvedSurface = contract.resolveRelease(ReleaseNumber(release))

class ResolvedSurfaceTest {
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
        assertEquals(RevisionNumber(1), resolve(1).exposedRevisions["creditScore"])
        assertEquals(RevisionNumber(2), resolve().exposedRevisions["creditScore"])
        assertNull(resolve(1).exposedRevisions["riskBand"])
    }

    @Test
    fun aSumTypesConstructorsHaveRevisionsThoughNoReleaseEntryNamesThem() {
        val exposedRevisions = resolve().exposedRevisions
        assertEquals(RevisionNumber(2), exposedRevisions["Circle"])
        assertEquals(RevisionNumber(2), exposedRevisions["Square"])
        assertNull(resolve(1).exposedRevisions["Circle"])
    }

    @Test
    fun aTypeOnlyNameHasARevisionThoughItBindsNothing() {
        assertEquals(RevisionNumber(2), resolve().exposedRevisions["Shape"])
    }

    // ── the two halves agree ─────────────────────────────────────────────────

    @Test
    fun everyNameTypesBindsOrRegistersHasARevision() {
        for (release in contract.releases) {
            val (ruleTypeEnv, exposedRevisions) = resolve(release.value).let { it.ruleTypeEnv to it.exposedRevisions }
            contract.declarations.map { it.name }.distinct().forEach { name ->
                if (ruleTypeEnv.lookup(name) != null) assertNotNull(exposedRevisions[name], "$name bound without a revision")
            }
            ruleTypeEnv.allTypeDefs().forEach { assertNotNull(exposedRevisions[it.name], "${it.name} registered without a revision") }
            ruleTypeEnv.allConstructors().forEach { assertNotNull(exposedRevisions[it.name], "${it.name} registered without a revision") }
            exposedRevisions.keys.forEach { name ->
                val visible = ruleTypeEnv.lookup(name) != null || ruleTypeEnv.lookupTypeDef(name) != null || ruleTypeEnv.lookupConstructor(name, null) != null
                assertTrue(visible, "$name has a revision but nothing in the rule type env")
            }
        }
    }
}
