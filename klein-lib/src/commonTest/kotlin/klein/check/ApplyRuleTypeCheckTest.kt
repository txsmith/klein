package klein.check

import klein.check.Type.*
import klein.types.TypeError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Red targets for the Pierce–Turner application rules `inferApply` does not implement yet (see its
 * doc comment):
 *  - the **checked-argument** premise of S-App/C-App — a bare lambda gets its parameter types from a
 *    concrete parameter (naive `inferApply` synthesizes every argument, so this fails today), and
 *  - **S-App-Bot / C-App-Bot** — applying a `⊥`-typed callee yields `⊥` with no `NotAFunction`.
 *
 * These go green as the per-argument `isGround` pass and the `⊥`-callee case land.
 */
class ApplyRuleTypeCheckTest {
    // --- checked-argument premise: an unannotated lambda is checked against a concrete parameter ---

    @Test
    fun bareLambdaArgToConcreteParam() =
        assertEquals(TNum, infer("fun apply(f: (Num) -> Num): Num = f(3)\napply(|x -> x + 1|)").type)

    @Test
    fun bareLambdaArgToGroundParamOfPolymorphicFunction() =
        // `f`'s param is ground even though the call is polymorphic in `'T`; it should be checked.
        assertEquals(TNum, infer("fun withCb(f: (Num) -> Num, x: 'T): 'T = x\nwithCb(|n -> n + 1|, 5)").type)

    // --- S-App-Bot / C-App-Bot: a ⊥-typed callee is applicable, yields ⊥, and reports no error ---

    @Test
    fun applyingBottomYieldsBottomWithoutError() {
        val env = TypeEnv.empty()
        env.bind("nope", TBottom) // nope : Nothing
        val result = infer("nope(1, 2)", env)
        assertEquals(TBottom, result.type)
        assertTrue(result.errors.isEmpty(), "applying Nothing should not error: ${result.errors}")
    }

    @Test
    fun unboundCalleeDoesNotCascadeToNotAFunction() {
        // `foo` is unbound (⊥); applying it must not stack a second "not a function" on the real error.
        val result = infer("foo(3)")
        assertTrue(
            result.errors.none { it is TypeError.NotAFunction },
            "spurious NotAFunction after unbound callee: ${result.errors}",
        )
    }
}
