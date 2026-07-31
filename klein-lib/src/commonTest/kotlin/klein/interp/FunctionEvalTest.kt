package klein.interp

import klein.interp.Value.VBool
import klein.interp.Value.VNum
import kotlin.test.Test

class FunctionEvalTest {
    @Test
    fun immediateLambdaCall() = assertEvaluatesTo(VNum(42.0), "|x: Num -> x + 1|(41)")

    @Test
    fun thunk() = assertEvaluatesTo(VNum(42.0), "|42|()")

    @Test
    fun twoParameterLambda() = assertEvaluatesTo(VNum(5.0), "|a: Num, b: Num -> a + b|(2, 3)")

    @Test
    fun closuresCaptureLexically() =
        assertEvaluatesTo(
            VNum(15.0),
            """
            fun makeAdder(n: Num): (Num) -> Num = |x -> x + n|
            addTen = makeAdder(10)
            addTen(5)
            """,
        )

    @Test
    fun higherOrderFunction() =
        assertEvaluatesTo(
            VNum(9.0),
            """
            fun twice(f: (Num) -> Num, x: Num): Num = f(f(x))
            twice(|n -> n * 3|, 1)
            """,
        )

    @Test
    fun implicitParam() {
        assertEvaluatesTo(
            VBool(true),
            """
            big: (Num) -> Bool = |. > 100|
            big(101)
            """,
        )
        assertEvaluatesTo(
            VNum(3.0),
            """
            f: ({ x: Num, y: Num }) -> Num = |.x + .y|
            f({ x = 1, y = 2 })
            """,
        )
    }

    // The implicit param resolves through the lowering environment; inside a desugared arm it
    // sits one scope deeper than the lambda's params.
    @Test
    fun implicitParamInsideIfBranch() =
        assertEvaluatesTo(
            VNum(5.0),
            """
            f: Num -> Num = |if . > 1 then . else 0|
            f(5)
            """,
        )

    @Test
    fun implicitParamInsideAndRightOperand() =
        assertEvaluatesTo(
            VBool(true),
            """
            p: Num -> Bool = |. > 0 and . < 10|
            p(5)
            """,
        )

    @Test
    fun recursiveFib() =
        assertEvaluatesTo(
            VNum(55.0),
            """
            fun fib(n: Num): Num = if n < 2 then n else fib(n - 1) + fib(n - 2)
            fib(10)
            """,
        )

    // The lambda body's `n` is Var(depth 1, slot 0) relative to the captured scope. A machine
    // that parents the body on the call-site scope instead of the closure's captured scope
    // would resolve those coordinates to caller's `m` (999) and yield 1099.
    @Test
    fun closureBodyWalksCapturedScopeNotCallSiteScope() =
        assertEvaluatesTo(
            VNum(101.0),
            """
            fun make(n: Num): (Num) -> Num = |x -> x + n|
            f = make(1)
            fun caller(m: Num): Num = f(100)
            caller(999)
            """,
        )

    @Test
    fun recursion() =
        assertEvaluatesTo(
            VNum(120.0),
            """
            fun fact(n: Num): Num = if n <= 1 then 1 else n * fact(n - 1)
            fact(5)
            """,
        )

    @Test
    fun mutualRecursion() =
        assertEvaluatesTo(
            VBool(true),
            """
            fun isEven(n: Num): Bool = if n == 0 then true else isOdd(n - 1)
            fun isOdd(n: Num): Bool = if n == 0 then false else isEven(n - 1)
            isEven(10)
            """,
        )

    @Test
    fun namedFunPassedToHigherOrderFun() =
        assertEvaluatesTo(
            VNum(40.0),
            """
            fun double(x: Num): Num = x * 2
            fun twice(f: (Num) -> Num, x: Num): Num = f(f(x))
            twice(double, 10)
            """,
        )

