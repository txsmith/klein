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

    // ── Destructuring bindings ─────────────────────────────────────────────────

    @Test
    fun destructuredFieldsBindAtTheirTypes() {
        assertInfersType(
            TStr,
            """
            p = { name = "a", age = 1 }
            { name, age } = p
            name
            """.trimIndent(),
        )
    }

    @Test
    fun destructuredRenameBindsTheNewName() {
        assertInfersType(
            TNum,
            """
            p = { name = "a", age = 1 }
            { age = years } = p
            years
            """.trimIndent(),
        )
    }

    @Test
    fun destructuringProjectsANominalInterface() {
        assertInfersType(
            TStr,
            """
            type Person = Person { name: String, age: Num }
            someone = Person("a", 1)
            { name } = someone
            name
            """.trimIndent(),
        )
    }

    @Test
    fun destructuringProjectsASumCommonInterface() {
        assertInfersType(
            TStr,
            """
            type Pet = Dog { name: String, legs: Num } | Cat { name: String, lives: Num }
            pet: Pet = Dog("d", 4)
            { name } = pet
            name
            """.trimIndent(),
        )
    }

    @Test
    fun destructuringASumFieldNotOnEveryConstructorErrors() {
        val e =
            infer(
                """
                type Pet = Dog { name: String, legs: Num } | Cat { name: String, lives: Num }
                pet: Pet = Dog("d", 4)
                { legs } = pet
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.MissingField>(e)
    }

    @Test
    fun destructuringAnOptionalIsRefutable() {
        val e =
            infer(
                """
                type Person = Person { name: String, age: Num }
                mp: Person? = null
                { name } = mp
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.RefutableBinding>(e)
        assertEquals(listOf("null"), e.missing)
    }

    @Test
    fun destructuringANonRecordErrors() {
        val e =
            infer(
                """
                { name } = 5
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.NotARecord>(e)
    }

    @Test
    fun destructuringAnUnknownFieldErrors() {
        val e =
            infer(
                """
                p = { a = 1 }
                { b } = p
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.MissingField>(e)
    }

    @Test
    fun wildcardFieldInDestructuringBindsNothing() {
        assertInfersType(
            TNum,
            """
            p = { name = "a" }
            { name = _ } = p
            1
            """.trimIndent(),
        )
    }

    @Test
    fun duplicateBinderNamesInOnePatternError() {
        val e =
            infer(
                """
                p = { a = 1, b = 2 }
                { a = x, b = x } = p
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.DuplicateBinding>(e)
    }

    @Test
    fun destructuredNameDuplicatingAnEarlierBindingErrors() {
        val e =
            infer(
                """
                x = 1
                p = { a = 2 }
                { a = x } = p
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.DuplicateBinding>(e)
    }

    @Test
    fun constructorDestructuringOnASingleConstructorType() {
        assertInfersType(
            TStr,
            """
            type Person = Person { name: String, age: Num }
            someone = Person("a", 1)
            Person { name } = someone
            name
            """.trimIndent(),
        )
    }

    @Test
    fun constructorBinderBindsAtTheConstructorType() {
        assertInfersType(
            TNum,
            """
            type Shape = Circle { radius: Num } | Square { side: Num }
            c0: Circle = Circle(2)
            Circle c = c0
            c.radius
            """.trimIndent(),
        )
    }

    @Test
    fun genericSingleConstructorDestructures() {
        assertInfersType(
            TNum,
            """
            type Box<'A> = Box { value: 'A }
            b = Box(1)
            Box { value } = b
            value
            """.trimIndent(),
        )
    }

    @Test
    fun refutableConstructorBindingErrors() {
        val e =
            infer(
                """
                type Shape = Circle { radius: Num } | Square { side: Num } | Tri { base: Num, height: Num }
                s: Shape = Circle(1)
                Circle { radius } = s
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.RefutableBinding>(e)
        assertEquals(listOf("Square", "Tri"), e.missing)
    }

    @Test
    fun wrongConstructorBindingErrors() {
        val e =
            infer(
                """
                type Person = Person { name: String, age: Num }
                type Shape = Circle { radius: Num } | Square { side: Num }
                someone = Person("a", 1)
                Circle { radius } = someone
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.NotAConstructorOf>(e)
    }

    @Test
    fun destructuringInABlock() {
        assertInfersType(
            TFun(listOf(TRecord(mapOf("a" to TNum))), TNum),
            """
            fun f(p: { a: Num }): Num =
              { a } = p
              a + 1
            f
            """.trimIndent(),
        )
    }
}
