package klein.core

import kotlin.test.Test

/**
 * Golden lowering spec for blocks: surface [klein.surface.Block] -> core [EnterScope].
 *
 * The whole program is itself one block, so it lowers to one `scope`; a nested block expression
 * (an indented RHS) lowers to a nested `scope`, indented one further level. Statement mapping:
 *   - `val` (surface [klein.surface.Val]) -> `Bind`, filling the next slot. slot = the 0-based
 *     ordinal counting only binds; a `Run` in between does not bump it. A bind reads back as
 *     `name[depth;slot]`.
 *   - a bare statement-expression (an [klein.surface.Expr] that is not the trailing one) ->
 *     `Run`: evaluated for effect, discarded, consumes NO slot.
 *   - the trailing expression -> the scope's `result`, which evaluates in tail position.
 *   - `fun` (surface [klein.surface.FunDef]) -> a `Bind` of a named `Lambda`, filling a slot like
 *     any other bind; it is in scope for the whole block, so an earlier line may reference it.
 */
class BlockLoweringTest {
    // --- vals + trailing expression: the top-level program is one block ---

    // Two top-level vals fill slots 0 and 1 in textual order; the trailing `x + y` is the result.
    @Test
    fun twoVals_thenTrailingExpr() =
        assertLowersTo(
            """
            x = 1
            y = 2
            x + y
            """,
            """
            scope
              bind x#0 = 1
              bind y#1 = 2
              (x[0;0] + y[0;1])
            """,
        )

    // --- bare statement-expressions become `run` and consume no slot ---

    // Minimal Run: a bare leading literal is evaluated-and-discarded (`run 1`); the trailing `2`
    // is the result. No binds at all, but the `run` keeps the `scope` wrapper.
    @Test
    fun bareLiteral_isRunStatement() =
        assertLowersTo(
            """
            1
            2
            """,
            """
            scope
              run 1
              2
            """,
        )

    // The load-bearing Run test: a bare `a` between two vals is a `run`, and because it takes no
    // slot, the following val `b` is still slot #1 (not #2) and reads back as b[0;1].
    @Test
    fun bareExpr_betweenVals_consumesNoSlot() =
        assertLowersTo(
            """
            a = 1
            a
            b = 2
            a + b
            """,
            """
            scope
              bind a#0 = 1
              run a[0;0]
              bind b#1 = 2
              (a[0;0] + b[0;1])
            """,
        )

    // A program that ends in a `val` has no trailing expression, so the scope's result is the
    // synthesized `unit` constant — matching the checker, which types such a program as Unit.
    @Test
    fun trailingValOnly_resultIsUnit() =
        assertLowersTo(
            """
            x = 1
            """,
            """
            scope
              bind x#0 = 1
              unit
            """,
        )

    // --- `fun` definitions ---

    // A `fun` lowers to a Bind of a named Lambda (name = "double", param annotation erased); the
    // trailing call reads it as double[0;0]. The param `n` is the lambda's own slot 0 at depth 0.
    @Test
    fun funDef_usedByTrailingExpr() =
        assertLowersTo(
            """
            fun double(n: Num) = n * 2
            double(21)
            """,
            """
            scope
              bind double#0 = fun double/1 -> (n[0;0] * 2)
              double[0;0](21)
            """,
        )

    // A `val` references a `fun` defined later in the same block — legal because the `fun` is in
    // scope block-wide. Slots follow written order: `x` is #0, `double` is #1, so x's body reads
    // double[0;1].
    @Test
    fun hoistedFun_usedByEarlierVal() =
        assertLowersTo(
            """
            x = double(21)
            fun double(n: Num) = n * 2
            x
            """,
            """
            scope
              bind x#0 = double[0;1](21)
              bind double#1 = fun double/1 -> (n[0;0] * 2)
              x[0;0]
            """,
        )

    // A `fun` call in a bare (non-trailing) position is a `Run` — evaluated for effect, consumes no
    // slot. `log` is defined first (slot #0); the trailing `2` is the result.
    @Test
    fun bareCall_isRunStatement() =
        assertLowersTo(
            """
            fun log(m: Num) = m
            log(1)
            2
            """,
            """
            scope
              bind log#0 = fun log/1 -> m[0;0]
              run log[0;0](1)
              2
            """,
        )

    // --- nested block expressions: a nested scope, indented one level further ---

    // An indented RHS is a Block, so `r`'s bind body is a nested `scope`. The nested block is its
    // own lexical scope: `a` there is depth 0 (current), slot 0. Note the extra indentation of the
    // inner scope's lines (4 spaces vs the outer scope's 2).
    @Test
    fun nestedBlock_asValBody() =
        assertLowersTo(
            """
            r =
              a = 10
              a + 1
            r
            """,
            """
            scope
              bind r#0 = scope
                bind a#0 = 10
                (a[0;0] + 1)
              r[0;0]
            """,
        )

    // A nested block that reads an OUTER binding shows the scope boundary in the depths: inside the
    // block, `inner` is depth 0 (the block's own slot 0) but `outer` is depth 1 (one scope up).
    // `outer` is outer slot #0, `r` is outer slot #1, so the trailing ref is r[0;1].
    @Test
    fun nestedBlock_referencesOuterScope() =
        assertLowersTo(
            """
            outer = 5
            r =
              inner = 10
              outer + inner
            r
            """,
            """
            scope
              bind outer#0 = 5
              bind r#1 = scope
                bind inner#0 = 10
                (outer[1;0] + inner[0;0])
              r[0;1]
            """,
        )
}
