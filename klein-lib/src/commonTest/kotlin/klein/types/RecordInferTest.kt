package klein.types

import klein.types.DisplayType.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordInferTest {
    @Test
    fun emptyRecord() {
        assertType(DRecord(emptyMap()), infer("{}"))
    }

    @Test
    fun singleField() {
        assertType(DRecord(mapOf("x" to DNum)), infer("{ x = 1 }"))
    }

    @Test
    fun twoFields() {
        assertType(DRecord(mapOf("x" to DNum, "y" to DString)), infer("{ x = 1, y = 'hello' }"))
    }

    @Test
    fun nestedRecord() {
        assertType(DRecord(mapOf("inner" to DRecord(mapOf("x" to DNum)))), infer("{ inner = { x = 1 } }"))
    }

    @Test
    fun fieldWithFunction() {
        assertType("{ f: (a) -> a }", infer("{ f = |x -> x| }"))
    }

    @Test
    fun mixedTypes() {
        assertType(DRecord(mapOf("a" to DNum, "b" to DString, "c" to DBool)), infer("{ a = 1, b = 'hi', c = true }"))
    }

    @Test
    fun duplicateField_reportsError() {
        val result = inferWithErrors("{ x = 1, x = 2 }")
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.DuplicateField)
    }

    @Test
    fun duplicateField_usesLastValue() {
        assertType(DRecord(mapOf("x" to DString)), infer("{ x = 1, x = 'hello' }"))
    }

    @Test
    fun fieldAccess_simple() {
        assertType(DNum, infer("{ x = 1 }.x"))
    }

    @Test
    fun fieldAccess_nested() {
        assertType(DNum, infer("{ inner = { x = 1 } }.inner.x"))
    }

    @Test
    fun fieldAccess_fromVariable() {
        assertType(DString, infer("r = { name = 'alice' }\nr.name"))
    }

    @Test
    fun fieldAccess_missingField() {
        val result = inferWithErrors("{ x = 1 }.y")
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.MissingField)
    }

    @Test
    fun fieldAccess_polymorphic() {
        assertType("({ x: a }) -> a", infer("|r -> r.x|"))
    }

    @Test
    fun record_functionResultInField() {
        assertType("((Num) -> a) -> a", infer("|f -> { x = f(42) }.x|"))
    }

    @Test
    fun record_unusedFieldResult() {
        assertType("((Num) -> Any) -> Num", infer("|f -> { x = f(42), y = 123 }.y|"))
    }

    @Test
    fun record_polymorphicFieldAccess_applied() {
        assertType(DNum, infer("|x -> x.f|({ f = 42 })"))
    }

    @Test
    fun record_multipleFields() {
        assertType(DRecord(mapOf("a" to DNum, "b" to DBool, "c" to DString)), infer("{ a = 1, b = true, c = 'hello' }"))
    }

    @Test
    fun record_deeplyNested() {
        assertType(DNum, infer("{ outer = { inner = 42 } }.outer.inner"))
    }
}
