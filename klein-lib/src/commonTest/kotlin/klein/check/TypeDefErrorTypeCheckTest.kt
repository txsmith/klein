package klein.check

import klein.check.Type.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Type-definition errors: constructor/field checks, arity/variance, and builtin-shadowing.
 *
 * Each case checks a program and asserts the expected [TypeError] appears in `.errors`. Rendered
 * type strings aren't pinned because nominal rendering is still open.
 */
class TypeDefErrorTypeCheckTest {
    @Test
    fun shadowsBuiltinType_Num() {
        infer("type Num = Zero | Succ { n: Num }").errors.filterIsInstance<TypeError.ShadowsBuiltinType>().single()
    }

    @Test
    fun shadowsBuiltinType_Bool() {
        infer("type Bool = True | False").errors.filterIsInstance<TypeError.ShadowsBuiltinType>().single()
    }

    @Test
    fun shadowsBuiltinType_String() {
        infer("type String = String { value: Num }").errors.filterIsInstance<TypeError.ShadowsBuiltinType>().single()
    }

    @Test
    fun shadowsBuiltinType_Unit() {
        infer("type Unit = Unit").errors.filterIsInstance<TypeError.ShadowsBuiltinType>().single()
    }

    @Test
    fun shadowsBuiltinType_constructorNamedNum() {
        infer("type Wrapper = Num { value: Num }").errors.filterIsInstance<TypeError.ShadowsBuiltinType>().single()
    }

    @Test
    fun undeclaredTypeParamInConstructor_error() {
        infer(
            """
            type Foo = Bar { value: 'A }
            """.trimIndent(),
        ).errors.filterIsInstance<TypeError.UndeclaredTypeParam>().single()
    }

    @Test
    fun unknownTypeInField_error() {
        val e =
            infer(
                """
                type Foo = Foo { x: Bar }
                """.trimIndent(),
            ).errors.filterIsInstance<TypeError.UnboundVariable>().single()
        assertEquals("Bar", e.name)
    }

    @Test
    fun duplicateConstructorName_acrossTypes_error() {
        val e =
            infer(
                """
                type Option<'A> = None | Some { value: 'A }
                type Result<'A, 'E> = None | Ok { value: 'A } | Err { error: 'E }

                None
                """.trimIndent(),
            ).errors.filterIsInstance<TypeError.DuplicateBinding>().single()
        assertEquals("None", e.name)
    }

    @Test
    fun duplicateTypeName_error() {
        val e =
            infer(
                """
                type Foo = Foo { x: Num }
                type Foo = Bar { y: String }
                """.trimIndent(),
            ).errors.filterIsInstance<TypeError.DuplicateBinding>().single()
        assertEquals("Foo", e.name)
    }

    @Test
    fun sameNamedConstructor_sumType_error() {
        val e =
            infer(
                """
                type Foo = Foo { x: Num } | Bar
                """.trimIndent(),
            ).errors.filterIsInstance<TypeError.DuplicateBinding>().single()
        assertEquals("Foo", e.name)
    }

    @Test
    fun circularTypeDefinition_directSelfWorks() {
        val errors =
            infer(
                """
                type T = T { x: T }

                fun mkT(): T = T(mkT())
                mkT()
                """.trimIndent(),
            ).errors
        assertTrue(errors.isEmpty(), "direct self-recursive type should check clean, got: $errors")
    }

    @Test
    fun fieldAccessOnBareConstructor() {
        val e =
            infer(
                """
                type Option<'A> = None | Some { value: 'A }

                None.value
                """.trimIndent(),
            ).errors.filterIsInstance<TypeError.MissingField>().single()
        assertEquals("value", e.field)
    }

    @Test
    fun undefinedConstructor_error() {
        val errors =
            infer(
                """
                Cons(1, Nil)
                """.trimIndent(),
            ).errors
        assertEquals(setOf("Cons", "Nil"), errors.filterIsInstance<TypeError.UnboundVariable>().map { it.name }.toSet())
        assertTrue(errors.all { it is TypeError.UnboundVariable }, "unexpected non-unbound errors: $errors")
    }

