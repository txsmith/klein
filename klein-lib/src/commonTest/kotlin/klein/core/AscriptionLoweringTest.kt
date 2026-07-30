package klein.core

import kotlin.test.Test

/**
 * Golden lowering spec for type ascription: surface [klein.surface.Ascription] `(e : T)` is
 * ERASED. There is no core node for ascription — `(e : T)` lowers to exactly `lower(e)`, so the
 * type annotation leaves no trace in the IR. Each test's golden is therefore identical to what
 * the un-ascribed program would produce; the paired comment names that bare equivalent.
 *
 * Types, type definitions, and ascriptions are all erased at lowering. A program that is a single
 * trailing expression with no bindings lowers to just that expression — there is no `scope`
 * wrapper. Only a program with top-level bindings wraps in one `scope` (EnterScope): those bindings
 * become `bind <name>#<slot>` lines and the trailing expression becomes the `result` line.
 */
class AscriptionLoweringTest {
    // `(1 : Num)` erases to `1` — a single bare literal, no `scope` wrapper.
    @Test
    fun ascription_onLiteral_erasesToBareLiteral() =
        assertLowersTo(
            "(1 : Num)",
            "1",
        )

    // `("hello" : String)` erases to `"hello"` — same golden as the un-ascribed string.
    @Test
    fun ascription_onStringLiteral_erases() =
        assertLowersTo(
            """("hello" : String)""",
            """"hello"""",
        )

    // `(x : Num)` erases to `x` — the var reference is unchanged: `x[0;0]`. The `x = 1` binding
    // keeps the `scope` wrapper.
    @Test
    fun ascription_onVar_erasesToBareVar() =
        assertLowersTo(
            """
            x = 1
            (x : Num)
            """,
            """
            scope
              bind x#0 = 1
              x[0;0]
            """,
        )

    // `(|x -> x| : Num -> Num)` erases to `|x -> x|` — the lambda lowers as if unannotated, and
    // with no bindings there is no `scope` wrapper.
    @Test
    fun ascription_onLambda_erasesToBareLambda() =
        assertLowersTo(
            "(|x -> x| : Num -> Num)",
            "fun/1 -> x[0;0]",
        )

    // Ascription inside a binding value: `x = (1 : Num)` lowers the bind body to just `1`; the
    // whole program is identical to `x = 1\nx`, so the binding keeps the `scope` wrapper.
    @Test
    fun ascription_insideBindingValue_erases() =
        assertLowersTo(
            """
            x = (1 : Num)
            x
            """,
            """
            scope
              bind x#0 = 1
              x[0;0]
            """,
        )

    // Nested inside a PrimApp: only the left operand is ascribed, and its erasure leaves the
    // surrounding `(1 + 2)` structure intact — golden is identical to `1 + 2`, no `scope` wrapper.
    @Test
    fun ascription_nestedInPrimApp_leavesStructureIntact() =
        assertLowersTo(
            "(1 : Num) + 2",
            "(1 + 2)",
        )

    // Both operands ascribed — every ascription erases independently, still yielding `(1 + 2)`.
    @Test
    fun ascription_onBothPrimOperands_allErase() =
        assertLowersTo(
            "(1 : Num) + (2 : Num)",
            "(1 + 2)",
        )

    // Ascription wrapping a whole compound expression: `((1 + 2) : Num)` erases the outer
    // annotation and preserves the inner prim tree — golden is identical to `1 + 2`.
    @Test
    fun ascription_wrappingCompoundExpr_erasesOuterKeepsInner() =
        assertLowersTo(
            "((1 + 2) : Num)",
            "(1 + 2)",
        )
}
