package klein.core

import kotlin.test.Test

/**
 * Golden lowering spec for `match`: surface [klein.surface.Match] -> core [Match].
 *
 * Arm-kind mapping (surface [klein.surface.Pattern] -> core [Match.Arm]):
 *   - literal pattern (`1`, `"a"`, `true`, `null`) -> [Match.LitArm], printed `lit <const>`
 *   - constructor pattern (`Circle { radius }`)     -> [Match.ConstructorArm], printed `Tag{f1, f2}`
 *     where `fields` are the field keys extracted into the arm's own slots (position = slot)
 *   - wildcard / variable pattern (`_`, `x`)         -> [Match.Default], printed `_`
 * Guards are preserved on the arm (` if <expr>`). Vars read back as `name[depth;slot]`, and a
 * binary prim prints infix (`(radius[0;0] > 10)`).
 *
 * Arm scoping: the machine pushes a fresh binding scope for EVERY arm (a constructor arm's extracted
 * fields; an empty scope for `lit`/`_`), so every arm body and guard runs one scope deeper than the
 * match. A constructor arm's own fields are slots 0, 1, … at depth 0 (`radius[0;0]`, a second field
 * `[0;1]`), but any reference OUT to the scrutinee or an enclosing binding is one deeper — the
 * scrutinee is `s[0;0]` at the match site and `s[1;0]` from inside an arm.
 *
 * A renamed field `{ value = v }` extracts the field KEY (`fields = [value]`, so the pattern prints
 * `Ok{value}`); the binder name `v` survives only as Var metadata at the arm's slot 0 (`v[0;0]`). A
 * variable pattern lowers to [Match.Default] (no binder), so a body reference to the pattern name
 * lowers to the scrutinee ref one scope deeper (`x -> x` becomes `_ -> s[1;0]`) — which is why a
 * non-trivial scrutinee is hoisted to a slot when a whole-value binder re-references it.
 *
 * Every source is well-typed — the lowerer only receives checked programs. Each case wraps the match
 * in `fun f(...) = match ...`, so the whole program lowers to one `scope`: `f` fills a bind slot and
 * the trailing `f` reads it back. Constructor-pattern cases add the minimal `type` declaration that
 * makes them check; the type is erased but its constructors survive as ordinary scope binds ahead of
 * `f` (an eta-expanded lambda over MakeData with fields, bare MakeData when nullary), pushing `f` to
 * slot 2 (`f[0;2]`).
 */
class MatchLoweringTest {
    @Test
    fun numLiteralArmsWithWildcardDefault() =
        assertLowersTo(
            """
            fun f(s: Num) = match s
              1 -> 10
              2 -> 20
              _ -> 0
            f
            """,
            """
            scope
              bind f#0 = fun f/1 -> match s[0;0]
                lit 1 -> 10
                lit 2 -> 20
                _ -> 0
              f[0;0]
            """,
        )

    @Test
    fun stringLiteralArms() =
        assertLowersTo(
            """
            fun f(s: String) = match s
              "a" -> 1
              "b" -> 2
              _ -> 0
            f
            """,
            """
            scope
              bind f#0 = fun f/1 -> match s[0;0]
                lit "a" -> 1
                lit "b" -> 2
                _ -> 0
              f[0;0]
            """,
        )

    @Test
    fun boolLiteralArmsAreExhaustiveWithoutADefault() =
        assertLowersTo(
            """
            fun f(b: Bool) = match b
              true -> 1
              false -> 0
            f
            """,
            """
            scope
              bind f#0 = fun f/1 -> match b[0;0]
                lit true -> 1
                lit false -> 0
              f[0;0]
            """,
        )

    @Test
    fun constructorArmExtractsAFieldUsedInTheBody() =
        assertLowersTo(
            """
            type Shape = Circle { radius: Num } | Tri { base: Num, height: Num }
            fun f(s: Shape) = match s
              Circle { radius } -> radius
              _ -> 0
            f
            """,
            """
            scope
              bind Circle#0 = fun Circle/1 -> Circle{radius: radius[0;0]}
              bind Tri#1 = fun Tri/2 -> Tri{base: base[0;0], height: height[0;1]}
              bind f#2 = fun f/1 -> match s[0;0]
                Circle{radius} -> radius[0;0]
                _ -> 0
              f[0;2]
            """,
        )

    @Test
    fun constructorArmExtractsMultipleFields() =
        assertLowersTo(
            """
            type Shape = Circle { radius: Num } | Tri { base: Num, height: Num }
            fun f(s: Shape) = match s
              Tri { base, height } -> base + height
              _ -> 0
            f
            """,
            """
            scope
              bind Circle#0 = fun Circle/1 -> Circle{radius: radius[0;0]}
              bind Tri#1 = fun Tri/2 -> Tri{base: base[0;0], height: height[0;1]}
              bind f#2 = fun f/1 -> match s[0;0]
                Tri{base, height} -> (base[0;0] + height[0;1])
                _ -> 0
              f[0;2]
            """,
        )

