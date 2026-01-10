package klein.types

import klein.Type
import klein.TypeEnv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TypeEnvTest {
    // ========== Lookup ==========

    @Test
    fun lookup_returnsBoundType() {
        val env = TypeEnv.empty()
        env.bind("x", Type.TInt)
        assertEquals(Type.TInt, env.lookup("x"))
    }

    @Test
    fun lookup_returnsNullForUnbound() {
        val env = TypeEnv.empty()
        assertNull(env.lookup("x"))
    }

    @Test
    fun lookup_returnsNullForDifferentName() {
        val env = TypeEnv.empty()
        env.bind("x", Type.TInt)
        assertNull(env.lookup("y"))
    }

    // ========== Child Scope ==========

    @Test
    fun child_canSeeParentBindings() {
        val parent = TypeEnv.empty()
        parent.bind("x", Type.TInt)

        val child = parent.child()
        assertEquals(Type.TInt, child.lookup("x"))
    }

    @Test
    fun child_canShadowParentBindings() {
        val parent = TypeEnv.empty()
        parent.bind("x", Type.TInt)

        val child = parent.child()
        child.bind("x", Type.TString)

        assertEquals(Type.TString, child.lookup("x"))
        assertEquals(Type.TInt, parent.lookup("x"))
    }

    @Test
    fun child_bindingDoesNotAffectParent() {
        val parent = TypeEnv.empty()
        val child = parent.child()
        child.bind("x", Type.TInt)

        assertNull(parent.lookup("x"))
        assertEquals(Type.TInt, child.lookup("x"))
    }

    // ========== Multiple Bindings ==========

    @Test
    fun multipleBindings() {
        val env = TypeEnv.empty()
        env.bind("x", Type.TInt)
        env.bind("y", Type.TString)
        env.bind("z", Type.TBool)

        assertEquals(Type.TInt, env.lookup("x"))
        assertEquals(Type.TString, env.lookup("y"))
        assertEquals(Type.TBool, env.lookup("z"))
    }

    @Test
    fun rebindInSameScope() {
        val env = TypeEnv.empty()
        env.bind("x", Type.TInt)
        env.bind("x", Type.TString)

        assertEquals(Type.TString, env.lookup("x"))
    }

    // ========== Nested Scopes ==========

    @Test
    fun nestedScopes() {
        val grandparent = TypeEnv.empty()
        grandparent.bind("x", Type.TInt)

        val parent = grandparent.child()
        parent.bind("y", Type.TString)

        val child = parent.child()
        child.bind("z", Type.TBool)

        assertEquals(Type.TInt, child.lookup("x"))
        assertEquals(Type.TString, child.lookup("y"))
        assertEquals(Type.TBool, child.lookup("z"))

        assertNull(parent.lookup("z"))
        assertNull(grandparent.lookup("y"))
        assertNull(grandparent.lookup("z"))
    }

    // ========== Function Types in Environment ==========

    @Test
    fun functionTypeBinding() {
        val env = TypeEnv.empty()
        val fnType = Type.TFun(listOf(Type.TInt), Type.TString)
        env.bind("f", fnType)

        assertEquals(fnType, env.lookup("f"))
    }

    @Test
    fun recordTypeBinding() {
        val env = TypeEnv.empty()
        val recType = Type.TRecord(mapOf("a" to Type.TInt, "b" to Type.TString))
        env.bind("r", recType)

        assertEquals(recType, env.lookup("r"))
    }
}
