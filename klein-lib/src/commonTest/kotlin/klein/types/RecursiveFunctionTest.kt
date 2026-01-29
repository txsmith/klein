package klein.types

import kotlin.test.Test
import kotlin.test.assertEquals

class RecursiveFunctionTest {
    @Test
    fun recursive_selfCall() {
        assertType(
            "({ u: 'A } as 'A) -> Nothing",
            infer(
                """
                fun f(x) = f(x.u)
                f
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursive_y_combinator() {
        assertType(
            "(('A) -> 'A) -> 'A",
            infer(
                """
              fun fix(f) = f(fix(f))
              fix
            """,
            ),
        )
    }

    @Test
    fun recursive_constantRecursion() {
        assertType(
            "(Any) -> 'A as 'A",
            infer(
                """
                fun r(a) = r
                r
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursive_monster() {
        assertType(
            "('A) -> { self: 'B, thing: 'A } as 'B",
            infer(
                """
                fun monster(x) = { thing = x, self = monster(x) }
                monster
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursive_trutru() {
        assertType(
            "((Bool) -> 'A as 'A) -> Nothing",
            infer(
                """
                fun trutru(g) = trutru(g(true))
                trutru
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursive_produce() {
        assertType(
            "(Num) -> { head: Num, tail: 'A } as 'A",
            infer(
                """
                fun produce(arg) = { head = arg, tail = produce(arg + 1) }
                produce
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursive_consume() {
        assertType(
            "({ head: Num, tail: 'A } as 'A) -> Num",
            infer(
                """
                fun consume(strm) = strm.head + consume(strm.tail)
                consume
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursive_selfRecordBounds() {
        assertType(
            "({ tail: 'A } as 'A, Any) -> Num",
            infer(
                """
                fun f(x, y) = f(x.tail, y) + f(x, y)
                f
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursive_twoRecursiveParams() {
        assertType(
            "({ tail: 'A } as 'A, { tail: 'B } as 'B) -> Num",
            infer(
                """
                fun f(x, y) = f(x.tail, y) + f(y, x)
                f
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursive_bothParamsTails() {
        assertType(
            "({ tail: 'A } as 'A, { tail: 'B } as 'B) -> Num",
            infer(
                """
                fun f(x, y) = f(x.tail, y) + f(x, y.tail)
                f
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursive_ifBranching() {
        assertType(
            "('A & { t: 'B } as 'B, { t: 'C } as 'C) -> 'A | { t: 'D } as 'D",
            infer(
                """
                fun f(x, y) = if true then x else { t = f(x.t, y.t) }
                f
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursive_lambdaNotRecursive() {
        // Lambdas bound with = cannot refer to themselves (unlike fun f(x) = ...)
        val errors =
            inferErrors(
                """
                f = |x -> f(x)|
                f
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertUnbound(errors[0], "f")
    }

    @Test
    fun recursive_simpleRecursiveType() {
        assertType(
            "(Any) -> Nothing",
            infer(
                """
                fun f(x) = f(x)
                f
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursive_join() {
        assertType(
            "('A) -> ('A) -> 'A",
            infer("|a -> |b -> if true then a else b||"),
        )
    }

    @Test
    fun recursive_joinWithRecursive() {
        // With canonicalization, identical recursive types merge
        assertType(
            "(Any) -> 'A as 'A",
            infer(
                """
                fun r(a) = r
                fun join(a, b) = if true then a else b
                join(r, r)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursive_twoRecursiveSameCycle() {
        // With canonicalization, recursive types with different arities merge
        assertType(
            "(Any) -> 'A as 'A",
            infer(
                """
                fun l(a) = l
                fun r(a, b) = r
                fun join(a, b) = if true then a else b
                join(l, r)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursive_nestedRecordSelfReference() {
        assertType(
            "{ a: 'A, b: 'A } as 'A",
            infer(
                """
                fun x() = { a = x(), b = x() }
                x()
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursive_functionReturningRecursive() {
        assertType(
            "(Any) -> { a: 'A, b: 'A } as 'A",
            infer(
                """
                fun x(v) = { a = x(v), b = x(v) }
                x
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursive_mutuallyRecursiveStyle() {
        assertType(
            "('A) -> { l: 'B, r: 'A } as 'B",
            infer(
                """
                fun f(x) = { l = f(x), r = x }
                f
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursive_yCombinatorStyle() {
        assertType(
            "(('A) -> 'A) -> 'A",
            infer("|f -> |x -> f(x(x))|(|x -> f(x(x))|)|"),
        )
    }

    @Test
    fun recursive_selfAppWithLetResult() {
        assertType(
            "('A & (('A) -> 'B)) -> 'B",
            infer(
                """
                fun x(y) =
                  z = y(y)
                  z
                x
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursive_recordFieldSelfApp() {
        assertType(
            "({ v: 'A } & (('A) -> Any)) -> Num",
            infer(
                """
                |x ->
                  y = x(x.v)
                  0
                |
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursive_omegaAppliedToIdentity() {
        assertType(
            "'A | (('A) -> 'B) as 'B",
            infer("|x -> x(x)|(|x -> x|)"),
        )
    }

    @Test
    fun recursive_ySelfApp() {
        assertType(
            "(('A) -> 'A & (('A) -> 'B) as 'B) -> 'A",
            infer(
                """
                fun x(y) = y(x(x))
                x
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursive_yXX() {
        assertType(
            "(('A) -> ('A) -> 'B) -> 'B as 'A",
            infer(
                """
                fun x(y) = y(x)(x)
                x
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursive_xYY() {
        assertType(
            "('A & (('A) -> 'B) as 'B) -> Nothing",
            infer(
                """
                fun x(y) = x(y(y))
                x
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursive_letThenIdentity() {
        assertType(
            "('A) -> 'A | (('A) -> 'B) as 'B",
            infer(
                """
                fun x(y) =
                  z = x(x)
                  y
                x
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursive_recordUWithY() {
        assertType(
            "('A) -> { u: 'A | (('A) -> 'B), v: 'C } as 'C as 'B",
            infer(
                """
                fun x(y) = { u = y, v = x(x) }
                x
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursive_recordVWithY() {
        assertType(
            "('A) -> { u: 'B, v: 'A | (('A) -> 'C) } as 'B as 'C",
            infer(
                """
                fun x(y) = { u = x(x), v = y }
                x
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursive_yXPattern() {
        assertType(
            "('A & (('B) -> Any)) -> 'A as 'B",
            infer(
                """
                fun x(y) =
                  z = y(x)
                  y
                x
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursive_fixpointStyle() {
        assertType(
            "(('A) -> 'B) -> ('C & (('C) -> 'A)) -> 'B",
            infer("|x -> |y -> x(y(y))||"),
        )
    }

    @Test
    fun recursive_arbitraryArgs() {
        assertType(
            "(Any) -> (Any) -> 'A as 'A",
            infer("|f -> |x -> f(|v -> x(x)(v)|)|(|x -> f(|v -> x(x)(v)|)|)|(|f -> |x -> f||)"),
        )
    }

    @Test
    fun mutualRecursion_isEvenIsOdd() {
        assertType(
            "(Num) -> Bool",
            infer(
                """
                fun isEven(n) = if n == 0 then true else isOdd(n - 1)
                fun isOdd(n) = if n == 0 then false else isEven(n - 1)
                isEven
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun mutualRecursion_threeWay() {
        assertType(
            "(Num) -> Num",
            infer(
                """
                fun a(x) = if x == 0 then 0 else b(x - 1)
                fun b(x) = if x == 0 then 1 else c(x - 1)
                fun c(x) = if x == 0 then 2 else a(x - 1)
                a
                """.trimIndent(),
            ),
        )
    }
}
