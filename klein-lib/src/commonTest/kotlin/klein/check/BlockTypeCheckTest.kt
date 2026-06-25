package klein.check

import klein.check.Type.*
import klein.types.TypeError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BlockTypeCheckTest {
    @Test
    fun block_singleExpr() {
        val program =
            """
            |
              42
            |
            """.trimIndent()
        assertEquals(TFun(emptyList(), TNum), infer(program).type)
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
        assertEquals(TFun(emptyList(), TNum), infer(program).type)
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
        assertEquals(TFun(emptyList(), TNum), infer(program).type)
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
        assertEquals(TFun(emptyList(), TNum), infer(program).type)
    }

    @Test
    fun block_bindingOnlyReturnsUnit() {
        val program =
            """
            |
              x = 1
            |
            """.trimIndent()
        assertEquals(TFun(emptyList(), TUnit), infer(program).type)
    }

    // Lambda annotated (Path G: bare params are an error) — assertion unchanged.
    @Test
    fun block_lambdaBinding() {
        val program =
            """
            |
              double = |x: Num -> x * 2|
              double(21)
            |
            """.trimIndent()
        assertEquals(TFun(emptyList(), TNum), infer(program).type)
    }

    @Test
    fun block_lambdaBindingOnlyReturnsUnit() {
        val program =
            """
            |
              double = |x: Num -> x * 2|
            |
            """.trimIndent()
        assertEquals(TFun(emptyList(), TUnit), infer(program).type)
    }

    @Test
    fun block_scoping_innerDoesNotLeakOut() {
        val errors =
            infer(
                """
                f = |
                  x = 1
                |
                x
                """.trimIndent(),
            ).errors
        assertEquals(1, errors.size)
        val error = errors[0]
        assertIs<TypeError.UnboundVariable>(error)
        assertEquals("x", error.name)
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
        assertEquals(TNum, infer(program).type)
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
        assertEquals(TNum, infer(program).type)
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
        assertEquals(TFun(emptyList(), TNum), infer(program).type)
    }

    @Test
    fun block_lambdaInBlock() {
        val program =
            """
            |
              double = |x: Num -> x * 2|
              double(21)
            |
            """.trimIndent()
        assertEquals(TFun(emptyList(), TNum), infer(program).type)
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
        assertEquals(TFun(emptyList(), TStr), infer(program).type)
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
        assertEquals(TFun(emptyList(), TNum), infer(program).type)
    }

    // Local-polymorphism targets (the old let-poly cluster, rewritten to Path G form) live in
    // GenericsTypeCheckTest — they're tvar-scoping/generics red targets, not block-structure tests.
    //
    // --- Deferred (unrelated to polymorphism) ---
    //   block_duplicateBinding — needs same-scope duplicate-binding detection (small TypeEnv feature)
}
