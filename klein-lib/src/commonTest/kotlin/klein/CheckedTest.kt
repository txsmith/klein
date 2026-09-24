package klein

import klein.interp.Value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

private fun evaluate(source: String): Checked<Value> =
    Klein
        .tokenize(source)
        .andThen(Klein::parse)
        .andThen { program -> Klein.check(program).andThen { Klein.lower(program) } }
        .andThen(Klein::execute)

class CheckedTest {
    @Test
    fun getOrThrowReturnsAnAcceptedValue() {
        assertEquals(Value.VNum(3.0), evaluate("1 + 2").getOrThrow())
    }

    @Test
    fun getOrThrowThrowsTheRejectedDiagnostics() {
        val rejected = evaluate("1 +")
        val thrown = assertFailsWith<RejectedException> { rejected.getOrThrow() }
        assertEquals(rejected.assertRejected(), thrown.diagnostics)
    }

    @Test
    fun anAcceptedNullIsAValueNotARejection() {
        assertEquals(Value.VNull, evaluate("null").getOrThrow())
    }

    @Test
    fun mapLeavesARejectionAsItIs() {
        val rejected = evaluate("1 + \"a\"")
        assertSame(rejected, rejected.map { it })
    }

    @Test
    fun aRejectionNeedsADiagnostic() {
        assertFailsWith<IllegalArgumentException> { Checked.Rejected(emptyList()) }
    }
}
