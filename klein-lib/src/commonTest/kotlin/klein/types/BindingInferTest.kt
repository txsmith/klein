package klein.types

import klein.types.DisplayType.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BindingInferTest {
    @Test
    fun val_single() {
        assertType(DNum, infer("x = 1\nx"))
    }

    @Test
    fun val_multiple() {
        assertType(DNum, infer("x = 1\ny = 2\nx + y"))
    }

    @Test
    fun val_duplicate() {
        val result = inferWithErrors("x = 1\nx = 2\nx")
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.DuplicateBinding)
    }

    @Test
    fun val_tripleDuplicate() {
        val result = inferWithErrors("x = 1\nx = 2\nx = 3\nx")
        assertEquals(2, result.errors.size)
        assertTrue(result.errors.all { it is TypeError.DuplicateBinding })
    }

    @Test
    fun funDef_single() {
        assertType(DNum, infer("fun f(x) = x\nf(1)"))
    }

    @Test
    fun funDef_duplicate() {
        val result = inferWithErrors("fun f(x) = x\nfun f(y) = y\nf(1)")
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.DuplicateBinding)
    }

    @Test
    fun val_and_funDef_sameName() {
        val result = inferWithErrors("x = 1\nfun x(y) = y\nx")
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.DuplicateBinding)
    }

    @Test
    fun funDef_and_val_sameName() {
        val result = inferWithErrors("fun x(y) = y\nx = 1\nx")
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.DuplicateBinding)
    }
}
