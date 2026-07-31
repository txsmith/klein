package klein.core

import kotlin.test.Test

/**
 * Golden lowering spec for operators. Arithmetic/comparison [klein.surface.BinaryOp] and both
 * [klein.surface.UnaryOp] forms (Neg, Not) map to [PrimApp]; the short-circuit `and`/`or` are the
 * exception — they lower to [Match], not a prim. Binary prims print infix as `(a + b)`; unary prims
 * stay word-prefix as `(neg x)` / `(not b)`. Operator precedence is the parser's job; these tests
 * just supply source and assert the nesting the parser produced. A program that is a single trailing
 * expression lowers to that expression directly; only bindings introduce a `scope`.
 *
 * Inputs are well-typed (the lowerer only receives checked programs).
 */
class OperatorLoweringTest {
    // No bindings, so the trailing expression is the whole program: no `scope` wrapper.
    @Test
    fun add() =
        assertLowersTo(
            """
            1 + 2
            """,
            """
            (1 + 2)
            """,
        )

    @Test
    fun subtract() =
        assertLowersTo(
            """
            5 - 3
            """,
            """
            (5 - 3)
            """,
        )

    // Precedence is the parser's: `*` binds tighter, so the tree is (1 + (2 * 3)), not ((1 + 2) * 3).
    @Test
    fun chainedArithmeticNestsByPrecedence() =
        assertLowersTo(
            """
            1 + 2 * 3
            """,
            """
            (1 + (2 * 3))
            """,
        )

    @Test
    fun lessThan() =
        assertLowersTo(
            """
            1 < 2
            """,
            """
            (1 < 2)
            """,
        )

    @Test
    fun equals() =
        assertLowersTo(
            """
            1 == 2
            """,
            """
            (1 == 2)
            """,
        )

    // Unary neg -> PrimApp(Neg). Bind the operand first so it lowers to a slot ref, exercising both
    // the prim and the depth/slot addressing; the binding is what introduces the `scope`.
    @Test
    fun negateVariable() =
        assertLowersTo(
            """
            x = 1
            -x
            """,
            """
            scope
              bind x#0 = 1
              (neg x[0;0])
            """,
        )

    // Unary not -> PrimApp(Not). Operand bound to a Bool first so `not` is well-typed.
    @Test
    fun notVariable() =
        assertLowersTo(
            """
            b = true
            not b
            """,
            """
            scope
              bind b#0 = true
              (not b[0;0])
            """,
        )

    // `a and b` desugars to `match a { lit true -> b; _ -> false }`. Like `if`, there is no
    // whole-value binder, so the condition is the inline scrutinee (no hoist). No bindings, so the
    // Match is the whole program — no `scope` wrapper.
    @Test
    fun andShortCircuitsToMatch() =
        assertLowersTo(
            """
            false and true
            """,
            """
            match false
              lit true -> true
              _ -> false
            """,
        )

    // `a or b` desugars to `match a { lit true -> true; _ -> b }`.
    @Test
    fun orShortCircuitsToMatch() =
        assertLowersTo(
            """
            true or false
            """,
            """
            match true
              lit true -> true
              _ -> false
            """,
        )

    // The right operand is the short-circuit arm's body, which runs one scope deeper than the match,
    // so a reference to an enclosing binding resolves at depth+1 (`b[1;0]`) — the condition, at the
    // match site, stays `b[0;0]`.
    @Test
    fun andRightOperandOverBoundVariableIsOneScopeDeeper() =
        assertLowersTo(
            """
            b = true
            b and b
            """,
            """
            scope
              bind b#0 = true
              match b[0;0]
                lit true -> b[1;0]
                _ -> false
            """,
        )
}
