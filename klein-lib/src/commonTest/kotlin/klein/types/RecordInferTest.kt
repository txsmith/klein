package klein.types

import klein.Type
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordInferTest {
    @Test
    fun emptyRecord() {
        assertType(Type.Record(emptyMap()), infer("{}"))
    }

    @Test
    fun singleField() {
        assertType(Type.Record(mapOf("x" to Type.Num)), infer("{ x = 1 }"))
    }

    @Test
    fun twoFields() {
        assertType(Type.Record(mapOf("x" to Type.Num, "y" to Type.Str)), infer("{ x = 1, y = \"hello\" }"))
    }

    @Test
    fun nestedRecord() {
        assertType(Type.Record(mapOf("inner" to Type.Record(mapOf("x" to Type.Num)))), infer("{ inner = { x = 1 } }"))
    }

    @Test
    fun fieldWithFunction() {
        assertType("{ f: ('A) -> 'A }", infer("{ f = |x -> x| }"))
    }

    @Test
    fun mixedTypes() {
        assertType(Type.Record(mapOf("a" to Type.Num, "b" to Type.Str, "c" to Type.Bool)), infer("{ a = 1, b = \"hi\", c = true }"))
    }

    @Test
    fun duplicateField_reportsError() {
        val errors = inferErrors("{ x = 1, x = 2 }")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.DuplicateField)
    }

    @Test
    fun duplicateField_usesLastValue() {
        val errors = inferErrors("{ x = 1, x = \"hello\" }")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.DuplicateField)
    }

    @Test
    fun fieldAccess_simple() {
        assertType(Type.Num, infer("{ x = 1 }.x"))
    }

    @Test
    fun fieldAccess_nested() {
        assertType(Type.Num, infer("{ inner = { x = 1 } }.inner.x"))
    }

    @Test
    fun fieldAccess_fromVariable() {
        assertType(Type.Str, infer("r = { name = \"alice\" }\nr.name"))
    }

    @Test
    fun fieldAccess_missingField() {
        val errors = inferErrors("{ x = 1 }.y")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.MissingField)
    }

    @Test
    fun fieldAccess_polymorphic() {
        assertType("({ x: 'A }) -> 'A", infer("|r -> r.x|"))
    }

    @Test
    fun record_functionResultInField() {
        assertType("((Num) -> 'A) -> 'A", infer("|f -> { x = f(42) }.x|"))
    }

    @Test
    fun record_unusedFieldResult() {
        assertType("((Num) -> Any) -> Num", infer("|f -> { x = f(42), y = 123 }.y|"))
    }

    @Test
    fun record_polymorphicFieldAccess_applied() {
        assertType(Type.Num, infer("|x -> x.f|({ f = 42 })"))
    }

    @Test
    fun record_multipleFields() {
        assertType(Type.Record(mapOf("a" to Type.Num, "b" to Type.Bool, "c" to Type.Str)), infer("{ a = 1, b = true, c = \"hello\" }"))
    }

    @Test
    fun record_deeplyNested() {
        assertType(Type.Num, infer("{ outer = { inner = 42 } }.outer.inner"))
    }
}
