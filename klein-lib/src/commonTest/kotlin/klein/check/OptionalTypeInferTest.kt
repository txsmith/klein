package klein.check

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Optional (`T?`) synthesis.
 *
 * `null : Null`; a branch that is `Null` (or already `T?`) joins with the other branch's non-null
 * core to give `core?` (`lub(Num, Null) = Num?`); both-null stays `Null`. Printed type variables
 * render without the leading tick (`A`, not `'A`).
 *
 * Not covered: implicit-param `null` (implicit-param suite) and passing a polymorphic function to a
 * generic parameter (higher-rank, currently unsupported).
 */
class OptionalTypeInferTest {
    private fun assertType(
        expected: String,
        src: String,
    ) {
        val r = infer(src)
        assertTrue(r.errors.isEmpty(), "unexpected errors: ${r.errors}")
        assertEquals(expected, klein.Type.print(r.type.toLegacy()))
    }

    // --- null literal ---

    @Test
    fun nullLiteral_hasTypeNull() = assertType("Null", "null")

    @Test
    fun nullLiteral_inBinding() =
        assertType(
            "Null",
            """
            x = null
            x
            """.trimIndent(),
        )

    @Test
    fun nullLiteral_multipleBindings() =
        assertType(
            "Null",
            """
            x = null
            y = null
            y
            """.trimIndent(),
        )

    // --- optional (`T?`) annotations ---

    @Test
    fun optionalAnnotation_acceptsValue() =
        assertType(
            "Num?",
            """
            x: Num? = 5
            x
            """.trimIndent(),
        )

    @Test
    fun optionalAnnotation_acceptsNull() =
        assertType(
            "Num?",
            """
            x: Num? = null
            x
            """.trimIndent(),
        )

    // --- if-else with a null branch joins to optional ---

    @Test
    fun ifElse_nullInElseBranch_infersOptional() = assertType("Num?", "if true then 42 else null")

    @Test
    fun ifElse_nullInThenBranch_infersOptional() = assertType("Num?", "if true then null else 42")

    @Test
    fun ifElse_bothBranchesNull_infersNull() = assertType("Null", "if true then null else null")

    @Test
    fun ifElse_optionalInOneBranch_nullInOther() =
        assertType(
            "Num?",
            """
            maybeNum = if true then 42 else null
            if false then maybeNum else null
            """.trimIndent(),
        )

    @Test
    fun ifElse_nestedWithNull() =
        assertType(
            "Num?",
            """
            if true then
                if false then 42 else null
            else
                99
            """.trimIndent(),
        )

    @Test
    fun ifElse_nestedBothOptional() =
        assertType(
            "Num?",
            """
            if true then
                if false then 42 else null
            else
                if true then null else 99
            """.trimIndent(),
        )

    @Test
    fun ifElse_withRecord() = assertType("{ x: Num }?", "if true then { x = 1 } else null")

    @Test
    fun ifElse_withFunction() = assertType("((Num) -> Num)?", "if true then |x: Num -> x + 1| else null")

    // --- if without else is optional (a false condition yields null) ---

    @Test
    fun ifWithoutElse_infersOptional() = assertType("Num?", "if true then 42")

    @Test
    fun ifWithoutElse_record() = assertType("{ x: Num }?", "if true then { x = 1 }")

    @Test
    fun ifWithoutElse_inFunction() =
        assertType(
            "(Bool) -> Num?",
            """
            fun f(b: Bool) = if b then 42
            f
            """.trimIndent(),
        )

    @Test
    fun ifWithoutElse_optionalThenBranchFlattens() =
        assertType("Num?", "if true then if false then 42 else null")

    @Test
    fun ifWithoutElse_nestedNoElseFlattens() = assertType("Num?", "if true then if false then 42")

    @Test
    fun ifWithoutElse_asRecordField() =
        assertType(
            "Num?",
            """
            r = { x = if true then 42 }
            r.x
            """.trimIndent(),
        )

    @Test
    fun function_returnsOptional_fromIfElse() =
        assertType(
            "(Num, Bool) -> Num?",
            """
            fun maybeDouble(x: Num, useIt: Bool) = if useIt then x * 2 else null
            maybeDouble
            """.trimIndent(),
        )

    @Test
    fun function_returnsOptional_applied() =
        assertType(
            "Num?",
            """
            fun maybeDouble(x: Num, useIt: Bool) = if useIt then x * 2 else null
            maybeDouble(21, true)
            """.trimIndent(),
        )

