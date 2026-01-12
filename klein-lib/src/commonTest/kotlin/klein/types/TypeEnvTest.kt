package klein.types

import klein.types.SimpleType.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TypeEnvTest {
    @Test
    fun lookup_returnsBoundType() {
        val env = TypeEnv.empty()
        env.bind("x", TNum)
        assertEquals(TNum, env.lookup("x"))
    }

    @Test
    fun lookup_returnsNullForUnbound() {
        val env = TypeEnv.empty()
        assertNull(env.lookup("x"))
    }

    @Test
    fun lookup_returnsNullForDifferentName() {
        val env = TypeEnv.empty()
        env.bind("x", TNum)
        assertNull(env.lookup("y"))
    }

    @Test
    fun child_canSeeParentBindings() {
        val parent = TypeEnv.empty()
        parent.bind("x", TNum)

        val child = parent.child()
        assertEquals(TNum, child.lookup("x"))
    }

    @Test
    fun child_canShadowParentBindings() {
        val parent = TypeEnv.empty()
        parent.bind("x", TNum)

        val child = parent.child()
        child.bind("x", TString)

        assertEquals(TString, child.lookup("x"))
        assertEquals(TNum, parent.lookup("x"))
    }

    @Test
    fun child_bindingDoesNotAffectParent() {
        val parent = TypeEnv.empty()
        val child = parent.child()
        child.bind("x", TNum)

        assertNull(parent.lookup("x"))
        assertEquals(TNum, child.lookup("x"))
    }

    @Test
    fun multipleBindings() {
        val env = TypeEnv.empty()
        env.bind("x", TNum)
        env.bind("y", TString)
        env.bind("z", TBool)

        assertEquals(TNum, env.lookup("x"))
        assertEquals(TString, env.lookup("y"))
        assertEquals(TBool, env.lookup("z"))
    }

    @Test
    fun rebindInSameScope() {
        val env = TypeEnv.empty()
        env.bind("x", TNum)
        env.bind("x", TString)

        assertEquals(TString, env.lookup("x"))
    }

    @Test
    fun nestedScopes() {
        val grandparent = TypeEnv.empty()
        grandparent.bind("x", TNum)

        val parent = grandparent.child()
        parent.bind("y", TString)

        val child = parent.child()
        child.bind("z", TBool)

        assertEquals(TNum, child.lookup("x"))
        assertEquals(TString, child.lookup("y"))
        assertEquals(TBool, child.lookup("z"))

        assertNull(parent.lookup("z"))
        assertNull(grandparent.lookup("y"))
        assertNull(grandparent.lookup("z"))
    }

    @Test
    fun functionTypeBinding() {
        val env = TypeEnv.empty()
        val fnType = TFun(listOf(TNum), TString)
        env.bind("f", fnType)

        assertEquals(fnType, env.lookup("f"))
    }

    @Test
    fun recordTypeBinding() {
        val env = TypeEnv.empty()
        val recType = TRecord(mapOf("a" to TNum, "b" to TString))
        env.bind("r", recType)

        assertEquals(recType, env.lookup("r"))
    }
}
