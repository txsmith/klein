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
        assertEquals(TNum, env.lookupAndInstantiate("x"))
    }

    @Test
    fun lookup_returnsNullForUnbound() {
        val env = TypeEnv.empty()
        assertNull(env.lookupAndInstantiate("x"))
    }

    @Test
    fun lookup_returnsNullForDifferentName() {
        val env = TypeEnv.empty()
        env.bind("x", TNum)
        assertNull(env.lookupAndInstantiate("y"))
    }

    @Test
    fun child_canSeeParentBindings() {
        val parent = TypeEnv.empty()
        parent.bind("x", TNum)

        val child = parent.child()
        assertEquals(TNum, child.lookupAndInstantiate("x"))
    }

    @Test
    fun child_canShadowParentBindings() {
        val parent = TypeEnv.empty()
        parent.bind("x", TNum)

        val child = parent.child()
        child.bind("x", TString)

        assertEquals(TString, child.lookupAndInstantiate("x"))
        assertEquals(TNum, parent.lookupAndInstantiate("x"))
    }

    @Test
    fun child_bindingDoesNotAffectParent() {
        val parent = TypeEnv.empty()
        val child = parent.child()
        child.bind("x", TNum)

        assertNull(parent.lookupAndInstantiate("x"))
        assertEquals(TNum, child.lookupAndInstantiate("x"))
    }

    @Test
    fun multipleBindings() {
        val env = TypeEnv.empty()
        env.bind("x", TNum)
        env.bind("y", TString)
        env.bind("z", TBool)

        assertEquals(TNum, env.lookupAndInstantiate("x"))
        assertEquals(TString, env.lookupAndInstantiate("y"))
        assertEquals(TBool, env.lookupAndInstantiate("z"))
    }

    @Test
    fun rebindInSameScope() {
        val env = TypeEnv.empty()
        env.bind("x", TNum)
        env.bind("x", TString)

        assertEquals(TString, env.lookupAndInstantiate("x"))
    }

    @Test
    fun nestedScopes() {
        val grandparent = TypeEnv.empty()
        grandparent.bind("x", TNum)

        val parent = grandparent.child()
        parent.bind("y", TString)

        val child = parent.child()
        child.bind("z", TBool)

        assertEquals(TNum, child.lookupAndInstantiate("x"))
        assertEquals(TString, child.lookupAndInstantiate("y"))
        assertEquals(TBool, child.lookupAndInstantiate("z"))

        assertNull(parent.lookupAndInstantiate("z"))
        assertNull(grandparent.lookupAndInstantiate("y"))
        assertNull(grandparent.lookupAndInstantiate("z"))
    }

    @Test
    fun functionTypeBinding() {
        val env = TypeEnv.empty()
        val fnType = TFun(listOf(TNum), TString)
        env.bind("f", fnType)

        assertEquals(fnType, env.lookupAndInstantiate("f"))
    }

    @Test
    fun recordTypeBinding() {
        val env = TypeEnv.empty()
        val recType = TRecord(mapOf("a" to TNum, "b" to TString))
        env.bind("r", recType)

        assertEquals(recType, env.lookupAndInstantiate("r"))
    }
}
