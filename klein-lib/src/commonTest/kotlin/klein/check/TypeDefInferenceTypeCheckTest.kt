package klein.check

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Type-definition inference: constructing and accessing nominal types, including recursive and
 * mutually-recursive types, composed generics, sibling joins to a common parent, and type-variable
 * name preservation. Printed type variables render without the leading tick (`A`, not `'A`).
 *
 * Sibling constructors with *incompatible* type args (`Cons<Num>` vs `Cons<String>`) have no
 * expressible join and are an error, not an inferred union. Not covered: passing a polymorphic
 * constructor through a generic parameter (`apply(Cons, …)`) — higher-rank, currently unsupported.
 */
class TypeDefInferenceTypeCheckTest {
    private fun assertType(
        expected: String,
        src: String,
    ) {
        val r = infer(src)
        assertTrue(r.errors.isEmpty(), "unexpected errors: ${r.errors}")
        assertEquals(expected, Type.print(r.type))
    }

    private fun cannotJoin(src: String) = assertTrue(infer(src).errors.isNotEmpty(), "expected a join failure")

    private val list = "type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }"
    private val option = "type Option<'A> = None | Some { value: 'A }"

    // --- enums and single constructors ---

    @Test
    fun basicEnum_constructorUsable() =
        assertType(
            "True",
            """
            type MyBool = True | False
            True
            """.trimIndent(),
        )

    @Test
    fun basicEnum_bothConstructorsJoinToParent() =
        assertType(
            "MyBool",
            """
            type MyBool = True | False
            if true then True else False
            """.trimIndent(),
        )

    @Test
    fun singleConstructor_usable() =
        assertType(
            "Money",
            """
            type Money = Money { value: Num }
            Money(100)
            """.trimIndent(),
        )

    @Test
    fun singleConstructor_fieldAccess() =
        assertType(
            "Num",
            """
            type Money = Money { value: Num }
            m = Money(100)
            m.value
            """.trimIndent(),
        )

    // --- generic types ---

    @Test
    fun genericType_constructorInference() =
        assertType(
            "Some<Num>",
            """
            $option
            Some(42)
            """.trimIndent(),
        )

    @Test
    fun genericType_fieldAccess() =
        assertType(
            "Num",
            """
            $option
            s = Some(42)
            s.value
            """.trimIndent(),
        )

    // --- recursive types ---

    @Test
    fun recursiveType_listNil() =
        assertType(
            "Nil",
            """
            $list
            Nil
            """.trimIndent(),
        )

    @Test
    fun recursiveType_listCons() =
        assertType(
            "Cons<Num>",
            """
            $list
            Cons(1, Nil)
            """.trimIndent(),
        )

    @Test
    fun recursiveType_listTailAccess() =
        assertType(
            "List<Num>",
            """
            $list
            xs = Cons(1, Cons(2, Nil))
            xs.tail
            """.trimIndent(),
        )

    @Test
    fun bareConstructor_joinWithTypedConstructor() =
        assertType(
            "List<Num>",
            """
            $list
            if true then Nil else Cons(1, Nil)
            """.trimIndent(),
        )

    // --- mutually recursive types ---

    @Test
    fun mutualRecursion_fooUsable() =
        assertType(
            "Foo<Num>",
            """
            type Foo<'A> = Foo { bar: Bar<'A> }
            type Bar<'A> = Bar { value: 'A, foo: Foo<'A> }
            fun mkFoo(x: Num): Foo<Num> = Foo(Bar(x, mkFoo(x)))
            mkFoo(42)
            """.trimIndent(),
        )

    @Test
    fun mutualRecursion_fieldAccess() =
        assertType(
            "Bar<Num>",
            """
            type Foo<'A> = Foo { bar: Bar<'A> }
            type Bar<'A> = Bar { value: 'A, foo: Foo<'A> }
            fun mkFoo(x: Num): Foo<Num> = Foo(Bar(x, mkFoo(x)))
            f = mkFoo(42)
            f.bar
            """.trimIndent(),
        )

