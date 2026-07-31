package klein.core

import kotlin.test.Test

/**
 * Golden lowering spec for conditionals. There is no core `If` node — a surface
 * [klein.surface.IfThenElse] lowers to a [Match] on the condition, encoded as a `lit true` arm
 * carrying the then-branch plus a `_` default carrying the else-branch:
 *
 *     if c then a else b  ->  match c
 *                               lit true -> a
 *                               _ -> b
 *
 * The condition is always the inline `Match` scrutinee: an `if` never binds or re-references it, so
 * it is never hoisted to a slot whatever its shape. A binary condition prints infix, `(1 < 2)`.
 *
 * An else-less `if c then a` has type `Optional(a)` — the checker gives an absent else-branch
 * `optionalOf(then)` — so its missing branch lowers to a `null` default (`_ -> null`).
 *
 * A single trailing expression with no bindings lowers to just that expression (no `scope`); a
 * program with bindings wraps its trailing expression as the `result` of a `scope`. Inputs are
 * well-typed (the lowerer only receives checked programs).
 */
class IfThenElseLoweringTest {
    @Test
    fun simpleLiteralCondition() =
        assertLowersTo(
            """
            if true then 1 else 2
            """,
            """
            match true
              lit true -> 1
              _ -> 2
            """,
        )

    // A false literal condition still produces the same lit-true/default shape — the arm encoding
    // is on the condition's value, not on which branch the parser thinks is "taken".
    @Test
    fun falseLiteralCondition() =
        assertLowersTo(
            """
            if false then 1 else 2
            """,
            """
            match false
              lit true -> 1
              _ -> 2
            """,
        )

    // Condition from a comparison: the PrimApp is the inline scrutinee — an `if` never re-references
    // its condition, so there is no hoist. The binary prim prints infix as `(1 < 2)`.
    @Test
    fun conditionFromComparison() =
        assertLowersTo(
            """
            if 1 < 2 then 10 else 20
            """,
            """
            match (1 < 2)
              lit true -> 10
              _ -> 20
            """,
        )

    // Computed condition over a bound variable: `n` fills bind slot #0 of the program scope and is
    // referenced both in the condition and the then-branch as `n[0;0]`.
    @Test
    fun conditionAndBranchOverBoundVariable() =
        assertLowersTo(
            """
            n = 5
            if n < 2 then n else 0
            """,
            """
            scope
              bind n#0 = 5
              match (n[0;0] < 2)
                lit true -> n[1;0]
                _ -> 0
            """,
        )

    // String branches carry through unchanged as the arm bodies.
    @Test
    fun stringBranches() =
        assertLowersTo(
            """
            if 1 < 2 then "yes" else "no"
            """,
            """
            match (1 < 2)
              lit true -> "yes"
              _ -> "no"
            """,
        )

    // Nested `if` in the then-branch: the inner conditional lowers to its own Match, printed
    // inline after the `lit true ->` of the outer arm.
    @Test
    fun nestedIfInThenBranch() =
        assertLowersTo(
            """
            if true then if false then 1 else 2 else 3
            """,
            """
            match true
              lit true -> match false
                lit true -> 1
                _ -> 2
              _ -> 3
            """,
        )

    // Nested `if` in the else-branch: the inner Match becomes the body of the outer `_` default.
    @Test
    fun nestedIfInElseBranch() =
        assertLowersTo(
            """
            if true then 1 else if false then 2 else 3
            """,
            """
            match true
              lit true -> 1
              _ -> match false
                lit true -> 2
                _ -> 3
            """,
        )

    // Else-less `if`: the missing else-branch lowers to a `null` default.
    @Test
    fun elseLessIf() =
        assertLowersTo(
            """
            if true then 1
            """,
            """
            match true
              lit true -> 1
              _ -> null
            """,
        )

    // Else-less `if` with a computed condition over a bound variable — exercises the scrutinee slot
    // ref and the null default together.
    @Test
    fun elseLessIfOverBoundVariable() =
        assertLowersTo(
            """
            n = 5
            if n < 2 then n
            """,
            """
            scope
              bind n#0 = 5
              match (n[0;0] < 2)
                lit true -> n[1;0]
                _ -> null
            """,
        )
}
