package klein.check.contract

import klein.Klein
import klein.KleinException
import klein.ReleaseNumber
import klein.RevisionNumber
import klein.core.PreludeBinding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val CONTRACT =
    """
    environment acme

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
        assertEquals(RevisionNumber(1), resolve(1).pins["creditScore"])
        assertEquals(RevisionNumber(2), resolve().pins["creditScore"])
        assertNull(resolve(1).pins["riskBand"])
    }

    @Test
    fun aSumTypesConstructorsAreExposedAtTheirTypesRevisionWithoutAPinOfTheirOwn() {
        val surface = resolve()
        assertTrue(surface.isExposed("Circle"))
        assertTrue(surface.isExposed("Square"))
        assertEquals(RevisionNumber(2), surface.getRevision("Circle"))
        assertEquals(RevisionNumber(2), surface.getRevision("Square"))
        assertNull(surface.pins["Circle"])
        assertFalse(resolve(1).isExposed("Circle"))
    }

    @Test
    fun aTypeOnlyNameHasAPinThoughItBindsNothing() {
        assertEquals(RevisionNumber(2), resolve().pins["Shape"])
        assertEquals(RevisionNumber(2), resolve().getRevision("Shape"))
    }

    @Test
    fun aNameTheReleaseDoesNotExposeIsNotExposed() {
        assertFalse(resolve(1).isExposed("riskBand"))
        assertFalse(resolve().isExposed("nobody"))
    }

    // ── resolvePins ──────────────────────────────────────────────────────────

    @Test
    fun resolvePinsAcceptsADeclarationATypeAndAConstructor() {
        val surface = contract.resolvePins(mapOf("riskBand" to RevisionNumber(2), "Flag" to RevisionNumber(2), "Circle" to RevisionNumber(2))).getOrThrow()
        assertEquals(
            mapOf("riskBand" to RevisionNumber(2), "Flag" to RevisionNumber(2), "Shape" to RevisionNumber(2), "Customer" to RevisionNumber(2)),
            surface.pins,
        )
        assertTrue(surface.isExposed("On"))
        assertTrue(surface.isExposed("Circle"))
    }

    @Test
    fun resolvePinsReportsAnUnknownPinPerUnknownNameOrRevision() {
        val pins = mapOf("creditScore" to RevisionNumber(1), "riskBand" to RevisionNumber(1), "Flag" to RevisionNumber(1), "nobody" to RevisionNumber(1))
        val errors = assertFailsWith<KleinException> { contract.resolvePins(pins).getOrThrow() }.errors
        assertEquals(
            listOf("riskBand" to RevisionNumber(1), "Flag" to RevisionNumber(1), "nobody" to RevisionNumber(1)),
            errors.map { assertIs<UnknownPin>(it) }.map { it.name to it.revision },
        )
        assertEquals("pin 'nobody' revision 1 names a revision the contract does not declare", errors.last().message)
    }

    // ── the two halves agree ─────────────────────────────────────────────────

    @Test
    fun everyExposedNameResolvesToAPinAndEveryPinIsExposed() {
        for (release in contract.releases) {
            val surface = resolve(release.value)
            val ruleTypeEnv = surface.ruleTypeEnv
            contract.declarations.map { it.name }.distinct().forEach { name ->
                if (ruleTypeEnv.lookup(name) != null) assertNotNull(surface.pins[name], "$name bound without a pin")
            }
            ruleTypeEnv.allTypeDefs().forEach { assertTrue(surface.isExposed(it.name), "${it.name} registered but not exposed") }
            ruleTypeEnv.allConstructors().forEach { assertEquals(surface.pins[it.parentType], surface.getRevision(it.name), "${it.name} registered without its type's pin") }
            surface.pins.keys.forEach { name ->
                val visible = ruleTypeEnv.lookup(name) != null || ruleTypeEnv.lookupTypeDef(name) != null
                assertTrue(visible, "$name is pinned but nothing in the rule type env")
            }
        }
    }
}
