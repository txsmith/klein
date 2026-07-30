package klein.core

import kotlin.test.Test

/**
 * Golden lowering spec for function application (surface [klein.surface.Apply] -> core [Apply]).
 * Call syntax lowers uniformly: `callee(args...)` becomes `Apply(callee, args)` with arguments
 * lowered left-to-right. Types are erased. The printer renders application in call syntax as
 * `callee(arg1, arg2)` (a lambda callee is parenthesized); a zero-arg call prints `callee()`.
 * `=`-bound functions occupy bind slots in declaration order and read back as `name[depth;slot]`;
 * the whole program is one EnterScope whose `result` is the trailing expression.
 *
 * Inputs are well-typed (the lowerer only receives checked programs).
 */
class ApplyLoweringTest {
    // Single-arg call of a `=`-bound function: the binding fills slot #0 and the trailing call
    // reads it back as f[0;0], applied to the literal argument.
    @Test
    fun singleArgCall() =
        assertLowersTo(
            """
            f = |x -> x|
            f(1)
            """,
            """
            scope
              bind f#0 = fun f/1 -> x[0;0]
              f[0;0](1)
            """,
        )

    // A zero-arg call carries no argument operands, so it prints as the callee with empty parens.
    @Test
    fun zeroArgCall() =
        assertLowersTo(
            """
            f = |42|
            f()
            """,
            """
            scope
              bind f#0 = fun f/0 -> 42
              f[0;0]()
            """,
        )

    // Multiple arguments render comma-separated in source order.
    @Test
    fun multiArgCall() =
        assertLowersTo(
            """
            f = |x, y -> x|
            f(1, 2)
            """,
            """
            scope
              bind f#0 = fun f/2 -> x[0;0]
              f[0;0](1, 2)
            """,
        )

    // Nested call f(g(1)): f is bound first (slot 0), g second (slot 1); the inner call becomes the
    // sole argument of the outer, so both callees appear at their own slots.
    @Test
    fun nestedCall() =
        assertLowersTo(
            """
            f = |x -> x|
            g = |x -> x|
            f(g(1))
            """,
            """
            scope
              bind f#0 = fun f/1 -> x[0;0]
              bind g#1 = fun g/1 -> x[0;0]
              f[0;0](g[0;1](1))
            """,
        )

    // Immediate application of a lambda literal: the anonymous lambda has no name, so it prints
    // `fun/1 -> ...`, and as a callee it is parenthesized to disambiguate from its own body.
    @Test
    fun immediateLambdaApplication() =
        assertLowersTo(
            """
            |x -> x|(1)
            """,
            """
            (fun/1 -> x[0;0])(1)
            """,
        )

    // An argument that is itself an expression lowers in place: `1 + 2` becomes the infix prim
    // application `(1 + 2)` sitting in the argument position.
    @Test
    fun argIsExpression() =
        assertLowersTo(
            """
            f = |x -> x|
            f(1 + 2)
            """,
            """
            scope
              bind f#0 = fun f/1 -> x[0;0]
              f[0;0]((1 + 2))
            """,
        )

    // Curried application f(1)(2): f must RETURN a function for this to type-check, so f is
    // curried (x -> y -> x). The outer Apply's callee is the inner Apply f[0;0](1), left-nested.
    @Test
    fun curriedApplication() =
        assertLowersTo(
            """
            f = |x -> |y -> x||
            f(1)(2)
            """,
            """
            scope
              bind f#0 = fun f/1 -> fun/1 -> x[1;0]
              f[0;0](1)(2)
            """,
        )

    // Multiple compound arguments: each argument lowers independently, left-to-right. f is slot 0,
    // g is slot 1.
    @Test
    fun multipleCompoundArgs() =
        assertLowersTo(
            """
            f = |x, y -> x|
            g = |x -> x|
            f(g(1), g(2))
            """,
            """
            scope
              bind f#0 = fun f/2 -> x[0;0]
              bind g#1 = fun g/1 -> x[0;0]
              f[0;0](g[0;1](1), g[0;1](2))
            """,
        )
}
