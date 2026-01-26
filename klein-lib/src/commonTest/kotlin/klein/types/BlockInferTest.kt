package klein.types

import klein.Type
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlockInferTest {
    @Test
    fun block_singleExpr() {
        val program =
            """
            |
              42
            |
            """.trimIndent()
        assertType(Type.Fun(emptyList(), Type.Num), infer(program))
    }

    @Test
    fun block_multipleExprs_returnsLast() {
        val program =
            """
            |
              1
              2
              3
            |
            """.trimIndent()
        assertType(Type.Fun(emptyList(), Type.Num), infer(program))
    }

    @Test
    fun block_valBinding() {
        val program =
            """
            |
              x = 1
              x
            |
            """.trimIndent()
        assertType(Type.Fun(emptyList(), Type.Num), infer(program))
    }

    @Test
    fun block_multipleBindings() {
        val program =
            """
            |
              x = 1
              y = 2
              x + y
            |
            """.trimIndent()
        assertType(Type.Fun(emptyList(), Type.Num), infer(program))
    }

    @Test
    fun block_bindingOnlyReturnsUnit() {
        val program =
            """
            |
              x = 1
            |
            """.trimIndent()
        assertType(Type.Fun(emptyList(), Type.Unit), infer(program))
    }

    @Test
    fun block_lambdaBinding() {
        val program =
            """
            |
              double = |x -> x * 2|
              double(21)
            |
            """.trimIndent()
        assertType(Type.Fun(emptyList(), Type.Num), infer(program))
    }

    @Test
    fun block_lambdaBindingOnlyReturnsUnit() {
        val program =
            """
            |
              double = |x -> x * 2|
            |
            """.trimIndent()
        assertType(Type.Fun(emptyList(), Type.Unit), infer(program))
    }

    @Test
    fun block_scoping_innerDoesNotLeakOut() {
        val errors =
            inferErrors(
                """
                f = |
                  x = 1
                |
                x
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.UnboundVariable)
    }

    @Test
    fun block_scoping_canAccessOuter() {
        val program =
            """
            outer = 10
            f = |
              inner = 5
              outer + inner
            |
            f()
            """.trimIndent()
        assertType(Type.Num, infer(program))
    }

    @Test
    fun block_duplicateBinding() {
        val errors =
            inferErrors(
                """
                |
                  x = 1
                  x = 2
                  x
                |
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.DuplicateBinding)
    }

    @Test
    fun block_shadowsOuterBinding() {
        val program =
            """
            x = "outer"
            f = |
              x = 42
              x
            |
            f()
            """.trimIndent()
        assertType(Type.Num, infer(program))
    }

    @Test
    fun block_nestedBlocks() {
        val program =
            """
            |
              f = |
                g = |
                  42
                |
                g()
              |
              f()
            |
            """.trimIndent()
        assertType(Type.Fun(emptyList(), Type.Num), infer(program))
    }

    @Test
    fun block_lambdaInBlock() {
        val program =
            """
            |
              double = |x -> x * 2|
              double(21)
            |
            """.trimIndent()
        assertType(Type.Fun(emptyList(), Type.Num), infer(program))
    }

    @Test
    fun block_ifThenElseInBlock() {
        val program =
            """
            |
              x = 5
              if x > 3 then "big" else "small"
            |
            """.trimIndent()
        assertType(Type.Fun(emptyList(), Type.Str), infer(program))
    }

    @Test
    fun block_recordInBlock() {
        val program =
            """
            |
              person = { name = "Alice", age = 30 }
              person.age
            |
            """.trimIndent()
        assertType(Type.Fun(emptyList(), Type.Num), infer(program))
    }

    @Test
    fun block_letPolymorphism_idUsedTwice() {
        val program =
            """
            |
              id = |x -> x|
              a = id(1)
              b = id(true)
              a
            |
            """.trimIndent()
        val errors = inferErrors(program)
        assertEquals(0, errors.size)
    }

    @Test
    fun block_letPolymorphism_escapingFunction() {
        val program =
            """
            escape = |
              id = |x -> x|
              id
            |
            f = escape()
            a = f(1)
            b = f(true)
            a
            """.trimIndent()
        val errors = inferErrors(program)
        assertEquals(0, errors.size)
    }

    @Test
    fun block_letPolymorphism_nestedCapture() {
        val program =
            """
            f = |y ->
              id = |x -> x|
              id(y)
              id
            |
            g = f(1)
            g(true)
            """.trimIndent()
        val errors = inferErrors(program)
        assertEquals(0, errors.size)
    }

    @Test
    fun block_letPolymorphism_escapingCapture_rejectsBoolPlusNum() {
        val program =
            """
            escape_direct = |x -> |y -> x||
            h = escape_direct(true)
            h(42) + 1
            """.trimIndent()
        val errors = inferErrors(program)
        assertTrue(
            errors.any { it is TypeError.TypeMismatch },
            "Expected type mismatch error for Bool + 1, but got: $errors",
        )
    }

    @Test
    fun block_letPolymorphism_capturedVar_preservesConnection() {
        val program =
            """
            escape = |x ->
              f = |y -> x|
              f
            |
            h = escape(true)
            h(42) + 1
            """.trimIndent()
        val errors = inferErrors(program)
        assertTrue(
            errors.any { it is TypeError.TypeMismatch },
            "Expected type mismatch error for Bool + 1, but got: $errors",
        )
    }

    @Test
    fun block_letPolymorphism_recordWithMixedLevels() {
        val program =
            """
            outer = |x ->
              inner = |y -> {first = x, second = y}|
              a = inner(1)
              b = inner(true)
              a.second + 1
            |
            outer(42)
            """.trimIndent()
        val errors = inferErrors(program)
        assertEquals(
            emptyList(),
            errors,
            "Expected no errors - each inner call should get fresh type variables",
        )
    }

    @Test
    fun block_letPolymorphism_classic() {
        val program =
            """
            f = |x -> x|
            { a = f(0), b = f(true) }
            """.trimIndent()
        assertType(Type.Record(mapOf("a" to Type.Num, "b" to Type.Bool)), infer(program))
    }

    @Test
    fun block_letPolymorphism_withCapture() {
        val program =
            """
            |y ->
              f = |x -> x|
              { a = f(y), b = f(true) }
            |
            """.trimIndent()
        assertType("('A) -> { a: 'A, b: Bool }", infer(program))
    }

    @Test
    fun block_letPolymorphism_withUnionInput() {
        val program =
            """
            |y ->
              f = |x -> y(x)|
              { a = f(0), b = f(true) }
            |
            """.trimIndent()
        val errors = inferErrors(program)
        assertEquals(0, errors.size, "Let-polymorphism with union input should type-check: $errors")
    }

    @Test
    fun block_letPolymorphism_idAppliedToItself() {
        val program =
            """
            id = |x -> x|
            id(id)
            """.trimIndent()
        assertType("('A) -> 'A", infer(program))
    }

    @Test
    fun block_letPolymorphism_nestedLet() {
        val program =
            """
            f = |x -> x|
            g = f
            g(42)
            """.trimIndent()
        assertType(Type.Num, infer(program))
    }

    @Test
    fun block_twiceCombinator() {
        val errors = inferErrors("|f -> |x -> f(f(x))||")
        assertEquals(0, errors.size, "Twice combinator should type-check: $errors")
    }

    @Test
    fun block_twiceCombinator_applied() {
        val program =
            """
            twice = |f -> |x -> f(f(x))||
            twice(|n -> n + 1|)(0)
            """.trimIndent()
        assertType(Type.Num, infer(program))
    }

    @Test
    fun block_twiceCombinator_onIdentity() {
        val program =
            """
            twice = |f -> |x -> f(f(x))||
            twice(|x -> x|)
            """.trimIndent()
        assertType("('A) -> 'A", infer(program))
    }
}
