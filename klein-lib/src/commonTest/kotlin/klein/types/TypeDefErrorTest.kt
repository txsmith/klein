package klein.types

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

@Ignore("Type definitions not yet implemented - these tests specify expected error behavior")
class TypeDefErrorTest {
    @Test
    fun duplicateConstructorName_acrossTypes_error() {
        val result =
            inferWithErrors(
                """
                type Option<'A> = None | Some { value: 'A }
                type Result<'A, 'E> = None | Ok { value: 'A } | Err { error: 'E }

                None
                """.trimIndent(),
            )
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun duplicateTypeName_error() {
        val result =
            inferWithErrors(
                """
                type Foo = Foo { x: Num }
                type Foo = Bar { y: String }
                """.trimIndent(),
            )
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun sameNamedConstructor_sumType_error() {
        val result =
            inferWithErrors(
                """
                type Foo = Foo { x: Num } | Bar
                """.trimIndent(),
            )
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun constructorArityError_tooManyArgs() {
        val result =
            inferWithErrors(
                """
                type Option<'A> = None | Some { value: 'A }

                Some(1, 2)
                """.trimIndent(),
            )
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.any { it is TypeError.ArityMismatch })
    }

    @Test
    fun constructorArityError_tooFewArgs() {
        val result =
            inferWithErrors(
                """
                type Pair<'A, 'B> = Pair { first: 'A, second: 'B }

                Pair(1)
                """.trimIndent(),
            )
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.any { it is TypeError.ArityMismatch })
    }

    @Test
    fun constructorArityError_argsOnBareConstructor() {
        val result =
            inferWithErrors(
                """
                type Option<'A> = None | Some { value: 'A }

                None(42)
                """.trimIndent(),
            )
        assertTrue(result.errors.isNotEmpty())
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
        val result =
            inferWithErrors(
                """
                type Box = Box { value: Num }

                Box("not a number")
                """.trimIndent(),
            )
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.any { it is TypeError.TypeMismatch })
    }

    @Test
    fun fieldAccessOnBareConstructor() {
        val result =
            inferWithErrors(
                """
                type Option<'A> = None | Some { value: 'A }

                None.value
                """.trimIndent(),
            )
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.any { it is TypeError.MissingField })
    }

    @Test
    fun undefinedConstructor_error() {
        val result =
            inferWithErrors(
                """
                Cons(1, Nil)
                """.trimIndent(),
            )
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun constructorAsValue_wrongContext() {
        val result =
            inferWithErrors(
                """
                type Option<'A> = None | Some { value: 'A }

                Some + 1
                """.trimIndent(),
            )
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.any { it is TypeError.TypeMismatch })
    }

    @Test
    fun bareConstructorAsFunction_error() {
        val result =
            inferWithErrors(
                """
                type Option<'A> = None | Some { value: 'A }

                f = None
                f(42)
                """.trimIndent(),
            )
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun constructorFieldTypeMismatch_nested() {
        val result =
            inferWithErrors(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }

                Cons(1, Cons(2, "not a list"))
                """.trimIndent(),
            )
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.any { it is TypeError.TypeMismatch })
    }

    @Test
    fun constructorWithWrongFieldOrder() {
        val result =
            inferWithErrors(
                """
                type Person = Person { name: String, age: Num }

                Person(25, "Alice")
                """.trimIndent(),
            )
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.any { it is TypeError.TypeMismatch })
    }

    @Test
    fun fieldAccessOnParentType_nonCommonField() {
        val result =
            inferWithErrors(
                """
                type Shape = Circle { radius: Num } | Rectangle { width: Num, height: Num }

                fun getRadius(s) = s.radius
                getRadius(if true then Circle(5) else Rectangle(3, 4))
                """.trimIndent(),
            )
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.any { it is TypeError.MissingField })
    }

    @Test
    fun mutualRecursion_brokenReference() {
        val result =
            inferWithErrors(
                """
                type Foo<'A> = Foo { bar: Bar<'A> }
                type Bar<'A> = Bar { foo: Baz<'A> }

                Foo(Bar(Nil))
                """.trimIndent(),
            )
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun infiniteExpansion_doubleNesting() {
        val result =
            inferWithErrors(
                """
                type Bad<'A> = Bad { x: Bad<Bad<'A>> }

                Bad(Bad(42))
                """.trimIndent(),
            )
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun typeArity_tooManyArgs_inFieldType() {
        val result =
            inferWithErrors(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                type Bad = Bad { items: List<Num, String> }

                Bad(Nil)
                """.trimIndent(),
            )
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun typeArity_tooManyArgs_moreExcess_inFieldType() {
        val result =
            inferWithErrors(
                """
                type Option<'A> = None | Some { value: 'A }
                type Bad = Bad { opt: Option<Num, String, Bool> }

                Bad(None)
                """.trimIndent(),
            )
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun typeArity_tooFewArgs_inFieldType() {
        val result =
            inferWithErrors(
                """
                type Map<'K, 'V> = Empty | Entry { key: 'K, value: 'V, rest: Map<'K, 'V> }
                type Bad = Bad { m: Map<String> }

                Bad(Empty)
                """.trimIndent(),
            )
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun typeArity_noArgsForGeneric_inFieldType() {
        val result =
            inferWithErrors(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                type Bad = Bad { items: List }

                Bad(Nil)
                """.trimIndent(),
            )
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun typeArity_argsOnNonGenericType_inFieldType() {
        val result =
            inferWithErrors(
                """
                type MyBool = True | False
                type Bad = Bad { flag: MyBool<Num> }

                Bad(True)
                """.trimIndent(),
            )
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun typeArity_nestedError_innerTypeWrongArity_inFieldType() {
        val result =
            inferWithErrors(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                type Option<'A> = None | Some { value: 'A }
                type Bad = Bad { items: List<Option<Num, String>> }

                Bad(Nil)
                """.trimIndent(),
            )
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun typeArity_nestedError_outerTypeWrongArity_inFieldType() {
        val result =
            inferWithErrors(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                type Option<'A> = None | Some { value: 'A }
                type Bad = Bad { items: List<Option<Num>, String> }

                Bad(Nil)
                """.trimIndent(),
            )
        assertTrue(result.errors.isNotEmpty())
    }
}
