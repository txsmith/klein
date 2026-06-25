package klein.check

import klein.check.Type.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsSubtypeTest {
    private val checker = Checker()
    private val env = TypeEnv.empty()

    private infix fun Type.subOf(other: Type) = checker.isSubtype(this, other, env)

    private fun rec(vararg fields: Pair<String, Type>) = TRecord(fields.toMap())

    // --- primitives ---

    @Test
    fun primitivesReflexive() {
        assertTrue(TNum subOf TNum)
        assertTrue(TStr subOf TStr)
        assertTrue(TBool subOf TBool)
        assertTrue(TUnit subOf TUnit)
    }

    @Test
    fun distinctPrimitivesUnrelated() {
        assertFalse(TNum subOf TStr)
        assertFalse(TStr subOf TNum)
        assertFalse(TBool subOf TNum)
    }

    // --- top / bottom ---

    @Test
    fun everythingSubtypeOfTop() {
        assertTrue(TNum subOf TTop)
        assertTrue(TFun(listOf(TNum), TStr) subOf TTop)
        assertTrue(rec("a" to TNum) subOf TTop)
    }

    @Test
    fun bottomSubtypeOfEverything() {
        assertTrue(TBottom subOf TNum)
        assertTrue(TBottom subOf TFun(listOf(TNum), TStr))
        assertTrue(TBottom subOf rec("a" to TNum))
    }

    // --- functions ---

    @Test
    fun functionResultCovariant() {
        assertTrue(TFun(listOf(TNum), rec("x" to TNum, "y" to TNum)) subOf TFun(listOf(TNum), rec("x" to TNum)))
        assertFalse(TFun(listOf(TNum), rec("x" to TNum)) subOf TFun(listOf(TNum), rec("x" to TNum, "y" to TNum)))
    }

    @Test
    fun functionParamContravariant() {
        val acceptsX = TFun(listOf(rec("x" to TNum)), TNum)
        val acceptsXY = TFun(listOf(rec("x" to TNum, "y" to TNum)), TNum)
        assertTrue(acceptsX subOf acceptsXY) // accepting less is more general
        assertFalse(acceptsXY subOf acceptsX)
    }

    @Test
    fun functionArityMismatch() {
        assertFalse(TFun(listOf(TNum), TNum) subOf TFun(listOf(TNum, TNum), TNum))
        assertFalse(TFun(emptyList(), TNum) subOf TFun(listOf(TNum), TNum))
    }

    @Test
    fun functionNotSubtypeOfPrimitive() {
        assertFalse(TFun(listOf(TNum), TNum) subOf TNum)
    }

    @Test
    fun functionParamNamesIrrelevant() {
        assertTrue(TFun(listOf(TNum), TNum, listOf("a")) subOf TFun(listOf(TNum), TNum, listOf("b")))
    }

    @Test
    fun functionMultiParamPointwiseContravariant() {
        // params compared pointwise; accepting a narrower record in a param is more general
        assertTrue(TFun(listOf(rec("a" to TNum), TNum), TNum) subOf TFun(listOf(rec("a" to TNum, "b" to TNum), TNum), TNum))
        assertFalse(TFun(listOf(rec("a" to TNum, "b" to TNum), TNum), TNum) subOf TFun(listOf(rec("a" to TNum), TNum), TNum))
    }

    @Test
    fun higherOrderFunctionParamContravariant() {
        // function in param position: the *inner* function flips variance again.
        // accepting an inner fn that returns less ({a}) is more general than one returning more ({a,b}).
        val takesNarrowReturner = TFun(listOf(TFun(listOf(TNum), rec("a" to TNum))), TNum)
        val takesWideReturner = TFun(listOf(TFun(listOf(TNum), rec("a" to TNum, "b" to TNum))), TNum)
        assertTrue(takesNarrowReturner subOf takesWideReturner)
        assertFalse(takesWideReturner subOf takesNarrowReturner)
    }

    @Test
    fun higherOrderFunctionUnrelatedResultsFail() {
        // ((Num) -> Num) -> Num  vs  ((Num) -> Str) -> Num — neither direction holds.
        val takesNumReturner = TFun(listOf(TFun(listOf(TNum), TNum)), TNum)
        val takesStrReturner = TFun(listOf(TFun(listOf(TNum), TStr)), TNum)
        assertFalse(takesNumReturner subOf takesStrReturner)
        assertFalse(takesStrReturner subOf takesNumReturner)
    }

    // --- records ---

    @Test
    fun recordWidth() {
        assertTrue(rec("x" to TNum, "y" to TNum) subOf rec("x" to TNum))
    }

    @Test
    fun recordMissingField() {
        assertFalse(rec("x" to TNum) subOf rec("x" to TNum, "y" to TNum))
    }

    @Test
    fun recordFieldTypeMismatch() {
        assertFalse(rec("x" to TStr) subOf rec("x" to TNum))
    }

    @Test
    fun recordDepthCovariant() {
        assertTrue(rec("p" to rec("a" to TNum, "b" to TNum)) subOf rec("p" to rec("a" to TNum)))
    }

    @Test
    fun emptyRecordIsSupertype() {
        assertTrue(rec("x" to TNum) subOf rec())
    }

    @Test
    fun recordNotSubtypeOfPrimitive() {
        assertFalse(rec("x" to TNum) subOf TNum)
    }

    // --- optional ---

    @Test
    fun valueSubtypeOfOptional() {
        assertTrue(TNum subOf TOptional(TNum))
    }

    @Test
    fun nullSubtypeOfOptional() {
        assertTrue(TNull subOf TOptional(TNum))
    }

    @Test
    fun optionalCovariant() {
        assertTrue(TOptional(rec("x" to TNum, "y" to TNum)) subOf TOptional(rec("x" to TNum)))
    }

    @Test
    fun optionalNotSubtypeOfNonOptional() {
        assertFalse(TOptional(TNum) subOf TNum) // could be null
        assertFalse(TNull subOf TNum)
    }
}
