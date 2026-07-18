package klein.check

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Least-upper-bound / greatest-lower-bound of `if`/`else` branch types.
 *
 * Merged type args follow declared variance (covariant → parent, contravariant → subtype); an
 * invariant param with different args has no expressible result and is an error. A join with no
 * common supertype — no nominal ancestor and no shared field — is the top, which is an error, never
 * an inferred `Any` or `{}` (the same top); likewise a meet with no common subtype is `Nothing`, an
 * error. `glb` is exercised through contravariant args and glb-of-record function joins.
 */
class LubGlbTypeCheckTest {
    private fun assertLub(
        expected: String,
        src: String,
    ) {
        val r = infer(src)
        assertTrue(r.errors.isEmpty(), "unexpected errors: ${r.errors}")
        assertEquals(expected, Type.print(r.type))
    }

    private fun cannotJoin(
        src: String,
        thenType: String? = null,
        elseType: String? = null,
    ) {
        val e = infer(src).errors.single()
        assertIs<TypeError.CannotJoinBranches>(e)
        thenType?.let { assertEquals(it, Type.print(e.thenType)) }
        elseType?.let { assertEquals(it, Type.print(e.elseType)) }
    }

    // --- Sibling constructors join to their parent ---

    @Test
    fun siblings_bareEnums_joinToParent() =
        assertLub(
            "MyBool",
            """
            type MyBool = True | False
            if true then True else False
            """.trimIndent(),
        )

    @Test
    fun siblings_nonExhaustive_stillJoinToParent() =
        assertLub(
            "Light",
            """
            type Light = Red | Yellow | Green
            if true then Red else Yellow
            """.trimIndent(),
        )

    @Test
    fun siblings_withFields_joinToParent() =
        assertLub(
            "Animal",
            """
            type Animal = Dog { name: String } | Cat { name: String }
            if true then Dog("Fido") else Cat("Whiskers")
            """.trimIndent(),
        )

    @Test
    fun bareAndTypedConstructor_joinToParent() =
        assertLub(
            "List<Num>",
            """
            type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
            if true then Nil else Cons(1, Nil)
            """.trimIndent(),
        )

    @Test
    fun allConstructors_joinToParent() =
        assertLub(
            "Result<String, Num>",
            """
            type Result<'A, 'B> = Ok { value: 'A } | Err { error: 'B } | Unknown
            if true then Ok("yes") else if true then Err(404) else Unknown
            """.trimIndent(),
        )

    @Test
    fun constructorAndParent_joinToParent() =
        assertLub(
            "List<Num>",
            """
            type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
            xs = Cons(1, Nil)
            if true then xs else xs.tail
            """.trimIndent(),
        )

    @Test
    fun branchSubtypeOfOther_joinsToSupertype() =
        assertLub(
            "Animal",
            """
            type Animal = Dog { name: String } | Cat { name: String }
            a: Animal = Cat("Whiskers")
            if true then Dog("Fido") else a
            """.trimIndent(),
        )

    @Test
    fun branchSubtypeThroughCovariant_joinsToSupertype() =
        assertLub(
            "List<Animal>",
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
            xs: List<Dog> = Cons(Dog("Fido"), Nil)
            ys: List<Animal> = Cons(Cat("Whiskers"), Nil)
            if true then xs else ys
            """.trimIndent(),
        )

    @Test
    fun multipleTypeParams_eachParamTakesItsArg() =
        assertLub(
            "Either<Dog, Cat>",
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type Either<'A, 'B> = Left { value: 'A } | Right { value: 'B }
            if true then Left(Dog("Fido")) else Right(Cat("Whiskers"))
            """.trimIndent(),
        )

    @Test
    fun singleConstructor_staysAsConstructor() =
        assertLub(
            "Wrap<Num>",
            """
            type Wrapper<'A> = Wrap { value: 'A }
            Wrap(42)
            """.trimIndent(),
        )

    // --- Type-arg merging by variance ---

    @Test
    fun covariantParam_lubsArgs() =
        assertLub(
            "Cons<Animal>",
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
            if true then Cons(Dog("Fido"), Nil) else Cons(Cat("Whiskers"), Nil)
            """.trimIndent(),
        )

    @Test
    fun sharedCovariantParam_lubsArgs() =
        assertLub(
            "Either<Animal>",
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type Either<'A> = Left { value: 'A } | Right { value: 'A }
            if true then Left(Dog("Fido")) else Right(Cat("Whiskers"))
            """.trimIndent(),
        )

