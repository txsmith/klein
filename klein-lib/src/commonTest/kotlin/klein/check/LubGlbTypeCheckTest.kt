package klein.check

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Least-upper-bound / greatest-lower-bound of branch types, ported from the SimpleSub
 * `LubGlbSimplificationTest`. Every case is an `if`/`else` whose branch types must join.
 *
 * SimpleSub represented merged type args as unions/intersections and unresolved invariant args as
 * `where`-clauses. Path G has none of those, so those cases are adjusted to args that have a real
 * nominal join/meet (covariant → parent, contravariant → subtype), or become an error when no
 * Path-G type can express the result (invariant with different args). A join with no common
 * supertype — no nominal ancestor and no shared field — is the top, which is an error, never an
 * inferred `Any` or `{}` (the same top); likewise a meet with no common subtype is `Nothing`, an
 * error. Meet (`glb`) is exercised
 * through contravariant args and glb-of-record function joins rather than inference-from-usage.
 * Dropped as un-portable: `where`-clause bounds (need M6 declared bounds), inference-from-usage
 * (`fun f(x) = …`), and free phantom-param cases.
 */
class LubGlbTypeCheckTest {
    private fun assertLub(
        expected: String,
        src: String,
    ) {
        val r = infer(src)
        assertTrue(r.errors.isEmpty(), "unexpected errors: ${r.errors}")
        assertEquals(expected, klein.Type.print(r.type.toLegacy()))
    }

    private fun cannotJoin(src: String) = assertTrue(infer(src).errors.isNotEmpty(), "expected a join failure")

    // --- Sibling constructors join to their parent ---

    @Test
    fun siblings_bareEnums_joinToParent() =
        assertLub("MyBool", "type MyBool = True | False\nif true then True else False")

    @Test
    fun siblings_nonExhaustive_stillJoinToParent() =
        assertLub("Light", "type Light = Red | Yellow | Green\nif true then Red else Yellow")

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
        cannotJoin("if true then { x = 1 } else { y = 2 }")

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

    @Test
    fun synthOnePolyBranch_isError() =
        cannotJoin("fun id(x: 'T): 'T = x\nfun g(n: Num): Num = n\nif true then id else g")

    @Test
    fun checkOnePolyBranch_instantiatesToDemand() =
        assertLub(
            "(Num) -> Num",
            "fun id(x: 'T): 'T = x\nfun g(n: Num): Num = n\nfoo: (Num) -> Num = if true then id else g\nfoo",
        )

    @Ignore
    @Test
    fun polymorphicBranches_join() {
        val r = infer("fun id(a: 'T): 'T = a\nif true then id else id")
        assertTrue(r.errors.isEmpty())
        assertTrue(r.type is Type.TForall)
    }
}
