package klein.interp

import klein.interp.Value.VNum
import klein.interp.Value.VStr
import kotlin.test.Test

class MatchEvalTest {
    @Test
    fun literalArmsSelectByValue() {
        assertEvaluatesTo(
            VStr("two"),
            """
            fun name(n: Num): String = match n
              1 -> "one"
              2 -> "two"
              _ -> "many"
            name(2)
            """,
        )
        assertEvaluatesTo(
            VStr("many"),
            """
            fun name(n: Num): String = match n
              1 -> "one"
              2 -> "two"
              _ -> "many"
            name(5)
            """,
        )
    }

    @Test
    fun nullArm() =
        assertEvaluatesTo(
            VNum(0.0),
            """
            fun f(p: { x: Num }?): Num = match p
              null -> 0
              { x } -> x
            f(null)
            """,
        )

    @Test
    fun recordArmBindsFields() =
        assertEvaluatesTo(
            VNum(7.0),
            """
            fun f(p: { x: Num }?): Num = match p
              null -> 0
              { x } -> x
            f({ x = 7 })
            """,
        )

    @Test
    fun dataArmsSelectByTag() =
        assertEvaluatesTo(
            VNum(3.0),
            """
            type Shape = Circle { radius: Num } | Point
            fun size(s: Shape): Num = match s
              Circle { radius } -> radius
              Point -> 0
            size(Circle(3))
            """,
        )

    @Test
    fun nullaryTagArm() =
        assertEvaluatesTo(
            VNum(0.0),
            """
            type Shape = Circle { radius: Num } | Point
            fun size(s: Shape): Num = match s
              Circle { radius } -> radius
              Point -> 0
            size(Point)
            """,
        )

    @Test
    fun constructorBinderArm() =
        assertEvaluatesTo(
            VNum(3.0),
            """
            type Shape = Circle { radius: Num } | Point
            fun size(s: Shape): Num = match s
              Circle c -> c.radius
              Point -> 0
            size(Circle(3))
            """,
        )

    @Test
    fun variablePatternBindsScrutinee() =
        assertEvaluatesTo(
            VNum(10.0),
            """
            fun f(n: Num): Num = match n
              0 -> 0
              m -> m * 2
            f(5)
            """,
        )

    @Test
    fun guardsFallThroughInOrder() {
        assertEvaluatesTo(
            VStr("big"),
            """
            fun size(n: Num): String = match n
              m if m > 100 -> "big"
              m if m > 10 -> "medium"
              _ -> "small"
            size(500)
            """,
        )
        assertEvaluatesTo(
            VStr("medium"),
            """
            fun size(n: Num): String = match n
              m if m > 100 -> "big"
              m if m > 10 -> "medium"
              _ -> "small"
            size(50)
            """,
        )
        assertEvaluatesTo(
            VStr("small"),
            """
            fun size(n: Num): String = match n
              m if m > 100 -> "big"
              m if m > 10 -> "medium"
              _ -> "small"
            size(5)
            """,
        )
    }

    @Test
    fun guardReadsBoundField() =
        assertEvaluatesTo(
            VNum(9.0),
            """
            type Shape = Circle { radius: Num } | Point
            fun f(s: Shape): Num = match s
              Circle { radius } if radius > 5 -> radius
              Circle { radius } -> 0 - radius
              Point -> 0
            f(Circle(9))
            """,
        )

    @Test
    fun recursiveListSum() =
        assertEvaluatesTo(
            VNum(6.0),
            """
            type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
            fun sum(xs: List<Num>): Num = match xs
              Nil -> 0
              Cons { head, tail } -> head + sum(tail)
            sum(Cons(1, Cons(2, Cons(3, Nil))))
            """,
        )

    @Test
    fun recursiveTreeTotal() =
        assertEvaluatesTo(
            VNum(6.0),
            """
            type Tree<'A> = Leaf { value: 'A } | Node { left: Tree<'A>, right: Tree<'A> }
            fun total(t: Tree<Num>): Num = match t
              Leaf { value } -> value
              Node { left, right } -> total(left) + total(right)
            total(Node(Leaf(1), Node(Leaf(2), Leaf(3))))
            """,
        )