    @Test
    fun namedFunStoredInValThenCalled() =
        assertEvaluatesTo(
            VNum(42.0),
            """
            fun double(x: Num): Num = x * 2
            d = double
            d(21)
            """,
        )

    @Test
    fun discardParamSlotAlignment() =
        assertEvaluatesTo(VNum(2.0), "|_: Num, x: Num -> x|(99, 2)")

    @Test
    fun discardedArgumentIsStillEvaluated() =
        assertRunFails(
            """
            f: (Num) -> Num = |_ -> 0|
            f(1 / 0)
            """,
            "Division by zero",
        )

    @Test
    fun unusedCallbackIsNeverInvoked() =
        assertEvaluatesTo(
            VNum(5.0),
            """
            fun withCb(f: (Num) -> Num, x: 'T): 'T = x
            withCb(|n -> n / 0|, 5)
            """,
        )

    @Test
    fun implicitParamAsCallArgument() =
        assertEvaluatesTo(
            VNum(42.0),
            """
            fun inc(n: Num): Num = n + 1
            f: (Num) -> Num = |inc(.)|
            f(41)
            """,
        )

    @Test
    fun bareImplicitIdentity() =
        assertEvaluatesTo(
            VNum(7.0),
            """
            f: ('A) -> 'A = |.|
            f(7)
            """,
        )

    @Test
    fun implicitParamInsideExplicitParamLambda() =
        assertEvaluatesTo(
            VNum(42.0),
            """
            f: (Num) -> (Num) -> Num = |x: Num -> |. * 2||
            f(99)(21)
            """,
        )

    @Test
    fun genericFunctionAtTwoTypes() =
        assertEvaluatesTo(
            VNum(1.0),
            """
            fun id(x: 'T): 'T = x
            if id(true) then id(1) else 2
            """,
        )

    @Test
    fun genericFunPassedWhereGroundFunExpected() =
        assertEvaluatesTo(
            VNum(3.0),
            """
            fun id(a: 'T): 'T = a
            fun useN(g: (Num) -> Num, x: Num): Num = g(x)
            useN(id, 3)
            """,
        )

    @Test
    fun chainedApplication() =
        assertEvaluatesTo(
            VNum(15.0),
            """
            fun makeAdder(n: Num): (Num) -> Num = |x -> x + n|
            makeAdder(10)(5)
            """,
        )

    @Test
    fun contravariantParamCalledWithWiderRecord() =
        assertEvaluatesTo(
            VNum(10.0),
            """
            fun use(f: ({ x: Num, y: Num }) -> Num): Num = f({ x = 1, y = 2 })
            use(|r: { x: Num } -> r.x * 10|)
            """,
        )

    @Test
    fun covariantResultReadThroughNarrowerType() =
        assertEvaluatesTo(
            VNum(5.0),
            """
            fun use(f: (Num) -> { x: Num }): Num = f(0).x
            use(|n: Num -> { x = 5, y = 6 }|)
            """,
        )

    @Test
    fun branchJoinedFunctionsCalled() =
        assertEvaluatesTo(
            VNum(2.0),
            """
            f = |x: { a: Num, b: Num } -> x.a|
            g = |x: { b: Num, c: Num } -> x.b|
            h = if false then f else g
            h({ a = 1, b = 2, c = 3 })
            """,
        )

    @Test
    fun polyMonoBranchJoinCalled() =
        assertEvaluatesTo(
            VNum(21.0),
            """
            fun id(x: 'T): 'T = x
            fun g(n: Num): Num = n * 2
            h = if true then id else g
            h(21)
            """,
        )

    @Test
    fun ascribedBareLambdaApplied() =
        assertEvaluatesTo(VNum(42.0), "(|x -> x + 1| : (Num) -> Num)(41)")

    @Test
    fun deepTailRecursionTerminates() =
        assertEvaluatesTo(
            VNum(0.0),
            """
            fun loop(n: Num): Num = if n == 0 then 0 else loop(n - 1)
            loop(100000)
            """,
        )
}
