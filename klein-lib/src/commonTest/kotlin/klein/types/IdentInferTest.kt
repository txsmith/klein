package klein.types

import klein.Type
import klein.TypeError
import klein.TypeEnv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IdentInferTest {
    // ========== Successful Lookup ==========

    @Test
    fun ident_intBinding() {
        val env = TypeEnv.empty()
        env.bind("x", Type.TInt)

        val type = inferExpr("x", env)
        assertEquals(Type.TInt, type)
    }

    @Test
    fun ident_stringBinding() {
        val env = TypeEnv.empty()
        env.bind("name", Type.TString)

        val type = inferExpr("name", env)
        assertEquals(Type.TString, type)
    }

    @Test
    fun ident_boolBinding() {
        val env = TypeEnv.empty()
        env.bind("flag", Type.TBool)

        val type = inferExpr("flag", env)
        assertEquals(Type.TBool, type)
    }

    @Test
    fun ident_functionBinding() {
        val env = TypeEnv.empty()
        val fnType = Type.TFun(listOf(Type.TInt), Type.TString)
        env.bind("f", fnType)

        val type = inferExpr("f", env)
        assertType("Int -> String", type)
    }

    @Test
    fun ident_recordBinding() {
        val env = TypeEnv.empty()
        val recType = Type.TRecord(mapOf("a" to Type.TInt))
        env.bind("r", recType)

        val type = inferExpr("r", env)
        assertType("{ a: Int }", type)
    }

    // ========== Unbound Variables (Errors) ==========

    @Test
    fun ident_unbound_simple() {
        val result = inferExprWithErrors("x")

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
        env.bind("name", Type.TString)

        val result = inferExprWithErrors("naem", env)

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
