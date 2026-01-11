package klein.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IfThenElseInferTest {
    @Test
    fun ifThenElse_sameBranchTypes() {
        // Result var with Num lower bounds simplifies to Num
        assertType("Num", infer("if true then 1 else 2"))
    }

    @Test
    fun ifThenElse_differentBranchTypes() {
        // Result var with Num and String lower bounds - picks first concrete type
        assertType("Num", infer("if true then 1 else 'hello'"))
    }

    @Test
    fun ifThenElse_conditionMustBeBool() {
        val result = inferWithErrors("if 1 then 2 else 3")
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun ifThenElse_withVariable() {
        assertType("Num", infer("x = true\nif x then 1 else 2"))
    }

    @Test
    fun ifThenElse_nested() {
        // All branches are Num
        assertType("Num", infer("if true then if false then 1 else 2 else 3"))
    }

    @Test
    fun ifThenElse_inFunction() {
        // x has Bool upper bound (negative-only), result has Num lower bound
        assertType("(Bool) -> Num", infer("|x -> if x then 1 else 2|"))
    }

    @Test
    fun ifThenElse_withComparison() {
        assertType("String", infer("if 1 < 2 then 'yes' else 'no'"))
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
        // Result var with two different record lower bounds - picks first
        assertType("{ x: Num }", infer("if true then { x = 1 } else { y = 'hi' }"))
    }

    @Test
    fun ifThenElse_functionBranches() {
        // Result var with two function lower bounds - picks first
        assertType("(a) -> a", infer("if true then |x -> x| else |y -> 1|"))
    }

    @Test
    fun ifThenElse_conditionFromFieldAccess() {
        // x's record constraint, y has Bool upper bound, result is Num
        assertType("({ y: Bool }) -> Num", infer("|x -> if x.y then 1 else 2|"))
    }
}