    @Test
    fun sameTagGuardedArmsFallThrough() {
        fun program(legs: Double) =
            """
            type Animal = Dog { name: String, legs: Num } | Cat { name: String, lives: Num } | Snake
            fun f(a: Animal): Num = match a
              Dog d if d.legs > 3 -> d.legs
              Dog { legs } if legs > 1 -> legs
              _ -> 0
            f(Dog("rex", ${legs.toInt()}))
            """
        assertEvaluatesTo(VNum(4.0), program(4.0))
        assertEvaluatesTo(VNum(2.0), program(2.0))
        assertEvaluatesTo(VNum(0.0), program(1.0))
    }

    @Test
    fun guardedThenBareConstructorArm() {
        fun program(call: String) =
            """
            type Shape = Circle { radius: Num } | Square { side: Num }
            fun f(s: Shape): String = match s
              Circle { radius } if radius > 10 -> "big circle"
              Circle -> "circle"
              _ -> "not a circle"
            f($call)
            """
        assertEvaluatesTo(VStr("big circle"), program("Circle(11)"))
        assertEvaluatesTo(VStr("circle"), program("Circle(3)"))
        assertEvaluatesTo(VStr("not a circle"), program("Square(2)"))
    }

    @Test
    fun recordPatternMatchesTaggedValue() =
        assertEvaluatesTo(
            VStr("felix"),
            """
            type Pet = Dog { name: String, legs: Num } | Cat { name: String, lives: Num }
            fun f(p: Pet): String = match p
              { name } -> name
            f(Cat("felix", 9))
            """,
        )

    @Test
    fun constructorArmThenRecordResidual() {
        fun program(call: String) =
            """
            type Pet = Dog { name: String, legs: Num } | Cat { name: String, lives: Num }
            fun f(p: Pet): String = match p
              Dog d -> d.name
              { name } -> name
            f($call)
            """
        assertEvaluatesTo(VStr("rex"), program("Dog(\"rex\", 4)"))
        assertEvaluatesTo(VStr("felix"), program("Cat(\"felix\", 9)"))
    }

    @Test
    fun variablePatternBindsNull() =
        assertEvaluatesTo(
            Value.VNull,
            """
            fun f(n: Num?): Num? = match n
              x -> x
            f(null)
            """,
        )

    @Test
    fun nullArmThenVariableResidual() {
        fun program(arg: String) =
            """
            fun f(n: Num?): Num = match n
              null -> 0
              x -> x + 1
            f($arg)
            """
        assertEvaluatesTo(VNum(42.0), program("41"))
        assertEvaluatesTo(VNum(0.0), program("null"))
    }

    @Test
    fun guardedResidualBetweenNullAndVariableArms() {
        fun program(arg: String) =
            """
            fun f(n: Num?): Num = match n
              null -> 0
              x if x > 1 -> x
              y -> y + 1
            f($arg)
            """
        assertEvaluatesTo(VNum(0.0), program("null"))
        assertEvaluatesTo(VNum(5.0), program("5"))
        assertEvaluatesTo(VNum(2.0), program("1"))
    }

    @Test
    fun genericOrElse() {
        fun program(arg: String) =
            """
            fun orElse(d: 'T, x: 'T?): 'T = match x
              null -> d
              y -> y
            orElse(5, $arg)
            """
        assertEvaluatesTo(VNum(7.0), program("7"))
        assertEvaluatesTo(VNum(5.0), program("null"))
    }

    @Test
    fun zeroAndNullAreDistinct() {
        fun program(arg: String) =
            """
            fun f(n: Num?): String = match n
              null -> "none"
              0 -> "zero"
              _ -> "other"
            f($arg)
            """
        assertEvaluatesTo(VStr("zero"), program("0"))
        assertEvaluatesTo(VStr("none"), program("null"))
        assertEvaluatesTo(VStr("other"), program("3"))
    }

    @Test
    fun mixedNullConstructorWildcardArms() {
        fun program(arg: String) =
            """
            type Animal = Dog { name: String, legs: Num } | Cat { name: String, lives: Num } | Snake
            fun f(a: Animal?): String = match a
              null -> "none"
              Dog d -> d.name
              _ -> "other"
            f($arg)
            """
        assertEvaluatesTo(VStr("none"), program("null"))
        assertEvaluatesTo(VStr("rex"), program("Dog(\"rex\", 4)"))
        assertEvaluatesTo(VStr("other"), program("Cat(\"felix\", 9)"))
    }