    @Test
    fun function_alwaysReturnsNull() =
        assertType(
            "(Any) -> Null",
            """
            fun nothing(x: Any) = null
            nothing
            """.trimIndent(),
        )

    @Test
    fun lambda_returnsOptional() = assertType("(Bool) -> Num?", "|b: Bool -> if b then 42 else null|")

    @Test
    fun lambda_returnsNull() = assertType("(Any) -> Null", "|x: Any -> null|")

    @Test
    fun identity_appliedToNull() =
        assertType(
            "Null",
            """
            fun identity(x: 'A) = x
            identity(null)
            """.trimIndent(),
        )

    @Test
    fun identity_polymorphicWithNull() =
        assertType(
            "Null",
            """
            fun identity(x: 'A) = x
            a = identity(42)
            b = identity(null)
            b
            """.trimIndent(),
        )

    @Test
    fun choose_valueThenNull() =
        assertType(
            "Num?",
            """
            fun choose(cond: Bool, x: 'A, y: 'A) = if cond then x else y
            choose(true, 42, null)
            """.trimIndent(),
        )

    @Test
    fun choose_nullThenValue() =
        assertType(
            "Num?",
            """
            fun choose(cond: Bool, x: 'A, y: 'A) = if cond then x else y
            choose(true, null, 42)
            """.trimIndent(),
        )

    @Test
    fun choose_nullAndNull() =
        assertType(
            "Null",
            """
            fun choose(cond: Bool, x: 'A, y: 'A) = if cond then x else y
            choose(true, null, null)
            """.trimIndent(),
        )

    @Test
    fun typeVariable_boundedByNullAndNum() =
        assertType(
            "(Bool) -> Num?",
            """
            fun returnArg(x: 'A) = x
            f = |b: Bool -> if b then returnArg(42) else returnArg(null)|
            f
            """.trimIndent(),
        )

    @Test
    fun record_withOptionalField_fromIfElse() =
        assertType(
            "(Bool) -> { value: Num? }",
            """
            fun makeRecord(hasValue: Bool) = { value = if hasValue then 42 else null }
            makeRecord
            """.trimIndent(),
        )

    @Test
    fun record_withNullField() = assertType("{ x: Null }", "{ x = null }")

    @Test
    fun record_accessNullField() =
        assertType(
            "Null",
            """
            r = { x = null }
            r.x
            """.trimIndent(),
        )

    @Test
    fun record_accessOptionalField() =
        assertType(
            "Num?",
            """
            r = { x = if true then 42 else null }
            r.x
            """.trimIndent(),
        )

    @Test
    fun record_multipleFieldsSomeOptional() =
        assertType(
            "{ age: Num, name: String, spouse: String? }",
            """{ name = "Alice", age = 30, spouse = if true then "Bob" else null }""",
        )

    @Test
    fun function_acceptsNullArgument() =
        assertType(
            "Null",
            """
            fun first(x: 'A, y: 'B) = x
            first(null, 42)
            """.trimIndent(),
        )

    @Test
    fun function_acceptsNullSecondArgument() =
        assertType(
            "Null",
            """
            fun second(x: 'A, y: 'B) = y
            second(42, null)
            """.trimIndent(),
        )

    // --- chained operations with optionals ---

    @Test
    fun chained_optionalThroughMultipleFunctions() =
        assertType(
            "Num?",
            """
            fun maybeVal(b: Bool) = if b then 42 else null
            fun passThrough(x: 'A) = x
            passThrough(maybeVal(true))
            """.trimIndent(),
        )

    @Test
    fun chained_optionalInRecord() =
        assertType(
            "Num?",
            """
            fun maybeVal(b: Bool) = if b then 42 else null
            r = { x = maybeVal(true) }
            r.x
            """.trimIndent(),
        )

    @Test
    fun ifElse_recordsCommonField() =
        assertType("{ x: Num? }", "if true then { x = 1, } else { x = null }")

    @Test
    fun const_returningNull() = assertType("() -> Null", "|null|")

    @Test
    fun complex_nestedOptionalInFunction() =
        assertType(
            "({ valid: Bool, value: A }) -> { output: A? }",
            """
            fun process(data: { valid: Bool, value: 'A }) =
                result = if data.valid then data.value else null
                { output = result }
            process
            """.trimIndent(),
        )

    @Test
    fun complex_higherOrderWithOptional() =
        assertType(
            "((A) -> B, A, Bool) -> B?",
            """
            fun maybeApply(f: ('A) -> 'B, x: 'A, doIt: Bool) = if doIt then f(x) else null
            maybeApply
            """.trimIndent(),
        )
}
