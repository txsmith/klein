@file:Suppress("ktlint")

package klein.types

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Error case tests for optional types and null safety.
 *
 * These tests verify that the type system properly rejects:
 * 1. Using null where a non-optional type is required
 * 2. Using optional types where non-optional types are required
 * 3. Operations that would be unsound with nulls
 */
class OptionalTypeErrorTest {

    // =========================================================================
    // SECTION 1: Null in Arithmetic Operations
    // =========================================================================

    @Test
    fun null_cannotBeAddedToNum() {
        val errors = inferErrors("42 + null")
        assertTrue(errors.isNotEmpty(), "Adding null to num should produce error")
    }

    @Test
    fun null_cannotSubtractFromNum() {
        val errors = inferErrors("42 - null")
        assertTrue(errors.isNotEmpty(), "Subtracting null from num should produce error")
    }

    @Test
    fun null_cannotMultiplyWithNum() {
        val errors = inferErrors("42 * null")
        assertTrue(errors.isNotEmpty(), "Multiplying null with num should produce error")
    }

    @Test
    fun null_cannotDivideNum() {
        val errors = inferErrors("42 / null")
        assertTrue(errors.isNotEmpty(), "Dividing by null should produce error")
    }

    @Test
    fun null_cannotBeNegated() {
        val errors = inferErrors("-null")
        assertTrue(errors.isNotEmpty(), "Negating null should produce error")
    }

    // =========================================================================
    // SECTION 2: Null in Boolean Operations
    // =========================================================================

    @Test
    fun null_cannotBeUsedWithAnd() {
        val errors = inferErrors("true and null")
        assertTrue(errors.isNotEmpty(), "null in 'and' should produce error")
    }

    @Test
    fun null_cannotBeUsedWithOr() {
        val errors = inferErrors("true or null")
        assertTrue(errors.isNotEmpty(), "null in 'or' should produce error")
    }

    @Test
    fun null_cannotBeNegatedWithNot() {
        val errors = inferErrors("not null")
        assertTrue(errors.isNotEmpty(), "'not null' should produce error")
    }

    // =========================================================================
    // SECTION 3: Null in Comparisons
    // =========================================================================

    @Test
    fun null_cannotBeLessThanNum() {
        val errors = inferErrors("null < 42")
        assertTrue(errors.isNotEmpty(), "null < num should produce error")
    }

    @Test
    fun null_cannotBeGreaterThanNum() {
        val errors = inferErrors("null > 42")
        assertTrue(errors.isNotEmpty(), "null > num should produce error")
    }

    @Test
    fun null_cannotBeLessThanOrEqualNum() {
        val errors = inferErrors("null <= 42")
        assertTrue(errors.isNotEmpty(), "null <= num should produce error")
    }

    @Test
    fun null_cannotBeGreaterThanOrEqualNum() {
        val errors = inferErrors("null >= 42")
        assertTrue(errors.isNotEmpty(), "null >= num should produce error")
    }

    // Note: null == null and null != null might be allowed for equality checks
    // depending on design. These tests document expected behavior.

    // =========================================================================
    // SECTION 4: Null as Condition
    // =========================================================================

    @Test
    fun null_cannotBeIfCondition() {
        val errors = inferErrors("if null then 1 else 2")
        assertTrue(errors.isNotEmpty(), "null as if condition should produce error")
    }

    @Test
    fun optionalBool_cannotBeIfCondition() {
        val code = """
            maybeBool = if true then true else null
            if maybeBool then 1 else 2
        """.trimIndent()
        val errors = inferErrors(code)
        assertTrue(errors.isNotEmpty(), "Bool? as if condition should produce error")
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
        assertTrue(errors.isNotEmpty(), "Num? + Num should produce error")
    }

    @Test
    fun optionalNum_cannotBeSubtractedFromNum() {
        val code = """
            maybeNum = if true then 42 else null
            100 - maybeNum
        """.trimIndent()
        val errors = inferErrors(code)
        assertTrue(errors.isNotEmpty(), "Num - Num? should produce error")
    }

    @Test
    fun twoOptionalNums_cannotBeAdded() {
        val code = """
            a = if true then 1 else null
            b = if false then 2 else null
            a + b
        """.trimIndent()
        val errors = inferErrors(code)
        assertTrue(errors.isNotEmpty(), "Num? + Num? should produce error")
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
        assertTrue(errors.isNotEmpty(), "Passing null to (Num) -> Num should produce error")
    }

