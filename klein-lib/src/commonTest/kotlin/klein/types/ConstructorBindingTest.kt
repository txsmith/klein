package klein.types

import kotlin.test.Test
import kotlin.test.assertEquals

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
    // Type params are applied by calling the constructor
    // ============================================================

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
        assertCallArityMismatch(errors[0], expected = 1, actual = 2)
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
        assertCallArityMismatch(errors[0], expected = 2, actual = 1)
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
        assertMismatch(errors[0], "Nil", "(Num) -> Any")
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
        assertEquals(1, errors.size, "Expected exactly one error for type mismatch")
        assertMismatch(errors[0], "String", "Num")
    }

    // ============================================================
    // Constructors passed to higher-order functions
    // ============================================================

    @Test
    fun genericConstructor_passedToPolymorphicApply() {
        assertType(
            "Some<Num>",
            infer(
                """
                type Option<'A> = None | Some { value: 'A }

                fun apply(f, x) = f(x)
                apply(Some, 42)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun multiParamConstructor_passedToHOF() {
        assertType(
            "Pair<Num, String>",
            infer(
                """
                type Pair<'A, 'B> = Pair { fst: 'A, snd: 'B }

                fun apply2(f, x, y) = f(x, y)
                apply2(Pair, 42, "hello")
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun constructor_composedWithFunction() {
        assertType(
            "(Num) -> Some<Num>",
            infer(
                """
                type Option<'A> = None | Some { value: 'A }

                fun compose(f, g) = |x -> f(g(x))|
                fun double(n) = n + n
                compose(Some, double)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun constructor_returnedFromFunction_thenApplied() {
        assertType(
            "Some<Num>",
            infer(
                """
                type Option<'A> = None | Some { value: 'A }

                fun getWrapper() = Some
                w = getWrapper()
                w(42)
                """.trimIndent(),
            ),
        )
    }

    // ============================================================
    // Bare constructor polymorphism
    // ============================================================

    @Test
    fun bareConstructor_subtypesNumAndStringInstantiations() {
        assertType(
            "{ nums: Cons<Num>, strs: Cons<String> }",
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                { nums = Cons(1, Nil), strs = Cons("hello", Nil) }
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun bareConstructor_passedToFunctionExpectingSpecificType() {
        assertType(
            "List<Num>",
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                type NumListConsumer = NumListConsumer { f: List<Num> -> List<Num> }
                nlc = NumListConsumer(|xs -> xs|)
                nlc.f(Nil)
                """.trimIndent(),
            ),
        )
    }
}
