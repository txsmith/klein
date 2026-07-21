package klein.check

import klein.check.Type.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class IdentTypeCheckTest {
    @Test
    fun ident_intBinding() {
        val env = TypeEnv.empty()
        env.bind("x", TNum)
        assertEquals(TNum, infer("x", env).type)
    }

    @Test
    fun ident_stringBinding() {
        val env = TypeEnv.empty()
        env.bind("name", TStr)
        assertEquals(TStr, infer("name", env).type)
    }

    @Test
    fun ident_boolBinding() {
        val env = TypeEnv.empty()
        env.bind("flag", TBool)
        assertEquals(TBool, infer("flag", env).type)
    }

    @Test
    fun ident_functionBinding() {
        val env = TypeEnv.empty()
        env.bind("f", TFun(listOf(TNum), TStr))
        assertEquals(TFun(listOf(TNum), TStr), infer("f", env).type)
    }

    @Test
    fun ident_recordBinding() {
        val env = TypeEnv.empty()
        env.bind("r", TRecord(mapOf("a" to TNum)))
        assertEquals(TRecord(mapOf("a" to TNum)), infer("r", env).type)
    }

    @Test
    fun ident_unbound_simple() {
        val error = infer("x").errors.single()
        assertIs<TypeError.UnboundVariable>(error)
        assertEquals("x", error.name)
        assertEquals("Unbound variable: x", error.message)
    }

    @Test
    fun ident_unbound_similar() {
        val env = TypeEnv.empty()
        env.bind("name", TStr)

        val error = infer("naem", env).errors.single()
        assertIs<TypeError.UnboundVariable>(error)
        assertEquals("naem", error.name)
        assertEquals("Unbound variable: naem", error.message)
    }

    @Test
    fun underscoreIsAlwaysUnbound() {
        val error = infer("_ + 1").errors.single()
        assertIs<TypeError.UnboundVariable>(error)
    }
}