    @Test
    fun function_rejectsOptionalForNonOptionalParam() {
        val code = """
            fun double(x) = x * 2
            maybeNum = if true then 21 else null
            double(maybeNum)
        """.trimIndent()
        val errors = inferErrors(code)
        assertTrue(errors.isNotEmpty(), "Passing Num? to (Num) -> Num should produce error")
    }

    @Test
    fun null_cannotBeApplied() {
        val code = """
            f = null
            f(42)
        """.trimIndent()
        val errors = inferErrors(code)
        assertTrue(errors.isNotEmpty(), "Applying null as function should produce error")
    }

    @Test
    fun optionalFunction_cannotBeApplied() {
        val code = """
            f = if true then |x -> x + 1| else null
            f(42)
        """.trimIndent()
        val errors = inferErrors(code)
        assertTrue(errors.isNotEmpty(), "Applying ((Num) -> Num)? should produce error")
    }

    // =========================================================================
    // SECTION 7: Field Access Errors
    // =========================================================================

    @Test
    fun null_cannotHaveFieldAccess() {
        val errors = inferErrors("null.x")
        assertTrue(errors.isNotEmpty(), "Field access on null should produce error")
    }

    @Test
    fun optionalRecord_cannotHaveDirectFieldAccess() {
        val code = """
            r = if true then { x = 42 } else null
            r.x
        """.trimIndent()
        val errors = inferErrors(code)
        assertTrue(errors.isNotEmpty(), "Field access on { x: Num }? should produce error")
    }

    // =========================================================================
    // SECTION 8: Return Type Mismatches
    // =========================================================================

    @Test
    fun function_returnTypeConstraint_rejectsOptional() {
        // When a function is used in a context requiring non-optional return,
        // returning optional should fail
        val code = """
            fun maybeVal(b) = if b then 42 else null
            fun double(x) = x * 2
            double(maybeVal(true))
        """.trimIndent()
        val errors = inferErrors(code)
        // maybeVal returns Num?, double expects Num
        assertTrue(errors.isNotEmpty(), "Num? as argument to Num -> Num should produce error")
    }

    // =========================================================================
    // SECTION 9: Record Field Type Mismatches
    // =========================================================================

    @Test
    fun record_optionalFieldAssignedToNonOptionalExpectation() {
        // If a record is expected to have field x: Num, but actual has x: Num?
        val code = """
            fun getX(r) = r.x + 1
            rec = { x = if true then 42 else null }
            getX(rec)
        """.trimIndent()
        val errors = inferErrors(code)
        // r.x needs to be Num for + 1, but rec.x is Num?
        assertTrue(errors.isNotEmpty(), "Using { x: Num? } where { x: Num } expected should fail")
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
        assertTrue(errors.isNotEmpty(), "Arithmetic on chained optional should produce error")
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
        assertTrue(errors.isNotEmpty(), "Field access through optional should produce error")
    }

    @Test
    fun higherOrderFunction_optionalCallback_error() {
        val code = """
            fun apply(f, x) = f(x)
            maybeF = if true then |x -> x + 1| else null
            apply(maybeF, 42)
        """.trimIndent()
        val errors = inferErrors(code)
        assertTrue(errors.isNotEmpty(), "Passing optional function where function expected should fail")
    }

    // =========================================================================
    // SECTION 11: Error Message Quality (informational)
    // =========================================================================

    @Test
    fun error_nullArithmetic_hasUsefulMessage() {
        val errors = inferErrors("42 + null")
        assertTrue(errors.isNotEmpty())
        val error = errors[0]
        // The error message should mention type mismatch or null
        assertTrue(
            error.message.contains("mismatch") ||
            error.message.contains("Null") ||
            error.message.contains("null"),
            "Error message should be informative: ${error.message}"
        )
    }

    @Test
    fun error_optionalAsCondition_hasUsefulMessage() {
        val code = """
            b = if true then true else null
            if b then 1 else 2
        """.trimIndent()
        val errors = inferErrors(code)
        assertTrue(errors.isNotEmpty())
        val error = errors[0]
        assertTrue(
            error.message.contains("Bool") ||
            error.message.contains("mismatch"),
            "Error message should mention Bool or type mismatch: ${error.message}"
        )
    }
}
