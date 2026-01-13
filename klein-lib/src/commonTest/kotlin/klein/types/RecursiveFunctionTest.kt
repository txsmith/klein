package klein.types

import kotlin.test.Test
import kotlin.test.assertTrue

class RecursiveFunctionTest {
    @Test
    fun recursive_selfCall() {
        assertType(
            "({ u: a } as a) -> Nothing",
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
            "((a) -> a) -> a",
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
            "(Any) -> a as a",
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
            "(a) -> { self: b, thing: a } as b",
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
            "((Bool) -> a as a) -> Nothing",
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
            "(Num) -> { head: Num, tail: a } as a",
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
            "({ head: Num, tail: a } as a) -> Num",
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
            "({ tail: a } as a, Any) -> Num",
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
            "({ tail: a } as a, { tail: b } as b) -> Num",
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
            "({ tail: a } as a, { tail: b } as b) -> Num",
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
            "(a & { t: b } as b, { t: c } as c) -> a | { t: d } as d",
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
        val result =
            inferWithErrors(
                """
                f = |x -> f(x)|
                f
                """.trimIndent(),
            )
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.any { it is TypeError.UnboundVariable })
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
            "(a) -> (a) -> a",
            infer("|a -> |b -> if true then a else b||"),
        )
    }

    @Test
    fun recursive_joinWithRecursive() {
        // Without canonicalization we get a union of two recursive types
        assertType(
            "(Any) -> (Any) -> a as a | (Any) -> b as b",
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
        // simple-sub with canonicalization: (⊤ -> ⊤ -> 'a) as 'a
        // Without canonicalization we get a union of two recursive types with different arities
        assertType(
            "(Any) -> (Any) -> b as b | (Any, Any) -> a as a",
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
            "{ a: a, b: a } as a",
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
            "(Any) -> { a: a, b: a } as a",
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
            "(a) -> { l: b, r: a } as b",
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
            "((a) -> a) -> a",
            infer("|f -> |x -> f(x(x))|(|x -> f(x(x))|)|"),
        )
    }

    @Test
    fun recursive_selfAppWithLetResult() {
        assertType(
            "(a & ((a) -> b)) -> b",
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
            "({ v: a } & ((a) -> Any)) -> Num",
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
            "a | ((a) -> b) as b",
            infer("|x -> x(x)|(|x -> x|)"),
        )
    }

    @Test
    fun recursive_ySelfApp() {
        assertType(
            "((a) -> a & b as b) -> a",
            infer(
                """
                fun x(y) = y(x(x))
                x
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recursive_xYY() {
        assertType(
            "(a & ((a) -> b) as b) -> Nothing",
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
            "(a) -> a | ((a) -> b) as b",
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
            "(a) -> { u: a | ((a) -> b), v: b } as b",
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
            "(a) -> { u: b, v: a | ((a) -> b) } as b",
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
            "(a & ((b) -> Any)) -> a as b",
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
            "((a) -> b) -> (c & ((c) -> a)) -> b",
            infer("|x -> |y -> x(y(y))||"),
        )
    }

    @Test
    fun recursive_arbitraryArgs() {
        assertType(
            "(Any) -> (Any) -> a as a",
            infer("|f -> |x -> f(|v -> x(x)(v)|)|(|x -> f(|v -> x(x)(v)|)|)|(|f -> |x -> f||)"),
        )
    }
}
