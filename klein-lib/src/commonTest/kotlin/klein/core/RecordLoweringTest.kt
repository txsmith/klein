package klein.core

import kotlin.test.Test

/**
 * Golden lowering spec for records and field access:
 *   - [klein.surface.RecordLiteral] -> [MakeData] with `tag = null` (the null tag is what
 *     survives erasure of structural-vs-nominal; fields keep source order), printed `{f: A, ...}`.
 *   - [klein.surface.FieldAccess] `t.f` -> [FieldGet], printed `TARGET.f`.
 *   - [klein.surface.SafeFieldAccess] `t?.f` -> `match t { null -> null; _ -> t.f }`; a non-trivial
 *     receiver is hoisted to a slot so it is evaluated once.
 *
 * `=`-bound vals occupy bind slots in declaration order and read back as `name[depth;slot]`; a
 * program that has any bind (or run) wraps in one EnterScope, printed `scope`, whose trailing
 * expression is the `result` line. A program that is a single trailing expression with no binds
 * or runs lowers to just that expression, with no `scope` wrapper.
 *
 * Inputs are well-typed (the lowerer only receives checked programs).
 */
class RecordLoweringTest {
    // An empty record literal lowers to a tagless MakeData with no fields -> `{}`. No binds, so no
    // scope wrapper: the record is the whole result.
    @Test
    fun emptyRecord() =
        assertLowersTo(
            """
            {}
            """,
            """
            {}
            """,
        )

    // Two fields, tag null, source order preserved. A bindless trailing expression, so no scope.
    @Test
    fun recordLiteralTwoFields() =
        assertLowersTo(
            """
            { x = 1, y = 2 }
            """,
            """
            {x: 1, y: 2}
            """,
        )

    // Heterogeneous field values; each value is lowered in place.
    @Test
    fun recordLiteralMixedFieldTypes() =
        assertLowersTo(
            """
            { name = "alice", active = true }
            """,
            """
            {name: "alice", active: true}
            """,
        )

    // A computed field value lowers to whatever that expression lowers to (here an infix PrimApp),
    // nested inline inside the record.
    @Test
    fun recordLiteralComputedFieldValue() =
        assertLowersTo(
            """
            { sum = 1 + 2 }
            """,
            """
            {sum: (1 + 2)}
            """,
        )

    // FieldGet directly on a record literal (no intervening bind), so the whole program is the one
    // trailing expression and needs no scope.
    @Test
    fun fieldAccessOnRecordLiteral() =
        assertLowersTo(
            """
            { x = 1 }.x
            """,
            """
            {x: 1}.x
            """,
        )

    // FieldGet on a bound var: the val fills slot #0 and the target reads it back as `p[0;0]`. The
    // bind means the program keeps its scope wrapper.
    @Test
    fun fieldAccessOnBoundVar() =
        assertLowersTo(
            """
            p = { x = 1 }
            p.x
            """,
            """
            scope
              bind p#0 = {x: 1}
              p[0;0].x
            """,
        )

    // Nested FieldGet(FieldGet(p, x), y) prints left-to-right -> `p[0;0].x.y`.
    @Test
    fun nestedFieldAccess() =
        assertLowersTo(
            """
            p = { x = { y = 2 } }
            p.x.y
            """,
            """
            scope
              bind p#0 = {x: {y: 2}}
              p[0;0].x.y
            """,
        )

    // Field access from inside a lambda resolves the receiver one scope up: depth 1.
    @Test
    fun fieldAccessAcrossLambdaBoundary() =
        assertLowersTo(
            """
            p = { x = 1 }
            |_ -> p.x|
            """,
            """
            scope
              bind p#0 = {x: 1}
              fun/1 -> p[1;0].x
            """,
        )

    // Safe field access desugars to `match p { null -> null; _ -> p.x }`. The receiver is a trivial
    // var, so the scrutinee is `p[0;0]` with no hoist, and referencing it in both the scrutinee and
    // the default body is evaluate-once because it is already a slot.
    @Test
    fun safeFieldAccessDesugarsToNullMatch() =
        assertLowersTo(
            """
            p = { x = 1 }
            p?.x
            """,
            """
            scope
              bind p#0 = {x: 1}
              match p[0;0]
                lit null -> null
                _ -> p[1;0].x
            """,
        )
}
