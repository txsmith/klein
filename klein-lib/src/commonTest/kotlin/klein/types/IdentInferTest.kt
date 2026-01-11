package klein.types

import klein.types.SimpleType.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IdentInferTest {
    @Test
    fun ident_intBinding() {
        val env = TypeEnv.empty()
        env.bind("x", TInt)

        val type = infer("x", env)
        assertEquals(TInt, type)
    }

    @Test
    fun ident_stringBinding() {
        val env = TypeEnv.empty()
        env.bind("name", TString)

        val type = infer("name", env)
        assertEquals(TString, type)
    }

    @Test
    fun ident_boolBinding() {
        val env = TypeEnv.empty()
        env.bind("flag", TBool)

        val type = infer("flag", env)
        assertEquals(TBool, type)
    }

    @Test
    fun ident_functionBinding() {
        val env = TypeEnv.empty()
        val fnType = TFun(listOf(TInt), TString)
        env.bind("f", fnType)

        val type = infer("f", env)
        assertType("Int -> String", type)
    }

    @Test
    fun ident_recordBinding() {
        val env = TypeEnv.empty()
        val recType = TRecord(mapOf("a" to TInt))
        env.bind("r", recType)

        val type = infer("r", env)
        assertType("{ a: Int }", type)
    }

    @Test
    fun ident_unbound_simple() {
        val result = inferWithErrors("x")

        assertTrue(result.hasErrors)
        assertEquals(1, result.errors.size)

        val error = result.errors[0]
        assertIs<TypeError.UnboundVariable>(error)
        assertEquals("x", error.name)
        assertEquals("Unbound variable: x", error.message)
    }

    @Test
    fun ident_unbound_similar() {
        val env = TypeEnv.empty()
        env.bind("name", TString)

        val result = inferWithErrors("naem", env)

        assertTrue(result.hasErrors)
        assertEquals(1, result.errors.size)

        val error = result.errors[0]
        assertIs<TypeError.UnboundVariable>(error)
        assertEquals("naem", error.name)
        assertEquals("Unbound variable: naem", error.message)
    }

    // Note: ident_unbound_inExpr requires operators (x + 1)
    // This will be handled in Phase 5 (Operators)
}