    @Test
    fun constructorAsValue_wrongContext() {
        infer(
            """
            type Option<'A> = None | Some { value: 'A }

            Some + 1
            """.trimIndent(),
        ).errors.filterIsInstance<TypeError.TypeMismatch>().single()
    }

    @Test
    fun constructorFieldTypeMismatch_nested() {
        infer(
            """
            type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }

            Cons(1, Cons(2, "not a list"))
            """.trimIndent(),
        ).errors.filterIsInstance<TypeError.TypeMismatch>().single()
    }

    @Test
    fun constructorWithWrongFieldOrder() {
        val errors =
            infer(
                """
                type Person = Person { name: String, age: Num }

                Person(25, "Alice")
                """.trimIndent(),
            ).errors
        assertEquals(2, errors.size, "expected two field mismatches, got: $errors")
        assertTrue(errors.all { it is TypeError.TypeMismatch }, "expected type mismatches, got: $errors")
    }

    @Test
    fun mutualRecursion_brokenReference() {
        val errors =
            infer(
                """
                type Foo<'A> = Foo { bar: Bar<'A> }
                type Bar<'A> = Bar { foo: Baz<'A> }

                Foo(Bar(Nil))
                """.trimIndent(),
            ).errors
        assertEquals(setOf("Baz", "Nil"), errors.filterIsInstance<TypeError.UnboundVariable>().map { it.name }.toSet())
    }

    @Test
    fun infiniteExpansion_doubleNesting() {
        infer(
            """
            type Bad<'A> = Bad { x: Bad<Bad<'A>> }

            Bad(Bad(42))
            """.trimIndent(),
        ).errors.filterIsInstance<TypeError.TypeMismatch>().single()
    }

    @Test
    fun typeArity_tooManyArgs_inFieldType() {
        val e =
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                type Bad = Bad { items: List<Num, String> }

                Bad(Nil)
                """.trimIndent(),
            ).errors.filterIsInstance<TypeError.TypeArityMismatch>().single()
        assertEquals("List", e.typeName)
    }

    @Test
    fun typeArity_tooFewArgs_inFieldType() {
        val e =
            infer(
                """
                type Map<'K, 'V> = Empty | Entry { key: 'K, value: 'V, rest: Map<'K, 'V> }
                type Bad = Bad { m: Map<String> }

                Bad(Empty)
                """.trimIndent(),
            ).errors.filterIsInstance<TypeError.TypeArityMismatch>().single()
        assertEquals("Map", e.typeName)
    }

    @Test
    fun typeArity_noArgsForGeneric_inFieldType() {
        val e =
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                type Bad = Bad { items: List }

                Bad(Nil)
                """.trimIndent(),
            ).errors.filterIsInstance<TypeError.TypeArityMismatch>().single()
        assertEquals("List", e.typeName)
    }

    @Test
    fun typeArity_argsOnNonGenericType_inFieldType() {
        val e =
            infer(
                """
                type MyBool = True | False
                type Bad = Bad { flag: MyBool<Num> }

                Bad(True)
                """.trimIndent(),
            ).errors.filterIsInstance<TypeError.TypeArityMismatch>().single()
        assertEquals("MyBool", e.typeName)
    }

    @Test
    fun typeArity_nestedError_innerTypeWrongArity_inFieldType() {
        val e =
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                type Option<'A> = None | Some { value: 'A }
                type Bad = Bad { items: List<Option<Num, String>> }

                Bad(Nil)
                """.trimIndent(),
            ).errors.filterIsInstance<TypeError.TypeArityMismatch>().single()
        assertEquals("Option", e.typeName)
    }

    @Test
    fun annotation_unknownTypeName() {
        val e =
            infer(
                """
                fun f(x: UnknownType) = x
                f
                """.trimIndent(),
            ).errors.filterIsInstance<TypeError.UnboundVariable>().single()
        assertEquals("UnknownType", e.name)
    }

    @Test
    fun annotation_typeArityMismatch() {
        val e =
            infer(
                """
                type Option<'A> = None | Some { value: 'A }
                fun f(x: Option) = x
                f
                """.trimIndent(),
            ).errors.filterIsInstance<TypeError.TypeArityMismatch>().single()
        assertEquals("Option", e.typeName)
    }
}
