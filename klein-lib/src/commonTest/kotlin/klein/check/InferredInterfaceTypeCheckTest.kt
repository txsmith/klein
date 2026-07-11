package klein.check

import klein.types.TypeError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The interface a sum type exposes: a field is readable through the sum only if every constructor
 * has it *and* its types cleanly join. A field that is missing from some constructor, or whose types
 * across constructors have no common join (`Num` vs `String`), is erased from the interface —
 * accessing it is a `MissingField` error either way. Printed type variables render without the tick.
 *
 * The sum type is forced with a binding annotation (`v: AB = A(…)`) rather than a holder constructor.
 */
class InferredInterfaceTypeCheckTest {
    private fun assertType(
        expected: String,
        src: String,
    ) {
        val r = infer(src)
        assertTrue(r.errors.isEmpty(), "unexpected errors: ${r.errors}")
        assertEquals(expected, klein.Type.print(r.type.toLegacy()))
    }

    private fun assertMissingField(
        field: String,
        src: String,
    ) {
        val errors = infer(src).errors
        assertTrue(
            errors.any { it is TypeError.MissingField && it.field == field },
            "expected MissingField('$field'), got: $errors",
        )
    }

    private val light = "type Light = Red { duration: Num } | Yellow { duration: Num } | Green { duration: Num }"

    // --- common fields are readable through the sum ---

    @Test
    fun commonField_sameType() =
        assertType(
            "Num",
            """
            $light
            x: Light = Red(10)
            x.duration
            """.trimIndent(),
        )

    @Test
    fun commonField_fromConditional() =
        assertType(
            "Num",
            """
            $light
            x: Light = if true then Red(10) else Green(20)
            x.duration
            """.trimIndent(),
        )

    @Test
    fun partialOverlap_commonFieldReadable() =
        assertType(
            "Num",
            """
            type AB = A { x: Num, y: String } | B { x: Num }
            v: AB = A(1, "hello")
            v.x
            """.trimIndent(),
        )

    @Test
    fun singleConstructor_fullRecordInterface() =
        assertType(
            "Num",
            """
            type Money = Money { value: Num }
            m: Money = Money(100)
            m.value
            """.trimIndent(),
        )

    @Test
    fun singleConstructor_allFieldsAccessible() =
        assertType(
            "{ a: Num, b: String }",
            """
            type Point = Point { x: Num, y: String }
            p: Point = Point(1, "hello")
            { a = p.x, b = p.y }
            """.trimIndent(),
        )

    @Test
    fun bareConstructors_typePreserved() =
        assertType(
            "MyBool",
            """
            type MyBool = True | False
            b: MyBool = if true then True else False
            b
            """.trimIndent(),
        )

    @Test
    fun multipleCommonFields_allReadable() =
        assertType(
            "{ a: Num, b: String }",
            """
            type ABC = A { x: Num, y: String, z: Bool } | B { x: Num, y: String } | C { x: Num, y: String, w: Num }
            v: ABC = A(1, "hi", true)
            { a = v.x, b = v.y }
            """.trimIndent(),
        )

    @Test
    fun genericSum_commonFieldWithTypeParam() =
        assertType(
            "Num",
            """
            type Container<'A> = Box { value: 'A } | Wrapper { value: 'A, label: String }
            c: Container<Num> = Box(42)
            c.value
            """.trimIndent(),
        )

    @Test
    fun nestedRecordCommonField_intersectsFields() =
        assertType(
            "{ x: Num }",
            """
            type AB = A { inner: { x: Num, y: String } } | B { inner: { x: Num } }
            v: AB = A({ x = 1, y = "hi" })
            v.inner
            """.trimIndent(),
        )

    @Test
    fun functionTypeCommonField_sameType() =
        assertType(
            "(Num) -> Num",
            """
            type Processor = Inc { f: Num -> Num } | Dec { f: Num -> Num }
            p: Processor = Inc(|x: Num -> x + 1|)
            p.f
            """.trimIndent(),
        )

    @Test
    fun optionalCommonField_preserved() =
        assertType(
            "Option<Num>",
            """
            type Option<'A> = Some { value: 'A } | None
            type AB = A { maybe: Option<Num> } | B { maybe: Option<Num> }
            v: AB = A(Some(42))
            v.maybe
            """.trimIndent(),
        )

    @Test
    fun specificConstructor_exposesAllItsFields() =
        assertType(
            "{ d: Num, i: Num }",
            """
            type Light = Red { duration: Num, intensity: Num } | Yellow { duration: Num } | Green { duration: Num, direction: String }
            red = Red(10, 100)
            { d = red.duration, i = red.intensity }
            """.trimIndent(),
        )

