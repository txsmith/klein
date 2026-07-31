package klein.interp

import klein.interp.Value.VNum
import klein.interp.Value.VStruct
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConstructorEvalTest {
    @Test
    fun constructionProducesTaggedStruct() {
        val v =
            runSource(
                """
                type Shape = Circle { radius: Num } | Point
                Circle(3)
                """,
            )
        assertIs<VStruct>(v)
        assertEquals("Circle", v.tag)
        assertEquals(mapOf("radius" to VNum(3.0)), v.fields)
    }

    @Test
    fun constructorFieldAccess() =
        assertEvaluatesTo(
            VNum(3.0),
            """
            type Shape = Circle { radius: Num } | Point
            Circle(3).radius
            """,
        )

    @Test
    fun nullaryConstructorIsAValue() {
        val v =
            runSource(
                """
                type Shape = Circle { radius: Num } | Point
                Point
                """,
            )
        assertIs<VStruct>(v)
        assertEquals("Point", v.tag)
        assertTrue(v.fields.isEmpty())
    }

    @Test
    fun genericConstructors() =
        assertEvaluatesTo(
            VNum(1.0),
            """
            type List<'A> = Cons { head: 'A, tail: List<'A> } | Nil
            Cons(1, Nil).head
            """,
        )

    @Test
    fun constructorAsFunctionValue() =
        assertEvaluatesTo(
            VNum(7.0),
            """
            type Box = Box { value: Num }
            fun make(f: (Num) -> Box, n: Num): Box = f(n)
            make(Box, 7).value
            """,
        )

    @Test
    fun constructorBinderDestructuring() =
        assertEvaluatesTo(
            VNum(5.0),
            """
            type Circle = Circle { radius: Num }
            Circle c = Circle(5)
            c.radius
            """,
        )

    @Test
    fun constructorFieldDestructuring() =
        assertEvaluatesTo(
            VNum(5.0),
            """
            type Circle = Circle { radius: Num }
            Circle { radius } = Circle(5)
            radius
            """,
        )

    @Test
    fun upcastAscriptionKeepsTagAndFields() {
        val v =
            runSource(
                """
                type Animal = Dog { name: String } | Cat { name: String }
                (Dog("Rex") : Animal)
                """,
            )
        assertIs<VStruct>(v)
        assertEquals("Dog", v.tag)
        assertEquals(mapOf("name" to Value.VStr("Rex")), v.fields)
    }

    @Test
    fun nominalValueIntoStructuralParam() =
        assertEvaluatesTo(
            VNum(100.0),
            """
            type Money = Money { value: Num }
            fun getValue(r: { value: Num }): Num = r.value
            getValue(Money(100))
            """,
        )

    @Test
    fun chainedNominalFieldAccess() =
        assertEvaluatesTo(
            VNum(500.0),
            """
            type Money = Money { value: Num }
            type Account = Account { balance: Money, owner: String }
            fun getBalance(a: Account): Num = a.balance.value
            getBalance(Account(Money(500), "Alice"))
            """,
        )

    @Test
    fun nestedGenericAccess() =
        assertEvaluatesTo(
            VNum(42.0),
            """
            type Wrapper<'A> = Wrapper { inner: 'A }
            type Box<'A> = Box { content: Wrapper<'A> }
            fun unwrapBox(b: Box<Num>): Num = b.content.inner
            unwrapBox(Box(Wrapper(42)))
            """,
        )

    @Test
    fun genericConstructorAsFunctionValue() =
        assertEvaluatesTo(
            VNum(9.0),
            """
            type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
            fun build(f: (Num, List<Num>) -> List<Num>): List<Num> = f(9, Nil)
            fun headOr(xs: List<Num>, d: Num): Num = match xs
              Cons { head } -> head
              Nil -> d
            headOr(build(Cons), 0)
            """,
        )

    @Test
    fun heterogeneousTypeParams() =
        assertEvaluatesTo(
            Value.VStr("hello"),
            """
            type Pair<'A, 'B> = Pair { fst: 'A, snd: 'B }
            Pair(42, "hello").snd
            """,
        )

    @Test
    fun nestedDataShapeIsFullyMaterialized() =
        assertEquals(
            VStruct("Cons", mapOf("head" to VNum(1.0), "tail" to VStruct("Nil", emptyMap()))),
            runSource(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                type Holder = Holder { list: List<Num> }
                Holder(Cons(1, Nil)).list
                """,
            ),
        )

    @Test
    fun commonFieldAcrossDifferentlyShapedVariants() =
        assertEvaluatesTo(
            VNum(130.0),
            """
            type Light = Red { duration: Num, intensity: Num } | Yellow { duration: Num } | Green { duration: Num, direction: String }
            fun getDuration(light: Light) = light.duration
            getDuration(Red(100, 50)) + getDuration(Yellow(30))
            """,
        )

    @Test
    fun closureStoredInNominalField() =
        assertEvaluatesTo(
            VNum(42.0),
            """
            type Table<'K, 'V> = Table { get: 'K -> 'V }
            t = Table(|k: Num -> k + 1|)
            t.get(41)
            """,
        )

    @Test
    fun phantomTypeParamIsRuntimeInvisible() =
        assertEvaluatesTo(
            VNum(8.0),
            """
            type Phantom<'A> = Phantom { tag: Num }
            fun useNum(p: Phantom<Num>): Num = p.tag
            fun useStr(p: Phantom<String>): Num = p.tag
            q: Phantom<'A> = Phantom(4)
            useNum(q) + useStr(q)
            """,
        )

    @Test
    fun recordPatternDestructuresNominal() =
        assertEvaluatesTo(
            Value.VStr("a"),
            """
            type Person = Person { name: String, age: Num }
            someone = Person("a", 1)
            { name } = someone
            name
            """,
        )

    @Test
    fun recordPatternDestructuresSumValue() =
        assertEvaluatesTo(
            Value.VStr("d"),
            """
            type Pet = Dog { name: String, legs: Num } | Cat { name: String, lives: Num }
            pet: Pet = Dog("d", 4)
            { name } = pet
            name
            """,
        )

    @Test
    fun binderWithFieldsBindsBothFromOneEvaluation() =
        assertEvaluatesTo(
            VNum(4.0),
            """
            type Shape = Circle { radius: Num } | Square { side: Num }
            c0: Circle = Circle(2)
            Circle c { radius } = c0
            c.radius + radius
            """,
        )

    @Test
    fun contravariantConsumerConsumes() =
        assertEvaluatesTo(
            Value.VStr("Rex"),
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Consumer<'A> = Consumer { consume: 'A -> String }
            fun runConsumer(c: Consumer<Dog>): String = c.consume(Dog("Rex", "Poodle"))
            runConsumer(Consumer(|a -> a.name|))
            """,
        )

    @Test
    fun crossFamilyStructuralJoin() =
        assertEvaluatesTo(
            Value.VStr("Fido"),
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Vehicle = Car { name: String, wheels: Num } | Bike { name: String }
            v = if true then Dog("Fido", "Labrador") else Car("Tesla", 4)
            v.name
            """,
        )

    @Test
    fun dataValuePrinting() =
        assertEquals(
            "Circle(1)",
            klein.interp.Value.print(
                runSource(
                    """
                    type Shape = Circle { radius: Num } | Point
                    Circle(1)
                    """,
                ),
            ),
        )
}
