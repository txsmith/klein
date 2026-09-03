package klein.host

import klein.RevisionNumber
import klein.check.RuleType
import klein.check.Type
import klein.check.contract.DeclarationKind
import klein.check.contract.Edition
import klein.check.contract.ResolvedRelease
import klein.interp.Execution
import klein.interp.Interpreter
import klein.interp.Value

/**
 * Start, resume, and replay are this one call. A null [log] starts fresh; otherwise the log is
 * replayed first (start values by name, replies by position) and every call past the
 * end of the log is answered by [registerHandlers], or failing that by the environment's own
 * registrations.
 *
 * A rule that fails at runtime is a normal result: [RunOutcome.Failed] carries its diagnostics and
 * the log so far. Everything else Klein detects — a bad registration, an unservable pin, a recorded
 * answer that does not fit the contract, a divergence, a call whose arguments do not fit the
 * contract, a handler answering the wrong type — is host
 * misuse and throws [RunFailure]; an exception from the host's own code (a handler, an initiation,
 * [persist], `transact`) escapes unwrapped. A call to a deferred capability runs its initiation,
 * records nothing, and returns [RunOutcome.Parked]; resume by calling run again with
 * `parked.toReply(answer)` appended to the log.
 *
 * [persist] is called with each newly recorded entry before execution continues, inside the same
 * `transact` as the handler work that produced it. Replayed entries are not persisted.
 */
fun Environment.run(
    edition: Edition,
    log: EffectLog? = null,
    persist: (LogEntry) -> Unit = {},
    registerHandlers: Registry.() -> Unit = {},
): RunOutcome {
    val handlers = Registry(contract.declarations).apply(registerHandlers)
    if (handlers.errors.isNotEmpty()) throw RunFailure(RunError.InvalidRegistration(handlers.errors))
    val pinProblems = checkPins(edition, handlers)
    if (pinProblems.isNotEmpty()) throw RunFailure(RunError.UnservablePins(pinProblems))
    if (log != null) {
        val logProblems = checkLog(edition, log)
        if (logProblems.isNotEmpty()) throw RunFailure(RunError.LogTypeMismatch(logProblems))
    }
    return Run(this, edition, handlers, persist, log).start()
}

sealed interface RunOutcome {
    val log: EffectLog

    class Completed(
        val value: Value,
        override val log: EffectLog,
    ) : RunOutcome

    class Failed(
        val diagnostics: List<Diagnostic>,
        override val log: EffectLog,
    ) : RunOutcome

    /**
     * Record `toReply(answer)` in your log store the moment the answer arrives — in your own
     * transaction, not Klein's. Resuming replays it like any other stored entry; [run] will not
     * persist it for you.
     */
    class Parked(
        val call: Call,
        override val log: EffectLog,
    ) : RunOutcome {
        fun toReply(answer: Value) = LogEntry.Reply(call, answer)
    }
}

class RunFailure internal constructor(
    val error: RunError,
) : Exception(error.message)

sealed interface RunError {
    val message: String

    class InvalidRegistration(
        val problems: List<RegistrationError>,
    ) : RunError {
        override val message get() = problems.joinToString("\n") { it.message }
    }

    class UnservablePins(
        val problems: List<PinProblem>,
    ) : RunError {
        override val message get() = problems.joinToString("\n") { it.message }
    }

    class LogTypeMismatch(
        val problems: List<Problem>,
    ) : RunError {
        class Problem(
            val at: Int,
            val name: String,
            val answerType: String,
            val declaredType: RuleType,
        ) {
            val message get() = "log entry $at holds $answerType for '$name' where the contract declares ${Type.print(declaredType)}"
        }

        override val message get() = problems.joinToString("\n") { it.message }
    }

    class Diverged(
        val expected: String,
        val got: String,
        val at: Int,
        val call: Call?,
    ) : RunError {
        override val message get() = "replay diverged at log entry $at: expected $expected, got $got"
    }

    class HandlerTypeMismatch(
        val call: String,
        val answerType: String,
        val declaredType: RuleType,
    ) : RunError {
        override val message get() = "'$call' answered with $answerType where the contract declares ${Type.print(declaredType)}"
    }

    class CallTypeMismatch(
        val call: String,
        val got: String,
        val declared: String,
    ) : RunError {
        override val message get() = "'$call' was called with $got where the contract declares $declared"
    }
}

