package klein.check

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TypeEnvTest {
    @Test
    fun lookup_returnsBoundType() {
        val env = TypeEnv.empty()
        env.bind("x", Type.TNum)
        assertEquals(Type.TNum, env.lookup("x"))
    }

    @Test
    fun lookup_returnsNullForUnbound() {
        val env = TypeEnv.empty()
        assertNull(env.lookup("x"))
    }

    @Test
    fun lookup_returnsNullForDifferentName() {
        val env = TypeEnv.empty()
        env.bind("x", Type.TNum)
        assertNull(env.lookup("y"))
    }

    @Test
    fun child_canSeeParentBindings() {
        val parent = TypeEnv.empty()
        parent.bind("x", Type.TNum)

        val child = parent.child()
        assertEquals(Type.TNum, child.lookup("x"))
    }

    @Test
    fun child_canShadowParentBindings() {
        val parent = TypeEnv.empty()
        parent.bind("x", Type.TNum)

        val child = parent.child()
        child.bind("x", Type.TStr)

        assertEquals(Type.TStr, child.lookup("x"))
        assertEquals(Type.TNum, parent.lookup("x"))
    }

    @Test
    fun child_bindingDoesNotAffectParent() {
        val parent = TypeEnv.empty()
        val child = parent.child()
        child.bind("x", Type.TNum)

        assertNull(parent.lookup("x"))
        assertEquals(Type.TNum, child.lookup("x"))
    }

    @Test
    fun multipleBindings() {
        val env = TypeEnv.empty()
        env.bind("x", Type.TNum)
        env.bind("y", Type.TStr)
        env.bind("z", Type.TBool)

        assertEquals(Type.TNum, env.lookup("x"))
        assertEquals(Type.TStr, env.lookup("y"))
        assertEquals(Type.TBool, env.lookup("z"))
    }

    @Test
    fun rebindInSameScope() {
        val env = TypeEnv.empty()
        env.bind("x", Type.TNum)
        env.bind("x", Type.TStr)

        assertEquals(Type.TStr, env.lookup("x"))
    }

    @Test
    fun nestedScopes() {
        val grandparent = TypeEnv.empty()
        grandparent.bind("x", Type.TNum)

        val parent = grandparent.child()
        parent.bind("y", Type.TStr)

        val child = parent.child()
        child.bind("z", Type.TBool)

        assertEquals(Type.TNum, child.lookup("x"))
        assertEquals(Type.TStr, child.lookup("y"))
        assertEquals(Type.TBool, child.lookup("z"))

        assertNull(parent.lookup("z"))
        assertNull(grandparent.lookup("y"))
        assertNull(grandparent.lookup("z"))
    }

    @Test
    fun functionTypeBinding() {
        val env = TypeEnv.empty()
        val fnType = Type.TFun(listOf(Type.TNum), Type.TStr)
        env.bind("f", fnType)

        assertEquals(fnType, env.lookup("f"))
    }

    @Test
    fun recordTypeBinding() {
        val env = TypeEnv.empty()
        val recType = Type.TRecord(mapOf("a" to Type.TNum, "b" to Type.TStr))
        env.bind("r", recType)

        assertEquals(recType, env.lookup("r"))
    }
}
