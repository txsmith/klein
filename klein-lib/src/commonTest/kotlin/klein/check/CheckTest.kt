package klein.check

import klein.check.Type.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Programs that bottom out in `check` / `subsume` / `isSubtype`. */
class CheckTest {
    @Test
    fun arithmetic() = assertEquals(TNum, infer("1 + 2").type)

    @Test
    fun comparison() = assertEquals(TBool, infer("1 < 2").type)

    @Test
    fun logical() = assertEquals(TBool, infer("true and false").type)

    @Test
    fun ifThenElse() = assertEquals(TNum, infer("if true then 1 else 2").type)

    @Test
    fun ascription() = assertEquals(TNum, infer("(1 : Num)").type)

    @Test
    fun annotatedBinding() =
        assertEquals(
            TNum,
            infer(
                """
                x: Num = 5
                x
                """.trimIndent(),
            ).type,
        )

    @Test
    fun application() =
        assertEquals(
            TNum,
            infer(
                """
                f = |x: Num -> x|
                f(1)
                """.trimIndent(),
            ).type,
        )

    // --- error cases ---

    @Test
    fun operandMismatchErrors() = assertTrue(infer("true + 1").errors.isNotEmpty())

    @Test
    fun annotatedBindingMismatchErrors() =
        assertTrue(
            infer(
                """
                x: Num = true
                x
                """.trimIndent(),
            ).errors.isNotEmpty(),
        )

    @Test
    fun argMismatchErrors() =
        assertTrue(
            infer(
                """
                f = |x: Num -> x|
                f(true)
                """.trimIndent(),
            ).errors.isNotEmpty(),
        )

    @Test
    fun lambdaDiscardParamTakesAndIgnoresTheArgument() =
        assertInfersType(
            TFun(listOf(TNum), TNum),
            """
            f: (Num) -> Num = |_ -> 0|
            f
            """.trimIndent(),
        )

    @Test
    fun lambdaDiscardParamIsNotReferenceable() {
        val e =
            infer(
                """
                f: (Num) -> Num = |_ -> _ + 1|
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.UnboundVariable>(e)
    }

    @Test
    fun lambdaWithTwoDiscardParamsIsFine() =
        assertInfersType(
            TFun(listOf(TNum, TStr), TNum),
            """
            q = |_: Num, _: String -> 0|
            q
            """.trimIndent(),
        )

    @Test
    fun annotatedLambdaDiscardParamChecks() =
        assertInfersType(
            TFun(listOf(TNum), TNum),
            """
            q = |_: Num -> 0|
            q
            """.trimIndent(),
        )

    @Test
    fun twoParamFunctionTypeAnnotationChecksATwoParamLambda() =
        assertInfersType(
            TFun(listOf(TNum, TStr), TNum),
            """
            f: (Num, String) -> Num = |_, _ -> 0|
            f
            """.trimIndent(),
        )
}
