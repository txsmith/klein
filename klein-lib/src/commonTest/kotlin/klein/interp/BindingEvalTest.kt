package klein.interp

import klein.interp.Value.VNum
import kotlin.test.Test

class BindingEvalTest {
    @Test
    fun bindingsEvaluateInTextualOrder() =
        assertEvaluatesTo(
            VNum(30.0),
            """
            x = 10
            y = x * 2
            x + y
            """,
        )

    @Test
    fun blockValueIsLastExpression() =
        assertEvaluatesTo(
            VNum(9.0),
            """
            fun f(n: Num): Num =
                m = n * n
                m
            f(3)
            """,
        )

    @Test
    fun innerBlockReadsOuterBinding() =
        assertEvaluatesTo(
            VNum(11.0),
            """
            a = 10
            fun f(n: Num): Num =
                b = a + n
                b
            f(1)
            """,
        )

    @Test
    fun valMayCallLaterFunction() =
        assertEvaluatesTo(
            VNum(6.0),
            """
            x = double(3)
            fun double(n: Num): Num = n * 2
            x
            """,
        )

    @Test
    fun readingALaterValThroughAFunctionFailsAtRuntime() =
        assertRunFails(
            """
            x = g(1)
            y = 2
            fun g(n: Num): Num = y
            x
            """,
            "'y' used before its binding was evaluated",
        )

    @Test
    fun transitiveEarlyReadThroughTwoFunctionsFailsAtRuntime() =
        assertRunFails(
            """
            x = f()
            y = 5
            fun f(): Num = g()
            fun g(): Num = y
            x
            """,
            "'y' used before its binding was evaluated",
        )

    @Test
    fun outerBindingIntactAfterInnerShadow() =
        assertEvaluatesTo(
            VNum(109.0),
            """
            x = 10
            fun f(): Num =
                x = 99
                x
            f() + x
            """,
        )

    @Test
    fun threeDeepScopesReadEveryLevel() =
        assertEvaluatesTo(
            VNum(6.0),
            """
            a = 1
            fun f(): Num =
                b = 2
                g = |x: Num -> a + b + x|
                g(3)
            f()
            """,
        )

    // The checker resolves `r = g` positionally: a local val shadows only statements after its
    // own definition, so `g` here is the top-level fun. The lowerer must agree.
    @Test
    fun localValShadowsOnlyAfterItsDefinition() =
        assertEvaluatesTo(
            VNum(37.0),
            """
            fun f(x: Num): Num =
                r = g
                g = 7
                r(x) + g
            fun g(y: Num): Num = y * 10
            f(3)
            """,
        )

    @Test
    fun closureCapturesPositionalResolution() =
        assertEvaluatesTo(
            VNum(23.0),
            """
            fun f(x: Num): Num =
                h = |x2: Num -> g(x2)|
                g = 3
                h(x) + g
            fun g(y: Num): Num = y * 10
            f(2)
            """,
        )

    @Test
    fun blockLocalValShadowsHoistedFun() =
        assertEvaluatesTo(
            VNum(11.0),
            """
            fun f(x: Num): Num =
                g = 1
                g + x
            fun g(y: Num): Num = y
            f(10)
            """,
        )

    @Test
    fun constructorUsableBeforeItsTypeDefinition() =
        assertEvaluatesTo(
            VNum(42.0),
            """
            x = Some(42)
            type Option<'A> = None | Some { value: 'A }
            x.value
            """,
        )

    @Test
    fun blockEndingInBindingYieldsUnit() =
        assertEvaluatesTo(
            Value.VUnit,
            """
            fun f() =
                x = 1
            f()
            """,
        )
}
