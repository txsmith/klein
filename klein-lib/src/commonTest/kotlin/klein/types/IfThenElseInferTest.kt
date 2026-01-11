package klein.types

import klein.types.DisplayType.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IfThenElseInferTest {
    @Test
    fun ifThenElse_sameBranchTypes() {
        assertType(DNum, infer("if true then 1 else 2"))
    }

    @Test
    fun ifThenElse_differentBranchTypes() {
        assertType("Num | String", infer("if true then 1 else 'hello'"))
    }

    @Test
    fun ifThenElse_conditionMustBeBool() {
        val result = inferWithErrors("if 1 then 2 else 3")
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun ifThenElse_withVariable() {
        assertType(DNum, infer("x = true\nif x then 1 else 2"))
    }

    @Test
    fun ifThenElse_nested() {
        assertType(DNum, infer("if true then if false then 1 else 2 else 3"))
    }

    @Test
    fun ifThenElse_inFunction() {
        assertType(DFun(listOf(DBool), DNum), infer("|x -> if x then 1 else 2|"))
    }

    @Test
    fun ifThenElse_withComparison() {
        assertType(DString, infer("if 1 < 2 then 'yes' else 'no'"))
    }

    @Test
    fun ifThenElse_noElse_returnsUnit() {
        assertType(DUnit, infer("if true then 1"))
    }

    @Test
    fun ifThenElse_noElse_conditionMustBeBool() {
        val result = inferWithErrors("if 1 then 2")
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun ifThenElse_recordBranches() {
        assertType(DRecord(emptyMap()), infer("if true then { x = 1 } else { y = 'hi' }"))
    }

    @Test
    fun ifThenElse_functionBranches() {
        assertType("(a) -> a | Num", infer("if true then |x -> x| else |y -> 1|"))
    }

    @Test
    fun ifThenElse_conditionFromFieldAccess() {
        assertType("({ y: Bool }) -> Num", infer("|x -> if x.y then 1 else 2|"))
    }

    @Test
    fun ifThenElse_polymorphicBranches() {
        assertType("(Bool) -> (a) -> (a) -> a", infer("|x -> |y -> |z -> if x then y else z|||"))
    }

    @Test
    fun ifThenElse_conditionUsedInElse() {
        val result = inferWithErrors("|x -> |y -> if x then y else x||")
        assertEquals(0, result.errors.size, "Intersection from conditional should type-check: ${result.errors}")
    }

    @Test
    fun ifThenElse_recordBranchesWithCommonField() {
        assertType(DRecord(mapOf("b" to DBool)), infer("if true then { a = 1, b = true } else { b = false, c = 'hi' }"))
    }

    @Test
    fun ifThenElse_recordBranchesWithCommonFieldIncompatibleTypes() {
        assertType("{ x: Num | String }", infer("if true then { x = 1 } else { x = 'hello' }"))
    }

    @Test
    fun ifThenElse_recordBranchesWithMixedCompatibility() {
        assertType("{ a: Num, b: Num | String }", infer("if true then { a = 1, b = 2 } else { a = 3, b = 'hi' }"))
    }

    @Test
    fun ifThenElse_recordBranchesWithNestedRecords() {
        assertType(DRecord(mapOf("r" to DRecord(emptyMap()))), infer("if true then { r = { x = 1 } } else { r = { y = 2 } }"))
    }

    @Test
    fun ifThenElse_recordBranchesWithNestedIncompatiblePrim() {
        assertType("{ r: { x: Num | String } }", infer("if true then { r = { x = 1 } } else { r = { x = 'hi' } }"))
    }

    @Test
    fun ifThenElse_recordBranchesWithNestedPrimAndRecord() {
        assertType("{ x: Num | { a: Num } }", infer("if true then { x = 1 } else { x = { a = 2 } }"))
    }

    @Test
    fun ifThenElse_incompatibleBranches_primAndRecord() {
        assertType("Num | { x: Num }", infer("if true then 0 else { x = 3 }"))
    }

    @Test
    fun ifThenElse_incompatibleBranches_viaPolymorphicFunction() {
        assertType(
            "Num | { x: Num }",
            infer(
                """
                f = |f, c, x, y -> if f(c) then x else y|
                f(|x -> x|, true, 0, {x=3})
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun ifThenElse_functionBranches_appliedToString() {
        // f could be identity (returning String) or constant (returning Num)
        // So f('hello') could be String or Num - result is Num | String
        assertType(
            "Num | String",
            infer(
                """
                f = if true then |x -> x| else |y -> 1|
                f('hello')
                """.trimIndent(),
            ),
        )
    }
}
