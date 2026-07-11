@file:Suppress("ktlint")

package klein.types

import klein.Type
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OptionalTypeInferTest {

    @Test
    fun nullLiteral_hasTypeNull() {
        assertType(Type.Null, infer("null"))
    }

    @Test
    fun nullLiteral_inBinding() {
        assertType(Type.Null, infer("x = null\nx"))
    }

    @Test
    fun nullLiteral_multipleBindings() {
        assertType(Type.Null, infer("""
            x = null
            y = null
            y
        """.trimIndent()))
    }

    @Test
    fun annotation_bindingResolvesToOptional() {
        assertType(Type.Optional(Type.Num), infer("x: Num? = null\nx"))
    }

    @Test
    fun annotation_acceptsNonNullValue() {
        assertType(Type.Optional(Type.Num), infer("x: Num? = 42\nx"))
    }

    @Test
    fun annotation_nonOptionalRejectsNull() {
        val errors = inferErrors("x: Num = null")
        assertEquals(1, errors.size)
        val error = errors[0]
        assertIs<TypeError.NullNotAllowed>(error)
        assertEquals(Type.Num, error.expected)
    }

    @Test
    fun annotation_doubleOptionalCollapses() {
        assertType(Type.Optional(Type.Num), infer("x: Num?? = null\nx"))
    }

    @Test
    fun annotation_paramAcceptsNullArgument() {
        assertType(Type.Optional(Type.Num), infer("fun f(x: Num?): Num? = x\nf(null)"))
    }

    @Test
    fun ifElse_nullInElseBranch_infersOptional() {
        assertType("Num?", infer("if true then 42 else null"))
    }

    @Test
    fun ifElse_nullInThenBranch_infersOptional() {
        assertType("Num?", infer("if true then null else 42"))
    }

    @Test
    fun ifElse_nullInElseBranch_withString() {
        assertType("String?", infer("if true then \"hello\" else null"))
    }

    @Test
    fun ifElse_nullInElseBranch_withBool() {
        assertType("Bool?", infer("if true then false else null"))
    }

    @Test
    fun ifElse_bothBranchesNull_infersNull() {
        assertType(Type.Null, infer("if true then null else null"))
    }

    @Test
    fun ifElse_optionalInOneBranch_nullInOther() {
        val code = """
            maybeNum = if true then 42 else null
            if false then maybeNum else null
        """.trimIndent()
        assertType("Num?", infer(code))
    }

    @Test
    fun ifElse_nestedWithNull() {
        val code = """
            if true then
                if false then 42 else null
            else
                99
        """.trimIndent()
        assertType("Num?", infer(code))
    }

    @Test
    fun ifElse_nestedBothOptional() {
        val code = """
            if true then
                if false then 42 else null
            else
                if true then null else 99
        """.trimIndent()
        assertType("Num?", infer(code))
    }

    @Test
    fun ifElse_nullWithRecord() {
        assertType("{ x: Num }?", infer("if true then { x = 1 } else null"))
    }

    @Test
    fun ifElse_nullWithFunction() {
        assertType("((Num) -> Num)?", infer("if true then |x -> x + 1| else null"))
    }

    @Test
    fun ifElse_nullWithUnit() {
        val code = """
            fun doNothing() = {}
            if true then doNothing() else null
        """.trimIndent()
        assertType("{}?", infer(code))
    }

    @Test
    fun function_returnsOptional_fromIfElse() {
        val code = """
            fun maybeDouble(x, useIt) =
                if useIt then x * 2 else null
        """.trimIndent()
        assertType("(Num, Bool) -> Num?", infer(code + "\nmaybeDouble"))
    }

    @Test
    fun function_returnsOptional_applied() {
        val code = """
            fun maybeDouble(x, useIt) =
                if useIt then x * 2 else null
            maybeDouble(21, true)
        """.trimIndent()
        assertType("Num?", infer(code))
    }

    @Test
    fun function_alwaysReturnsNull() {
        val code = """
            fun nothing(x) = null
        """.trimIndent()
        assertType("(Any) -> Null", infer(code + "\nnothing"))
    }

    @Test
    fun function_returnsNullApplied() {
        val code = """
            fun nothing(x) = null
            nothing(42)
        """.trimIndent()
        assertType(Type.Null, infer(code))
    }

    @Test
    fun lambda_returnsOptional() {
        assertType("(Bool) -> Num?", infer("|b -> if b then 42 else null|"))
    }

    @Test
    fun lambda_returnsNull() {
        assertType("(Any) -> Null", infer("|x -> null|"))
    }

    @Test
    fun identity_appliedToNull() {
        val code = """
            fun identity(x) = x
            identity(null)
        """.trimIndent()
        assertType(Type.Null, infer(code))
    }

    @Test
    fun identity_polymorphicWithNull() {
        val code = """
            fun identity(x) = x
            a = identity(42)
            b = identity(null)
            b
        """.trimIndent()
        assertType(Type.Null, infer(code))
    }

    @Test
    fun polymorphicFunction_choosesBetweenValueAndNull() {
        val code = """
            fun choose(cond, x, y) = if cond then x else y
            choose(true, 42, null)
        """.trimIndent()
        assertType("Num?", infer(code))
    }

    @Test
    fun polymorphicFunction_choosesBetweenNullAndValue() {
        val code = """
            fun choose(cond, x, y) = if cond then x else y
            choose(true, null, 42)
        """.trimIndent()
        assertType("Num?", infer(code))
    }

    @Test
    fun polymorphicFunction_choosesNullAndNull() {
        val code = """
            fun choose(cond, x, y) = if cond then x else y
            choose(true, null, null)
        """.trimIndent()
        assertType(Type.Null, infer(code))
    }

    @Test
    fun typeVariable_boundedByNullAndNum() {
        val code = """
            fun returnArg(x) = x
            f = |b -> if b then returnArg(42) else returnArg(null)|
        """.trimIndent()
        assertType("(Bool) -> Num?", infer(code + "\nf"))
    }

    @Test
    fun record_withOptionalField_fromIfElse() {
        val code = """
            fun makeRecord(hasValue) = { value = if hasValue then 42 else null }
        """.trimIndent()
        assertType("(Bool) -> { value: Num? }", infer(code + "\nmakeRecord"))
    }

    @Test
    fun record_withNullField() {
        assertType("{ x: Null }", infer("{ x = null }"))
    }

    @Test
    fun record_accessNullField() {
        val code = """
            r = { x = null }
            r.x
        """.trimIndent()
        assertType(Type.Null, infer(code))
    }

    @Test
    fun record_accessOptionalField() {
        val code = """
            r = { x = if true then 42 else null }
            r.x
        """.trimIndent()
        assertType("Num?", infer(code))
    }

    @Test
    fun record_multipleFieldsSomeOptional() {
        val code = """
            {
                name = "Alice",
                age = 30,
                spouse = if true then "Bob" else null
            }
        """.trimIndent()
        assertType("{ age: Num, name: String, spouse: String? }", infer(code))
    }

    @Test
    fun function_acceptsNullArgument() {
        val code = """
            fun first(x, y) = x
            first(null, 42)
        """.trimIndent()
        assertType(Type.Null, infer(code))
    }

    @Test
    fun function_acceptsNullSecondArgument() {
        val code = """
            fun second(x, y) = y
            second(42, null)
        """.trimIndent()
        assertType(Type.Null, infer(code))
    }

    @Test
    fun higherOrderFunction_appliedWithNull() {
        val code = """
            fun apply(f, x) = f(x)
            fun returnArg(x) = x
            apply(returnArg, null)
        """.trimIndent()
        assertType(Type.Null, infer(code))
    }

    @Test
    fun chained_optionalThroughMultipleFunctions() {
        val code = """
            fun maybeVal(b) = if b then 42 else null
            fun passThrough(x) = x
            passThrough(maybeVal(true))
        """.trimIndent()
        assertType("Num?", infer(code))
    }

    @Test
    fun chained_optionalInRecord() {
        val code = """
            fun maybeVal(b) = if b then 42 else null
            r = { x = maybeVal(true) }
            r.x
        """.trimIndent()
        assertType("Num?", infer(code))
    }

    @Test
    fun union_numOrStringOrNull() {
        val code = """
            fun triChoice(a, b) =
                if a then 42
                else if b then "hello"
                else null
        """.trimIndent()
        assertType("(Bool, Bool) -> (Num | String)?", infer(code + "\ntriChoice"), expectedLub = "(Bool, Bool) -> (Num | String)??")
    }

    @Test
    fun union_recordsWithOptional() {
        val code = """
            if true then
                { x = 1, y = if true then 2 else null }
            else
                { x = 3 }
        """.trimIndent()
        assertType("{ x: Num }", infer(code))
    }

    @Test
    fun implicitParam_canBeNull() {
        val code = """
            f = |if . then 42 else null|
        """.trimIndent()
        assertType("(Bool) -> Num?", infer(code + "\nf"))
    }

    @Test
    fun const_returning_null() {
        assertType("() -> Null", infer("|null|"))
    }

    @Test
    fun complex_nestedOptionalInFunction() {
        val code = """
            fun process(data) =
                result = if data.valid then data.value else null
                { output = result }
        """.trimIndent()
        assertType("({ valid: Bool, value: 'A }) -> { output: 'A? }", infer(code + "\nprocess"))
    }

    @Test
    fun complex_optionalChain() {
        val code = """
            fun step1(x) = if x > 0 then x else null
            fun step2(x) = if x > 10 then x * 2 else null
            fun pipeline(input) =
                r1 = step1(input)
                if true then r1 else null
        """.trimIndent()
        assertType("('A & Num) -> 'A?", infer(code + "\npipeline"))
    }

    @Test
    fun complex_mutuallyRecursiveWithOptional() {
        val code = """
            fun isEven(n) = if n == 0 then true else isOdd(n - 1)
            fun isOdd(n) = if n == 0 then false else isEven(n - 1)
            fun maybeCheck(x, check) = if check then isEven(x) else null
        """.trimIndent()
        assertType("(Num, Bool) -> Bool?", infer(code + "\nmaybeCheck"))
    }

    @Test
    fun complex_higherOrderWithOptional() {
        val code = """
            fun maybeApply(f, x, doIt) =
                if doIt then f(x) else null
        """.trimIndent()
        assertType("(('A) -> 'B, 'A, Bool) -> 'B?", infer(code + "\nmaybeApply"))
    }
}