    // A guard is preserved on the arm; here it reads the arm's own extracted field (slot 0).
    @Test
    fun guardedThenUnguardedArmOnTheSameConstructor() =
        assertLowersTo(
            """
            type Shape = Circle { radius: Num } | Tri { base: Num, height: Num }
            fun f(s: Shape) = match s
              Circle { radius } if radius > 10 -> 1
              Circle { radius } -> 2
              _ -> 0
            f
            """,
            """
            scope
              bind Circle#0 = fun Circle/1 -> Circle{radius: radius[0;0]}
              bind Tri#1 = fun Tri/2 -> Tri{base: base[0;0], height: height[0;1]}
              bind f#2 = fun f/1 -> match s[0;0]
                Circle{radius} if (radius[0;0] > 10) -> 1
                Circle{radius} -> 2
                _ -> 0
              f[0;2]
            """,
        )

    // A literal arm binds nothing, so its guard sees the scrutinee at the enclosing depth.
    @Test
    fun literalArmWithAGuardOnTheScrutinee() =
        assertLowersTo(
            """
            fun f(s: Num) = match s
              1 if s > 0 -> 100
              _ -> 0
            f
            """,
            """
            scope
              bind f#0 = fun f/1 -> match s[0;0]
                lit 1 if (s[1;0] > 0) -> 100
                _ -> 0
              f[0;0]
            """,
        )

    @Test
    fun variablePatternLowersToADefaultAliasingTheScrutinee() =
        assertLowersTo(
            """
            fun f(s: Num) = match s
              0 -> 100
              x -> x
            f
            """,
            """
            scope
              bind f#0 = fun f/1 -> match s[0;0]
                lit 0 -> 100
                _ -> s[1;0]
              f[0;0]
            """,
        )

    @Test
    fun renamedFieldExtractsTheKeyAndBindsTheNewName() =
        assertLowersTo(
            """
            type Result = Ok { value: Num } | Err
            fun f(s: Result) = match s
              Ok { value = v } -> v
              _ -> 0
            f
            """,
            """
            scope
              bind Ok#0 = fun Ok/1 -> Ok{value: value[0;0]}
              bind Err#1 = Err{}
              bind f#2 = fun f/1 -> match s[0;0]
                Ok{value} -> v[0;0]
                _ -> 0
              f[0;2]
            """,
        )

    // A tagless record pattern lowers to a null-tag [Match.DataArm] (prints `{fields}`) — the same
    // node as a constructor arm, just without a tag. It extracts fields into arm slots like any data
    // arm, and the machine refutes it only against null, so the `null` arm still discriminates.
    @Test
    fun recordArmExtractsFields() =
        assertLowersTo(
            """
            fun f(p: { name: String }?) = match p
              null -> "none"
              { name } -> name
            f
            """,
            """
            scope
              bind f#0 = fun f/1 -> match p[0;0]
                lit null -> "none"
                {name} -> name[0;0]
              f[0;0]
            """,
        )

    // A named record binder `r { name }`: `name` is an arm slot (depth 0), while `r` aliases the whole
    // scrutinee one scope deeper (`p[1;0]`), exactly like a constructor whole-value binder.
    @Test
    fun namedRecordBinderAliasesTheScrutinee() =
        assertLowersTo(
            """
            fun f(p: { name: String, age: Num }) = match p
              r { name } -> r.age
            f
            """,
            """
            scope
              bind f#0 = fun f/1 -> match p[0;0]
                {name} -> p[1;0].age
              f[0;0]
            """,
        )

    // The null literal pattern lowers to a LitArm on CNull; the following variable arm is Default.
    @Test
    fun nullLiteralArmThenVariableResidual() =
        assertLowersTo(
            """
            fun f(s: Num?) = match s
              null -> 0
              x -> x
            f
            """,
            """
            scope
              bind f#0 = fun f/1 -> match s[0;0]
                lit null -> 0
                _ -> s[1;0]
              f[0;0]
            """,
        )

    // Scrutinee hoisting — the truth table for "hoist iff non-trivial AND a whole-value binder".
    // A synthesized `_scrut` bind evaluates the scrutinee once; the match wraps in a fresh `scope`,
    // and the variable pattern's body aliases the `_scrut` slot.

    // Non-trivial scrutinee + variable pattern (whole-value binder): HOIST.
    @Test
    fun nonTrivialScrutineeWithVariablePatternHoists() =
        assertLowersTo(
            """
            a = 3
            match a + 1
              0 -> 100
              x -> x
            """,
            """
            scope
              bind a#0 = 3
              scope
                bind _scrut#0 = (a[1;0] + 1)
                match _scrut[0;0]
                  lit 0 -> 100
                  _ -> _scrut[1;0]
            """,
        )

    // Non-trivial scrutinee but NO whole-value binder (lit + wildcard): NO hoist — the machine
    // evaluates the scrutinee once for dispatch, so it stays inline.
    @Test
    fun nonTrivialScrutineeWithoutAWholeValueBinderIsInline() =
        assertLowersTo(
            """
            a = 3
            match a + 1
              0 -> 100
              _ -> 0
            """,
            """
            scope
              bind a#0 = 3
              match (a[0;0] + 1)
                lit 0 -> 100
                _ -> 0
            """,
        )

    // Trivial scrutinee (already a slot) + variable pattern: NO hoist — the binder aliases the
    // existing slot directly.
    @Test
    fun trivialScrutineeWithVariablePatternDoesNotHoist() =
        assertLowersTo(
            """
            a = 3
            match a
              0 -> 100
              x -> x
            """,
            """
            scope
              bind a#0 = 3
              match a[0;0]
                lit 0 -> 100
                _ -> a[1;0]
            """,
        )
}
