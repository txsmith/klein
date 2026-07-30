package klein.core

import kotlin.test.Test

/**
 * Golden lowering spec for the literal surface nodes (IntLiteral, DoubleLiteral, StringLiteral,
 * BoolLiteral, NullLiteral). Mapping: Int/Double -> Constant.CNum (IEEE double), String -> CStr,
 * Bool -> CBool, Null -> CNull; types erased. A program that is a single trailing expression with
 * no bindings and no run-statements lowers to just that expression, so each bare literal lowers to
 * itself with no `scope` wrapper.
 */
class LiteralLoweringTest {
    @Test
    fun intLiteral() =
        assertLowersTo(
            "42",
            "42",
        )

    @Test
    fun intLiteral_zero() =
        assertLowersTo(
            "0",
            "0",
        )

    @Test
    fun intLiteral_large() =
        assertLowersTo(
            "9999999999",
            "9999999999",
        )

    @Test
    fun doubleLiteral_fractional() =
        assertLowersTo(
            "3.5",
            "3.5",
        )

    // A DoubleLiteral with no fractional part lowers to CNum(1.0); the printer collapses integral
    // doubles, so it renders as `1` — Int and Double share the CNum representation.
    @Test
    fun doubleLiteral_integralCollapsesToInt() =
        assertLowersTo(
            "1.0",
            "1",
        )

    @Test
    fun stringLiteral() =
        assertLowersTo(
            "\"hello world\"",
            "\"hello world\"",
        )

    @Test
    fun stringLiteral_empty() =
        assertLowersTo(
            "\"\"",
            "\"\"",
        )

    @Test
    fun boolLiteral_true() =
        assertLowersTo(
            "true",
            "true",
        )

    @Test
    fun boolLiteral_false() =
        assertLowersTo(
            "false",
            "false",
        )

    @Test
    fun nullLiteral() =
        assertLowersTo(
            "null",
            "null",
        )

    // A literal in a top-level binding fills slot #0 (bind-ordinal), so the program sequences a bind
    // and a result — the one case here that keeps a `scope` wrapper. The trailing reference reads the
    // binding back as Var(depth 0, slot 0), printed `x[0;0]`.
    @Test
    fun literalInBinding() =
        assertLowersTo(
            """
            x = 42
            x
            """,
            """
            scope
              bind x#0 = 42
              x[0;0]
            """,
        )
}
