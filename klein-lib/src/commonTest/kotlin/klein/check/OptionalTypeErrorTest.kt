package klein.check

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Null-safety rejections. Every case is rejected; the error *kind* depends on where the null flows:
 * a nullable value demanded as non-null (operator operand, `if` condition, argument to a ground
 * parameter) is `NullNotAllowed`; a null/optional *callee* is `NotAFunction`; a null/optional
 * field-access *receiver* is `NotARecord`; and a null that reaches a demand only through an
 * application result or a nested record field surfaces as the structural `TypeMismatch`.
 */
class OptionalTypeErrorTest {
    private inline fun <reified T : TypeError> assertError(
        src: String,
        count: Int = 1,
    ) {
        val errors = infer(src).errors
        assertEquals(count, errors.size, "expected $count error(s), got: $errors")
        assertTrue(errors.all { it is T }, "expected all ${T::class.simpleName}, got: $errors")
    }

    // --- null in arithmetic ---

    @Test
    fun null_cannotBeAddedToNum() = assertError<TypeError.NullNotAllowed>("42 + null")

    @Test
    fun null_cannotSubtractFromNum() = assertError<TypeError.NullNotAllowed>("42 - null")

    @Test
    fun null_cannotMultiplyWithNum() = assertError<TypeError.NullNotAllowed>("42 * null")

    @Test
    fun null_cannotDivideNum() = assertError<TypeError.NullNotAllowed>("42 / null")

    @Test
    fun null_cannotBeNegated() = assertError<TypeError.NullNotAllowed>("-null")

    // --- null in boolean operations ---

    @Test
    fun null_cannotBeUsedWithAnd() = assertError<TypeError.NullNotAllowed>("true and null")

    @Test
    fun null_cannotBeUsedWithOr() = assertError<TypeError.NullNotAllowed>("true or null")

    @Test
    fun null_cannotBeNegatedWithNot() = assertError<TypeError.NullNotAllowed>("not null")

    // --- null in comparisons ---

    @Test
    fun null_cannotBeLessThanNum() = assertError<TypeError.NullNotAllowed>("null < 42")

    @Test
    fun null_cannotBeGreaterThanNum() = assertError<TypeError.NullNotAllowed>("null > 42")

    @Test
    fun null_cannotBeLessThanOrEqualNum() = assertError<TypeError.NullNotAllowed>("null <= 42")

    @Test
    fun null_cannotBeGreaterThanOrEqualNum() = assertError<TypeError.NullNotAllowed>("null >= 42")

    // --- null as condition ---

    @Test
    fun null_cannotBeIfCondition() = assertError<TypeError.NullNotAllowed>("if null then 1 else 2")

    @Test
    fun optionalBool_cannotBeIfCondition() =
        assertError<TypeError.NullNotAllowed>(
            """
            maybeBool = if true then true else null
            if maybeBool then 1 else 2
            """.trimIndent(),
        )

    // --- optional types in arithmetic ---

    @Test
    fun optionalNum_cannotBeAddedToNum() =
        assertError<TypeError.NullNotAllowed>(
            """
            maybeNum = if true then 42 else null
            maybeNum + 1
            """.trimIndent(),
        )

    @Test
    fun optionalNum_cannotBeSubtractedFromNum() =
        assertError<TypeError.NullNotAllowed>(
            """
            maybeNum = if true then 42 else null
            100 - maybeNum
            """.trimIndent(),
        )

    @Test
    fun twoOptionalNums_cannotBeAdded() =
        assertError<TypeError.NullNotAllowed>(
            """
            a = if true then 1 else null
            b = if false then 2 else null
            a + b
            """.trimIndent(),
            count = 2,
        )

    // --- function application ---

    @Test
    fun function_rejectsNullForNonOptionalParam() =
        assertError<TypeError.NullNotAllowed>(
            """
            fun double(x: Num) = x * 2
            double(null)
            """.trimIndent(),
        )

    @Test
    fun function_rejectsOptionalForNonOptionalParam() =
        assertError<TypeError.NullNotAllowed>(
            """
            fun double(x: Num) = x * 2
            maybeNum = if true then 21 else null
            double(maybeNum)
            """.trimIndent(),
        )

    @Test
    fun null_cannotBeApplied() =
        assertError<TypeError.NotAFunction>(
            """
            f = null
            f(42)
            """.trimIndent(),
        )

    @Test
    fun optionalFunction_cannotBeApplied() =
        assertError<TypeError.NotAFunction>(
            """
            f = if true then |x: Num -> x + 1| else null
            f(42)
            """.trimIndent(),
        )

    // --- field access ---

    @Test
    fun null_cannotHaveFieldAccess() = assertError<TypeError.NotARecord>("null.x")

    @Test
    fun optionalRecord_cannotHaveDirectFieldAccess() =
        assertError<TypeError.NotARecord>(
            """
            r = if true then { x = 42 } else null
            r.x
            """.trimIndent(),
        )

    // --- null reaching a demand through an application result / nested field is structural ---

    @Test
    fun optionalFromCall_rejectedByNonOptionalParam() =
        assertError<TypeError.TypeMismatch>(
            """
            fun maybeVal(b: Bool) = if b then 42 else null
            fun double(x: Num) = x * 2
            double(maybeVal(true))
            """.trimIndent(),
        )

    @Test
    fun record_optionalFieldAgainstNonOptionalField() =
        assertError<TypeError.TypeMismatch>(
            """
            fun getX(r: { x: Num }) = r.x + 1
            rec = { x = if true then 42 else null }
            getX(rec)
            """.trimIndent(),
        )

    // --- complex scenarios ---

    @Test
    fun chainedOptional_arithmeticError() =
        assertError<TypeError.NullNotAllowed>(
            """
            fun step(x: Num, b: Bool) = if b then x + 1 else null
            result = step(0, true)
            result + 10
            """.trimIndent(),
        )

    @Test
    fun nestedOptionalRecord_fieldAccessError() =
        assertError<TypeError.NotARecord>(
            """
            outer = { inner = if true then { value = 42 } else null }
            outer.inner.value
            """.trimIndent(),
        )

    @Test
    fun higherOrderFunction_optionalCallback_error() =
        assertError<TypeError.TypeMismatch>(
            """
            fun apply(f: ('A) -> 'B, x: 'A) = f(x)
            maybeF = if true then |x: Num -> x + 1| else null
            apply(maybeF, 42)
            """.trimIndent(),
        )

    @Test
    fun error_nullArithmetic_hasUsefulMessage() {
        val errors = infer("42 + null").errors
        assertEquals(1, errors.size)
        val e = errors[0]
        assertIs<TypeError.NullNotAllowed>(e)
        assertTrue(e.message.contains("Null") || e.message.contains("null"), "message: ${e.message}")
    }

    @Test
    fun error_optionalAsCondition_hasUsefulMessage() {
        val errors =
            infer(
                """
                b = if true then true else null
                if b then 1 else 2
                """.trimIndent(),
            ).errors
        assertEquals(1, errors.size)
        val e = errors[0]
        assertIs<TypeError.NullNotAllowed>(e)
        assertTrue(e.message.contains("Bool") || e.message.contains("Null"), "message: ${e.message}")
    }
}
