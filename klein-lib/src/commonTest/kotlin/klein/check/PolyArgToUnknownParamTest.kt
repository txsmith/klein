package klein.check

import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Exposes the Pierce–Turner "unknowns on two sides" limitation: passing a **still-polymorphic** value
 * where the parameter's expected type **still mentions an unsolved type variable**. Nothing to do with
 * records — a bare higher-order function reaches it.
 *
 * `id : ∀T. (T) -> T` and `use : ∀X. ((X) -> X, X) -> X`. In `use(id, 3)` the parameter `g: ('X)->'X`
 * is *not ground* (it mentions `use`'s unknown `X`), so the argument is **synthesized** → `∀T.(T)->T`,
 * and we then face
 *
 *     ∀T.(T)->T   <:   ('X) -> 'X
 *
 * — an unknown (`T`) on the left and an unknown (`X`) on the right. That is *unification*, not
 * *matching*: it violates the well-formedness of Pierce–Turner's constraint sets, whose bounds must
 * satisfy `FV(bound) ∩ (V ∪ X) = ∅`. (Note `x = 3` *would* pin `X = Num`, but Tier-1 synthesizes the
 * argument before solving `X` — no floating — so it never gets the chance.)
 *
 * Today this trips the `require(!is TForall)` guard in the solver (an `IllegalArgumentException`)
 * rather than emitting a diagnostic — the crash *is* the thing this test pins. When the corner is
 * handled it should instead become a clean "cannot infer / cannot pass a polymorphic value here"
 * error, or — with floating (Tier-2) — infer `Num`. Either way this test will change shape then.
 */
class PolyArgToUnknownParamTest {
    @Test
    fun polymorphicArgWhereParamStillMentionsUnknown_hitsTheTwoUnknownsLimit() {
        assertFailsWith<IllegalArgumentException> {
            infer(
                """
                fun id(a: 'T): 'T = a
                fun use(g: ('X) -> 'X, x: 'X): 'X = g(x)
                use(id, 3)
                """.trimIndent(),
            )
        }
    }
}
