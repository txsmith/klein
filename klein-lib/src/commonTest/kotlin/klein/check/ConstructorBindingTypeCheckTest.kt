package klein.check

import klein.types.TypeError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Constructors as functions: a constructor synthesizes to a (possibly polymorphic) function from
 * its field types to its nominal type, applied by calling it. Printed type variables render without
 * the leading tick (`A`, not `'A`).
 *
 * Not covered: passing or returning a *polymorphic* constructor through a generic parameter
 * (`apply(Some, 42)`, `fun getWrapper() = Some`) — higher-rank, currently unsupported.
 */
class ConstructorBindingTypeCheckTest {
    private fun assertType(
        expected: String,
        src: String,
    ) {
        val r = infer(src)
        assertTrue(r.errors.isEmpty(), "unexpected errors: ${r.errors}")
        assertEquals(expected, klein.Type.print(r.type.toSurface()))
    }

    // --- constructors infer to functions ---

    @Test
    fun withParams_noTypeParam_infersToFunction() =
        assertType(
            "(Num) -> Money",
            """
            type Money = Money { value: Num }
            Money
            """.trimIndent(),
        )

    @Test
    fun withParams_hasTypeParam_infersToPolymorphicFunction() =
        assertType(
            "(A, List<A>) -> Cons<A>",
            """
            type List<'A> = Cons { head: 'A, tail: List<'A> } | Nil
            Cons
            """.trimIndent(),
        )

    // --- type params applied by calling ---

    @Test
    fun typeParamApplied_byCalling_multipleParams() =
        assertType(
            "Pair<Num, String>",
            """
            type Pair<'A, 'B> = Pair { fst: 'A, snd: 'B }
            Pair(42, "hello")
            """.trimIndent(),
        )

    // --- type params constrained by an expected type ---

    @Test
    fun typeParamConstrained_byAnnotation_bareConstructor() =
        assertType(
            "List<Num>",
            """
            type List<'A> = Cons { head: 'A, tail: List<'A> } | Nil
            xs: List<Num> = Nil
            xs
            """.trimIndent(),
        )

    @Test
    fun typeParamConstrained_byAnnotation_constructorWithParams() =
        assertType(
            "List<Num>",
            """
            type List<'A> = Cons { head: 'A, tail: List<'A> } | Nil
            xs: List<Num> = Cons(1, Nil)
            xs
            """.trimIndent(),
        )

    // --- type params constrained through a constructor field ---

    @Test
    fun typeParamConstrained_byHolder_bareConstructor() =
        assertType(
            "List<Num>",
            """
            type List<'A> = Cons { head: 'A, tail: List<'A> } | Nil
            type NumListHolder = NumListHolder { list: List<Num> }
            NumListHolder(Nil).list
            """.trimIndent(),
        )

    @Test
    fun typeParamConstrained_byHolder_constructorWithParams() =
        assertType(
            "List<Num>",
            """
            type List<'A> = Cons { head: 'A, tail: List<'A> } | Nil
            type NumListHolder = NumListHolder { list: List<Num> }
            NumListHolder(Cons(1, Nil)).list
            """.trimIndent(),
        )

    // --- bare-constructor polymorphism (each use instantiates independently) ---

    @Test
    fun bareConstructor_subtypesNumAndStringInstantiations() =
        assertType(
            "{ nums: Cons<Num>, strs: Cons<String> }",
            """
            type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
            { nums = Cons(1, Nil), strs = Cons("hello", Nil) }
            """.trimIndent(),
        )

    @Test
    fun bareConstructor_passedToFunctionExpectingSpecificType() =
        assertType(
            "List<Num>",
            """
            type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
            fun consume(xs: List<Num>) = xs
            consume(Nil)
            """.trimIndent(),
        )

    // --- error cases ---

    @Test
    fun error_wrongArity_tooMany() {
        val errors =
            infer(
                """
                type Option<'A> = Some { value: 'A } | None
                Some(1, 2)
                """.trimIndent(),
            ).errors
        assertEquals(1, errors.size, "errors: $errors")
        val e = errors[0]
        assertIs<TypeError.CallArityMismatch>(e)
        assertEquals(1, e.expected)
        assertEquals(2, e.actual)
    }

    @Test
    fun error_wrongArity_tooFew() {
        val errors =
            infer(
                """
                type Pair<'A, 'B> = Pair { fst: 'A, snd: 'B }
                Pair(1)
                """.trimIndent(),
            ).errors
        assertEquals(1, errors.size, "errors: $errors")
        val e = errors[0]
        assertIs<TypeError.CallArityMismatch>(e)
        assertEquals(2, e.expected)
        assertEquals(1, e.actual)
    }

    @Test
    fun error_bareConstructorCalled() {
        val errors =
            infer(
                """
                type List<'A> = Cons { head: 'A, tail: List<'A> } | Nil
                Nil(1)
                """.trimIndent(),
            ).errors
        assertEquals(1, errors.size, "errors: $errors")
        assertIs<TypeError.NotAFunction>(errors[0])
    }

    @Test
    fun error_typeMismatch() {
        val errors =
            infer(
                """
                type Box = Box { value: Num }
                Box("hello")
                """.trimIndent(),
            ).errors
        assertEquals(1, errors.size, "errors: $errors")
        val e = errors[0]
        assertIs<TypeError.TypeMismatch>(e)
        assertEquals("String", klein.Type.print(e.subtype))
        assertEquals("Num", klein.Type.print(e.supertype))
    }
}
