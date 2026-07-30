package klein.interp

import klein.interp.Value.VBool
import klein.interp.Value.VNum
import kotlin.test.Test

class OperatorEvalTest {
    @Test
    fun arithmeticPrecedence() {
        assertEvaluatesTo(VNum(7.0), "1 + 2 * 3")
        assertEvaluatesTo(VNum(9.0), "(1 + 2) * 3")
    }

    @Test
    fun divisionAndModulo() {
        assertEvaluatesTo(VNum(2.5), "10 / 4")
        assertEvaluatesTo(VNum(1.0), "7 % 3")
    }

    @Test
    fun unaryNegation() = assertEvaluatesTo(VNum(-5.0), "-(2 + 3)")

    @Test
    fun comparisons() {
        assertEvaluatesTo(VBool(true), "1 < 2 and 2 <= 2")
        assertEvaluatesTo(VBool(true), "3 > 2 and 2 >= 2")
        assertEvaluatesTo(VBool(true), "1 != 2")
        assertEvaluatesTo(VBool(false), "not (1 == 1)")
    }

    @Test
    fun equalityIsStructural() {
        assertEvaluatesTo(VBool(true), "\"a\" == \"a\"")
        assertEvaluatesTo(VBool(true), "{ x = 1 } == { x = 1 }")
        assertEvaluatesTo(VBool(false), "{ x = 1 } == { x = 2 }")
        assertEvaluatesTo(VBool(true), "{ x = 1, y = 2 } == { y = 2, x = 1 }")
        assertEvaluatesTo(
            VBool(true),
            """
            type Color = Red | Green
            Red == Red
            """,
        )
        assertEvaluatesTo(
            VBool(false),
            """
            type Color = Red | Green
            a: Color = Red
            b: Color = Green
            a == b
            """,
        )
    }

    @Test
    fun shortCircuit() {
        assertEvaluatesTo(VBool(true), "true or (1 / 0 > 0)")
        assertEvaluatesTo(VBool(false), "false and (1 / 0 > 0)")
    }

    // `and`/`or` desugar to a `match`; the right operand runs one scope deeper and must still
    // resolve enclosing bindings at depth+1.
    @Test
    fun andRightOperandReadsOuterVar() =
        assertEvaluatesTo(VBool(true), "fun f(x: Num) = x > 0 and x < 10\nf(5)")

    @Test
    fun orRightOperandReadsOuterVar() =
        assertEvaluatesTo(VBool(false), "fun f(x: Num) = x > 10 or x < 0\nf(5)")

    @Test
    fun divisionByZeroFailsFast() {
        assertRunFails("1 / 0", "Division by zero")
        assertRunFails("1 % 0", "Division by zero")
    }

    @Test
    fun operandsEvaluateLeftToRight() =
        assertRunFails(
            """
            x = (1 / 0) + g(1)
            y = 2
            fun g(n: Num): Num = y
            x
            """,
            "Division by zero",
        )

    @Test
    fun errorPropagatesFromNestedOperand() = assertRunFails("2 + (1 / 0)", "Division by zero")

    @Test
    fun precedenceChains() {
        assertEvaluatesTo(VBool(true), "1 < 2 == true")
        assertEvaluatesTo(VBool(true), "true and false or true")
    }

    @Test
    fun taggedEqualityComparesFields() {
        assertEvaluatesTo(
            VBool(true),
            """
            type Shape = Circle { radius: Num } | Square { side: Num }
            Circle(1) == Circle(1)
            """,
        )
        assertEvaluatesTo(
            VBool(false),
            """
            type Shape = Circle { radius: Num } | Square { side: Num }
            Circle(1) == Circle(2)
            """,
        )
    }

    @Test
    fun ascriptionTransparentMidExpression() =
        assertEvaluatesTo(VNum(42.0), "(1 + 1 : Num) * 21")
}
