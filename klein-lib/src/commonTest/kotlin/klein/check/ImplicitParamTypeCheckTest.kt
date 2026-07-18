package klein.check

import klein.types.TypeError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The implicit dot parameter `.` of a bare (parameterless) lambda. Its type comes from the demand,
 * exactly like an unannotated explicit parameter, so it's only usable in check mode — a bare
 * `|.x|` in synth mode has no type to draw from. Printed type variables render without the tick.
 *
 * `.` is unusable outside a lambda, alongside explicit parameters, or in a named function; each is
 * its own error.
 */
class ImplicitParamTypeCheckTest {
    private fun assertType(
        expected: String,
        src: String,
    ) {
        val r = infer(src)
        assertTrue(r.errors.isEmpty(), "unexpected errors: ${r.errors}")
        assertEquals(expected, klein.Type.print(r.type.toSurface()))
    }

    private inline fun <reified T : TypeError> assertError(src: String) {
        assertIs<T>(infer(src).errors.single())
    }

    // --- the implicit parameter takes its type from the demand ---

    @Test
    fun bareIdentity() =
        assertType(
            "(A) -> A",
            """
            f: ('A) -> 'A = |.|
            f
            """.trimIndent(),
        )

    @Test
    fun fieldAccess() =
        assertType(
            "({ x: A }) -> A",
            """
            f: ({ x: 'A }) -> 'A = |.x|
            f
            """.trimIndent(),
        )

    @Test
    fun multipleFieldAccess() =
        assertType(
            "({ x: Num, y: Num }) -> Num",
            """
            f: ({ x: Num, y: Num }) -> Num = |.x + .y|
            f
            """.trimIndent(),
        )

    @Test
    fun comparison() =
        assertType(
            "(Num) -> Bool",
            """
            f: (Num) -> Bool = |. > 100|
            f
            """.trimIndent(),
        )

    @Test
    fun inCondition() =
        assertType(
            "({ active: Bool }) -> Num",
            """
            f: ({ active: Bool }) -> Num = |if .active then 1 else 0|
            f
            """.trimIndent(),
        )

    @Test
    fun passthrough() =
        assertType(
            "(Num) -> Num",
            """
            fun inc(n: Num) = n + 1
            f: (Num) -> Num = |inc(.)|
            f
            """.trimIndent(),
        )

    // --- nested lambdas get separate implicit-param scopes ---

    @Test
    fun nestedLambda_separateScopes() =
        assertType(
            "() -> () -> ({ x: A }) -> A",
            """
            f: () -> () -> ({ x: 'A }) -> 'A = || |.x| ||
            f
            """.trimIndent(),
        )

    @Test
    fun nestedLambda_innerUsesImplicit() =
        assertType(
            "(Num) -> (Num) -> Num",
            """
            f: (Num) -> (Num) -> Num = |x: Num -> |. * 2||
            f
            """.trimIndent(),
        )

    // --- a constant (`.`-free) lambda is nullary, inferable in synth mode ---

    @Test
    fun constantLambda_noParam() = assertType("() -> Num", "|42|")

    // --- error cases ---

    @Test
    fun synthMode_bareIdentity_isError() = assertError<TypeError.ImplicitParamWithoutExpectedType>("|.|")

    @Test
    fun synthMode_fieldAccess_isError() = assertError<TypeError.ImplicitParamWithoutExpectedType>("|.x|")

    @Test
    fun outsideLambda() = assertError<TypeError.ImplicitParamOutsideLambda>(".")

    @Test
    fun outsideLambda_fieldAccess() = assertError<TypeError.ImplicitParamOutsideLambda>(".x")

    @Test
    fun mixedWithExplicitParams() {
        val e = infer("|x: Num -> . + x|").errors.single()
        assertIs<TypeError.ImplicitParamWithExplicitParams>(e)
        assertEquals(listOf("x"), e.params)
    }

    @Test
    fun mixedWithExplicitParams_fieldAccess() {
        val e = infer("|x: Num -> .y + x|").errors.single()
        assertIs<TypeError.ImplicitParamWithExplicitParams>(e)
        assertEquals(listOf("x"), e.params)
    }

    @Test
    fun inNamedFunction() = assertError<TypeError.ImplicitParamInNamedFunction>("fun g() = .")

    @Test
    fun inNamedFunctionWithParams() = assertError<TypeError.ImplicitParamInNamedFunction>("fun f(x: Num) = .")
}
