package klein.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypeDefErrorTest {
    @Test
    fun shadowsBuiltinType_Num() {
        val errors = inferErrors("type Num = Zero | Succ { n: Num }")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.ShadowsBuiltinType)
    }

    @Test
    fun shadowsBuiltinType_Bool() {
        val errors = inferErrors("type Bool = True | False")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.ShadowsBuiltinType)
    }

    @Test
    fun shadowsBuiltinType_String() {
        val errors = inferErrors("type String = String { value: Num }")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.ShadowsBuiltinType)
    }

    @Test
    fun shadowsBuiltinType_Unit() {
        val errors = inferErrors("type Unit = Unit")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.ShadowsBuiltinType)
    }

    @Test
    fun shadowsBuiltinType_constructorNamedNum() {
        val errors = inferErrors("type Wrapper = Num { value: Num }")
        assertTrue(errors.any { it is TypeError.ShadowsBuiltinType })
    }

    @Test
    fun undeclaredTypeParamInConstructor_error() {
        val errors =
            inferErrors(
                """
                type Foo = Bar { value: 'A }
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.UndeclaredTypeParam)
    }

    @Test
    fun unknownTypeInField_error() {
        val errors =
            inferErrors(
                """
                type Foo = Foo { x: Bar }
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.UnboundVariable)
    }

    @Test
    fun duplicateConstructorName_acrossTypes_error() {
        val errors =
            inferErrors(
                """
                type Option<'A> = None | Some { value: 'A }
                type Result<'A, 'E> = None | Ok { value: 'A } | Err { error: 'E }

                None
                """.trimIndent(),
            )
        assertTrue(errors.any { it is TypeError.DuplicateBinding })
    }

    @Test
    fun duplicateTypeName_error() {
        val errors =
            inferErrors(
                """
                type Foo = Foo { x: Num }
                type Foo = Bar { y: String }
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.DuplicateBinding)
    }

    @Test
    fun sameNamedConstructor_sumType_error() {
        val errors =
            inferErrors(
                """
                type Foo = Foo { x: Num } | Bar
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { it is TypeError.DuplicateBinding })
    }

    @Test
    fun constructorArityError_tooManyArgs() {
        val errors =
            inferErrors(
                """
                type Option<'A> = None | Some { value: 'A }

                Some(1, 2)
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { it is TypeError.ArityMismatch })
    }

    @Test
    fun constructorArityError_tooFewArgs() {
        val errors =
            inferErrors(
                """
                type Pair<'A, 'B> = Pair { first: 'A, second: 'B }

                Pair(1)
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { it is TypeError.ArityMismatch })
    }

    @Test
    fun constructorArityError_argsOnBareConstructor() {
        val errors =
            inferErrors(
                """
                type Option<'A> = None | Some { value: 'A }

                None(42)
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertMismatch(errors[0], "None", "(Num) -> Nothing")
    }

    @Test
    fun circularTypeDefinition_directSelfWorks() {
        assertType(
            "T",
            infer(
                """
                type T = T { x: T }

                fun mkT() = T(mkT())
                mkT()
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun constructorWrongFieldType() {
        val errors =
            inferErrors(
                """
                type Box = Box { value: Num }

                Box("not a number")
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertMismatch(errors[0], "String", "Num")
    }

    @Test
    fun fieldAccessOnBareConstructor() {
        val errors =
            inferErrors(
                """
                type Option<'A> = None | Some { value: 'A }

                None.value
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { it is TypeError.MissingField })
    }

    @Test
    fun undefinedConstructor_error() {
        val errors =
            inferErrors(
                """
                Cons(1, Nil)
                """.trimIndent(),
            )
        assertEquals(2, errors.size)
        assertTrue(errors.all { it is TypeError.UnboundVariable })
    }

    @Test
    fun constructorAsValue_wrongContext() {
        val errors =
            inferErrors(
                """
                type Option<'A> = None | Some { value: 'A }

                Some + 1
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertMismatch(errors[0], "('A) -> Some<'A>", "Num")
    }

    @Test
    fun bareConstructorAsFunction_error() {
        val errors =
            inferErrors(
                """
                type Option<'A> = None | Some { value: 'A }

                f = None
                f(42)
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertMismatch(errors[0], "None", "(Num) -> Nothing")
    }

    @Test
    fun constructorFieldTypeMismatch_nested() {
        val errors =
            inferErrors(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }

                Cons(1, Cons(2, "not a list"))
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertMismatch(errors[0], "String", "List<Nothing>")
    }

    @Test
    fun constructorWithWrongFieldOrder() {
        val errors =
            inferErrors(
                """
                type Person = Person { name: String, age: Num }

                Person(25, "Alice")
                """.trimIndent(),
            )
        assertEquals(2, errors.size)
        assertMismatch(errors[0], "Num", "String")
        assertMismatch(errors[1], "String", "Num")
    }

    @Test
    fun fieldAccessOnParentType_nonCommonField() {
        val errors =
            inferErrors(
                """
                type Shape = Circle { radius: Num } | Rectangle { width: Num, height: Num }

                fun getRadius(s) = s.radius
                getRadius(if true then Circle(5) else Rectangle(3, 4))
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { it is TypeError.MissingField })
    }

    @Test
    fun mutualRecursion_brokenReference() {
        val errors =
            inferErrors(
                """
                type Foo<'A> = Foo { bar: Bar<'A> }
                type Bar<'A> = Bar { foo: Baz<'A> }

                Foo(Bar(Nil))
                """.trimIndent(),
            )
        assertTrue(errors.any { it is TypeError.UnboundVariable })
    }

    @Test
    fun infiniteExpansion_doubleNesting() {
        val errors =
            inferErrors(
                """
                type Bad<'A> = Bad { x: Bad<Bad<'A>> }

                Bad(Bad(42))
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertMismatch(errors[0], "Num", "Bad<Bad<Nothing>>")
    }

    @Test
    fun typeArity_tooManyArgs_inFieldType() {
        val errors =
            inferErrors(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                type Bad = Bad { items: List<Num, String> }

                Bad(Nil)
                """.trimIndent(),
            )
        assertTrue(errors.any { it is TypeError.ArityMismatch })
    }

    @Test
    fun typeArity_tooManyArgs_moreExcess_inFieldType() {
        val errors =
            inferErrors(
                """
                type Option<'A> = None | Some { value: 'A }
                type Bad = Bad { opt: Option<Num, String, Bool> }

                Bad(None)
                """.trimIndent(),
            )
        assertTrue(errors.any { it is TypeError.ArityMismatch })
    }

    @Test
    fun typeArity_tooFewArgs_inFieldType() {
        val errors =
            inferErrors(
                """
                type Map<'K, 'V> = Empty | Entry { key: 'K, value: 'V, rest: Map<'K, 'V> }
                type Bad = Bad { m: Map<String> }

                Bad(Empty)
                """.trimIndent(),
            )
        assertTrue(errors.any { it is TypeError.ArityMismatch })
    }

    @Test
    fun typeArity_noArgsForGeneric_inFieldType() {
        val errors =
            inferErrors(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                type Bad = Bad { items: List }

                Bad(Nil)
                """.trimIndent(),
            )
        assertTrue(errors.any { it is TypeError.ArityMismatch })
    }

    @Test
    fun typeArity_argsOnNonGenericType_inFieldType() {
        val errors =
            inferErrors(
                """
                type MyBool = True | False
                type Bad = Bad { flag: MyBool<Num> }

                Bad(True)
                """.trimIndent(),
            )
        assertTrue(errors.any { it is TypeError.ArityMismatch })
    }

    @Test
    fun typeArity_nestedError_innerTypeWrongArity_inFieldType() {
        val errors =
            inferErrors(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                type Option<'A> = None | Some { value: 'A }
                type Bad = Bad { items: List<Option<Num, String>> }

                Bad(Nil)
                """.trimIndent(),
            )
        assertTrue(errors.any { it is TypeError.ArityMismatch })
    }

    @Test
    fun typeArity_nestedError_outerTypeWrongArity_inFieldType() {
        val errors =
            inferErrors(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                type Option<'A> = None | Some { value: 'A }
                type Bad = Bad { items: List<Option<Num>, String> }

                Bad(Nil)
                """.trimIndent(),
            )
        assertTrue(errors.any { it is TypeError.ArityMismatch })
    }
}
