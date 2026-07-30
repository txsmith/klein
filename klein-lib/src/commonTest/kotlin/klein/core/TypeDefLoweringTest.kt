package klein.core

import kotlin.test.Test

/**
 * Golden lowering spec for type definitions (surface [klein.surface.TypeDef]) -> core.
 *
 * A `type` declaration is erased as a *type*; what survives is its **constructors**, which become
 * ordinary scope-bound values in the enclosing `scope` (EnterScope). Constructors are eta-expanded
 * lambdas over MakeData:
 *
 *  - a constructor *with fields* lowers to a [Lambda] named after the constructor, whose body is a
 *    [MakeData] carrying the tag; each field becomes one param in declaration order, named after
 *    the field, e.g. `Box { value: Num }` -> `fun Box/1 -> Box{value: value[0;0]}`;
 *  - a *nullary* constructor binds as a bare [MakeData] (no lambda), e.g. `Red` -> `Red{}`.
 *
 * Construction is an ordinary `Apply` of the bound constructor (`Box(42)` -> `Box[0;0](42)`);
 * folding a saturated construction to a direct MakeData is an optimizer concern, not v1. The type
 * name, all field *types*, and type params (`<'A>`) are erased; only the tag and field *names*
 * survive. Constructor binds share ONE slot sequence with ordinary val/fun binds in textual order,
 * so a `type` with K constructors occupies K consecutive slots and a following val continues it.
 */
class TypeDefLoweringTest {
    // A nullary sum type: each constructor binds as a bare MakeData in declaration order, and the
    // erased type contributes no bind of its own. The trailing `Green` reads back its slot.
    @Test
    fun nullarySum_constructorsBindAsBareData() =
        assertLowersTo(
            """
            type Color = Red | Green | Blue
            Green
            """,
            """
            scope
              bind Red#0 = Red{}
              bind Green#1 = Green{}
              bind Blue#2 = Blue{}
              Green[0;1]
            """,
        )

    // A single-field constructor eta-expands to a one-arg lambda over MakeData. `Box(42)` is an
    // ordinary Apply of that bound lambda.
    @Test
    fun singleFieldConstructor_appliedInConstruction() =
        assertLowersTo(
            """
            type Box = Box { value: Num }
            Box(42)
            """,
            """
            scope
              bind Box#0 = fun Box/1 -> Box{value: value[0;0]}
              Box[0;0](42)
            """,
        )

    // The same constructor referenced *bare* (not applied) is just the bound lambda value.
    @Test
    fun singleFieldConstructor_referencedBareIsTheLambda() =
        assertLowersTo(
            """
            type Money = Money { value: Num }
            Money
            """,
            """
            scope
              bind Money#0 = fun Money/1 -> Money{value: value[0;0]}
              Money[0;0]
            """,
        )

    // A two-field constructor becomes a fun/2; fields keep declaration order, each param slotting
    // in order (name[0;0], age[0;1]).
    @Test
    fun twoFieldConstructor_multiArgLambda() =
        assertLowersTo(
            """
            type Person = Person { name: String, age: Num }
            Person("Alice", 30)
            """,
            """
            scope
              bind Person#0 = fun Person/2 -> Person{name: name[0;0], age: age[0;1]}
              Person[0;0]("Alice", 30)
            """,
        )

    // A sum type mixing a nullary and a field constructor: Nil binds as bare data (#0), Cons as a
    // lambda (#1). `Cons(1, Nil)` reads both back by slot.
    @Test
    fun mixedSum_nullaryAndFieldConstructor() =
        assertLowersTo(
            """
            type List = Nil | Cons { head: Num, tail: List }
            Cons(1, Nil)
            """,
            """
            scope
              bind Nil#0 = Nil{}
              bind Cons#1 = fun Cons/2 -> Cons{head: head[0;0], tail: tail[0;1]}
              Cons[0;1](1, Nil[0;0])
            """,
        )

    // Generic type: the type parameter `'A` and the field's type are erased; only the field name
    // `value` survives on the MakeData. Nullary `None` still binds as bare data.
    @Test
    fun genericType_typeParamsErased() =
        assertLowersTo(
            """
            type Option<'A> = None | Some { value: 'A }
            Some(42)
            """,
            """
            scope
              bind None#0 = None{}
              bind Some#1 = fun Some/1 -> Some{value: value[0;0]}
              Some[0;1](42)
            """,
        )

    // Slot sharing: a val defined after a TypeDef takes the NEXT slot after the constructors. Here
    // Red/Green/Blue are #0..#2 and `favorite` is #3; its body reads Green[0;1].
    @Test
    fun constructorAndValShareOneSlotSequence() =
        assertLowersTo(
            """
            type Color = Red | Green | Blue
            favorite = Green
            favorite
            """,
            """
            scope
              bind Red#0 = Red{}
              bind Green#1 = Green{}
              bind Blue#2 = Blue{}
              bind favorite#3 = Green[0;1]
              favorite[0;3]
            """,
        )

    // A constructor used in application position as a first-class *argument* (not itself applied):
    // `Box` is passed to `apply`, so it lowers to a bare Var reading its bind slot.
    @Test
    fun constructorPassedAsArgument() =
        assertLowersTo(
            """
            type Box = Box { value: Num }
            apply = |f, x -> f(x)|
            apply(Box, 1)
            """,
            """
            scope
              bind Box#0 = fun Box/1 -> Box{value: value[0;0]}
              bind apply#1 = fun apply/2 -> f[0;0](x[0;1])
              apply[0;1](Box[0;0], 1)
            """,
        )
}
