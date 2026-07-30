package klein.interp

import klein.interp.Value.VNull
import klein.interp.Value.VNum
import kotlin.test.Test

class RecordEvalTest {
    @Test
    fun fieldAccess() =
        assertEvaluatesTo(
            VNum(3.0),
            """
            p = { x = 1, y = 2 }
            p.x + p.y
            """,
        )

    @Test
    fun nestedRecords() =
        assertEvaluatesTo(
            VNum(7.0),
            """
            r = { inner = { value = 7 } }
            r.inner.value
            """,
        )

    @Test
    fun widthSubtypingPassesExtraFields() =
        assertEvaluatesTo(
            VNum(1.0),
            """
            fun getX(r: { x: Num }): Num = r.x
            getX({ x = 1, y = 2 })
            """,
        )

    @Test
    fun callThroughRecordField() =
        assertEvaluatesTo(
            VNum(42.0),
            """
            r = { f = |x: Num -> x + 1| }
            r.f(41)
            """,
        )

    @Test
    fun bindingAnnotationNarrowsStaticallyNotAtRuntime() =
        assertEvaluatesTo(
            VNum(1.0),
            """
            r: { x: Num } = { x = 1, y = 2 }
            r.x
            """,
        )

    @Test
    fun narrowingDoesNotStripFields() =
        assertEvaluatesTo(
            Value.VBool(false),
            """
            fun f(x: { name: String, age: Num }): { name: String, age: Num } = x
            f({ name = "a", age = 1, z = 9 }) == { name = "a", age = 1 }
            """,
        )

    @Test
    fun depthWidthSubtyping() =
        assertEvaluatesTo(
            VNum(1.0),
            """
            fun f(r: { p: { a: Num } }): Num = r.p.a
            f({ p = { a = 1, b = 2 } })
            """,
        )

    @Test
    fun renamedFieldDestructuring() =
        assertEvaluatesTo(
            VNum(1.0),
            """
            p = { name = "a", age = 1 }
            { age = years } = p
            years
            """,
        )

    @Test
    fun namedRecordBinderBindsWholeAndField() =
        assertEvaluatesTo(
            VNum(1.0),
            """
            person = { name = "a", age = 1 }
            r { name } = person
            r.age
            """,
        )

    @Test
    fun destructuringInsideFunBody() =
        assertEvaluatesTo(
            VNum(42.0),
            """
            fun f(p: { a: Num }): Num =
                { a } = p
                a + 1
            f({ a = 41 })
            """,
        )

    @Test
    fun nominalBuriedInStructural() =
        assertEvaluatesTo(
            Value.VStr("important"),
            """
            type Tag = Tag { label: String }
            fun getLabel(x: { meta: { tag: Tag } }): String = x.meta.tag.label
            getLabel({ meta = { tag = Tag("important") } })
            """,
        )

    @Test
    fun uniformFieldAccessAcrossTaggedAndUntagged() =
        assertEvaluatesTo(
            Value.VStr("x"),
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            v = if false then Dog("Fido", "Labrador") else { name = "x", weight = 5 }
            v.name
            """,
        )

    @Test
    fun recordDestructuring() =
        assertEvaluatesTo(
            VNum(7.0),
            """
            { x } = { x = 7, y = 1 }
            x
            """,
        )

    @Test
    fun multiFieldDestructuringSharesOneEvaluation() =
        assertEvaluatesTo(
            VNum(3.0),
            """
            { x, y } = { x = 1, y = 2 }
            x + y
            """,
        )
}
