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
        assertMismatch(errors[0], "Nil", "(Num) -> Nothing")
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
    fun genericConstructor_passedThroughTwoLevelsOfHOF() {
        assertType(
            "Some<Num>",
            infer(
                """
                type Option<'A> = None | Some { value: 'A }

                fun apply(f, x) = f(x)
                fun applyIndirect(g, y) = apply(g, y)
                applyIndirect(Some, 42)
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
    fun bareConstructor_inIfThenElseWithTypedSibling() {
        assertType(
            "Option<String>",
            infer(
                """
                type Option<'A> = None | Some { value: 'A }
                if true then None else Some("hello")
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

    @Test
    fun bareConstructors_fromDifferentTypesInSameExpression() {
        assertType(
            "{ lst: List<String>, opt: Option<Num> }",
            infer(
                """
                type Option<'A> = None | Some { value: 'A }
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                { opt = if true then None else Some(42), lst = if true then Nil else Cons("hi", Nil) }
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun bareConstructor_asRecursiveTail() {
        assertType(
            "List<Num>",
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                Cons(1, Cons(2, Cons(3, Nil))).tail
                """.trimIndent(),
            ),
        )
    }
}
