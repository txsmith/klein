package klein.check

import klein.check.Type.*
import klein.types.TypeError
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Type-definition errors — ported from the SimpleSub `TypeDefErrorTest`. **All red targets:** the new
 * bidirectional checker treats `type` declarations as a no-op (no constructors, no nominal fields,
 * no arity/variance checking, no builtin-shadowing check), so every test here fails until nominal
 * support lands.
 *
 * Verdict mapping (exact rendered type strings are never pinned — nominal rendering is TBD):
 *  - `assertMismatch` → some `TypeError.TypeMismatch` present
 *  - `assertUnbound` → some `TypeError.UnboundVariable` (with the named variable) present
 *  - `assertDuplicateBinding` → some `TypeError.DuplicateBinding` (with the named binding) present
 *  - `assertMissingField` → some `TypeError.MissingField` (with the named field) present
 *  - `assertTypeArityMismatch` → some `TypeError.TypeArityMismatch` (with typeName) present
 *  - `TypeError.ShadowsBuiltinType` / `.UndeclaredTypeParam` map straight across.
 *  - `assertType(<nominal>, ...)` → clean-check assertion (nominal names aren't expressible here).
 *
 * Dropped: none — every test is an error/verdict test and stays verdict-portable.
 */
class TypeDefErrorTypeCheckTest {
    @Test
    fun shadowsBuiltinType_Num() {
        val errors = infer("type Num = Zero | Succ { n: Num }").errors
        assertTrue(errors.any { it is TypeError.ShadowsBuiltinType }, "expected a builtin-shadow error, got: $errors")
    }

    @Test
    fun shadowsBuiltinType_Bool() {
        val errors = infer("type Bool = True | False").errors
        assertTrue(errors.any { it is TypeError.ShadowsBuiltinType }, "expected a builtin-shadow error, got: $errors")
    }

    @Test
    fun shadowsBuiltinType_String() {
        val errors = infer("type String = String { value: Num }").errors
        assertTrue(errors.any { it is TypeError.ShadowsBuiltinType }, "expected a builtin-shadow error, got: $errors")
    }

    @Test
    fun shadowsBuiltinType_Unit() {
        val errors = infer("type Unit = Unit").errors
        assertTrue(errors.any { it is TypeError.ShadowsBuiltinType }, "expected a builtin-shadow error, got: $errors")
    }

    @Test
    fun shadowsBuiltinType_constructorNamedNum() {
        val errors = infer("type Wrapper = Num { value: Num }").errors
        assertTrue(errors.any { it is TypeError.ShadowsBuiltinType }, "expected a builtin-shadow error, got: $errors")
    }

    @Test
    fun undeclaredTypeParamInConstructor_error() {
        val errors =
            infer(
                """
                type Foo = Bar { value: 'A }
                """.trimIndent(),
            ).errors
        assertTrue(errors.any { it is TypeError.UndeclaredTypeParam }, "expected an undeclared-type-param error, got: $errors")
    }

    @Test
    fun unknownTypeInField_error() {
        val errors =
            infer(
                """
                type Foo = Foo { x: Bar }
                """.trimIndent(),
            ).errors
        assertTrue(
            errors.any { it is TypeError.UnboundVariable && it.name == "Bar" },
            "expected an unbound 'Bar', got: $errors",
        )
    }

    @Test
    fun duplicateConstructorName_acrossTypes_error() {
        val errors =
            infer(
                """
                type Option<'A> = None | Some { value: 'A }
                type Result<'A, 'E> = None | Ok { value: 'A } | Err { error: 'E }

                None
                """.trimIndent(),
            ).errors
        assertTrue(
            errors.any { it is TypeError.DuplicateBinding && it.name == "None" },
            "expected a duplicate 'None', got: $errors",
        )
    }

    @Test
    fun duplicateTypeName_error() {
        val errors =
            infer(
                """
                type Foo = Foo { x: Num }
                type Foo = Bar { y: String }
                """.trimIndent(),
            ).errors
        assertTrue(
            errors.any { it is TypeError.DuplicateBinding && it.name == "Foo" },
            "expected a duplicate 'Foo', got: $errors",
        )
    }

    @Test
    fun sameNamedConstructor_sumType_error() {
        val errors =
            infer(
                """
                type Foo = Foo { x: Num } | Bar
                """.trimIndent(),
            ).errors
        assertTrue(
            errors.any { it is TypeError.DuplicateBinding && it.name == "Foo" },
            "expected a duplicate 'Foo', got: $errors",
        )
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
        val errors =
            infer(
                """
                type Option<'A> = None | Some { value: 'A }

                None.value
                """.trimIndent(),
            ).errors
        assertTrue(
            errors.any { it is TypeError.MissingField && it.field == "value" },
            "expected a missing-field 'value', got: $errors",
        )
    }

    @Test
    fun undefinedConstructor_error() {
        val errors =
            infer(
                """
                Cons(1, Nil)
                """.trimIndent(),
            ).errors
        assertTrue(
            errors.any { it is TypeError.UnboundVariable && it.name == "Cons" },
            "expected an unbound 'Cons', got: $errors",
        )
        assertTrue(
            errors.any { it is TypeError.UnboundVariable && it.name == "Nil" },
            "expected an unbound 'Nil', got: $errors",
        )
    }

    @Test
    fun constructorAsValue_wrongContext() {
        val errors =
            infer(
                """
                type Option<'A> = None | Some { value: 'A }

                Some + 1
                """.trimIndent(),
            ).errors
        assertTrue(errors.any { it is TypeError.TypeMismatch }, "expected a type mismatch, got: $errors")
    }

    @Test
    fun constructorFieldTypeMismatch_nested() {
        val errors =
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }

                Cons(1, Cons(2, "not a list"))
                """.trimIndent(),
            ).errors
        assertTrue(errors.any { it is TypeError.TypeMismatch }, "expected a type mismatch, got: $errors")
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
        assertTrue(errors.any { it is TypeError.TypeMismatch }, "expected a type mismatch, got: $errors")
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
        assertTrue(
            errors.any { it is TypeError.UnboundVariable && it.name == "Baz" },
            "expected an unbound 'Baz', got: $errors",
        )
        assertTrue(
            errors.any { it is TypeError.UnboundVariable && it.name == "Nil" },
            "expected an unbound 'Nil', got: $errors",
        )
    }

    @Test
    fun infiniteExpansion_doubleNesting() {
        val errors =
            infer(
                """
                type Bad<'A> = Bad { x: Bad<Bad<'A>> }

                Bad(Bad(42))
                """.trimIndent(),
            ).errors
        assertTrue(errors.any { it is TypeError.TypeMismatch }, "expected a type mismatch, got: $errors")
    }

    @Test
    fun typeArity_tooManyArgs_inFieldType() {
        val errors =
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                type Bad = Bad { items: List<Num, String> }

                Bad(Nil)
                """.trimIndent(),
            ).errors
        assertTrue(
            errors.any { it is TypeError.TypeArityMismatch && it.typeName == "List" },
            "expected a type-arity mismatch on 'List', got: $errors",
        )
    }

    @Test
    fun typeArity_tooFewArgs_inFieldType() {
        val errors =
            infer(
                """
                type Map<'K, 'V> = Empty | Entry { key: 'K, value: 'V, rest: Map<'K, 'V> }
                type Bad = Bad { m: Map<String> }

                Bad(Empty)
                """.trimIndent(),
            ).errors
        assertTrue(
            errors.any { it is TypeError.TypeArityMismatch && it.typeName == "Map" },
            "expected a type-arity mismatch on 'Map', got: $errors",
        )
    }

    @Test
    fun typeArity_noArgsForGeneric_inFieldType() {
        val errors =
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                type Bad = Bad { items: List }

                Bad(Nil)
                """.trimIndent(),
            ).errors
        assertTrue(
            errors.any { it is TypeError.TypeArityMismatch && it.typeName == "List" },
            "expected a type-arity mismatch on 'List', got: $errors",
        )
    }

    @Test
    fun typeArity_argsOnNonGenericType_inFieldType() {
        val errors =
            infer(
                """
                type MyBool = True | False
                type Bad = Bad { flag: MyBool<Num> }

                Bad(True)
                """.trimIndent(),
            ).errors
        assertTrue(
            errors.any { it is TypeError.TypeArityMismatch && it.typeName == "MyBool" },
            "expected a type-arity mismatch on 'MyBool', got: $errors",
        )
    }

    @Test
    fun typeArity_nestedError_innerTypeWrongArity_inFieldType() {
        val errors =
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                type Option<'A> = None | Some { value: 'A }
                type Bad = Bad { items: List<Option<Num, String>> }

                Bad(Nil)
                """.trimIndent(),
            ).errors
        assertTrue(
            errors.any { it is TypeError.TypeArityMismatch && it.typeName == "Option" },
            "expected a type-arity mismatch on 'Option', got: $errors",
        )
    }
}
