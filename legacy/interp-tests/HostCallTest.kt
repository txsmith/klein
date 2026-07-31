package klein.interp

import klein.Klein
import klein.surface.Program
import klein.interp.Value.VNum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Suspension at the host seam: every native application yields control to the host. */
class HostCallTest {
    private fun parse(src: String): Program =
        Klein
            .tokenize(src.trimIndent())
            .andThen(Klein::parse)
            .output ?: error("test program does not parse")

    @Test
    fun suspendsAtEachHostCallAndHostDictatesResults() {
        val program = parse("fetch(1) + fetch(10)")
        val bindings = mapOf("fetch" to Value.VNative("fetch", 1))

        val first = Interpreter().begin(program, bindings)
        assertIs<Execution.Suspended>(first)
        assertEquals("fetch", first.call.function.name)
        assertEquals(listOf<Value>(VNum(1.0)), first.call.args)

        // The host answers whatever it pleases — evaluation continues with its result.
        val second = first.resume(VNum(100.0))
        assertIs<Execution.Suspended>(second)
        assertEquals(listOf<Value>(VNum(10.0)), second.call.args)

        val done = second.resume(VNum(0.5))
        assertIs<Execution.Done>(done)
        assertEquals(VNum(100.5), done.value)
    }

    @Test
    fun hostCallResultsFlowThroughTheProgram() {
        val program =
            parse(
                """
                fun surcharge(amount: Num): Num = amount + lookupFee(amount)
                surcharge(100) + surcharge(10)
                """,
            )
        // A synchronous driver is just one host policy: resume immediately.
        val value =
            Interpreter().run(program, mapOf("lookupFee" to Value.VNative("lookupFee", 1))) { call ->
                VNum((call.args.single() as VNum).value / 10)
            }
        assertEquals(VNum(121.0), value)
    }

    @Test
    fun programWithNoHostCallsCompletesImmediately() {
        val done = Interpreter().begin(parse("1 + 2"))
        assertIs<Execution.Done>(done)
        assertEquals(VNum(3.0), done.value)
    }

    @Test
    fun unhandledHostCallIsARuntimeError() {
        val program = parse("fetch(1)")
        val result = Klein.interpret(program, mapOf("fetch" to Value.VNative("fetch", 1)))
        assertTrue(result.hasErrors)
        assertTrue(result.errors.single().message.contains("No host handler"))
    }

    @Test
    fun arityIsCheckedBeforeYieldingToTheHost() {
        val program = parse("fetch(1, 2)")
        val e =
            assertFailsWith<KleinRuntimeError> {
                Interpreter().begin(program, mapOf("fetch" to Value.VNative("fetch", 1)))
            }
        assertTrue(e.message.contains("Expected 1 argument"))
    }

    @Test
    fun runtimeErrorAfterResumeSurfacesFromResume() {
        val program = parse("fetch(1) / 0")
        val suspended = Interpreter().begin(program, mapOf("fetch" to Value.VNative("fetch", 1)))
        assertIs<Execution.Suspended>(suspended)
        val e = assertFailsWith<KleinRuntimeError> { suspended.resume(VNum(7.0)) }
        assertEquals("Division by zero", e.message)
    }

    @Test
    fun suspensionIsSingleShot() {
        val program = parse("fetch(1)")
        val suspended = Interpreter().begin(program, mapOf("fetch" to Value.VNative("fetch", 1)))
        assertIs<Execution.Suspended>(suspended)
        suspended.resume(VNum(1.0))
        assertFailsWith<IllegalStateException> { suspended.resume(VNum(2.0)) }
    }
}

/** Capabilities only the machine representation provides: K lives on the heap, not the host stack. */
class MachineTest {
    private fun parse(src: String): Program =
        Klein
            .tokenize(src.trimIndent())
            .andThen(Klein::parse)
            .output ?: error("test program does not parse")

    @Test
    fun deepNonTailRecursionDoesNotUseTheHostStack() {
        // ~50k nested frames would overflow any host thread stack in a recursive evaluator.
        val program =
            parse(
                """
                fun sumTo(n: Num): Num = if n == 0 then 0 else n + sumTo(n - 1)
                sumTo(50000)
                """,
            )
        val done = Interpreter().begin(program)
        assertIs<Execution.Done>(done)
        assertEquals(VNum(1250025000.0), done.value)
    }

    @Test
    fun tailCallsRunInConstantSpace() {
        // A million tail-recursive steps: only terminates in reasonable space if the
        // machine enters bodies on the caller's continuation.
        val program =
            parse(
                """
                fun countdown(n: Num): Num = if n == 0 then 0 else countdown(n - 1)
                countdown(1000000)
                """,
            )
        val done = Interpreter().begin(program)
        assertIs<Execution.Done>(done)
        assertEquals(VNum(0.0), done.value)
    }
}
