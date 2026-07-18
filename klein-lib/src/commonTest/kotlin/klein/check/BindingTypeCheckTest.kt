package klein.check

import klein.check.Type.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Value bindings (`x: T = e`): an annotation fixes the binding's type, accepts subtypes, admits
 * `Any` and rejects `Nothing`, and introduces its own rigid type variables. Also covers rejection
 * of duplicate binding names across vals and functions.
 */
class BindingTypeCheckTest {
    private val animal = "type Animal = Dog { name: String } | Cat { name: String }"
    private val option = "type Option<'A> = None | Some { value: 'A }"

    @Test
    fun annotation_acceptsSubtype() =
        assertInfersType(
            TRef("Animal", emptyList()),
            """
            $animal
            x: Animal = Dog("Rex")
            x
            """.trimIndent(),
        )

    @Test
    fun annotation_mismatch() = assertMismatch("String", "Num", "x: Num = \"hello\"")

    @Test
    fun anyAcceptsAnyValue() =
        assertInfersType(
            TTop,
            """
            x: Any = 42
            x
            """.trimIndent(),
        )

    @Test
    fun nothingRejectsValues() = assertMismatch("Num", "Nothing", "x: Nothing = 42")

    @Test
    fun typeVarAnnotation_rigidSkolemRejectsConcrete() = assertMismatch("Num", "A", "q: 'A = 4")

    @Test
    fun annotation_anonymousUnion_rejected() {
        assertIs<TypeError.AnonymousUnionType>(infer("x: Num | String = 1").errors.single())
    }

    @Test
    fun annotation_anonymousIntersection_rejected() {
        assertIs<TypeError.AnonymousIntersectionType>(infer("x: Num & Num = 1").errors.single())
    }

    @Test
    fun typeVarAnnotation_genericSkolemFromPolymorphicConstructor() =
        assertInfersType(
            TRef("Option", listOf(tv("A"))),
            """
            $option
            o: Option<'A> = None
            o
            """.trimIndent(),
        )

    @Test
    fun genericAnnotation_fromConstructor() =
        assertInfersType(
            TRef("Option", listOf(TNum)),
            """
            $option
            x: Option<Num> = Some(42)
            x
            """.trimIndent(),
        )

    @Test
    fun genericAnnotation_acceptsSubtype() =
        assertInfersType(
            TRef("Option", listOf(TNum)),
            """
            $option
            x: Option<Num> = None
            x
            """.trimIndent(),
        )

    @Test
    fun duplicateVal_reported() {
        val e =
            infer(
                """
                x = 1
                x = 2
                x
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.DuplicateBinding>(e)
        assertEquals("x", e.name)
    }

    @Test
    fun tripleDuplicateVal_reportsEachRedefinition() {
        val errors =
            infer(
                """
                x = 1
                x = 2
                x = 3
                x
                """.trimIndent(),
            ).errors
        assertEquals(2, errors.size)
        errors.forEach {
            assertIs<TypeError.DuplicateBinding>(it)
            assertEquals("x", it.name)
        }
    }

    @Test
    fun duplicateFunDef_reported() {
        val e =
            infer(
                """
                fun f(x: Num) = x
                fun f(y: Num) = y
                f(1)
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.DuplicateBinding>(e)
        assertEquals("f", e.name)
    }

    @Test
    fun valThenFunDef_sameName_reported() {
        val e =
            infer(
                """
                x = 1
                fun x(y: Num) = y
                x
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.DuplicateBinding>(e)
        assertEquals("x", e.name)
    }

    @Test
    fun funDefThenVal_sameName_reported() {
        val e =
            infer(
                """
                fun x(y: Num) = y
                x = 1
                x
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.DuplicateBinding>(e)
        assertEquals("x", e.name)
    }
}
