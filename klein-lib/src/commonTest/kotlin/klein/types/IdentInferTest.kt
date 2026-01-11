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
        assertType("Int", infer("x", env))
    }

    @Test
    fun ident_stringBinding() {
        val env = TypeEnv.empty()
        env.bind("name", TString)
        assertType("String", infer("name", env))
    }

    @Test
    fun ident_boolBinding() {
        val env = TypeEnv.empty()
        env.bind("flag", TBool)
        assertType("Bool", infer("flag", env))
    }

    @Test
    fun ident_functionBinding() {
        val env = TypeEnv.empty()
        env.bind("f", TFun(listOf(TInt), TString))
        assertType("Int -> String", infer("f", env))
    }

    @Test
    fun ident_recordBinding() {
        val env = TypeEnv.empty()
        env.bind("r", TRecord(mapOf("a" to TInt)))
        assertType("{ a: Int }", infer("r", env))
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
}
