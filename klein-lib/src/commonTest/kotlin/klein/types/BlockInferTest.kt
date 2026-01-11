package klein.types

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
        assertType("() -> Num", infer(program))
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
        assertType("() -> Num", infer(program))
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
        assertType("() -> Num", infer(program))
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
        assertType("() -> Num", infer(program))
    }

    @Test
    fun block_bindingOnlyReturnsUnit() {
        val program =
            """
            |
              x = 1
            |
            """.trimIndent()
        assertType("() -> Unit", infer(program))
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
        // Result simplifies to Num
        assertType("() -> Num", infer(program))
    }

    @Test
    fun block_lambdaBindingOnlyReturnsUnit() {
        val program =
            """
            |
              double = |x -> x * 2|
            |
            """.trimIndent()
        assertType("() -> Unit", infer(program))
    }

    @Test
    fun block_scoping_innerDoesNotLeakOut() {
        val result =
            inferWithErrors(
                """
                f = |
                  x = 1
                |
                x
                """.trimIndent(),
            )
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.UnboundVariable)
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
        // Result simplifies to Num
        assertType("Num", infer(program))
    }

    @Test
    fun block_duplicateBinding() {
        val result =
            inferWithErrors(
                """
                |
                  x = 1
                  x = 2
                  x
                |
                """.trimIndent(),
            )
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.DuplicateBinding)
    }

    @Test
    fun block_shadowsOuterBinding() {
        val program =
            """
            x = 'outer'
            f = |
              x = 42
              x
            |
            f()
            """.trimIndent()
        // Result simplifies to Num
        assertType("Num", infer(program))
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
        // Nested thunk calls all return Num
        assertType("() -> Num", infer(program))
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
        // Result simplifies to Num
        assertType("() -> Num", infer(program))
    }

    @Test
    fun block_ifThenElseInBlock() {
        val program =
            """
            |
              x = 5
              if x > 3 then 'big' else 'small'
            |
            """.trimIndent()
        // Result simplifies to String
        assertType("() -> String", infer(program))
    }

    @Test
    fun block_recordInBlock() {
        val program =
            """
            |
              person = { name = 'Alice', age = 30 }
              person.age
            |
            """.trimIndent()
        // Result simplifies to Num
        assertType("() -> Num", infer(program))
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
        val result = inferWithErrors(program)
        assertEquals(0, result.errors.size)
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
        val result = inferWithErrors(program)
        assertEquals(0, result.errors.size)
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
        val result = inferWithErrors(program)
        assertEquals(0, result.errors.size)
    }

    @Test
    fun block_letPolymorphism_escapingCapture_rejectsBoolPlusNum() {
        // escape_direct(true) returns a function that always returns true (Bool)
        // So h(42) should return Bool, and h(42) + 1 should fail
        val program =
            """
            escape_direct = |x -> |y -> x||
            h = escape_direct(true)
            h(42) + 1
            """.trimIndent()
        val result = inferWithErrors(program)
        assertTrue(
            result.errors.any { it is TypeError.TypeMismatch },
            "Expected type mismatch error for Bool + 1, but got: ${result.errors}",
        )
    }

    @Test
    fun block_letPolymorphism_capturedVar_preservesConnection() {
        // When f is bound inside escape, the captured x should NOT be generalized
        // because x comes from the outer scope (lower level)
        val program =
            """
            escape = |x ->
              f = |y -> x|
              f
            |
            h = escape(true)
            h(42) + 1
            """.trimIndent()
        val result = inferWithErrors(program)
        // h(42) should return Bool (captured from escape(true)), so + 1 should fail
        assertTrue(
            result.errors.any { it is TypeError.TypeMismatch },
            "Expected type mismatch error for Bool + 1, but got: ${result.errors}",
        )
    }

    // ==========================================
    // Classic let-polymorphism examples
    // ==========================================

    @Test
    fun block_letPolymorphism_classic() {
        // let f = fun x -> x in { a = f 0, b = f true } : {a: int, b: bool}
        val program = """
            f = |x -> x|
            { a = f(0), b = f(true) }
        """.trimIndent()
        assertType("{ a: Num, b: Bool }", infer(program))
    }

    @Test
    fun block_letPolymorphism_withCapture() {
        // fun y -> let f = fun x -> x in { a = f y, b = f true }
        val program = """
            |y ->
              f = |x -> x|
              { a = f(y), b = f(true) }
            |
        """.trimIndent()
        assertType("(a) -> { a: a, b: Bool }", infer(program))
    }

    @Test
    fun block_letPolymorphism_withUnionInput() {
        // fun y -> let f = fun x -> y x in { a = f 0, b = f true }
        // y is applied to both int and bool
        val program = """
            |y ->
              f = |x -> y(x)|
              { a = f(0), b = f(true) }
            |
        """.trimIndent()
        val result = inferWithErrors(program)
        assertEquals(0, result.errors.size, "Let-polymorphism with union input should type-check: ${result.errors}")
    }

    @Test
    fun block_letPolymorphism_idAppliedToItself() {
        // let id = fun x -> x in id id : 'a -> 'a
        val program = """
            id = |x -> x|
            id(id)
        """.trimIndent()
        assertType("(a) -> a", infer(program))
    }

    @Test
    fun block_letPolymorphism_nestedLet() {
        // let f = fun x -> x in let g = f in g 42
        val program = """
            f = |x -> x|
            g = f
            g(42)
        """.trimIndent()
        assertType("Num", infer(program))
    }

    // ==========================================
    // Twice combinator
    // ==========================================

    @Test
    fun block_twiceCombinator() {
        // fun f -> fun x -> f (f x)
        val result = inferWithErrors("|f -> |x -> f(f(x))||")
        assertEquals(0, result.errors.size, "Twice combinator should type-check: ${result.errors}")
    }

    @Test
    fun block_twiceCombinator_applied() {
        // let twice = fun f -> fun x -> f (f x) in twice (fun n -> n + 1) 0
        val program = """
            twice = |f -> |x -> f(f(x))||
            twice(|n -> n + 1|)(0)
        """.trimIndent()
        assertType("Num", infer(program))
    }

    @Test
    fun block_twiceCombinator_onIdentity() {
        // let twice = fun f -> fun x -> f (f x) in twice (fun x -> x)
        val program = """
            twice = |f -> |x -> f(f(x))||
            twice(|x -> x|)
        """.trimIndent()
        assertType("(a) -> a", infer(program))
    }
}
