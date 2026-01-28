@file:Suppress("ktlint")

package klein.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OptionalTypeErrorTest {

    // =========================================================================
    // SECTION 1: Null in Arithmetic Operations
    // =========================================================================

    @Test
    fun null_cannotBeAddedToNum() {
        val errors = inferErrors("42 + null")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    @Test
    fun null_cannotSubtractFromNum() {
        val errors = inferErrors("42 - null")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    @Test
    fun null_cannotMultiplyWithNum() {
        val errors = inferErrors("42 * null")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    @Test
    fun null_cannotDivideNum() {
        val errors = inferErrors("42 / null")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    @Test
    fun null_cannotBeNegated() {
        val errors = inferErrors("-null")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    // =========================================================================
    // SECTION 2: Null in Boolean Operations
    // =========================================================================

    @Test
    fun null_cannotBeUsedWithAnd() {
        val errors = inferErrors("true and null")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    @Test
    fun null_cannotBeUsedWithOr() {
        val errors = inferErrors("true or null")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    @Test
    fun null_cannotBeNegatedWithNot() {
        val errors = inferErrors("not null")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    // =========================================================================
    // SECTION 3: Null in Comparisons
    // =========================================================================

    @Test
    fun null_cannotBeLessThanNum() {
        val errors = inferErrors("null < 42")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    @Test
    fun null_cannotBeGreaterThanNum() {
        val errors = inferErrors("null > 42")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    @Test
    fun null_cannotBeLessThanOrEqualNum() {
        val errors = inferErrors("null <= 42")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    @Test
    fun null_cannotBeGreaterThanOrEqualNum() {
        val errors = inferErrors("null >= 42")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    // =========================================================================
    // SECTION 4: Null as Condition
    // =========================================================================

    @Test
    fun null_cannotBeIfCondition() {
        val errors = inferErrors("if null then 1 else 2")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    @Test
    fun optionalBool_cannotBeIfCondition() {
        val code = """
            maybeBool = if true then true else null
            if maybeBool then 1 else 2
        """.trimIndent()
        val errors = inferErrors(code)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    // =========================================================================
    // SECTION 5: Optional Types in Arithmetic
    // =========================================================================

    @Test
    fun optionalNum_cannotBeAddedToNum() {
        val code = """
            maybeNum = if true then 42 else null
            maybeNum + 1
        """.trimIndent()
        val errors = inferErrors(code)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    @Test
    fun optionalNum_cannotBeSubtractedFromNum() {
        val code = """
            maybeNum = if true then 42 else null
            100 - maybeNum
        """.trimIndent()
        val errors = inferErrors(code)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    @Test
    fun twoOptionalNums_cannotBeAdded() {
        val code = """
            a = if true then 1 else null
            b = if false then 2 else null
            a + b
        """.trimIndent()
        val errors = inferErrors(code)
        assertEquals(2, errors.size)
        assertTrue(errors.all { it is TypeError.NullNotAllowed })
    }

    // =========================================================================
    // SECTION 6: Function Application Errors
    // =========================================================================

    @Test
    fun function_rejectsNullForNonOptionalParam() {
        val code = """
            fun double(x) = x * 2
            double(null)
        """.trimIndent()
        val errors = inferErrors(code)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    @Test
    fun function_rejectsOptionalForNonOptionalParam() {
        val code = """
            fun double(x) = x * 2
            maybeNum = if true then 21 else null
            double(maybeNum)
        """.trimIndent()
        val errors = inferErrors(code)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    @Test
    fun null_cannotBeApplied() {
        val code = """
            f = null
            f(42)
        """.trimIndent()
        val errors = inferErrors(code)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    @Test
    fun optionalFunction_cannotBeApplied() {
        val code = """
            f = if true then |x -> x + 1| else null
            f(42)
        """.trimIndent()
        val errors = inferErrors(code)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    // =========================================================================
    // SECTION 7: Field Access Errors
    // =========================================================================

    @Test
    fun null_cannotHaveFieldAccess() {
        val errors = inferErrors("null.x")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    @Test
    fun optionalRecord_cannotHaveDirectFieldAccess() {
        val code = """
            r = if true then { x = 42 } else null
            r.x
        """.trimIndent()
        val errors = inferErrors(code)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    // =========================================================================
    // SECTION 8: Return Type Mismatches
    // =========================================================================

    @Test
    fun function_returnTypeConstraint_rejectsOptional() {
        val code = """
            fun maybeVal(b) = if b then 42 else null
            fun double(x) = x * 2
            double(maybeVal(true))
        """.trimIndent()
        val errors = inferErrors(code)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    // =========================================================================
    // SECTION 9: Record Field Type Mismatches
    // =========================================================================

    @Test
    fun record_optionalFieldAssignedToNonOptionalExpectation() {
        val code = """
            fun getX(r) = r.x + 1
            rec = { x = if true then 42 else null }
            getX(rec)
        """.trimIndent()
        val errors = inferErrors(code)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    // =========================================================================
    // SECTION 10: Complex Error Scenarios
    // =========================================================================

    @Test
    fun chainedOptional_arithmeticError() {
        val code = """
            fun step(x, b) = if b then x + 1 else null
            result = step(0, true)
            result + 10
        """.trimIndent()
        val errors = inferErrors(code)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    @Test
    fun nestedOptionalRecord_fieldAccessError() {
        val code = """
            outer = {
                inner = if true then { value = 42 } else null
            }
            outer.inner.value
        """.trimIndent()
        val errors = inferErrors(code)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    @Test
    fun higherOrderFunction_optionalCallback_error() {
        val code = """
            fun apply(f, x) = f(x)
            maybeF = if true then |x -> x + 1| else null
            apply(maybeF, 42)
        """.trimIndent()
        val errors = inferErrors(code)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
    }

    // =========================================================================
    // SECTION 11: Error Message Quality (informational)
    // =========================================================================

    @Test
    fun error_nullArithmetic_hasUsefulMessage() {
        val errors = inferErrors("42 + null")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
        assertTrue(
            errors[0].message.contains("Null") || errors[0].message.contains("null"),
            "Error message should mention null: ${errors[0].message}"
        )
    }

    @Test
    fun error_optionalAsCondition_hasUsefulMessage() {
        val code = """
            b = if true then true else null
            if b then 1 else 2
        """.trimIndent()
        val errors = inferErrors(code)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.NullNotAllowed)
        assertTrue(
            errors[0].message.contains("Bool") || errors[0].message.contains("Null"),
            "Error message should mention Bool or Null: ${errors[0].message}"
        )
    }
}