    @Test
    fun contravariantParam_glbsArgs() =
        assertLub(
            "Sink<Dog>",
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type Sink<'A> = Sink { consume: 'A -> String }
            x: Sink<Animal> = Sink(|a -> a.name|)
            y: Sink<Dog> = Sink(|d -> d.name|)
            if true then x else y
            """.trimIndent(),
        )

    @Test
    fun contravariantParam_siblingArgs_isError() =
        cannotJoin(
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type Sink<'A> = Sink { consume: 'A -> String }
            x: Sink<Dog> = Sink(|d -> d.name|)
            y: Sink<Cat> = Sink(|c -> c.name|)
            if true then x else y
            """.trimIndent(),
        )

    @Test
    fun invariantParam_differentArgs_cannotJoin() =
        cannotJoin(
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type Ref<'A> = Ref { get: () -> 'A, set: 'A -> String }
            x: Ref<Dog> = Ref(|Dog("Fido")|, |d -> d.name|)
            y: Ref<Animal> = Ref(|Dog("Fido")|, |a -> a.name|)
            if true then x else y
            """.trimIndent(),
        )

    @Test
    fun mixedVarianceParams_eachMergedByVariance() =
        assertLub(
            "Xform<Dog, Animal>",
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type Transform<'A, 'B> = Xform { run: ('A) -> 'B }
            if true then Xform(|a: Animal -> Dog("d")|) else Xform(|d: Dog -> Cat("c")|)
            """.trimIndent(),
        )

    @Test
    fun mixedVarianceConstructors_sameConcreteArg_collapseToConcreteParent() =
        assertLub(
            "Func<Dog>",
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type Func<'A> = In { f: ('A) -> 'A } | Out { o: 'A }
            inVal = In(|d: Dog -> d|)
            outVal = Out(Dog("Fido"))
            if true then inVal else outVal
            """.trimIndent(),
        )

    @Test
    fun mixedVarianceConstructors_differentArgs_cannotJoin() =
        cannotJoin(
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type Func<'A> = In { f: ('A) -> 'A } | Out { o: 'A }
            inVal = In(|d: Dog -> d|)
            outVal = Out(Cat("Whiskers"))
            if true then inVal else outVal
            """.trimIndent(),
        )

    // --- Unrelated nominals fall back to their structural interface ---

    @Test
    fun differentParentNominals_joinStructurally() =
        assertLub(
            "{ name: String }",
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Vehicle = Car { name: String, wheels: Num } | Bike { name: String }
            if true then Dog("Fido", "Labrador") else Car("Tesla", 4)
            """.trimIndent(),
        )

    @Test
    fun differentParentNominals_joinSharedFieldByType() =
        assertLub(
            "{ pet: Animal }",
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type Kennel = Kennel { pet: Dog }
            type Cattery = Cattery { pet: Cat }
            if true then Kennel(Dog("Fido")) else Cattery(Cat("Whiskers"))
            """.trimIndent(),
        )

    @Test
    fun nominalAndRecord_joinOnCommonFields() =
        assertLub(
            "{ name: String }",
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            if true then Dog("Fido", "Labrador") else { name = "x", weight = 5 }
            """.trimIndent(),
        )

    @Test
    fun nominalAndRecord_keepsConstructorSpecificField() =
        assertLub(
            "{ breed: String }",
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            if true then Dog("Fido", "Labrador") else { breed = "x", weight = 5 }
            """.trimIndent(),
        )

    @Test
    fun unrelatedNominals_noCommonFields_isError() =
        cannotJoin(
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type Gadget = Widget { id: Num } | Gizmo { id: Num }
            if true then Dog("Fido") else Widget(1)
            """.trimIndent(),
        )

    @Test
    fun emptyIfaceNominals_differentFamilies_isError() =
        cannotJoin(
            """
            type Light = Red | Green
            type Switch = On | Off
            if true then Red else On
            """.trimIndent(),
        )

    // --- Structural records and functions ---

    @Test
    fun records_lub_keepsCommonFields() =
        assertLub(
            "{ b: Num, c: String }",
            """
            if true then { a = 1, b = 2, c = "x" } else { b = 3, c = "y", d = true }
            """.trimIndent(),
        )

    @Test
    fun records_disjoint_isError() =
        cannotJoin("if true then { x = 1 } else { y = 2 }", thenType = "{ x: Num }", elseType = "{ y: Num }")

    @Test
    fun functions_lub_glbsParamsLubsResult() =
        assertLub(
            "(Num) -> { b: Num }",
            """
            f = |x: Num -> { a = x, b = x }|
            g = |x: Num -> { b = x, c = x }|
            if true then f else g
            """.trimIndent(),
        )

    @Test
    fun functions_glbParams_keepsAllFields() =
        assertLub(
            "({ a: Num, b: Num, c: Num }) -> Num",
            """
            f = |x: { a: Num, b: Num } -> x.a|
            g = |x: { b: Num, c: Num } -> x.b|
            if true then f else g
            """.trimIndent(),
        )

    @Test
    fun contravariantParam_glbsRecordArgs_keepsAllFields() =
        assertLub(
            "Sink<{ a: Num, b: Num, c: Num }>",
            """
            type Sink<'A> = Sink { consume: 'A -> String }
            f: Sink<{ a: Num, b: Num }> = Sink(|x -> "s"|)
            g: Sink<{ b: Num, c: Num }> = Sink(|x -> "s"|)
            if true then f else g
            """.trimIndent(),
        )

    // --- Nested / multi-layer ---

    @Test
    fun nested_siblingsInsideTypeArg() =
        assertLub(
            "Cons<Option<Num>>",
            """
            type Option<'A> = None | Some { value: 'A }
            type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
            Cons(if true then None else Some(42), Nil)
            """.trimIndent(),
        )

    @Test
    fun nested_covariantInsideCovariant() =
        assertLub(
            "Cons<List<Animal>>",
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
            x: List<Dog> = Cons(Dog("Fido"), Nil)
            y: List<Cat> = Cons(Cat("Whiskers"), Nil)
            if true then Cons(x, Nil) else Cons(y, Nil)
            """.trimIndent(),
        )

    // --- Join inside a function body ---

    @Test
    fun functionReturn_branchesJoinToParent() =
        assertLub(
            "(Bool) -> Option<Num>",
            """
            type Option<'A> = None | Some { value: 'A }
            fun maybe(b: Bool) = if b then Some(42) else None
            maybe
            """.trimIndent(),
        )

    // The polymorphic branch is instantiated against the monomorphic one (as at an application),
    // so synth mode needs no annotation.
    @Test
    fun synthOnePolyBranch_instantiatesToMono() =
        assertLub(
            "(Num) -> Num",
            """
            fun id(x: 'T): 'T = x
            fun g(n: Num): Num = n
            if true then id else g
            """.trimIndent(),
        )

    // Which branch is polymorphic doesn't matter — the join is commutative.
    @Test
    fun synthOnePolyBranch_monoThenPoly() =
        assertLub(
            "(Num) -> Num",
            """
            fun id(x: 'T): 'T = x
            fun g(n: Num): Num = n
            if true then g else id
            """.trimIndent(),
        )

    // No instantiation of the polymorphic branch fits the mono branch, so it can't be grounded.
    @Test
    fun synthOnePolyBranch_noFittingInstantiation_rejects() =
        cannotJoin(
            """
            fun id(x: 'T): 'T = x
            fun h(n: Num): String = "x"
            if true then id else h
            """.trimIndent(),
        )

    // A polymorphic value (a phantom-typed binding) is instantiated against the mono record branch;
    // the free variable is unconstrained by the record, so the join is the record.
    @Test
    fun synthPolyValueBranch_joinsWithRecord() =
        assertLub(
            "{ tag: Num }",
            """
            type Phantom<'A> = Phantom { tag: Num }
            q: Phantom<'A> = Phantom(4)
            if true then q else { tag = 8 }
            """.trimIndent(),
        )

    @Test
    fun checkOnePolyBranch_instantiatesToDemand() =
        assertLub(
            "(Num) -> Num",
            """
            fun id(x: 'T): 'T = x
            fun g(n: Num): Num = n
            foo: (Num) -> Num = if true then id else g
            foo
            """.trimIndent(),
        )

    // Two polymorphic branches join to the more general scheme (here the shared one), kept polymorphic.
    @Test
    fun polymorphicBranches_join() {
        val r =
            infer(
                """
                fun id(a: 'T): 'T = a
                if true then id else id
                """.trimIndent(),
            )
        assertTrue(r.errors.isEmpty())
        assertTrue(r.type is Type.TForall)
    }
}
