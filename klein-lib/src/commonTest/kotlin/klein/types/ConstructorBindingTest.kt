package klein.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConstructorBindingTest {
    // ============================================================
    // Constructors with parameters infer to functions
    // ============================================================

    @Test
    fun withParams_noTypeParam_infersToFunction() {
        assertType(
            "(Num) -> Money",
            infer(
                """
                type Money = Money { value: Num }
                Money
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun withParams_hasTypeParam_infersToPolymorphicFunction() {
        assertType(
            "('A, List<'A>) -> Cons<'A>",
            infer(
                """
                type List<'A> = Cons { head: 'A, tail: List<'A> } | Nil
                Cons
                """.trimIndent(),
            ),
        )
    }

    // ============================================================
    // Constructors without parameters infer to their own type
    // ============================================================

    @Test
    fun noParams_noTypeParam_infersToOwnType() {
        assertType(
            "True",
            infer(
                """
                type MyBool = True | False
                True
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun noParams_parentHasTypeParam_infersToOwnType() {
        assertType(
            "Nil",
            infer(
                """
                type List<'A> = Cons { head: 'A, tail: List<'A> } | Nil
                Nil
                """.trimIndent(),
            ),
        )
    }

    // ============================================================
    // Type params are applied by calling the constructor
    // ============================================================

    @Test
    fun typeParamApplied_byCalling_singleParam() {
        assertType(
            "Some<Num>",
            infer(
                """
                type Option<'A> = Some { value: 'A } | None
                Some(42)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun typeParamApplied_byCalling_multipleParams() {
        assertType(
            "Pair<Num, String>",
            infer(
                """
                type Pair<'A, 'B> = Pair { fst: 'A, snd: 'B }
                Pair(42, "hello")
                """.trimIndent(),
            ),
        )
    }

    // ============================================================
    // Type params are constrained by context
    // ============================================================

    @Test
    fun typeParamConstrained_byHolder_bareConstructor() {
        assertType(
            "List<Num>",
            infer(
                """
                type List<'A> = Cons { head: 'A, tail: List<'A> } | Nil
                type NumListHolder = NumListHolder { list: List<Num> }
                NumListHolder(Nil).list
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun typeParamConstrained_byHolder_constructorWithParams() {
        assertType(
            "List<Num>",
            infer(
                """
                type List<'A> = Cons { head: 'A, tail: List<'A> } | Nil
                type NumListHolder = NumListHolder { list: List<Num> }
                NumListHolder(Cons(1, Nil)).list
                """.trimIndent(),
            ),
        )
    }

    // ============================================================
    // Error cases
    // ============================================================

    @Test
    fun error_wrongArity_tooMany() {
        val errors =
            inferErrors(
                """
                type Option<'A> = Some { value: 'A } | None
                Some(1, 2)
                """.trimIndent(),
            )
        assertEquals(1, errors.size, "Expected exactly one error for too many arguments")
        assertTrue(errors[0] is TypeError.ArityMismatch)
    }

    @Test
    fun error_wrongArity_tooFew() {
        val errors =
            inferErrors(
                """
                type Pair<'A, 'B> = Pair { fst: 'A, snd: 'B }
                Pair(1)
                """.trimIndent(),
            )
        assertEquals(1, errors.size, "Expected exactly one error for too few arguments")
        assertTrue(errors[0] is TypeError.ArityMismatch)
    }

    @Test
    fun error_bareConstructorCalled() {
        val errors =
            inferErrors(
                """
                type List<'A> = Cons { head: 'A, tail: List<'A> } | Nil
                Nil(1)
                """.trimIndent(),
            )
        assertEquals(1, errors.size, "Expected exactly one error for calling bare constructor")
        assertTrue(errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun error_typeMismatch() {
        val errors =
            inferErrors(
                """
                type Box = Box { value: Num }
                Box("hello")
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty(), "Expected error for type mismatch")
    }
}
