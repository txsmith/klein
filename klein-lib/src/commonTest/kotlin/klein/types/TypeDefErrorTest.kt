package klein.types

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

@Ignore("Type definitions not yet implemented - these tests specify expected error behavior")
class TypeDefErrorTest {
    @Test
    fun undeclaredTypeParamInConstructor_error() {
        val errors =
            inferErrors(
                """
                type Foo = Bar { value: 'A }
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty())
    }

    @Test
    fun unknownTypeInField_error() {
        val errors =
            inferErrors(
                """
                type Foo = Foo { x: Bar }
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty())
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
        assertTrue(errors.isNotEmpty())
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
        assertTrue(errors.isNotEmpty())
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
        assertTrue(errors.isNotEmpty())
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
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { it is TypeError.TypeMismatch })
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
        assertTrue(errors.isNotEmpty())
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
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { it is TypeError.TypeMismatch })
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
        assertTrue(errors.isNotEmpty())
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
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { it is TypeError.TypeMismatch })
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
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { it is TypeError.TypeMismatch })
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
        assertTrue(errors.isNotEmpty())
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
        assertTrue(errors.isNotEmpty())
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
        assertTrue(errors.isNotEmpty())
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
        assertTrue(errors.isNotEmpty())
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
        assertTrue(errors.isNotEmpty())
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
        assertTrue(errors.isNotEmpty())
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
        assertTrue(errors.isNotEmpty())
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
        assertTrue(errors.isNotEmpty())
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
        assertTrue(errors.isNotEmpty())
    }
}
