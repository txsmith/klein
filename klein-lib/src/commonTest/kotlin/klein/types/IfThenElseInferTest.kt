package klein.types

import klein.Type
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IfThenElseInferTest {
    @Test
    fun ifThenElse_sameBranchTypes() {
        assertType(Type.Num, infer("if true then 1 else 2"))
    }

    @Test
    fun ifThenElse_differentBranchTypes() {
        assertType("Num | String", infer("if true then 1 else \"hello\""))
    }

    @Test
    fun ifThenElse_conditionMustBeBool() {
        val errors = inferErrors("if 1 then 2 else 3")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun ifThenElse_withVariable() {
        assertType(Type.Num, infer("x = true\nif x then 1 else 2"))
    }

    @Test
    fun ifThenElse_nested() {
        assertType(Type.Num, infer("if true then if false then 1 else 2 else 3"))
    }

    @Test
    fun ifThenElse_inFunction() {
        assertType(Type.Fun(listOf(Type.Bool), Type.Num), infer("|x -> if x then 1 else 2|"))
    }

    @Test
    fun ifThenElse_withComparison() {
        assertType(Type.Str, infer("if 1 < 2 then \"yes\" else \"no\""))
    }

    @Test
    fun ifThenElse_noElse_returnsUnit() {
        assertType(Type.Unit, infer("if true then 1"))
    }

    @Test
    fun ifThenElse_noElse_conditionMustBeBool() {
        val errors = inferErrors("if 1 then 2")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun ifThenElse_recordBranches() {
        assertType(Type.Record(emptyMap()), infer("if true then { x = 1 } else { y = \"hi\" }"))
    }

    @Test
    fun ifThenElse_functionBranches() {
        assertType("('A) -> 'A | Num", infer("if true then |x -> x| else |y -> 1|"))
    }

    @Test
    fun ifThenElse_conditionFromFieldAccess() {
        assertType("({ y: Bool }) -> Num", infer("|x -> if x.y then 1 else 2|"))
    }

    @Test
    fun ifThenElse_polymorphicBranches() {
        assertType("(Bool) -> ('A) -> ('A) -> 'A", infer("|x -> |y -> |z -> if x then y else z|||"))
    }

    @Test
    fun ifThenElse_conditionUsedInElse() {
        val errors = inferErrors("|x -> |y -> if x then y else x||")
        assertEquals(0, errors.size, "Intersection from conditional should type-check: $errors")
    }

    @Test
    fun ifThenElse_recordBranchesWithCommonField() {
        assertType(Type.Record(mapOf("b" to Type.Bool)), infer("if true then { a = 1, b = true } else { b = false, c = \"hi\" }"))
    }

    @Test
    fun ifThenElse_recordBranchesWithCommonFieldIncompatibleTypes() {
        assertType("{ x: Num | String }", infer("if true then { x = 1 } else { x = \"hello\" }"))
    }

    @Test
    fun ifThenElse_recordBranchesWithMixedCompatibility() {
        assertType("{ a: Num, b: Num | String }", infer("if true then { a = 1, b = 2 } else { a = 3, b = \"hi\" }"))
    }

    @Test
    fun ifThenElse_recordBranchesWithNestedRecords() {
        assertType(Type.Record(mapOf("r" to Type.Record(emptyMap()))), infer("if true then { r = { x = 1 } } else { r = { y = 2 } }"))
    }

    @Test
    fun ifThenElse_recordBranchesWithNestedIncompatiblePrim() {
        assertType("{ r: { x: Num | String } }", infer("if true then { r = { x = 1 } } else { r = { x = \"hi\" } }"))
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
        // So f("hello") could be String or Num - result is Num | String
        assertType(
            "Num | String",
            infer(
                """
                f = if true then |x -> x| else |y -> 1|
                f("hello")
                """.trimIndent(),
            ),
        )
    }
}
