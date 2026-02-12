package klein.types

import kotlin.test.Test

class MuubSimplificationTest {
    @Test
    fun siblings_bareEnumConstructors_joinToParent() {
        assertType(
            "MyBool",
            infer(
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
            infer(
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
            infer(
                """
                type Animal = Dog { name: String } | Cat { name: String }
                if true then Dog("Fido") else Cat("Whiskers")
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun siblings_multipleTypeParams_mergeByPosition() {
        assertType(
            "Either<String | Num, Num | Bool>",
            infer(
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
            infer(
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
            infer(
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
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                xs = Cons(1, Nil)
                if true then xs else xs.tail
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun nested_siblingsSimplifyInsideTypeArg() {
        assertType(
            "Cons<Option<Num>>",
            infer(
                """
                type Option<'A> = None | Some { value: 'A }
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                Cons(if true then None else Some(42), Nil)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun functionReturn_siblingsSimplifyToParent() {
        assertType(
            "(Bool) -> Option<Num>",
            infer(
                """
                type Option<'A> = None | Some { value: 'A }
                fun maybe(b) = if b then Some(42) else None
                maybe
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun sameConstructor_sameTypeArg_staysAsConstructor() {
        assertType(
            "Cons<Num>",
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                if true then Cons(1, Nil) else Cons(2, Nil)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun sameConstructor_differentTypeArgs_mergesTypeArgs() {
        assertType(
            "Cons<Num | String>",
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                if true then Cons(1, Nil) else Cons("hi", Nil)
                """.trimIndent(),
            ),
        )
    }
}