private class Run(
    val environment: Environment,
    val edition: Edition,
    val handlers: Registry,
    val persist: (LogEntry) -> Unit,
    val effectLog: EffectLog?,
) {
    val release = environment.contract.resolve(edition.release)
    lateinit var log: EffectLog

    fun start(): RunOutcome =
        try {
            val opening = Interpreter.start(edition.core)
            val replayed =
                if (effectLog != null) {
                    log = effectLog
                    val started = replayStart(effectLog.start, opening)
                    replayEntries(started)
                } else {
                    getHostInputs(opening)
                }
            val done = executeUntilParked(replayed)
            finish(done)
        } catch (e: Halt) {
            e.outcome
        }

    private fun replayStart(
        start: LogEntry.Start,
        from: Execution,
    ): Execution {
        var execution = from
        while (true) {
            val suspension = execution as? Execution.AwaitingHost ?: break
            if (!isValueAsk(suspension)) break
            val answer =
                start.inputs[suspension.call]
                    ?: diverge(RunError.Diverged("the start entry to hold '${suspension.call}'", "a start entry without it", 0, suspension.toCall()))
            execution = suspension.resume(answer)
        }
        return execution
    }

    private fun replayEntries(from: Execution): Execution {
        var execution = from
        var at = 1
        for (reply in log.replies) {
            compareToRecorded(at, reply, execution)?.let { diverge(it) }
            execution = (execution as Execution.AwaitingHost).resume(reply.answer)
            at++
        }
        log.ending?.let { ending ->
            compareToRecorded(at, ending, execution)?.let { diverge(it) }
            when (ending) {
                is LogEntry.Result -> throw Halt(RunOutcome.Completed(ending.value, log))
                is LogEntry.Failure -> throw Halt(RunOutcome.Failed(ending.errors, log))
            }
        }
        return execution
    }

    private fun getHostInputs(from: Execution): Execution {
        val inputs = linkedMapOf<String, Value>()
        var execution = from
        lateinit var started: LogEntry.Start
        transact {
            while (true) {
                val suspension = execution as? Execution.AwaitingHost ?: break
                if (!isValueAsk(suspension)) break
                val answer =
                    when (val response = askHandler(suspension)) {
                        is HandlerResponse.Answer -> response.value
                        HandlerResponse.Deferred -> throw IllegalStateException("'${suspension.call}' is a value and cannot be deferred")
                    }
                inputs[suspension.call] = answer
                execution = suspension.resume(answer)
            }
            started = LogEntry.Start(inputs)
            persist(started)
        }
        log = EffectLog(started)
        return execution
    }

    private fun executeUntilParked(from: Execution): Execution {
        var execution = from
        while (true) {
            val suspension = execution as? Execution.AwaitingHost ?: return execution
            val call = suspension.toCall()
            lateinit var reply: LogEntry.Reply
            val response =
                transact {
                    val answer = askHandler(suspension)
                    if (answer is HandlerResponse.Answer) {
                        reply = LogEntry.Reply(call, answer.value)
                        persist(reply)
                    }
                    answer
                }
            when (response) {
                HandlerResponse.Deferred -> throw Halt(RunOutcome.Parked(call, log))
                is HandlerResponse.Answer -> {
                    log += reply
                    execution = suspension.resume(response.value)
                }
            }
        }
    }

    private fun finish(end: Execution): RunOutcome =
        when (end) {
            is Execution.Failure -> {
                val failure = LogEntry.Failure(listOf(Diagnostic.of(end.error)))
                transact { persist(failure) }
                log += failure
                RunOutcome.Failed(failure.errors, log)
            }
            is Execution.Done -> {
                val ending = LogEntry.Result(end.value)
                transact { persist(ending) }
                log += ending
                RunOutcome.Completed(end.value, log)
            }
            is Execution.AwaitingHost -> throw IllegalStateException("cannot finish a run while awaiting '${end.call}'")
        }

    private fun diverge(diverged: RunError.Diverged): Nothing = throw RunFailure(diverged)

    private fun compareToRecorded(
        at: Int,
        recorded: LogEntry,
        execution: Execution,
    ): RunError.Diverged? {
        val call = (execution as? Execution.AwaitingHost)?.toCall()
        val matches =
            when (recorded) {
                is LogEntry.Reply -> recorded.call == call
                is LogEntry.Result -> execution is Execution.Done && recorded.value == execution.value
                is LogEntry.Failure -> execution is Execution.Failure && recorded.errors == listOf(Diagnostic.of(execution.error))
                is LogEntry.Start -> throw IllegalStateException("the start entry is replayed by name, not by position")
            }
        if (matches) return null
        return RunError.Diverged(describeRecorded(recorded), describeProduced(execution), at, call)
    }

    private fun describeRecorded(entry: LogEntry): String =
        when (entry) {
            is LogEntry.Reply -> "a call to ${entry.call.print()}"
            is LogEntry.Result -> "the recorded result ${Value.print(entry.value)}"
            is LogEntry.Failure -> "the recorded failure ${entry.errors.joinToString { "'${it.message}'" }}"
            is LogEntry.Start -> throw IllegalStateException("the start entry is replayed by name, not by position")
        }

    private fun describeProduced(execution: Execution): String =
        when (execution) {
            is Execution.AwaitingHost -> "a call to ${execution.toCall().print()}"
            is Execution.Done -> "the result ${Value.print(execution.value)}"
            is Execution.Failure -> "the failure '${execution.error.message}'"
        }

    private fun <T : Any> transact(block: () -> T): T {
        var result: T? = null
        environment.transact { result = block() }
        return result ?: throw IllegalStateException("transact returned without completing its block")
    }

    private fun isValueAsk(suspension: Execution.AwaitingHost) =
        environment.capability(suspension.call, edition.pins.getValue(suspension.call))!!.kind == DeclarationKind.Value

    private fun askHandler(suspension: Execution.AwaitingHost): HandlerResponse {
        if (!isValueAsk(suspension)) environment.checkCallTypes(suspension, edition.pins, release)
        return when (val handler = environment.resolveHandler(suspension.call, edition.pins, handlers)) {
            is Handler.Immediate -> {
                val answer = handler.answer(suspension.args)
                environment.checkAnswerType(suspension, edition.pins, release, answer)
                HandlerResponse.Answer(answer)
            }
            is Handler.Deferred -> {
                handler.initiate(suspension.toCall())
                HandlerResponse.Deferred
            }
        }
    }
}

