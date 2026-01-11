package klein.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for level-based constraints in type inference.
 *
 * These tests verify that type variables from inner scopes (higher levels)
 * don't escape into the bounds of type variables from outer scopes (lower levels).
 *
 * This is critical for let-polymorphism: when a polymorphic function is used
 * in an inner scope and then returned, it should remain polymorphic.
 */
class LevelConstraintTest {
    /**
     * Minimal case: id used with Int inside a function, then returned.
     * The returned id should still be polymorphic.
     */
    @Test
    fun levelEscape_minimal() {
        val program =
            """
            id = |x -> x|
            tainted = |
              q = id(1)
              id
            |
            t = tainted()
            a = t(42)
            b = t(true)
            a
            """.trimIndent()

        val result = inferWithErrors(program)
        assertEquals(
            0,
            result.errors.size,
            "id should remain polymorphic even after being used with Int. Errors: ${result.errors}",
        )
    }

    /**
     * The tainted function returns id after using it with Int.
     * When we use the returned function with Bool, it should work.
     */
    @Test
    fun levelEscape_returnedIdStaysPolymorphic() {
        val program =
            """
            id = |x -> x|
            maker = |
              _ = id(1)
              id
            |
            f = maker()
            f(true)
            """.trimIndent()

        val result = inferWithErrors(program)
        assertEquals(
            0,
            result.errors.size,
            "Returned id should be polymorphic and work with Bool. Errors: ${result.errors}",
        )
    }

    /**
     * Multiple uses of id in the same scope before returning it.
     * All uses create level-2 constraints that shouldn't leak.
     */
    @Test
    fun levelEscape_multipleUsesBeforeReturn() {
        val program =
            """
            id = |x -> x|
            wrapper = |
              p = id(1)
              q = id(2)
              r = id(3)
              id
            |
            f = wrapper()
            a = f(42)
            b = f(true)
            a
            """.trimIndent()

        val result = inferWithErrors(program)
        assertEquals(
            0,
            result.errors.size,
            "Multiple uses shouldn't constrain the returned id. Errors: ${result.errors}",
        )
    }

    /**
     * Two-level nesting: id used at level 2, returned to level 1, then to level 0.
     */
    @Test
    fun levelEscape_nestedReturns() {
        val program =
            """
            id = |x -> x|
            inner = |
              _ = id(1)
              id
            |
            outer = |
              f = inner()
              _ = f(2)
              f
            |
            g = outer()
            g(true)
            """.trimIndent()

        val result = inferWithErrors(program)
        assertEquals(
            0,
            result.errors.size,
            "Deeply nested returns should preserve polymorphism. Errors: ${result.errors}",
        )
    }

    /**
     * Similar to the SimpleSub blog example: using a function parameter
     * inside a nested scope shouldn't leak.
     */
    @Test
    fun levelEscape_parameterUsedInNested() {
        val program =
            """
            f = |x ->
              g = |y -> x|
              _ = g(1)
              g
            |
            h = f(true)
            h(42)
            """.trimIndent()

        val result = inferWithErrors(program)
        // h should return Bool (the captured x), so this should work
        assertEquals(
            0,
            result.errors.size,
            "Nested function should capture parameter correctly. Errors: ${result.errors}",
        )
    }

    /**
     * A polymorphic function created inside a lambda shouldn't
     * have its polymorphism destroyed by usage in that scope.
     */
    @Test
    fun levelEscape_innerPolymorphicFunction() {
        val program =
            """
            maker = |dummy ->
              id = |x -> x|
              p = id(1)
              q = id(true)
              id
            |
            f = maker(0)
            a = f(42)
            b = f('hello')
            a
            """.trimIndent()

        val result = inferWithErrors(program)
        assertEquals(
            0,
            result.errors.size,
            "Inner polymorphic function should be usable polymorphically after return. Errors: ${result.errors}",
        )
    }

    /**
     * The "reference cell" problem from ML.
     * Without proper level tracking, this could be unsound.
     */
    @Test
    fun levelEscape_referenceCellProblem() {
        val program =
            """
            makeCell = |
              id = |x -> x|
              { get = id, set = id }
            |
            cell = makeCell()
            _ = cell.set(1)
            cell.get(true)
            """.trimIndent()

        val result = inferWithErrors(program)
        // This should succeed - get and set should be independent
        assertEquals(
            0,
            result.errors.size,
            "cell.get and cell.set should have independent type variables. Errors: ${result.errors}",
        )
    }

