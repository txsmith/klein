package klein.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IfThenElseInferTest {
    @Test
    fun ifThenElse_sameBranchTypes() {
        assertType("a | Num", infer("if true then 1 else 2"))
    }

    @Test
    fun ifThenElse_differentBranchTypes() {
        assertType("a | Num | String", infer("if true then 1 else 'hello'"))
    }

    @Test
    fun ifThenElse_conditionMustBeBool() {
        val result = inferWithErrors("if 1 then 2 else 3")
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun ifThenElse_withVariable() {
        assertType("a | Num", infer("x = true\nif x then 1 else 2"))
    }

    @Test
    fun ifThenElse_nested() {
        assertType("a | Num | b", infer("if true then if false then 1 else 2 else 3"))
    }

    @Test
    fun ifThenElse_inFunction() {
        assertType("(a & Bool) -> b | Num", infer("|x -> if x then 1 else 2|"))
    }

    @Test
    fun ifThenElse_withComparison() {
        assertType("a | String", infer("if 1 < 2 then 'yes' else 'no'"))
    }

    @Test
    fun ifThenElse_noElse_returnsUnit() {
        assertType("Unit", infer("if true then 1"))
    }

    @Test
    fun ifThenElse_noElse_conditionMustBeBool() {
        val result = inferWithErrors("if 1 then 2")
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun ifThenElse_recordBranches() {
        assertType("a | { x: Num } | { y: String }", infer("if true then { x = 1 } else { y = 'hi' }"))
    }

    @Test
    fun ifThenElse_functionBranches() {
        assertType("a | ((b) -> b) | ((c) -> Num)", infer("if true then |x -> x| else |y -> 1|"))
    }

    @Test
    fun ifThenElse_conditionFromFieldAccess() {
        assertType("(a & { y: b & Bool }) -> c | Num", infer("|x -> if x.y then 1 else 2|"))
    }
}
