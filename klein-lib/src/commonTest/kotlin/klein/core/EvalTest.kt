package klein.core

import klein.interp.Value
import kotlin.test.Test

/**
 * End-to-end evaluation: source -> lower -> [Machine] -> value. Covers the surface->machine seam that
 * golden lowering tests can't — specifically that a desugared arm's body runs one scope deeper than
 * its match site, so any outer reference inside an `if`/`&&`/`||` branch must resolve at depth+1.
 */
class EvalTest {
    @Test
    fun arithmeticPrecedence() = assertEvaluatesTo(Value.VNum(7.0), "1 + 2 * 3")

    // `if`/`and`/`or` desugar to a `match`, whose arm bodies run one scope deeper. A branch that reads
    // an enclosing binding (`x`) crashes if the lowerer emits it at the match-site depth.
    @Test
    fun ifThenBranchReadsOuterVar() =
        assertEvaluatesTo(Value.VNum(1.0), "fun f(x: Num) = if x < 2 then x else 99\nf(1)")

    @Test
    fun ifElseBranchReadsOuterVar() =
        assertEvaluatesTo(Value.VNum(5.0), "fun f(x: Num) = if x < 2 then 99 else x\nf(5)")

    @Test
    fun andRightOperandReadsOuterVar() =
        assertEvaluatesTo(Value.VBool(true), "fun f(x: Num) = x > 0 and x < 10\nf(5)")

    @Test
    fun orRightOperandReadsOuterVar() =
        assertEvaluatesTo(Value.VBool(false), "fun f(x: Num) = x > 10 or x < 0\nf(5)")

    // The original repro: recursion through an `if` whose branches read the parameter.
    @Test
    fun recursiveFib() =
        assertEvaluatesTo(
            Value.VNum(55.0),
            "fun fib(n: Num): Num = if n < 2 then n else fib(n - 1) + fib(n - 2)\nfib(10)",
        )

    @Test
    fun matchRecordArm() =
        assertEvaluatesTo(
            Value.VNum(7.0),
            "fun f(p: { x: Num }?) = match p\n  null -> 0\n  { x } -> x\nf({ x = 7 })",
        )

    @Test
    fun safeFieldAccessPresent() = assertEvaluatesTo(Value.VNum(1.0), "p = { x = 1 }\np?.x")

    @Test
    fun safeFieldAccessNull() =
        assertEvaluatesTo(Value.VNull, "fun f(p: { x: Num }?) = p?.x\nf(null)")

    @Test
    fun constructorDestructure() =
        assertEvaluatesTo(
            Value.VNum(5.0),
            "type Circle = Circle { radius: Num }\nCircle c = Circle(5)\nc.radius",
        )
}
