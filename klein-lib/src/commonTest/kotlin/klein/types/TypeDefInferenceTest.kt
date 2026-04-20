package klein.types

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class TypeDefInferenceTest {
    @Test
    fun basicEnum_constructorsUsable() {
        assertType(
            "True",
            infer(
                """
                type MyBool = True | False
                True
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun basicEnum_bothConstructorsJoinToParent() {
        assertType(
            "MyBool",
            infer(
                """
                type MyBool = True | False
                if true then True else False
                """.trimIndent(),
            ),
            expectedLub = "MyBool",
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
                type Bar<'A> = Bar { value: 'A, foo: Foo<'A> }

                fun mkFoo(x) = Foo(Bar(x, mkFoo(x)))
                mkFoo(42)
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
                type Bar<'A> = Bar { value: 'A, foo: Foo<'A> }

                fun mkFoo(x) = Foo(Bar(x, mkFoo(x)))
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
                type Bar<'A> = Bar { value: 'A, foo: Foo<'A> }

                fun mkFoo(x) = Foo(Bar(x, mkFoo(x)))
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
            expectedLub = "List<Num>",
        )
    }

    @Test
    fun siblingConstructors_mergeTypeArgsByPosition() {
        assertType(
            "Cons<Num | String>",
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                if true then Cons(1, Nil) else Cons("hi", Nil)
                """.trimIndent(),
            ),
            expectedLub = "Cons<Num | String>",
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
        val errors =
            inferErrors(
                """
                type Light =
                    Red { duration: Num, intensity: Num }
                  | Yellow { duration: Num }
                  | Green { duration: Num, direction: String }

                fun getIntensity(light) = light.intensity
                getIntensity(if true then Red(100, 50) else Yellow(30))
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertMissingField(errors[0], "intensity")
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
            expectedLub = "Either<String, Num>",
        )
    }

    @Test
    fun multipleTypes_independent() {
        assertType(
            "{ l: Cons<String>, o: Some<Num> }",
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

    // ============================================================
    // Type application at use sites
    // ============================================================

    @Test
    fun mixedTypesInGenericContainer_infersUnion() {
        assertType(
            "Cons<Num | String>",
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }

                Cons(1, Cons("hello", Nil))
                """.trimIndent(),
            ),
            expectedLub = "Cons<Num | String>",
        )
    }

    @Test
    fun constructorResultUsedInIncompatibleContext() {
        val errors =
            inferErrors(
                """
                type Option<'A> = None | Some { value: 'A }

                Some(42) + 1
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertMismatch(errors[0], "Some<Num>", "Num")
    }

    @Test
    fun recursiveTypeConstructionWithMixedTypes_infersUnion() {
        assertType(
            "Node<Num | String>",
            infer(
                """
                type Tree<'A> = Leaf { value: 'A } | Node { left: Tree<'A>, right: Tree<'A> }

                Node(Leaf(1), Leaf("wrong"))
                """.trimIndent(),
            ),
            expectedLub = "Node<Num | String>",
        )
    }

    @Test
    fun constructorPassedToFunctionExpectingDifferentType() {
        val errors =
            inferErrors(
                """
                type Wrapper<'A> = Wrapper { value: 'A }

                fun addOne(w) = w.value + 1
                addOne(Wrapper("not a number"))
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertMismatch(errors[0], "String", "Num")
    }

    // ============================================================
    // Composed generic types
    // ============================================================

    @Test
    fun crossTypeComposition_resultContainingOption() {
        assertType(
            "Ok<Some<Num>>",
            infer(
                """
                type Option<'A> = None | Some { value: 'A }
                type Result<'A, 'E> = Ok { value: 'A } | Err { error: 'E }
                Ok(Some(42))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun pairOfDistinctNominalTypes() {
        assertType(
            "Pair<Money, Person>",
            infer(
                """
                type Money = Money { value: Num }
                type Person = Person { name: String, age: Num }
                type Pair<'A, 'B> = Pair { fst: 'A, snd: 'B }
                Pair(Money(100), Person("Alice", 30))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun threeDeepNesting_boxWrappingSomeWrappingCons() {
        assertType(
            "Box<Some<Cons<Num>>>",
            infer(
                """
                type Box<'A> = Box { inner: 'A }
                type Option<'A> = None | Some { value: 'A }
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                Box(Some(Cons(1, Nil)))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun fieldAccessThroughComposedTypes() {
        assertType(
            "Num",
            infer(
                """
                type Box<'A> = Box { inner: 'A }
                type Option<'A> = None | Some { value: 'A }
                b = Box(Some(42))
                b.inner.value
                """.trimIndent(),
            ),
        )
    }

    // --- Type variable name preservation ---

    @Test
    fun typeDef_preservesTypeVarName_inConstructor() {
        assertType(
            "('L) -> Left<'L>",
            infer(
                """
                type Either<'L, 'R> = Left { value: 'L } | Right { value: 'R }
                Left
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun typeDef_preservesMultipleTypeVarNames_inConstructor() {
        assertType(
            "(('Key) -> 'Value) -> Map<'Key, 'Value>",
            infer(
                """
                type Map<'Key, 'Value> = Map { get: 'Key -> 'Value }
                Map
                """.trimIndent(),
            ),
        )
    }

    // --- Any and Nothing in field types ---

    @Test
    fun typeDef_anyFieldType() {
        assertType(
            "Box",
            infer(
                """
                type Box = Box { item: Any }
                Box(42)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun typeDef_nothingFieldType() {
        // A field of type Nothing means the constructor can never be applied
        // (there's no value of type Nothing)
        assertType(
            "(Nothing) -> Never",
            infer(
                """
                type Never = Never { absurd: Nothing }
                Never
                """.trimIndent(),
            ),
        )
    }
}
