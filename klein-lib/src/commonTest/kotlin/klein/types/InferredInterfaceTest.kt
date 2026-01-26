package klein.types

import klein.Type
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Ignore("Type definitions not yet implemented - these tests specify expected behavior")
class InferredInterfaceTest {
    @Test
    fun allConstructorsShareField_sameType() {
        assertType(
            Type.Num,
            infer(
                """
                type Light = Red { duration: Num } | Yellow { duration: Num } | Green { duration: Num }
                type H = H { light: Light }

                H(Red(10)).light.duration
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun allConstructorsShareField_accessViaConditional() {
        assertType(
            Type.Num,
            infer(
                """
                type Light = Red { duration: Num } | Yellow { duration: Num } | Green { duration: Num }
                type H = H { light: Light }

                H(if true then Red(10) else Green(20)).light.duration
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun commonFieldWithSameType_interfaceHasField() {
        assertType(
            "Num",
            infer(
                """
                type AB = A { x: Num } | B { x: Num }
                type H = H { ab: AB }

                H(A(42)).ab.x
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun commonFieldWithDifferentTypes_unifiesViaSimpleSub() {
        assertType(
            "Num | String",
            infer(
                """
                type AB = A { x: Num } | B { x: String }
                type H = H { ab: AB }

                H(A(42)).ab.x
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun commonFieldWithDifferentTypes_appliedToBothViaHolder() {
        assertType(
            "{ a: Num | String, b: Num | String }",
            infer(
                """
                type AB = A { x: Num } | B { x: String }
                type H = H { ab: AB }

                { a = H(A(42)).ab.x, b = H(B("hello")).ab.x }
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun noCommonFields_fieldAccessFails() {
        val errors =
            inferErrors(
                """
                type AB = A { x: Num } | B { y: String }
                type H = H { ab: AB }

                H(A(42)).ab.x
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.MissingField)
    }

    @Test
    fun partialOverlap_onlyCommonFieldsInInterface() {
        assertType(
            Type.Num,
            infer(
                """
                type AB = A { x: Num, y: String } | B { x: Num }
                type H = H { ab: AB }

                H(A(1, "hello")).ab.x
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun partialOverlap_nonCommonFieldNotAccessible() {
        val errors =
            inferErrors(
                """
                type AB = A { x: Num, y: String } | B { x: Num }
                type H = H { ab: AB }

                H(A(1, "hello")).ab.y
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.MissingField)
    }

    @Test
    fun singleConstructor_interfaceIsFullRecord() {
        assertType(
            Type.Num,
            infer(
                """
                type Money = Money { value: Num }
                type H = H { m: Money }

                H(Money(100)).m.value
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun singleConstructor_allFieldsAccessible() {
        assertType(
            "{ a: Num, b: String }",
            infer(
                """
                type Point = Point { x: Num, y: String }
                type H = H { p: Point }

                h = H(Point(1, "hello"))
                { a = h.p.x, b = h.p.y }
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun bareConstructors_fieldAccessFails() {
        val errors =
            inferErrors(
                """
                type Bool = True | False
                type H = H { b: Bool }

                H(True).b.value
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.MissingField)
    }

    @Test
    fun bareConstructors_typePreserved() {
        assertType(
            "Bool",
            infer(
                """
                type Bool = True | False
                type H = H { b: Bool }

                H(if true then True else False).b
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun mixedBareAndFieldConstructors_noCommonFields() {
        val errors =
            inferErrors(
                """
                type Option = None | Some { value: Num }
                type H = H { opt: Option }

                H(Some(42)).opt.value
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.MissingField)
    }

    @Test
    fun multipleCommonFields_allAccessible() {
        assertType(
            "{ a: Num, b: String }",
            infer(
                """
                type ABC = A { x: Num, y: String, z: Bool }
                         | B { x: Num, y: String }
                         | C { x: Num, y: String, w: Num }
                type H = H { abc: ABC }

                h = H(A(1, "hi", true))
                { a = h.abc.x, b = h.abc.y }
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun multipleCommonFields_nonCommonNotAccessible() {
        val errors =
            inferErrors(
                """
                type ABC = A { x: Num, y: String, z: Bool }
                         | B { x: Num, y: String }
                         | C { x: Num, y: String, w: Num }
                type H = H { abc: ABC }

                H(A(1, "hi", true)).abc.z
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.MissingField)
    }

    @Test
    fun genericType_commonFieldWithTypeParam() {
        assertType(
            Type.Num,
            infer(
                """
                type Container<'A> = Box { value: 'A } | Wrapper { value: 'A, label: String }
                type H<'A> = H { c: Container<'A> }

                H(Box(42)).c.value
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun nestedRecordInCommonField() {
        assertType(
            "{ x: Num }",
            infer(
                """
                type AB = A { inner: { x: Num, y: String } } | B { inner: { x: Num } }
                type H = H { ab: AB }

                H(A({ x = 1, y = "hi" })).ab.inner
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun functionTypeInCommonField_sameType() {
        assertType(
            "(Num) -> Num",
            infer(
                """
                type Processor = Inc { f: Num -> Num } | Dec { f: Num -> Num }
                type H = H { p: Processor }

                H(Inc(|x -> x + 1|)).p.f
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun functionTypeInCommonField_differentReturnTypes() {
        assertType(
            "(Num) -> Num | String",
            infer(
                """
                type Processor = ToNum { f: Num -> Num } | ToStr { f: Num -> String }
                type H = H { p: Processor }

                H(ToNum(|x -> x + 1|)).p.f
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun sumTypeSubtypesInferredInterface() {
        assertType(
            Type.Num,
            infer(
                """
                type Light = Red { duration: Num } | Yellow { duration: Num } | Green { duration: Num }
                type H = H { light: Light }

                H(Red(10)).light.duration
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun constructorSubtypesParent_fieldStillAccessible() {
        assertType(
            Type.Num,
            infer(
                """
                type Light = Red { duration: Num } | Yellow { duration: Num } | Green { duration: Num }

                red = Red(10)
                red.duration
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun constructorHasMoreFieldsThanInterface() {
        assertType(
            "{ d: Num, i: Num }",
            infer(
                """
                type Light = Red { duration: Num, intensity: Num }
                           | Yellow { duration: Num }
                           | Green { duration: Num, direction: String }

                red = Red(10, 100)
                { d = red.duration, i = red.intensity }
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun sumTypeInIfThenElse_commonFieldAccessible() {
        assertType(
            Type.Num,
            infer(
                """
                type Light = Red { duration: Num } | Yellow { duration: Num } | Green { duration: Num }
                type H = H { light: Light }

                H(if true then Red(10) else Green(20)).light.duration
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun commonFieldWithTypeParamAndConcreteType() {
        assertType(
            "Num | 'A",
            infer(
                """
                type Mixed<'A> = Concrete { x: Num } | Generic { x: 'A }
                type H<'A> = H { m: Mixed<'A> }

                H(Concrete(42)).m.x
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun differentTypes_numAndString_unifiesViaUpperBound() {
        assertType(
            "Num | String",
            infer(
                """
                type Mixed = A { x: Num } | B { x: String }
                type H = H { m: Mixed }

                H(A(42)).m.x
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun functionTypes_differentReturnTypes_unifiesReturnType() {
        assertType(
            "(Num) -> String | Num",
            infer(
                """
                type Handlers = A { f: Num -> String } | B { f: Num -> Num }
                type H = H { h: Handlers }

                H(A(|x -> "hello"|)).h.f
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun functionTypes_differentParamTypes_unifiesContravariant() {
        assertType(
            "(Num & String) -> Num",
            infer(
                """
                type Handlers = A { f: Num -> Num } | B { f: String -> Num }
                type H = H { h: Handlers }

                H(A(|x -> 42|)).h.f
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun nestedRecords_differentInnerTypes_unifiesDeep() {
        assertType(
            "Num | String",
            infer(
                """
                type Nested = A { data: { inner: Num } } | B { data: { inner: String } }
                type H = H { n: Nested }

                H(A({ inner = 42 })).n.data.inner
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun nestedRecords_differentFieldSets_intersectsFields() {
        assertType(
            "{ common: Num }",
            infer(
                """
                type Nested = A { data: { common: Num, only_a: String } }
                            | B { data: { common: Num, only_b: Bool } }
                type H = H { n: Nested }

                H(A({ common = 1, only_a = "hi" })).n.data
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun multipleCommonFields_differentTypesEach_unifiesIndependently() {
        assertType(
            "{ x: Num | String, y: Bool | Num }",
            infer(
                """
                type Multi = A { x: Num, y: Bool } | B { x: String, y: Num }
                type H = H { m: Multi }

                h = H(A(42, true))
                { x = h.m.x, y = h.m.y }
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun genericWithDifferentInstantiations_unifiesTypeParams() {
        assertType(
            "Num | String",
            infer(
                """
                type Container<'A> = Container { value: 'A }
                type Boxes = NumBox { c: Container<Num> } | StrBox { c: Container<String> }
                type H = H { b: Boxes }

                H(NumBox(Container(42))).b.c.value
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun listLikeTypes_differentElementTypes_unifiesRecursively() {
        assertType(
            "List<Num> | List<String>",
            infer(
                """
                type List<'A> = Cons { head: 'A, tail: List<'A> } | Nil
                type Mixed = NumList { items: List<Num> } | StrList { items: List<String> }
                type H = H { m: Mixed }

                H(NumList(Nil)).m.items
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun optionalTypes_someVsNone_preservesOptionality() {
        assertType(
            "Option<Num>",
            infer(
                """
                type Option<'A> = Some { value: 'A } | None
                type AB = A { maybe: Option<Num> } | B { maybe: Option<Num> }
                type H = H { ab: AB }

                H(A(Some(42))).ab.maybe
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun functionReturningRecord_differentFieldTypes_unifiesResult() {
        assertType(
            "() -> { x: Num | String }",
            infer(
                """
                type Factories = A { make: () -> { x: Num } } | B { make: () -> { x: String } }
                type H = H { f: Factories }

                H(A(|{ x = 42 }|)).f.make
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun higherOrderFunction_differentCallbackTypes_unifiesCallback() {
        assertType(
            "((Num | String) -> Bool) -> Bool",
            infer(
                """
                type Validators = A { validate: (Num -> Bool) -> Bool }
                                | B { validate: (String -> Bool) -> Bool }
                type H = H { v: Validators }

                H(A(|check -> check(42)|)).v.validate
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun deeplyNestedUnification_threeLevels() {
        assertType(
            "{ l1: { l2: { l3: Num | String } } }",
            infer(
                """
                type Deep = A { data: { l1: { l2: { l3: Num } } } }
                          | B { data: { l1: { l2: { l3: String } } } }
                type H = H { d: Deep }

                H(A({ l1 = { l2 = { l3 = 42 } } })).d.data
                """.trimIndent(),
            ),
        )
    }
}