private sealed interface HandlerResponse {
    class Answer(
        val value: Value,
    ) : HandlerResponse

    data object Deferred : HandlerResponse
}

private class Halt(
    val outcome: RunOutcome,
) : Exception()

private fun Execution.AwaitingHost.toCall() = Call(call, args)

private fun Environment.checkAnswerType(
    suspension: Execution.AwaitingHost,
    pins: Map<String, RevisionNumber>,
    release: ResolvedRelease,
    answer: Value,
) {
    val declared = capability(suspension.call, pins.getValue(suspension.call))!!.declaration.answerType
    if (!fitsDeclaredType(answer, declared, release)) {
        throw RunFailure(RunError.HandlerTypeMismatch(suspension.call, printType(answer, release), declared))
    }
}

private fun Environment.checkCallTypes(
    suspension: Execution.AwaitingHost,
    pins: Map<String, RevisionNumber>,
    release: ResolvedRelease,
) {
    val declared = capability(suspension.call, pins.getValue(suspension.call))!!.declaration.parameterTypes
    if (suspension.args.size != declared.size) {
        val got = describeArgumentCount(suspension.args.size)
        throw RunFailure(RunError.CallTypeMismatch(suspension.call, got, "${declared.size}"))
    }
    for ((argument, parameter) in suspension.args.zip(declared)) {
        if (!fitsDeclaredType(argument, parameter, release)) {
            val got = printType(argument, release)
            val wanted = Type.print(parameter)
            throw RunFailure(RunError.CallTypeMismatch(suspension.call, got, wanted))
        }
    }
}

private fun describeArgumentCount(count: Int) = if (count == 1) "1 argument" else "$count arguments"

private fun Environment.resolveHandler(
    name: String,
    pins: Map<String, RevisionNumber>,
    handlers: Registry,
): Handler {
    val revision = pins.getValue(name)
    return handlers.registered[name to revision]
        ?: this[capability(name, revision)!!.id]
        ?: throw IllegalStateException("no handler for '$name' although checkPins passed")
}