    @Test
    fun mutualRecursion_transitiveFieldAccess() =
        assertType(
            "Foo<Num>",
            """
            type Foo<'A> = Foo { bar: Bar<'A> }
            type Bar<'A> = Bar { value: 'A, foo: Foo<'A> }
            fun mkFoo(x: Num): Foo<Num> = Foo(Bar(x, mkFoo(x)))
            f = mkFoo(42)
            f.bar.foo
            """.trimIndent(),
        )

    // --- sibling type-arg merge ---

    @Test
    fun siblingConstructors_differentArgs_cannotJoin() =
        cannotJoin(
            """
            $list
            if true then Cons(1, Nil) else Cons("hi", Nil)
            """.trimIndent(),
        )

    @Test
    fun mixedTypesInGenericContainer_cannotJoin() =
        cannotJoin(
            """
            $list
            Cons(1, Cons("hello", Nil))
            """.trimIndent(),
        )

    @Test
    fun recursiveTypeConstructionWithMixedTypes_cannotJoin() =
        cannotJoin(
            """
            type Tree<'A> = Leaf { value: 'A } | Node { left: Tree<'A>, right: Tree<'A> }
            Node(Leaf(1), Leaf("wrong"))
            """.trimIndent(),
        )

    // --- inferred interface over a sum's common fields ---

    @Test
    fun inferredInterface_commonFieldAccessible() =
        assertType(
            "Num",
            """
            type Light = Red { duration: Num, intensity: Num } | Yellow { duration: Num } | Green { duration: Num, direction: String }
            fun getDuration(light: Light) = light.duration
            getDuration(Red(100, 50))
            """.trimIndent(),
        )

    @Test
    fun inferredInterface_nonCommonFieldError() {
        val e =
            infer(
                """
                type Light = Red { duration: Num, intensity: Num } | Yellow { duration: Num } | Green { duration: Num, direction: String }
                fun getIntensity(light: Light) = light.intensity
                getIntensity(Red(100, 50))
                """.trimIndent(),
            ).errors.filterIsInstance<TypeError.MissingField>().single()
        assertEquals("intensity", e.field)
    }

    // --- forward reference to a constructor ---

    @Test
    fun forwardReference_constructorUsedBeforeDefinition() =
        assertType(
            "Some<Num>",
            """
            x = Some(42)
            $option
            x
            """.trimIndent(),
        )

    // --- nominal subtypes its structural interface ---

    @Test
    fun nominalVsStructural_nominalSubtypesRecord() =
        assertType(
            "Num",
            """
            type Money = Money { value: Num }
            fun getValue(r: { value: Num }) = r.value
            getValue(Money(100))
            """.trimIndent(),
        )

    // --- trees and either ---

    @Test
    fun binaryTree_construction() =
        assertType(
            "Node<Num>",
            """
            type Tree<'A> = Leaf { value: 'A } | Node { left: Tree<'A>, right: Tree<'A> }
            Node(Leaf(1), Node(Leaf(2), Leaf(3)))
            """.trimIndent(),
        )

    @Test
    fun binaryTree_fieldAccess() =
        assertType(
            "Tree<Num>",
            """
            type Tree<'A> = Leaf { value: 'A } | Node { left: Tree<'A>, right: Tree<'A> }
            t = Node(Leaf(1), Node(Leaf(2), Leaf(3)))
            t.left
            """.trimIndent(),
        )

    @Test
    fun eitherType_leftConstructor() =
        assertType(
            "Left<String>",
            """
            type Either<'A, 'B> = Left { value: 'A } | Right { value: 'B }
            Left("error")
            """.trimIndent(),
        )

    @Test
    fun eitherType_rightConstructor() =
        assertType(
            "Right<Num>",
            """
            type Either<'A, 'B> = Left { value: 'A } | Right { value: 'B }
            Right(42)
            """.trimIndent(),
        )

    @Test
    fun eitherType_bothSubtypeParent() =
        assertType(
            "Either<String, Num>",
            """
            type Either<'A, 'B> = Left { value: 'A } | Right { value: 'B }
            if true then Left("error") else Right(42)
            """.trimIndent(),
        )

