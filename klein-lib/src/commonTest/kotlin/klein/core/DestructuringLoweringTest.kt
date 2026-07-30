package klein.core

import kotlin.test.Test

/**
 * Golden lowering spec for destructuring bindings (surface [klein.surface.PatternVal]).
 *
 * A pattern binding is **irrefutable** (checker-enforced), so it lowers with NO [Match] — there is
 * no tag test and no branching, just field projection. Each bound name becomes its own `Bind`, as a
 * sibling `val` taking the next slot after the right-hand side, in pattern order:
 *   - `{ name } = person`        -> `bind name = person.name`
 *   - rename `{ name = n }`      -> `bind n = person.name` (project the key, bind the new name)
 *   - constructor `Person { name } = e` -> `bind name = e.name` (single-constructor => no tag test)
 *   - whole-value `Circle c = c0`-> `bind c = c0` (alias; the nominal type is erased)
 *
 * The right-hand side resolves outward (never to the names being bound). When the pattern extracts
 * two or more fields, the RHS is bound to a temp (`_rhs`) so it is evaluated once; a single-field
 * pattern (or a whole-value binder) projects the RHS directly.
 */
class DestructuringLoweringTest {
    // Single field, trivial RHS: one `Bind` projecting `person.name`. `person` fills slot 0, the
    // bound `name` slot 1.
    @Test
    fun singleFieldRecordDestructure() =
        assertLowersTo(
            """
            person = { name = "alice" }
            { name } = person
            name
            """,
            """
            scope
              bind person#0 = {name: "alice"}
              bind name#1 = person[0;0].name
              name[0;1]
            """,
        )

    // Multiple fields: the RHS is bound to a temp `_rhs` (evaluated once), then each field projects
    // from it — one `Bind` per field, in pattern order. The temp is emitted even though `person` is
    // already a slot; skipping it for a trivial RHS is a deferred optimization.
    @Test
    fun multiFieldRecordDestructure() =
        assertLowersTo(
            """
            person = { name = "alice", age = 30 }
            { name, age } = person
            age
            """,
            """
            scope
              bind person#0 = {name: "alice", age: 30}
              bind _rhs#1 = person[0;0]
              bind name#2 = _rhs[0;1].name
              bind age#3 = _rhs[0;1].age
              age[0;3]
            """,
        )

    // Rename `{ name = n }`: bind the new name `n`, project the field KEY `name`.
    @Test
    fun renamedField() =
        assertLowersTo(
            """
            person = { name = "alice" }
            { name = n } = person
            n
            """,
            """
            scope
              bind person#0 = {name: "alice"}
              bind n#1 = person[0;0].name
              n[0;1]
            """,
        )

    // Constructor destructure on a single-constructor type: irrefutable, so it is identical to the
    // record form — a plain FieldGet, no tag test. (Construction stays an Apply of the ctor lambda;
    // folding to MakeData is optimizer territory.)
    @Test
    fun constructorDestructure() =
        assertLowersTo(
            """
            type Person = Person { name: String }
            someone = Person("alice")
            Person { name } = someone
            name
            """,
            """
            scope
              bind Person#0 = fun Person/1 -> Person{name: name[0;0]}
              bind someone#1 = Person[0;0]("alice")
              bind name#2 = someone[0;1].name
              name[0;2]
            """,
        )

    // Whole-value constructor binder `Circle c = c0`: `c` binds the whole value, so it is just an
    // alias bind of the RHS (the nominal `Circle` type is erased).
    @Test
    fun wholeValueConstructorBinder() =
        assertLowersTo(
            """
            type Circle = Circle { radius: Num }
            c0 = Circle(1)
            Circle c = c0
            c
            """,
            """
            scope
              bind Circle#0 = fun Circle/1 -> Circle{radius: radius[0;0]}
              bind c0#1 = Circle[0;0](1)
              bind c#2 = c0[0;1]
              c[0;2]
            """,
        )

    // A computed RHS with multiple fields: the same temp-first shape — bound once to `_rhs`, then
    // each field projects from it.
    @Test
    fun multiFieldComputedRhs() =
        assertLowersTo(
            """
            fun bounds(): { min: Num, max: Num } = { min = 1, max = 9 }
            { min = lo, max = hi } = bounds()
            hi - lo
            """,
            """
            scope
              bind bounds#0 = fun bounds/0 -> {min: 1, max: 9}
              bind _rhs#1 = bounds[0;0]()
              bind lo#2 = _rhs[0;1].min
              bind hi#3 = _rhs[0;1].max
              (hi[0;3] - lo[0;2])
            """,
        )

    // `{ name = _ }` requires the field to exist (a static check) but binds nothing — the RHS still
    // evaluates (here a call), as a `run`.
    @Test
    fun testOnlyFieldRunsRhsBindsNothing() =
        assertLowersTo(
            """
            fun p(): { name: String } = { name = "alice" }
            { name = _ } = p()
            0
            """,
            """
            scope
              bind p#0 = fun p/0 -> {name: "alice"}
              run p[0;0]()
              0
            """,
        )

    // Binder and destructure together: bind the whole value to `c`, then project each field FROM
    // the binder (which is the once-evaluated receiver, so no separate `_rhs` temp).
    @Test
    fun binderAndDestructureTogether() =
        assertLowersTo(
            """
            type Circle = Circle { radius: Num }
            c0 = Circle(1)
            Circle c { radius } = c0
            radius
            """,
            """
            scope
              bind Circle#0 = fun Circle/1 -> Circle{radius: radius[0;0]}
              bind c0#1 = Circle[0;0](1)
              bind c#2 = c0[0;1]
              bind radius#3 = c[0;2].radius
              radius[0;3]
            """,
        )

    // Naming a structural record while destructuring: `r` binds the whole record and the fields
    // project from it. (Lowercase `r` is a binder, not a tag.)
    @Test
    fun namedRecordDestructure() =
        assertLowersTo(
            """
            person = { name = "alice" }
            r { name } = person
            name
            """,
            """
            scope
              bind person#0 = {name: "alice"}
              bind r#1 = person[0;0]
              bind name#2 = r[0;1].name
              name[0;2]
            """,
        )
}
