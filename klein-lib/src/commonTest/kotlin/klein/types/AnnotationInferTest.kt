package klein.types

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

@Ignore // Type annotation checking not yet implemented in type checker (Phase 2)
class AnnotationInferTest {

    @Test
    fun paramAnnotation_identityBecomesMonomorphic() {
        // Without annotation: ('A) -> 'A. With annotation: (Num) -> Num.
        assertType("(Num) -> Num", infer("fun f(x: Num) = x\nf"))
    }

    @Test
    fun lambdaParamAnnotation_identityBecomesMonomorphic() {
        assertType("(Num) -> Num", infer("|x: Num -> x|"))
    }

    @Test
    fun mixedParams_annotatedIsFixed_unannotatedIsInferred() {
        assertType("(Num, 'A) -> Num", infer("fun f(x: Num, y) = x\nf"))
    }

    @Test
    fun paramAnnotation_acceptsSubtype() {
        // Dog <: Animal, so passing Dog to (Animal) -> Animal is fine
        assertType(
            "Animal",
            infer(
                """
                type Animal = Dog { name: String } | Cat { name: String }
                fun wrap(x: Animal): Animal = x
                wrap(Dog("Rex"))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun paramAnnotation_hidesSubtypeFields() {
        // Inside the body, x is Animal — accessing .tricks (Dog-only) should fail
        val errors = inferErrors(
            """
            type Animal = Dog { name: String, tricks: Num } | Cat { name: String }
            fun f(x: Animal) = x.tricks
            f
            """.trimIndent(),
        )
        assertEquals(1, errors.size)
        assertMissingField(errors[0], "tricks")
    }

    @Test
    fun returnAnnotation_hidesSubtypeFromCaller() {
        // Return type is Animal, so caller can't access Dog-specific fields
        val errors = inferErrors(
            """
            type Animal = Dog { name: String, tricks: Num } | Cat { name: String }
            fun wrap(x): Animal = x
            wrap(Dog("Rex", 3)).tricks
            """.trimIndent(),
        )
        assertEquals(1, errors.size)
        assertMissingField(errors[0], "tricks")
    }

    @Test
    fun valAnnotation_acceptsSubtype() {
        assertType(
            "Animal",
            infer(
                """
                type Animal = Dog { name: String } | Cat { name: String }
                x: Animal = Dog("Rex")
                x
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun ascription_acceptsSubtype() {
        assertType(
            "Animal",
            infer(
                """
                type Animal = Dog { name: String } | Cat { name: String }
                (Dog("Rex") : Animal)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun typeVarAnnotation_bodyMustRespectReturnType() {
        // Body returns Num, but declared return is 'A — Num </: 'A
        val errors = inferErrors("fun f(x: 'A): 'A = 42\nf")
        assertEquals(1, errors.size)
    }

    @Test
    fun typeVarAnnotation_mixedWithConcrete() {
        // x: 'A, y: Num — 'A is unconstrained, y is fixed
        assertType("('A, Num) -> 'A", infer("fun f(x: 'A, y: Num) = x\nf"))
    }

    @Test
    fun typeVarAnnotation_sharedAcrossParams() {
        // Both params share 'A, so they must unify
        assertType("('A, 'A) -> 'A", infer("fun f(x: 'A, y: 'A) = x\nf"))
    }

    @Test
    fun paramAnnotation_mismatch() {
        val errors = inferErrors("fun f(x: Num) = x\nf(\"hello\")")
        assertEquals(1, errors.size)
    }

    @Test
    fun returnAnnotation_mismatch() {
        // Body returns Num (x + 1), but declared return is String
        val errors = inferErrors("fun f(x): String = x + 1\nf")
        assertEquals(1, errors.size)
    }

    @Test
    fun valAnnotation_mismatch() {
        val errors = inferErrors("x: Num = \"hello\"")
        assertEquals(1, errors.size)
    }

    @Test
    fun ascription_mismatch() {
        val errors = inferErrors("(\"hello\" : Num)")
        assertEquals(1, errors.size)
    }

    @Test
    fun genericTypeAnnotation_valBinding() {
        assertType(
            "Option<Num>",
            infer(
                """
                type Option<'A> = None | Some { value: 'A }
                x: Option<Num> = Some(42)
                x
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun genericTypeAnnotation_acceptsSubtype() {
        assertType(
            "Option<Num>",
            infer(
                """
                type Option<'A> = None | Some { value: 'A }
                x: Option<Num> = None
                x
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun functionTypeAnnotation_mismatch() {
        val errors = inferErrors("f: Num -> String = |x -> x + 1|")
        assertEquals(1, errors.size)
    }
}