    @Test
    fun multipleTypes_independent() =
        assertType(
            "{ l: Cons<String>, o: Some<Num> }",
            """
            $option
            $list
            { o = Some(42), l = Cons("hello", Nil) }
            """.trimIndent(),
        )

    // --- generic functions over type defs ---

    @Test
    fun genericFunction_wrappingIntoTypeDef() =
        assertType(
            "(A) -> Some<A>",
            """
            $option
            fun wrap(x: 'A) = Some(x)
            wrap
            """.trimIndent(),
        )

    @Test
    fun genericFunction_extractingFromRecord() =
        assertType(
            "({ value: A }) -> A",
            """
            $option
            fun unwrap(s: { value: 'A }) = s.value
            unwrap
            """.trimIndent(),
        )

    // --- error cases ---

    @Test
    fun constructorResultUsedInIncompatibleContext() {
        val errors =
            infer(
                """
                $option
                Some(42) + 1
                """.trimIndent(),
            ).errors
        assertEquals(1, errors.size, "errors: $errors")
        val e = errors[0]
        assertIs<TypeError.TypeMismatch>(e)
        assertEquals("Num", Type.print(e.supertype))
    }

    @Test
    fun constructorPassedToFunctionExpectingDifferentType() {
        val errors =
            infer(
                """
                type Wrapper<'A> = Wrapper { value: 'A }
                fun addOne(w: Wrapper<Num>) = w.value + 1
                addOne(Wrapper("not a number"))
                """.trimIndent(),
            ).errors
        assertEquals(1, errors.size, "errors: $errors")
        val e = errors[0]
        assertIs<TypeError.TypeMismatch>(e)
        assertEquals("String", Type.print(e.subtype))
        assertEquals("Num", Type.print(e.supertype))
    }

    // --- composed generic types ---

    @Test
    fun crossTypeComposition_resultContainingOption() =
        assertType(
            "Ok<Some<Num>>",
            """
            $option
            type Result<'A, 'E> = Ok { value: 'A } | Err { error: 'E }
            Ok(Some(42))
            """.trimIndent(),
        )

    @Test
    fun pairOfDistinctNominalTypes() =
        assertType(
            "Pair<Money, Person>",
            """
            type Money = Money { value: Num }
            type Person = Person { name: String, age: Num }
            type Pair<'A, 'B> = Pair { fst: 'A, snd: 'B }
            Pair(Money(100), Person("Alice", 30))
            """.trimIndent(),
        )

    @Test
    fun threeDeepNesting() =
        assertType(
            "Box<Some<Cons<Num>>>",
            """
            type Box<'A> = Box { inner: 'A }
            $option
            $list
            Box(Some(Cons(1, Nil)))
            """.trimIndent(),
        )

    @Test
    fun fieldAccessThroughComposedTypes() =
        assertType(
            "Num",
            """
            type Box<'A> = Box { inner: 'A }
            $option
            b = Box(Some(42))
            b.inner.value
            """.trimIndent(),
        )

    // --- type variable name preservation ---

    @Test
    fun typeDef_preservesTypeVarName_inConstructor() =
        assertType(
            "(L) -> Left<L>",
            """
            type Either<'L, 'R> = Left { value: 'L } | Right { value: 'R }
            Left
            """.trimIndent(),
        )

    @Test
    fun typeDef_preservesMultipleTypeVarNames_inConstructor() =
        assertType(
            "((Key) -> Value) -> Map<Key, Value>",
            """
            type Map<'Key, 'Value> = Map { get: 'Key -> 'Value }
            Map
            """.trimIndent(),
        )

    // --- Any and Nothing field types ---

    @Test
    fun typeDef_anyFieldType() =
        assertType(
            "Box",
            """
            type Box = Box { item: Any }
            Box(42)
            """.trimIndent(),
        )

    @Test
    fun typeDef_nothingFieldType() =
        assertType(
            "(Nothing) -> Never",
            """
            type Never = Never { absurd: Nothing }
            Never
            """.trimIndent(),
        )
}