    @Test
    fun nestedMatchReadsOuterArmBinder() {
        fun program(a: String, s: String) =
            """
            type Animal = Dog { name: String, legs: Num } | Snake
            type Shape = Circle { radius: Num } | Square { side: Num }
            fun f(a: Animal, s: Shape): Num = match a
              Dog d -> match s
                Circle { radius } -> radius + d.legs
                _ -> d.legs
              _ -> 0
            f($a, $s)
            """
        assertEvaluatesTo(VNum(7.0), program("Dog(\"rex\", 4)", "Circle(3)"))
        assertEvaluatesTo(VNum(4.0), program("Dog(\"rex\", 4)", "Square(2)"))
        assertEvaluatesTo(VNum(0.0), program("Snake", "Circle(3)"))
    }

    @Test
    fun complexScrutineeExpression() =
        assertEvaluatesTo(
            VStr("rex"),
            """
            type Animal = Dog { name: String, legs: Num } | Snake
            fun f(o: { pet: Animal }): String = match o.pet
              Dog { name } -> name
              _ -> "x"
            f({ pet = Dog("rex", 4) })
            """,
        )

    @Test
    fun hoistedCallScrutinee() {
        fun program(arg: String) =
            """
            fun step(x: Num, b: Bool) = if b then x + 1 else null
            fun f(b: Bool): Num = match step(0, b)
              null -> -1
              n -> n + 10
            f($arg)
            """
        assertEvaluatesTo(VNum(11.0), program("true"))
        assertEvaluatesTo(VNum(-1.0), program("false"))
    }

    @Test
    fun renamedFieldPatternBindsNewName() {
        fun program(call: String) =
            """
            type Result<'T, 'E> = Ok { value: 'T } | Err { error: 'E }
            fun unwrap(r: Result<Num, String>): Num = match r
              Ok { value = v } -> v
              Err -> 0
            unwrap($call)
            """
        assertEvaluatesTo(VNum(7.0), program("Ok(7)"))
        assertEvaluatesTo(VNum(0.0), program("Err(\"boom\")"))
    }

    @Test
    fun wildcardFieldDoesNotShadowOuterBinding() =
        assertEvaluatesTo(
            VStr("outer"),
            """
            name = "outer"
            fun f(p: { name: Num }): String = match p
              { name = _ } -> name
            f({ name = 1 })
            """,
        )

    @Test
    fun boolLiteralArms() =
        assertEvaluatesTo(
            VNum(0.0),
            """
            fun f(b: Bool): Num = match b
              true -> 1
              false -> 0
            f(false)
            """,
        )

    @Test
    fun stringLiteralArms() {
        fun program(arg: String) =
            """
            fun f(s: String): Num = match s
              "" -> 0
              "a" -> 1
              _ -> 9
            f($arg)
            """
        assertEvaluatesTo(VNum(0.0), program("\"\""))
        assertEvaluatesTo(VNum(1.0), program("\"a\""))
        assertEvaluatesTo(VNum(9.0), program("\"ab\""))
    }

    @Test
    fun eitherWithDifferentPayloadTypes() =
        assertEvaluatesTo(
            VNum(104.0),
            """
            type Either<'A, 'B> = Left { value: 'A } | Right { value: 'B }
            fun safeDiv(a: Num, b: Num): Either<String, Num> = if b == 0 then Left("div by zero") else Right(a / b)
            fun getOr(e: Either<String, Num>, fallback: Num): Num = match e
              Left { value } -> fallback
              Right { value } -> value
            getOr(safeDiv(10, 2), 0) + getOr(safeDiv(1, 0), 99)
            """,
        )

    @Test
    fun matchResultFeedsBinding() =
        assertEvaluatesTo(
            VNum(6.0),
            """
            fun classify(n: Num): Num = match n
              0 -> 0
              m -> m + 1
            x = classify(5)
            x
            """,
        )
}
