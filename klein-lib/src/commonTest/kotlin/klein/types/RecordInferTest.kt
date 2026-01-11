package klein.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordInferTest {
    @Test
    fun emptyRecord() {
        assertType("{}", infer("{}"))
    }

    @Test
    fun singleField() {
        assertType("{ x: Num }", infer("{ x = 1 }"))
    }

    @Test
    fun twoFields() {
        assertType("{ x: Num, y: String }", infer("{ x = 1, y = 'hello' }"))
    }

    @Test
    fun nestedRecord() {
        assertType("{ inner: { x: Num } }", infer("{ inner = { x = 1 } }"))
    }

    @Test
    fun fieldWithFunction() {
        assertType("{ f: (a) -> a }", infer("{ f = |x -> x| }"))
    }

    @Test
    fun mixedTypes() {
        assertType("{ a: Num, b: String, c: Bool }", infer("{ a = 1, b = 'hi', c = true }"))
    }

    @Test
    fun duplicateField_reportsError() {
        val result = inferWithErrors("{ x = 1, x = 2 }")
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.DuplicateField)
    }

    @Test
    fun duplicateField_usesLastValue() {
        assertType("{ x: String }", infer("{ x = 1, x = 'hello' }"))
    }

    @Test
    fun fieldAccess_simple() {
        // Result var is positive-only with Num lower bound
        assertType("Num", infer("{ x = 1 }.x"))
    }

    @Test
    fun fieldAccess_nested() {
        assertType("Num", infer("{ inner = { x = 1 } }.inner.x"))
    }

    @Test
    fun fieldAccess_fromVariable() {
        assertType("String", infer("r = { name = 'alice' }\nr.name"))
    }

    @Test
    fun fieldAccess_missingField() {
        val result = inferWithErrors("{ x = 1 }.y")
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.MissingField)
    }

    @Test
    fun fieldAccess_polymorphic() {
        // r appears both positively (input) and negatively (param)
        // The record constraint should be preserved, field type is truly polymorphic
        assertType("({ x: a }) -> a", infer("|r -> r.x|"))
    }
}
