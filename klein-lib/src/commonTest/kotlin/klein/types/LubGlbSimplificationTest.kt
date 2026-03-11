package klein.types

import kotlin.test.Test

class LubGlbSimplificationTest {
    // --- Sibling constructors ---

    @Test
    fun siblings_bareEnumConstructors_joinToParent() {
        assertType(
            "MyBool",
            inferLUB(
                """
                type MyBool = True | False
                if true then True else False
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun siblings_twoOfThree_joinToParent() {
        assertType(
            "Light",
            inferLUB(
                """
                type Light = Red | Yellow | Green
                if true then Red else Yellow
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun siblings_constructorsWithFields_joinToParent() {
        assertType(
            "Animal",
            inferLUB(
                """
                type Animal = Dog { name: String } | Cat { name: String }
                if true then Dog("Fido") else Cat("Whiskers")
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun siblings_multipleTypeParams_mergeByPosition() {
        // Both type params are covariant. LUB of String, Num = Any. LUB of Num, Bool = Any.
        assertType(
            "Either<Any, Any>",
            inferLUB(
                """
                type Either<'A, 'B> = Left { value: 'A } | Right { value: 'B }
                if true then Left("error") else if true then Right(42) else if true then Left(1) else Right(true)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun bareAndTyped_joinToParent() {
        assertType(
            "List<Num>",
            inferLUB(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                if true then Nil else Cons(1, Nil)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun allConstructors_simplifyToParent() {
        assertType(
            "Result<String, Num>",
            inferLUB(
                """
                type Result<'A, 'B> = Ok { value: 'A } | Err { error: 'B } | Unknown
                if true then Ok("yes") else if true then Err(404) else Unknown
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun constructorAndParent_joinToParent() {
        assertType(
            "List<Num>",
            inferLUB(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                xs = Cons(1, Nil)
                if true then xs else xs.tail
                """.trimIndent(),
            ),
        )
    }

    // --- Same-name refs with different type args ---

    @Test
    fun sameConstructor_differentTypeArgs_mergesTypeArgs() {
        // Cons is covariant in 'A. LUB of Num and String = Any.
        assertType(
            "Cons<Any>",
            inferLUB(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                if true then Cons(1, Nil) else Cons("hi", Nil)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun sameParentRef_covariantParam_lubsTypeArgs() {
        assertType(
            "List<Animal>",
            inferLUB(
                """
                type Animal = Dog { name: String } | Cat { name: String }
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                x = Cons(Dog("Fido"), Nil)
                y = Cons(Cat("Whiskers"), Nil)
                if true then x else y
                """.trimIndent(),
            ),
        )
    }

    // --- Variance-aware type arg merging ---

    @Test
    fun sameRef_contravariantParam_glbsTypeArgs() {
        // 'A only appears in input position, so it's contravariant.
        // LUB of Sink<Dog> | Sink<Cat> GLBs the args.
        // Dog and Cat are disjoint constructors, so their GLB is Nothing.
        assertType(
            "Sink<Nothing>",
            inferLUB(
                """
                type Animal = Dog { name: String } | Cat { name: String }
                type Sink<'A> = Drain { consume: ('A) -> Unit }
                type DogBox = DogBox { value: Dog }
                type CatBox = CatBox { value: Cat }
                x = Drain(|d ->
                  _ = DogBox(d)
                |)
                y = Drain(|c ->
                  _ = CatBox(c)
                |)
                if true then x else y
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun sameRef_invariantParam_fallsBackToStructural() {
        // 'A appears in both input and output, so it's invariant.
        // Handler<Dog> | Handler<Cat> can't merge args, falls back to structural record.
        // Input position GLBs: Dog & Cat = Nothing. Output position LUBs: Dog | Cat = Animal.
        assertType(
            "{run: (Nothing) -> Animal}",
            inferLUB(
                """
                type Animal = Dog { name: String } | Cat { name: String }
                type Handler<'A> = Handle { run: ('A) -> 'A }
                type DogBox = DogBox { value: Dog }
                type CatBox = CatBox { value: Cat }
                x = Handle(|d ->
                  _ = DogBox(d)
                  d
                |)
                y = Handle(|c ->
                  _ = CatBox(c)
                  c
                |)
                if true then x else y
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun sameRef_mixedVariance_eachArgSimplifiedByVariance() {
        // 'A is contravariant (input only), 'B is covariant (output only)
        // LUB of Transform<Dog, Num> | Transform<Cat, String>
        // Contravariant arg: GLB of Dog, Cat = Nothing (disjoint constructors)
        // Covariant arg: LUB of Num, String = Any (unrelated prims)
        assertType(
            "Transform<Nothing, Any>",
            inferLUB(
                """
                type Animal = Dog { name: String } | Cat { name: String }
                type Transform<'A, 'B> = Xform { run: ('A) -> 'B }
                type DogBox = DogBox { value: Dog }
                type CatBox = CatBox { value: Cat }
                x = Xform(|d ->
                  _ = DogBox(d)
                  1
                |)
                y = Xform(|c ->
                  _ = CatBox(c)
                  "hi"
                |)
                if true then x else y
                """.trimIndent(),
            ),
        )
    }

    // --- Unrelated refs ---

    @Test
    fun unrelatedRefs_commonFields_fallBackToStructuralRecord() {
        // Dog and Fish are from different type families but both have a name field
        assertType(
            "{name: String}",
            inferLUB(
                """
                type Animal = Dog { name: String } | Cat { name: String }
                type Sea = Fish { name: String } | Coral
                if true then Dog("Fido") else Fish("Nemo")
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun unrelatedRefs_noCommonStructure_becomesTop() {
        assertType(
            "Any",
            inferLUB(
                """
                type Light = Red | Green
                type Option<'A> = None | Some { value: 'A }
                if true then Red else Some(1)
                """.trimIndent(),
            ),
        )
    }

    // --- Ref + record ---

    @Test
    fun refAndRecord_fallsBackToRecordLub() {
        assertType(
            "{name: String}",
            inferLUB(
                """
                type Animal = Dog { name: String } | Cat { name: String }
                x = Dog("Fido")
                y = { name = "bare" }
                if true then x else y
                """.trimIndent(),
            ),
        )
    }

    // --- Structural record merging (LUB, positive) ---

    @Test
    fun unrelatedRefs_commonFieldsDifferentTypes_lubsFieldTypes() {
        // Both have a `value` field but with different types.
        // LUB keeps common fields, LUBs field types.
        // LUB of Num and String = Any (unrelated prims).
        assertType(
            "{value: Any}",
            inferLUB(
                """
                type Box = NumBox { value: Num } | EmptyBox
                type Tag = StrTag { value: String } | NoTag
                if true then NumBox(42) else StrTag("hi")
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun unrelatedRefs_partialOverlap_onlyCommonFieldsKept() {
        // Dog has name + age, Fish has name + fins.
        // LUB keeps only the common field: name.
        assertType(
            "{name: String}",
            inferLUB(
                """
                type Animal = Dog { name: String, age: Num }
                type Sea = Fish { name: String, fins: Num }
                if true then Dog("Fido", 3) else Fish("Nemo", 2)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun refAndRecord_partialOverlap_onlyCommonFieldsKept() {
        // Ref has name + age, record has name + color.
        // LUB keeps only name.
        assertType(
            "{name: String}",
            inferLUB(
                """
                type Animal = Dog { name: String, age: Num }
                x = Dog("Fido", 3)
                y = { name = "bare", color = "red" }
                if true then x else y
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun refAndRecord_recordHasExtraFields_onlyCommonFieldsKept() {
        // Record is wider than the ref's structure, but LUB only keeps common fields.
        assertType(
            "{name: String}",
            inferLUB(
                """
                type Animal = Dog { name: String }
                x = Dog("Fido")
                y = { name = "bare", age = 5, color = "red" }
                if true then x else y
                """.trimIndent(),
            ),
        )
    }

    // --- Structural record merging (GLB, negative) ---

    @Test
    fun unrelatedRefs_inFunctionParam_glbKeepsAllFields() {
        // x must satisfy both Dog and Fish constraints → GLB keeps all fields.
        assertType(
            "({name: String, age: Num, fins: Num}) -> String",
            inferLUB(
                """
                type Animal = Dog { name: String, age: Num }
                type Sea = Fish { name: String, fins: Num }
                type DogBox = DogBox { value: Dog }
                type FishBox = FishBox { value: Fish }
                fun f(x) =
                    _ = DogBox(x)
                    _ = FishBox(x)
                    x.name
                f
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun refAndRecord_inFunctionParam_glbKeepsAllFields() {
        // x must satisfy both Dog and {color: String} constraints → GLB keeps all fields.
        assertType(
            "({name: String, age: Num, color: String}) -> String",
            inferLUB(
                """
                type Animal = Dog { name: String, age: Num }
                type DogBox = DogBox { value: Dog }
                fun f(x) =
                    _ = DogBox(x)
                    _ = x.color
                    x.name
                f
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun unrelatedRefs_inFunctionParam_commonFieldTypesGlbd() {
        // x must satisfy both NumBox and StrTag constraints.
        // Both have `value` but with different types.
        // GLB keeps all fields, GLBs common field types.
        // GLB of Num and String = Nothing (unrelated prims).
        assertType(
            "({value: Nothing}) -> Nothing",
            inferLUB(
                """
                type Box = NumBox { value: Num }
                type Tag = StrTag { value: String }
                type NumBoxBox = NumBoxBox { value: NumBox }
                type StrTagBox = StrTagBox { value: StrTag }
                fun f(x) =
                    _ = NumBoxBox(x)
                    _ = StrTagBox(x)
                    x.value
                f
                """.trimIndent(),
            ),
        )
    }

    // --- Nested simplification ---

    @Test
    fun nested_siblingsSimplifyInsideTypeArg() {
        assertType(
            "Cons<Option<Num>>",
            inferLUB(
                """
                type Option<'A> = None | Some { value: 'A }
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                Cons(if true then None else Some(42), Nil)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun nested_invariantInsideCovariant() {
        // List is covariant, Handler is invariant.
        // Cons<Handler<Dog>> | Cons<Handler<Cat>> → same constructor, LUB the args →
        //   Handler<Dog> | Handler<Cat> → invariant, falls back to structural
        assertType(
            "Cons<{run: (Nothing) -> Animal}>",
            inferLUB(
                """
                type Animal = Dog { name: String } | Cat { name: String }
                type Handler<'A> = Handle { run: ('A) -> 'A }
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                type DogBox = DogBox { value: Dog }
                type CatBox = CatBox { value: Cat }
                h1 = Handle(|d ->
                  _ = DogBox(d)
                  d
                |)
                h2 = Handle(|c ->
                  _ = CatBox(c)
                  c
                |)
                x = Cons(h1, Nil)
                y = Cons(h2, Nil)
                if true then x else y
                """.trimIndent(),
            ),
        )
    }

    // --- Function return ---

    @Test
    fun functionReturn_siblingsSimplifyToParent() {
        assertType(
            "(Bool) -> Option<Num>",
            inferLUB(
                """
                type Option<'A> = None | Some { value: 'A }
                fun maybe(b) = if b then Some(42) else None
                maybe
                """.trimIndent(),
            ),
        )
    }
}
