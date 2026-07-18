package klein.check

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Safe navigation (`?.`). On an optional receiver `?.` short-circuits the whole trailing spine: a
 * safe field access yields `field?`, and a safe method call `r?.m(args)` applies the method and
 * lifts its result to optional. The wrap is idempotent, so a field or method that is itself optional
 * stays single-optional. On a non-optional receiver `?.` is redundant and yields the bare type.
 *
 * Optional receivers come both from value-level `if … else null` and from optional (`{ … }?`)
 * parameters; the lambda case uses a concrete element type because lambdas can't introduce type
 * variables.
 */
class SafeFieldAccessInferTest {
    private fun assertType(
        expected: String,
        src: String,
    ) {
        val r = infer(src)
        assertTrue(r.errors.isEmpty(), "unexpected errors: ${r.errors}")
        assertEquals(expected, Type.print(r.type))
    }

    // --- safe field access ---

    @Test
    fun safeFieldAccess_onOptionalRecord_returnsOptional() =
        assertType(
            "String?",
            """
            r = if true then { name = "Alice" } else null
            r?.name
            """.trimIndent(),
        )

    @Test
    fun safeFieldAccess_onNonOptionalRecord_isBare() =
        assertType(
            "String",
            """
            r = { name = "Alice" }
            r?.name
            """.trimIndent(),
        )

    @Test
    fun safeFieldAccess_chainedOnOptional() =
        assertType(
            "String?",
            """
            r = if true then { address = { city = "NYC" } } else null
            r?.address?.city
            """.trimIndent(),
        )

    @Test
    fun safeFieldAccess_chainWhereEachFieldIsOptional() =
        assertType(
            "Num?",
            """
            r = if true then { b = if true then { c = 1 } else null } else null
            r?.b?.c
            """.trimIndent(),
        )

    @Test
    fun safeFieldAccess_mixedWithRegularAccess() =
        assertType(
            "{ city: String }?",
            """
            r = if true then { address = { city = "NYC" } } else null
            r?.address
            """.trimIndent(),
        )

    @Test
    fun safeFieldAccess_nestedOptionalField() =
        assertType(
            "Num?",
            """
            r = if true then { value = if false then 42 else null } else null
            r?.value
            """.trimIndent(),
        )

    @Test
    fun safeFieldAccess_inFunction() =
        assertType(
            "({ name: A }?) -> A?",
            """
            fun getName(r: { name: 'A }?) = r?.name
            getName
            """.trimIndent(),
        )

    @Test
    fun safeFieldAccess_withLambda() =
        assertType(
            "({ value: Num }?) -> Num?",
            """
            f = |r: { value: Num }? -> r?.value|
            f
            """.trimIndent(),
        )

    @Test
    fun safeFieldAccess_onFunctionResult() =
        assertType(
            "Num?",
            """
            fun maybeRecord(b: Bool) = if b then { x = 1 } else null
            maybeRecord(true)?.x
            """.trimIndent(),
        )

    @Test
    fun safeFieldAccess_multipleFieldsOnSameRecord() =
        assertType(
            "{ a: Num?, b: String? }",
            """
            r = if true then { x = 1, y = "hello" } else null
            a = r?.x
            b = r?.y
            { a = a, b = b }
            """.trimIndent(),
        )

    @Test
    fun safeFieldAccess_numericField() =
        assertType(
            "Num?",
            """
            r = if true then { age = 42 } else null
            r?.age
            """.trimIndent(),
        )

    @Test
    fun safeFieldAccess_boolField() =
        assertType(
            "Bool?",
            """
            r = if true then { active = false } else null
            r?.active
            """.trimIndent(),
        )

    // --- safe method calls: r?.m(args) returns ReturnType? ---

    @Test
    fun safeMethodCall_simple() =
        assertType(
            "Num?",
            """
            r = if true then { double = |x: Num -> x * 2| } else null
            r?.double(21)
            """.trimIndent(),
        )

    @Test
    fun safeMethodCall_onNonOptionalRecord_isBare() =
        assertType(
            "Num",
            """
            r = { double = |x: Num -> x * 2| }
            r?.double(21)
            """.trimIndent(),
        )

    @Test
    fun safeMethodCall_noArgs() =
        assertType(
            "Num?",
            """
            r = if true then { getValue = |42| } else null
            r?.getValue()
            """.trimIndent(),
        )

    @Test
    fun safeMethodCall_multipleArgs() =
        assertType(
            "Num?",
            """
            r = if true then { add = |a: Num, b: Num -> a + b| } else null
            r?.add(1, 2)
            """.trimIndent(),
        )

    @Test
    fun safeMethodCall_returningBool() =
        assertType(
            "Bool?",
            """
            r = if true then { isValid = |x: Num -> x > 0| } else null
            r?.isValid(42)
            """.trimIndent(),
        )

    @Test
    fun safeMethodCall_chainedAfterFieldAccess() =
        assertType(
            "Num?",
            """
            r = { inner = if true then { process = |x: Num -> x + 1| } else null }
            r.inner?.process(5)
            """.trimIndent(),
        )

    @Test
    fun safeMethodCall_chainedAfterSafeFieldAccess() =
        assertType(
            "Num?",
            """
            r = if true then { inner = if false then { process = |x: Num -> x + 1| } else null } else null
            r?.inner?.process(5)
            """.trimIndent(),
        )

    @Test
    fun safeMethodCall_inFunction() =
        assertType(
            "({ compute: (Num) -> A }?) -> A?",
            """
            fun callMethod(r: { compute: (Num) -> 'A }?) = r?.compute(10)
            callMethod
            """.trimIndent(),
        )

    @Test
    fun safeMethodCall_withLambdaArg() =
        assertType(
            "Num?",
            """
            r = if true then { map = |g: ((Num) -> Num) -> g(42)| } else null
            r?.map(|x: Num -> x * 2|)
            """.trimIndent(),
        )

    @Test
    fun safeMethodCall_methodReturnsOptional() =
        assertType(
            "Num?",
            """
            r = if true then { find = |x: Num -> if x > 0 then x else null| } else null
            r?.find(5)
            """.trimIndent(),
        )

    @Test
    fun safeMethodCall_realWorldExample() =
        assertType(
            "({ purchasedAt: { isAfter: (Num) -> A }? }?) -> A?",
            """
            fun process(order: { purchasedAt: { isAfter: (Num) -> 'A }? }?) = order?.purchasedAt?.isAfter(0)
            process
            """.trimIndent(),
        )
}