    // --- incompatible common fields are erased (no common join → MissingField) ---

    @Test
    fun incompatibleCommonField_primitive_erased() =
        assertMissingField(
            "x",
            """
            type AB = A { x: Num } | B { x: String }
            v: AB = A(42)
            v.x
            """.trimIndent(),
        )

    @Test
    fun incompatibleCommonField_functionReturn_erased() =
        assertMissingField(
            "f",
            """
            type Processor = ToNum { f: Num -> Num } | ToStr { f: Num -> String }
            p: Processor = ToNum(|x: Num -> x + 1|)
            p.f
            """.trimIndent(),
        )

    @Test
    fun incompatibleCommonField_genericArg_erased() =
        assertMissingField(
            "c",
            """
            type Container<'A> = Container { value: 'A }
            type Boxes = NumBox { c: Container<Num> } | StrBox { c: Container<String> }
            b: Boxes = NumBox(Container(42))
            b.c
            """.trimIndent(),
        )

    @Test
    fun incompatibleCommonField_nestedRecord_erased() =
        assertMissingField(
            "data",
            """
            type Nested = A { data: { inner: Num } } | B { data: { inner: String } }
            v: Nested = A({ inner = 42 })
            v.data
            """.trimIndent(),
        )

    @Test
    fun incompatibleCommonField_typeParamVsConcrete_erased() =
        // `x` is `Num` in Concrete but the abstract `'A` in Generic; the interface is built before
        // instantiation, so the two don't join and `x` is erased even for `Mixed<Num>`.
        assertMissingField(
            "x",
            """
            type Mixed<'A> = Concrete { x: Num } | Generic { x: 'A }
            v: Mixed<Num> = Concrete(42)
            v.x
            """.trimIndent(),
        )

    // --- non-common fields are not readable through the sum ---

    @Test
    fun nonCommonField_erased() =
        assertMissingField(
            "x",
            """
            type AB = A { x: Num } | B { y: String }
            v: AB = A(42)
            v.x
            """.trimIndent(),
        )

    @Test
    fun partialOverlap_nonCommonFieldErased() =
        assertMissingField(
            "y",
            """
            type AB = A { x: Num, y: String } | B { x: Num }
            v: AB = A(1, "hello")
            v.y
            """.trimIndent(),
        )

    @Test
    fun bareConstructor_hasNoFields() =
        assertMissingField(
            "value",
            """
            type MyBool = True | False
            b: MyBool = True
            b.value
            """.trimIndent(),
        )

    @Test
    fun multipleCommonFields_nonCommonErased() =
        assertMissingField(
            "z",
            """
            type ABC = A { x: Num, y: String, z: Bool } | B { x: Num, y: String } | C { x: Num, y: String, w: Num }
            v: ABC = A(1, "hi", true)
            v.z
            """.trimIndent(),
        )

    // --- generic functions over a sum's interface ---

    @Test
    fun genericFunction_accessesCommonField() =
        assertType(
            "Num",
            """
            type Container<'A> = Box { value: 'A } | Wrapper { value: 'A, label: String }
            fun getValue(c: Container<Num>) = c.value
            getValue(Box(42))
            """.trimIndent(),
        )

    @Test
    fun genericFunction_callsCommonFunctionField() =
        assertType(
            "Num",
            """
            type Transformer<'A, 'B> = MapT { apply: 'A -> 'B } | FilterT { apply: 'A -> 'B }
            fun run(t: Transformer<Num, Num>, x: Num) = t.apply(x)
            run(MapT(|x: Num -> x + 1|), 10)
            """.trimIndent(),
        )

    @Test
    fun genericFunction_twoInstantiations() =
        assertType(
            "{ a: Num, b: String }",
            """
            type Tagged<'A> = Label { value: 'A, tag: String } | Plain { value: 'A, tag: String }
            fun getValue(t: Tagged<'A>) = t.value
            { a = getValue(Label(42, "num")), b = getValue(Plain("hello", "str")) }
            """.trimIndent(),
        )

    @Test
    fun singleConstructorGeneric_fullInterface() =
        assertType(
            "{ l: String, v: Num }",
            """
            type Wrapped<'A> = Wrapped { value: 'A, label: String }
            fun unwrap(w: Wrapped<Num>) = { v = w.value, l = w.label }
            unwrap(Wrapped(42, "answer"))
            """.trimIndent(),
        )
}
