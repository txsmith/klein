package klein.types

import klein.Type
import kotlin.test.Test
import kotlin.test.assertEquals

class BindingInferTest {
    @Test
    fun val_single() {
        assertType(Type.Num, infer("x = 1\nx"))
    }

    @Test
    fun val_multiple() {
        assertType(Type.Num, infer("x = 1\ny = 2\nx + y"))
    }

    @Test
    fun val_duplicate() {
        val errors = inferErrors("x = 1\nx = 2\nx")
        assertEquals(1, errors.size)
        assertDuplicateBinding(errors[0], "x")
    }

    @Test
    fun val_tripleDuplicate() {
        val errors = inferErrors("x = 1\nx = 2\nx = 3\nx")
        assertEquals(2, errors.size)
        assertDuplicateBinding(errors[0], "x")
        assertDuplicateBinding(errors[1], "x")
    }

    @Test
    fun funDef_single() {
        assertType(Type.Num, infer("fun f(x) = x\nf(1)"))
    }

    @Test
    fun funDef_duplicate() {
        val errors = inferErrors("fun f(x) = x\nfun f(y) = y\nf(1)")
        assertEquals(1, errors.size)
        assertDuplicateBinding(errors[0], "f")
    }

    @Test
    fun val_and_funDef_sameName() {
        val errors = inferErrors("x = 1\nfun x(y) = y\nx")
        assertEquals(1, errors.size)
        assertDuplicateBinding(errors[0], "x")
    }

    @Test
    fun funDef_and_val_sameName() {
        val errors = inferErrors("fun x(y) = y\nx = 1\nx")
        assertEquals(1, errors.size)
        assertDuplicateBinding(errors[0], "x")
    }
}
