package klein.interp

import klein.SourceSpan

/** A request from a running program to the host: apply native [function] to [args]. */
data class HostCall(
    val function: Value.VNative,
    val args: List<Value>,
    val span: SourceSpan,
)

/**
 * The state of a program execution. Evaluation runs uninterrupted between host calls; at
 * every native application the machine stops and yields a [Suspended] to the host. The host
 * executes the call however it pleases and resumes whenever it pleases — immediately for a
 * tight loop, or after I/O, a queue, a human. An abandoned [Suspended] is just data; there
 * is nothing to cancel.
 */
sealed class Execution {
    data class Done(
        val value: Value,
    ) : Execution()

    class Suspended internal constructor(
        val call: HostCall,
        private val machine: Machine,
        private val kont: Kont?,
    ) : Execution() {
        private var consumed = false

        /**
         * Continue evaluation with [result] as the host call's value, running until the next
         * host call or completion. Single-shot for now: the store is shared across the whole
         * execution, so resuming the same suspension twice is refused rather than unsound.
         * Throws [KleinRuntimeError] if evaluation fails.
         */
        fun resume(result: Value): Execution {
            check(!consumed) { "this suspension has already been resumed" }
            consumed = true
            return machine.resume(kont, result)
        }
    }
}
