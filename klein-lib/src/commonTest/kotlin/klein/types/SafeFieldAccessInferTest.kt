@file:Suppress("ktlint")

package klein.types

import kotlin.test.Test

class SafeFieldAccessInferTest {

    @Test
    fun safeFieldAccess_onOptionalRecord_returnsOptional() {
        val code = """
            r = if true then { name = "Alice" } else null
            r?.name
        """.trimIndent()
        assertType("String?", infer(code))
    }

    @Test
    fun safeFieldAccess_onNonOptionalRecord_returnsOptional() {
        val code = """
            r = { name = "Alice" }
            r?.name
        """.trimIndent()
        assertType("String?", infer(code))
    }

    @Test
    fun safeFieldAccess_chainedOnOptional() {
        val code = """
            r = if true then { address = { city = "NYC" } } else null
            r?.address?.city
        """.trimIndent()
        assertType("String?", infer(code))
    }

    @Test
    fun safeFieldAccess_mixedWithRegularAccess() {
        val code = """
            r = if true then { address = { city = "NYC" } } else null
            r?.address
        """.trimIndent()
        assertType("{ city: String }?", infer(code))
    }

    @Test
    fun safeFieldAccess_nestedOptionalField() {
        // Note: T?? doesn't flatten to T? yet (ADR says it should)
        val code = """
            r = if true then { value = if false then 42 else null } else null
            r?.value
        """.trimIndent()
        assertType("Num??", infer(code))
    }

    @Test
    fun safeFieldAccess_inFunction() {
        val code = """
            fun getName(r) = r?.name
        """.trimIndent()
        assertType("({ name: 'A }?) -> 'A?", infer(code + "\ngetName"))
    }

    @Test
    fun safeFieldAccess_withLambda() {
        val code = """
            f = |r -> r?.value|
        """.trimIndent()
        assertType("({ value: 'A }?) -> 'A?", infer(code + "\nf"))
    }

    @Test
    fun safeFieldAccess_onFunctionResult() {
        val code = """
            fun maybeRecord(b) = if b then { x = 1 } else null
            maybeRecord(true)?.x
        """.trimIndent()
        assertType("Num?", infer(code))
    }

    @Test
    fun safeFieldAccess_multipleFieldsOnSameRecord() {
        val code = """
            r = if true then { x = 1, y = "hello" } else null
            a = r?.x
            b = r?.y
            { a = a, b = b }
        """.trimIndent()
        assertType("{ a: Num?, b: String? }", infer(code))
    }

    @Test
    fun safeFieldAccess_numericField() {
        val code = """
            r = if true then { age = 42 } else null
            r?.age
        """.trimIndent()
        assertType("Num?", infer(code))
    }

    @Test
    fun safeFieldAccess_boolField() {
        val code = """
            r = if true then { active = false } else null
            r?.active
        """.trimIndent()
        assertType("Bool?", infer(code))
    }

    // =========================================================================
    // Safe Method Calls: x?.method(args) should return ReturnType?
    // =========================================================================

    @Test
    fun safeMethodCall_simple() {
        val code = """
            r = if true then { double = |x -> x * 2| } else null
            r?.double(21)
        """.trimIndent()
        assertType("Num?", infer(code))
    }

    @Test
    fun safeMethodCall_onNonOptionalRecord() {
        val code = """
            r = { double = |x -> x * 2| }
            r?.double(21)
        """.trimIndent()
        assertType("Num?", infer(code))
    }

    @Test
    fun safeMethodCall_noArgs() {
        val code = """
            r = if true then { getValue = |42| } else null
            r?.getValue()
        """.trimIndent()
        assertType("Num?", infer(code))
    }

    @Test
    fun safeMethodCall_multipleArgs() {
        val code = """
            r = if true then { add = |a, b -> a + b| } else null
            r?.add(1, 2)
        """.trimIndent()
        assertType("Num?", infer(code))
    }

    @Test
    fun safeMethodCall_returningBool() {
        val code = """
            r = if true then { isValid = |x -> x > 0| } else null
            r?.isValid(42)
        """.trimIndent()
        assertType("Bool?", infer(code))
    }

    @Test
    fun safeMethodCall_chainedAfterFieldAccess() {
        // Access inner safely, then call method on the (non-optional) inner record
        val code = """
            r = { inner = if true then { process = |x -> x + 1| } else null }
            r.inner?.process(5)
        """.trimIndent()
        assertType("Num?", infer(code))
    }

    @Test
    fun safeMethodCall_chainedAfterSafeFieldAccess() {
        val code = """
            r = if true then { inner = if false then { process = |x -> x + 1| } else null } else null
            r?.inner?.process(5)
        """.trimIndent()
        // Chained ?. collapses to single optional (either r or inner being null gives null)
        assertType("Num?", infer(code))
    }

    @Test
    fun safeMethodCall_inFunction() {
        val code = """
            fun callMethod(r) = r?.compute(10)
        """.trimIndent()
        assertType("({ compute: (Num) -> 'A }?) -> 'A?", infer(code + "\ncallMethod"))
    }

    @Test
    fun safeMethodCall_withLambdaArg() {
        val code = """
            r = if true then { map = |f -> f(42)| } else null
            r?.map(|x -> x * 2|)
        """.trimIndent()
        assertType("Num?", infer(code))
    }

    @Test
    fun safeMethodCall_methodReturnsOptional() {
        val code = """
            r = if true then { find = |x -> if x > 0 then x else null| } else null
            r?.find(5)
        """.trimIndent()
        // Note: T?? doesn't flatten yet
        assertType("Num??", infer(code))
    }

    @Test
    fun safeMethodCall_realWorldExample() {
        val code = """
            fun process(order) = order?.purchasedAt?.isAfter(0)
        """.trimIndent()
        // Chained ?. gives single optional result
        assertType("({ purchasedAt: { isAfter: (Num) -> 'A } }?) -> 'A?", infer(code + "\nprocess"))
    }
}
