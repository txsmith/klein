package klein.types

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

@Ignore("Type definitions not yet implemented - these tests specify expected behavior")
class TypeDefInferenceTest {
    @Test
    fun basicEnum_constructorsUsable() {
        assertType(
            "True",
            infer(
                """
                type Bool = True | False
                True
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun basicEnum_bothConstructorsJoinToParent() {
        assertType(
            "Bool",
            infer(
                """
                type Bool = True | False
                if true then True else False
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun singleConstructor_constructorUsable() {
        assertType(
            "Money",
            infer(
                """
                type Money = Money { value: Num }
                Money(100)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun singleConstructor_fieldAccess() {
        assertType(
            "Num",
            infer(
                """
                type Money = Money { value: Num }
                m = Money(100)
                m.value
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun sumType_constructorFieldAccess() {
        assertType(
            "Num",
            infer(
                """
                type Option = None | Some { value: Num }
                s = Some(42)
                s.value
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun genericType_constructorInference() {
        assertType(
            "Some<Num>",
            infer(
                """
                type Option<'A> = None | Some { value: 'A }
                Some(42)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun genericType_fieldAccess() {
        assertType(
            "Num",
            infer(
                """
                type Option<'A> = None | Some { value: 'A }
                s = Some(42)
                s.value
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursiveType_listNil() {
        assertType(
            "Nil",
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                Nil
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursiveType_listCons() {
        assertType(
            "Cons<Num>",
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                Cons(1, Nil)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursiveType_nestedCons() {
        assertType(
            "Cons<Num>",
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                Cons(1, Cons(2, Cons(3, Nil)))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursiveType_listFieldAccess() {
        assertType(
            "Num",
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                xs = Cons(1, Cons(2, Nil))
                xs.head
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursiveType_listTailAccess() {
        assertType(
            "List<Num>",
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                xs = Cons(1, Cons(2, Nil))
                xs.tail
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun mutualRecursion_fooUsable() {
        assertType(
            "Foo<Num>",
            infer(
                """
                type Foo<'A> = Foo { bar: Bar<'A> }
                type Bar<'A> = Bar { foo: Foo<'A> }

                fun mkFoo(x) = Foo(Bar(mkFoo(x)))
                mkFoo(42)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun mutualRecursion_barUsable() {
        assertType(
            "Bar<Num>",
            infer(
                """
                type Foo<'A> = Foo { bar: Bar<'A> }
                type Bar<'A> = Bar { foo: Foo<'A> }

                fun mkBar(x) = Bar(Foo(mkBar(x)))
                mkBar(42)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun mutualRecursion_fieldAccess() {
        assertType(
            "Bar<Num>",
            infer(
                """
                type Foo<'A> = Foo { bar: Bar<'A> }
                type Bar<'A> = Bar { foo: Foo<'A> }

                fun mkFoo(x) = Foo(Bar(mkFoo(x)))
                f = mkFoo(42)
                f.bar
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun mutualRecursion_transitiveFieldAccess() {
        assertType(
            "Foo<Num>",
            infer(
                """
                type Foo<'A> = Foo { bar: Bar<'A> }
                type Bar<'A> = Bar { foo: Foo<'A> }

                fun mkFoo(x) = Foo(Bar(mkFoo(x)))
                f = mkFoo(42)
                f.bar.foo
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun bareConstructor_joinWithTypedConstructor() {
        assertType(
            "List<Num>",
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                if true then Nil else Cons(1, Nil)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun bareConstructor_usedAsTailForTypedList() {
        assertType(
            "Cons<Num>",
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                Cons(42, Nil)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursiveVariance_sameTypeWorks() {
        assertType(
            "T<Num>",
            infer(
                """
                type T<'A> = T { x: T<'A> }

                fun mkT(v) = T(mkT(v))
                mkT(42)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun inferredInterface_commonFieldAccessible() {
        assertType(
            "Num",
            infer(
                """
                type Light =
                    Red { duration: Num, intensity: Num }
                  | Yellow { duration: Num }
                  | Green { duration: Num, direction: String }

                fun getDuration(light) = light.duration
                getDuration(Red(100, 50))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun inferredInterface_nonCommonFieldError() {
        val result =
            inferWithErrors(
                """
                type Light =
                    Red { duration: Num, intensity: Num }
                  | Yellow { duration: Num }
                  | Green { duration: Num, direction: String }

                fun getIntensity(light) = light.intensity
                getIntensity(if true then Red(100, 50) else Yellow(30))
                """.trimIndent(),
            )
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun forwardReference_constructorUsedBeforeDefinition() {
        assertType(
            "Some<Num>",
            infer(
                """
                x = Some(42)
                type Option<'A> = None | Some { value: 'A }
                x
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun constructorFunction_bareConstructor() {
        assertType(
            "Nil",
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                Nil
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun constructorFunction_passedToHigherOrder() {
        assertType(
            "Cons<Num>",
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }

                fun apply(f, x, y) = f(x, y)
                apply(Cons, 1, Nil)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun nominalVsStructural_nominalSubtypesRecord() {
        assertType(
            "Num",
            infer(
                """
                type Money = Money { value: Num }

                fun getValue(r) = r.value
                getValue(Money(100))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun binaryTree_construction() {
        assertType(
            "Node<Num>",
            infer(
                """
                type Tree<'A> = Leaf { value: 'A } | Node { left: Tree<'A>, right: Tree<'A> }

                Node(Leaf(1), Node(Leaf(2), Leaf(3)))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun binaryTree_fieldAccess() {
        assertType(
            "Tree<Num>",
            infer(
                """
                type Tree<'A> = Leaf { value: 'A } | Node { left: Tree<'A>, right: Tree<'A> }

                t = Node(Leaf(1), Node(Leaf(2), Leaf(3)))
                t.left
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun eitherType_leftConstructor() {
        assertType(
            "Left<String>",
            infer(
                """
                type Either<'A, 'B> = Left { value: 'A } | Right { value: 'B }

                Left("error")
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun eitherType_rightConstructor() {
        assertType(
            "Right<Num>",
            infer(
                """
                type Either<'A, 'B> = Left { value: 'A } | Right { value: 'B }

                Right(42)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun eitherType_bothSubtypeParent() {
        assertType(
            "Either<String, Num>",
            infer(
                """
                type Either<'A, 'B> = Left { value: 'A } | Right { value: 'B }

                if true then Left("error") else Right(42)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun multipleTypes_independent() {
        assertType(
            "{ o: Some<Num>, l: Cons<String> }",
            infer(
                """
                type Option<'A> = None | Some { value: 'A }
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }

                { o = Some(42), l = Cons("hello", Nil) }
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun multipleTypes_composedInFields() {
        assertType(
            "Some<Cons<Num>>",
            infer(
                """
                type Option<'A> = None | Some { value: 'A }
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }

                Some(Cons(1, Cons(2, Nil)))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun genericFunction_workingWithTypeDefs() {
        assertType(
            "('A) -> Some<'A>",
            infer(
                """
                type Option<'A> = None | Some { value: 'A }

                fun wrap(x) = Some(x)
                wrap
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun genericFunction_extractingFromTypeDef() {
        assertType(
            "({ value: 'A }) -> 'A",
            infer(
                """
                type Option<'A> = None | Some { value: 'A }

                fun unwrap(s) = s.value
                unwrap
                """.trimIndent(),
            ),
        )
    }
}