    /**
     * Using a function with different types at different levels.
     */
    @Test
    fun levelEscape_differentTypesAtDifferentLevels() {
        val program =
            """
            id = |x -> x|
            level1 = |
              _ = id(1)
              level2 = |
                _ = id(true)
                level3 = |
                  _ = id('hello')
                  id
                |
                level3()
              |
              level2()
            |
            f = level1()
            f(42)
            """.trimIndent()

        val result = inferWithErrors(program)
        assertEquals(
            0,
            result.errors.size,
            "id should be polymorphic at all levels. Errors: ${result.errors}",
        )
    }

    /**
     * Returning a function that captures a type variable from an intermediate level.
     */
    @Test
    fun levelEscape_captureIntermediateLevel() {
        val program =
            """
            outer = |x ->
              middle = |y ->
                inner = |z -> x|
                _ = inner(1)
                inner
              |
              middle
            |
            f = outer(true)
            g = f(2)
            g(3)
            """.trimIndent()

        val result = inferWithErrors(program)
        // g should return Bool (captured x), this should work
        assertEquals(
            0,
            result.errors.size,
            "Captured variable from intermediate level should work. Errors: ${result.errors}",
        )
    }

    /**
     * Test that demonstrates what SHOULD fail: actual type mismatch.
     * This verifies that our level system doesn't make everything succeed.
     */
    @Test
    fun levelEscape_actualTypeMismatch_shouldFail() {
        val program =
            """
            id = |x -> x|
            f = id(1)
            f + true
            """.trimIndent()

        val result = inferWithErrors(program)
        assertTrue(
            result.errors.isNotEmpty(),
            "Should fail: can't add Num and Bool",
        )
    }

    /**
     * Another case that SHOULD fail: monomorphic function used incorrectly.
     */
    @Test
    fun levelEscape_monomorphicUsage_shouldFail() {
        val program =
            """
            addOne = |x -> x + 1|
            result = addOne(true)
            result
            """.trimIndent()

        val result = inferWithErrors(program)
        assertTrue(
            result.errors.isNotEmpty(),
            "Should fail: can't add Bool and Num",
        )
    }

    /**
     * Test that a function constrains properly when NOT returned.
     * This is a control case - no escape, so constraints should apply.
     */
    @Test
    fun levelConstraint_innerUsageOnly() {
        val program =
            """
            id = |x -> x|
            wrapper = |
              a = id(1)
              b = id(2)
              a + b
            |
            wrapper()
            """.trimIndent()

        val result = inferWithErrors(program)
        assertEquals(
            0,
            result.errors.size,
            "Inner usage only should work fine. Errors: ${result.errors}",
        )
    }

    /**
     * Variation: using the same polymorphic function at the same level multiple times.
     */
    @Test
    fun levelConstraint_sameLevel_multipleUses() {
        val program =
            """
            id = |x -> x|
            a = id(1)
            b = id(true)
            a
            """.trimIndent()

        val result = inferWithErrors(program)
        assertEquals(
            0,
            result.errors.size,
            "Same level, different uses should work. Errors: ${result.errors}",
        )
    }

    /**
     * SimpleSub extrusion test case (line 110).
     *
     * This tests that the return type of the outer function is properly
     * connected to the result of calling k. Without proper level handling,
     * the return type becomes Nothing instead of being connected to k's result.
     *
     * SimpleSub infers: (('a ∧ int -> 'a) -> 'b) -> 'b
     * Klein currently infers: (((a & Num) -> a) -> Any) -> Nothing  <-- BUG
     *
     * The return type should be 'b (a type variable), not Nothing.
     * When we call f(myK) where myK returns Num, we should get Num, not Nothing.
     */
    @Test
    fun levelConstraint_extrusionCase() {
        val program =
            """
            f = |k ->
              test = k(|x ->
                tmp = x + 1
                x
              |)
              test
            |
            myK = |g -> g(42)|
            result = f(myK)
            result
            """.trimIndent()

        val result = inferWithErrors(program)
        val resultType = infer(program)

        assertEquals(
            0,
            result.errors.size,
            "Should have no errors. Errors: ${result.errors}",
        )

        // The result should be Num (from myK returning g(42)), not Nothing
        assertTrue(
            resultType !is DisplayType.DBottom,
            "result should be Num, not Nothing. Got: $resultType",
        )
    }
}
