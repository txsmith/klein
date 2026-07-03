package klein.check

import klein.check.Type.*
import klein.types.TypeError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RecordTypeCheckTest {
    @Test
    fun emptyRecord() = assertEquals(TRecord(emptyMap()), infer("{}").type)

    @Test
    fun singleField() = assertEquals(TRecord(mapOf("x" to TNum)), infer("{ x = 1 }").type)

    @Test
    fun twoFields() = assertEquals(TRecord(mapOf("x" to TNum, "y" to TStr)), infer("{ x = 1, y = \"hello\" }").type)

    @Test
    fun nestedRecord() =
        assertEquals(
            TRecord(mapOf("inner" to TRecord(mapOf("x" to TNum)))),
            infer("{ inner = { x = 1 } }").type,
        )

    @Test
    fun mixedTypes() =
        assertEquals(
            TRecord(mapOf("a" to TNum, "b" to TStr, "c" to TBool)),
            infer("{ a = 1, b = \"hi\", c = true }").type,
        )

    @Test
    fun duplicateField_reportsError() {
        val errors = infer("{ x = 1, x = 2 }").errors
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.DuplicateField)
    }

    @Test
    fun duplicateField_usesLastValue() {
        val errors = infer("{ x = 1, x = \"hello\" }").errors
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.DuplicateField)
    }

    @Test
    fun fieldAccess_simple() = assertEquals(TNum, infer("{ x = 1 }.x").type)

    @Test
    fun fieldAccess_nested() = assertEquals(TNum, infer("{ inner = { x = 1 } }.inner.x").type)

    @Test
    fun fieldAccess_fromVariable() = assertEquals(TStr, infer("r = { name = \"alice\" }\nr.name").type)

    @Test
    fun fieldAccess_missingField() {
        val errors = infer("{ x = 1 }.y").errors
        assertEquals(1, errors.size)
        val error = errors[0]
        assertIs<TypeError.MissingField>(error)
        assertEquals("y", error.field)
    }

    @Test
    fun record_multipleFields() =
        assertEquals(
            TRecord(mapOf("a" to TNum, "b" to TBool, "c" to TStr)),
            infer("{ a = 1, b = true, c = \"hello\" }").type,
        )

    @Test
    fun record_deeplyNested() = assertEquals(TNum, infer("{ outer = { inner = 42 } }.outer.inner").type)

    // --- record literal in check position (checkRecordLiteral) ---

    @Test
    fun recordCheckedAgainstAny_passes() =
        // A record literal is a value, so it satisfies `Any` — must not error "found a record".
        assertTrue(infer("r: Any = { x = 1 }\nr").errors.isEmpty())

    @Test
    fun recordCheckedAgainstNonRecord_reportsTypeMismatchNotMisc() {
        // Non-record expected → subsumption fallback → a real TypeMismatch, never a Misc.
        val errors = infer("r: Num = { x = 1 }\nr").errors
        assertTrue(errors.any { it is TypeError.TypeMismatch })
        assertTrue(errors.none { it is TypeError.Misc })
    }

    // --- Deferred until generics (M4) ---
    // Each of these infers the type of an *unannotated* lambda, which Path G doesn't do: bare
    // params are an error, and polymorphism comes from declared type variables. The 'A-typed ones
    // also need a type-variable node in Type. Reinstate (likely as annotated/generic forms) once
    // generics land:
    //   fieldWithFunction                      { f = |x -> x| }                -> { f: ('A) -> 'A }
    //   fieldAccess_polymorphic                |r -> r.x|                      -> ({ x: 'A }) -> 'A
    //   record_functionResultInField           |f -> { x = f(42) }.x|          -> ((Num) -> 'A) -> 'A
    //   record_unusedFieldResult                |f -> { x = f(42), y = 123 }.y| -> ((Num) -> Any) -> Num
    //   record_polymorphicFieldAccess_applied   |x -> x.f|({ f = 42 })          -> Num
}
