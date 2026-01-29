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
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.ShadowsBuiltinType)
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
        assertUnbound(errors[0], "Bar")
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
        assertEquals(1, errors.size)
        assertDuplicateBinding(errors[0], "None")
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
        assertDuplicateBinding(errors[0], "Foo")
    }

    @Test
    fun sameNamedConstructor_sumType_error() {
        val errors =
            inferErrors(
                """
                type Foo = Foo { x: Num } | Bar
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertDuplicateBinding(errors[0], "Foo")
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
    fun fieldAccessOnBareConstructor() {
        val errors =
            inferErrors(
                """
                type Option<'A> = None | Some { value: 'A }

                None.value
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertMissingField(errors[0], "value")
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
        assertUnbound(errors[0], "Cons")
        assertUnbound(errors[1], "Nil")
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
    fun mutualRecursion_brokenReference() {
        val errors =
            inferErrors(
                """
                type Foo<'A> = Foo { bar: Bar<'A> }
                type Bar<'A> = Bar { foo: Baz<'A> }

                Foo(Bar(Nil))
                """.trimIndent(),
            )
        assertEquals(2, errors.size)
        assertUnbound(errors[0], "Baz")
        assertUnbound(errors[1], "Nil")
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
        assertEquals(1, errors.size)
        assertTypeArityMismatch(errors[0], typeName = "List", expected = 1, actual = 2)
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
        assertEquals(1, errors.size)
        assertTypeArityMismatch(errors[0], typeName = "Map", expected = 2, actual = 1)
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
        assertEquals(1, errors.size)
        assertTypeArityMismatch(errors[0], typeName = "List", expected = 1, actual = 0)
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
        assertEquals(1, errors.size)
        assertTypeArityMismatch(errors[0], typeName = "MyBool", expected = 0, actual = 1)
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
        assertEquals(1, errors.size)
        assertTypeArityMismatch(errors[0], typeName = "Option", expected = 1, actual = 2)
    }

}
