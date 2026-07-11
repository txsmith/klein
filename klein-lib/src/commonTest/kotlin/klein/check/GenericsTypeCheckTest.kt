package klein.check

import klein.check.Type.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenericsTypeCheckTest {
    @Test
    fun identity_instantiatedToNum() =
        assertEquals(TNum, infer("fun id(x: 'T): 'T = x\nid(42)").type)

    @Test
    fun identity_instantiatedToBool() =
        assertEquals(TBool, infer("fun id(x: 'T): 'T = x\nid(true)").type)

    @Test
    fun genericFieldProjection() =
        assertEquals(TNum, infer("fun getX(r: { x: 'T }): 'T = r.x\ngetX({ x = 42 })").type)

    @Test
    fun genericFunctionResult() =
        assertEquals(TBool, infer("fun useF(f: (Num) -> 'A): 'A = f(42)\nuseF(|n: Num -> n > 0|)").type)

    @Test
    fun twoTypeParams_returnsFirst() =
        assertEquals(TNum, infer("fun first(a: 'A, b: 'B): 'A = a\nfirst(1, true)").type)

    @Test
    fun polymorphicArgInstantiatedAtMonomorphicParam() =
        // `id : ∀A. (A) -> A` passed where `(Num) -> Num` is demanded — instantiated at the
        // argument's check (subsume), not left polymorphic.
        assertEquals(
            TNum,
            infer("fun id(x: 'A) = x\nfun modify(f: (Num) -> Num) = f(3)\nmodify(id)").type,
        )

    @Test
    fun synthContravariantConstructor_keepsArgType() =
        assertEquals(
            TRef("Consumer", listOf(TNum)),
            infer(
                """
                type Consumer<'A> = Consumer { consume: 'A -> String }
                Consumer(|d: Num -> "x"|)
                """.trimIndent(),
            ).type,
        )

    // --- Local polymorphism (tvar-scoping revisit) ---
    // The old let-poly cluster, rewritten to Path G form: polymorphism comes from a written `'T`,
    // never inference. Bare-lambda inferred forms (`id = |x -> x|`) stay dead; these annotate the
    // binding so the `'T` is the source. All rank-1 — a local poly binding used *directly* at several
    // types. Red until a local `'T` may be introduced at its binding.

    @Test
    fun localPoly_idUsedTwice() {
        val program =
            """
            |
              id: ('T) -> 'T = |x -> x|
              a = id(1)
              b = id(true)
              a
            |
            """.trimIndent()
        assertTrue(infer(program).errors.isEmpty())
    }

    @Test
    fun localPoly_classic() {
        val program =
            """
            f: ('T) -> 'T = |x -> x|
            { a = f(0), b = f(true) }
            """.trimIndent()
        assertEquals(TRecord(mapOf("a" to TNum, "b" to TBool)), infer(program).type)
    }

    @Test
    fun localPoly_nestedLet() {
        val program =
            """
            f: ('T) -> 'T = |x -> x|
            g = f
            g(42)
            """.trimIndent()
        assertEquals(TNum, infer(program).type)
    }

    @Test
    fun localPoly_recordWithMixedLevels() {
        val program =
            """
            outer: (Num) -> Num = |x ->
              inner: ('Y) -> { first: Num, second: 'Y } = |y -> { first = x, second = y }|
              a = inner(1)
              b = inner(true)
              a.second + 1
            |
            outer(42)
            """.trimIndent()
        assertTrue(infer(program).errors.isEmpty())
    }

    @Test
    fun localPoly_withCapture() {
        val program =
            """
            |y: Num ->
              f: ('T) -> 'T = |x -> x|
              { a = f(y), b = f(true) }
            |
            """.trimIndent()
        assertTrue(infer(program).errors.isEmpty())
    }

    @Test
    fun localPoly_twiceCombinator() {
        val program =
            """
            twice: (('T) -> 'T) -> ('T) -> 'T = |f -> |x -> f(f(x))||
            twice
            """.trimIndent()
        assertTrue(infer(program).errors.isEmpty())
    }

    @Test
    fun localPoly_twiceApplied() {
        val program =
            """
            twice: (('T) -> 'T) -> ('T) -> 'T = |f -> |x -> f(f(x))||
            twice(|n: Num -> n + 1|)(0)
            """.trimIndent()
        assertEquals(TNum, infer(program).type)
    }

    // --- Parked: rank-2 / inference-only (NOT rank-1 targets) ---
    // Each threads a polymorphic value through a binding/return and uses it at several types — that
    // is rank-2 (higher-rank), out of scope for the rank-1-first plan. Asserting they type-check
    // would silently commit us to higher-rank, so they stay parked:
    //   escapingFunction, nestedCapture, escapingCapture, capturedVar,
    //   idAppliedToItself, twiceCombinator_onIdentity
    //   withUnionInput — needs union *inference* on the captured param (unsupported)
}
