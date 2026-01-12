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
        assertType("a | Num", infer("{ x = 1 }.x"))
    }

    @Test
    fun fieldAccess_nested() {
        assertType("a | Num", infer("{ inner = { x = 1 } }.inner.x"))
    }

    @Test
    fun fieldAccess_fromVariable() {
        assertType("a | String", infer("r = { name = 'alice' }\nr.name"))
    }

    @Test
    fun fieldAccess_missingField() {
        val result = inferWithErrors("{ x = 1 }.y")
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.MissingField)
    }

    @Test
    fun fieldAccess_polymorphic() {
        assertType("(a & { x: b }) -> b", infer("|r -> r.x|"))
    }
}
