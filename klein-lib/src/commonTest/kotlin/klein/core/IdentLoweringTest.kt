package klein.core

import kotlin.test.Test

/**
 * Golden lowering spec for identifier references: surface [klein.surface.Ident] -> core [Var],
 * printed `name[depth;slot]`. depth 0 = the current scope, +1 per enclosing scope crossed;
 * slot = the binding's 0-based ordinal in its own scope.
 *
 * Top-level vals become `bind name#<slot>` statements in textual order inside one `scope`
 * (EnterScope), and the trailing expression becomes the `result` line. A program that is a
 * single trailing expression with no binds and no run-statements has no scope to enter, so it
 * lowers straight to that expression with no `scope` wrapper.
 *
 * Inputs are well-typed (the lowerer only receives checked programs).
 */
class IdentLoweringTest {
    @Test
    fun ref_toSingleTopLevelVal() {
        assertLowersTo(
            """
            x = 1
            x
            """,
            """
            scope
              bind x#0 = 1
              x[0;0]
            """,
        )
    }

    @Test
    fun ref_toSecondOfSeveralVals() {
        assertLowersTo(
            """
            a = 1
            b = 2
            b
            """,
            """
            scope
              bind a#0 = 1
              bind b#1 = 2
              b[0;1]
            """,
        )
    }

    @Test
    fun ref_toFirstOfSeveralVals() {
        assertLowersTo(
            """
            a = 1
            b = 2
            a
            """,
            """
            scope
              bind a#0 = 1
              bind b#1 = 2
              a[0;0]
            """,
        )
    }

    @Test
    fun ref_valBodyToEarlierSlotSameScope() {
        // A later bind sees an earlier sibling at depth 0 (same scope), by its slot ordinal.
        assertLowersTo(
            """
            a = 1
            b = a
            b
            """,
            """
            scope
              bind a#0 = 1
              bind b#1 = a[0;0]
              b[0;1]
            """,
        )
    }

    @Test
    fun ref_crossesOneLambdaBoundary_depth1() {
        // `x` inside the lambda body resolves to the top-level val one scope up: depth 1.
        assertLowersTo(
            """
            x = 1
            |_ -> x|
            """,
            """
            scope
              bind x#0 = 1
              fun/1 -> x[1;0]
            """,
        )
    }

    @Test
    fun ref_toLambdaParam_depth0() {
        // The param is the current (innermost) scope's slot 0, so the body ref is depth 0.
        // With no top-level binds, the program is a bare trailing expression: it lowers to just
        // the lambda, with no enclosing `scope`.
        assertLowersTo(
            """
            |x -> x|
            """,
            """
            fun/1 -> x[0;0]
            """,
        )
    }

    @Test
    fun ref_toSecondLambdaParam_slotOrdering() {
        // A fresh lambda scope resets depth to 0 even inside an enclosing scope; params slot in
        // declaration order, so the second param is slot 1.
        assertLowersTo(
            """
            k = 0
            |a, b -> b|
            """,
            """
            scope
              bind k#0 = 0
              fun/2 -> b[0;1]
            """,
        )
    }

    @Test
    fun ref_toFirstLambdaParam_slotOrdering() {
        assertLowersTo(
            """
            k = 0
            |a, b -> a|
            """,
            """
            scope
              bind k#0 = 0
              fun/2 -> a[0;0]
            """,
        )
    }
}
