package klein.check

import klein.check.Type.*
import klein.types.TypeError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RecordTypeCheckTest {
    @Test
    fun emptyRecord_isTop() = assertEquals(TTop, infer("{}").type)

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
    fun fieldAccess_fromVariable() =
        assertEquals(
            TStr,
            infer(
                """
                r = { name = "alice" }
                r.name
                """.trimIndent(),
            ).type,
        )

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
        assertTrue(
            infer(
                """
                r: Any = { x = 1 }
                r
                """.trimIndent(),
            ).errors.isEmpty(),
        )

    @Test
    fun recordCheckedAgainstNonRecord_reportsTypeMismatchNotMisc() {
        // Non-record expected → subsumption fallback → a real TypeMismatch, never a Misc.
        assertIs<TypeError.TypeMismatch>(
            infer(
                """
                r: Num = { x = 1 }
                r
                """.trimIndent(),
            ).errors.single(),
        )
    }

    // --- record annotations ---

    private val nameAge = TRecord(mapOf("name" to TStr, "age" to TNum))

    @Test
    fun annotation_hidesExtraFields() {
        val e =
            infer(
                """
                r: { x: Num } = { x = 1, y = 2 }
                r.y
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.MissingField>(e)
        assertEquals("y", e.field)
    }

    @Test
    fun fieldConcreteAnnotation_mismatch() = assertMismatch("String", "Num", "{ x: Num = \"hello\" }")

    @Test
    fun fieldLambdaIsMonomorphic() =
        assertInfersType(TRecord(mapOf("id" to TFun(listOf(TNum), TNum))), "{ id = |x: Num -> x| }")

    @Test
    fun fieldLambdaMismatchAtCallSite() =
        assertMismatch(
            "String",
            "Num",
            """
            r = { f = |x: Num -> x| }
            r.f("hello")
            """.trimIndent(),
        )

    @Test
    fun fieldLambdaCannotIntroduceTypeVar() {
        val e = infer("{ f = |x: 'A -> x + 1| }").errors.single()
        assertIs<TypeError.UnboundVariable>(e)
        assertEquals("A", e.name)
    }

    @Test
    fun multiFieldParam_fieldAccess() =
        assertInfersType(
            TFun(listOf(nameAge), TStr),
            """
            fun f(x: { name: String, age: Num }) =
              ignored = x.age + 1
              x.name
            f
            """.trimIndent(),
        )

    @Test
    fun multiFieldParam_accessSecondField() =
        assertInfersType(
            TFun(listOf(nameAge), TNum),
            """
            fun f(x: { name: String, age: Num }) = x.age
            f
            """.trimIndent(),
        )

    @Test
    fun multiFieldParam_rejectsMissingField() {
        val e =
            infer(
                """
                fun f(x: { name: String, age: Num }) = x.bone
                f
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.MissingField>(e)
        assertEquals("bone", e.field)
    }

    @Test
    fun multiFieldParam_outputAnnotation() =
        assertInfersType(
            TFun(listOf(nameAge), nameAge),
            """
            fun f(x: { name: String, age: Num }): { name: String, age: Num } = x
            f
            """.trimIndent(),
        )

    @Test
    @kotlin.test.Ignore // fun defs in records aren't implemented yet
    fun fieldFunDefWithAnnotatedParams() =
        assertInfersType(
            TRecord(mapOf("double" to TFun(listOf(TNum), TNum))),
            """
            {
              fun double(x: Num): Num = x * 2
            }
            """.trimIndent(),
        )

    // --- Not portable: unannotated-lambda inference + polymorphic record fields ---
    // Each infers the type of an *unannotated* lambda from usage, which Path G doesn't do (bare
    // params are an error). The polymorphic results would also need polymorphic record fields —
    // explicit `forall`, still deferred; records stay monomorphic. Not a milestone away, so they
    // stay out unless rewritten into annotated/monomorphic forms:
    //   fieldWithFunction                      { f = |x -> x| }                -> { f: ('A) -> 'A }
    //   fieldAccess_polymorphic                |r -> r.x|                      -> ({ x: 'A }) -> 'A
    //   record_functionResultInField           |f -> { x = f(42) }.x|          -> ((Num) -> 'A) -> 'A
    //   record_unusedFieldResult                |f -> { x = f(42), y = 123 }.y| -> ((Num) -> Any) -> Num
    //   record_polymorphicFieldAccess_applied   |x -> x.f|({ f = 42 })          -> Num
}
