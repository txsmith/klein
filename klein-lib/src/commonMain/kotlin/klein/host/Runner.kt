package klein.host

import klein.Diagnostic
import klein.HostError
import klein.KleinException
import klein.RevisionNumber
import klein.check.RuleType
import klein.check.Type
import klein.check.contract.ContractDeclaration
import klein.check.contract.Edition
import klein.check.contract.ResolvedSurface
import klein.interp.Execution
import klein.interp.Interpreter
import klein.interp.Value

sealed interface RunOutcome {
    val log: EffectLog

    class Completed internal constructor(
        val value: Value,
        override val log: EffectLog,
    ) : RunOutcome

    class Failed internal constructor(
        val diagnostics: List<Diagnostic>,
        override val log: EffectLog,
    ) : RunOutcome

    /**
     * Record `toReply(answer)` in your log store the moment the answer arrives — in your own
     * transaction, not Klein's. Resuming replays it like any other stored entry; [run] will not
     * persist it for you.
     */
    class Parked internal constructor(
        val call: Call,
        override val log: EffectLog,
    ) : RunOutcome {
        fun toReply(answer: Value) = LogEntry.Reply(call, answer)
    }
}

class Diverged internal constructor(
    val expected: String,
    val got: String,
    val at: Int,
    val call: Call?,
) : HostError {
    override val message get() = "replay diverged at log entry $at: expected $expected, got $got"
}

class HandlerTypeMismatch internal constructor(
    val call: String,
    val answerType: String,
    val declaredType: RuleType,
) : HostError {
    override val message get() = "'$call' answered with $answerType where the contract declares ${Type.print(declaredType)}"
}

class CallTypeMismatch internal constructor(
    val call: String,
    val got: String,
    val declared: String,
) : HostError {
    override val message get() = "'$call' was called with $got where the contract declares $declared"
}

internal class Run(
    val environment: Environment,
    val edition: Edition,
    val handlers: HandlerRegistry,
    val persist: (LogEntry) -> Unit,
    val effectLog: EffectLog?,
) {
    val resolvedPins = environment.contract.resolvePins(edition.pins)
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
                    ?: diverge(Diverged("the start entry to hold '${suspension.call}'", "a start entry without it", 0, suspension.toCall()))
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
                val failure = LogEntry.Failure(listOf(end.error))
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

    private fun diverge(diverged: Diverged): Nothing = throw KleinException(listOf(diverged))

    private fun compareToRecorded(
        at: Int,
        recorded: LogEntry,
        execution: Execution,
    ): Diverged? {
        val call = (execution as? Execution.AwaitingHost)?.toCall()
        val matches =
            when (recorded) {
                is LogEntry.Reply -> recorded.call == call
                is LogEntry.Result -> execution is Execution.Done && recorded.value == execution.value
                is LogEntry.Failure -> execution is Execution.Failure && recorded.errors == listOf(execution.error)
                is LogEntry.Start -> throw IllegalStateException("the start entry is replayed by name, not by position")
            }
        if (matches) return null
        return Diverged(describeRecorded(recorded), describeProduced(execution), at, call)
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
        environment.getCapabilityDeclaration(suspension.call, edition.pins.getValue(suspension.call)) is ContractDeclaration.Value

    private fun askHandler(suspension: Execution.AwaitingHost): HandlerResponse {
        if (!isValueAsk(suspension)) environment.checkCallTypes(suspension, resolvedPins)
        return when (val handler = environment.resolveHandler(suspension.call, edition.pins, handlers)) {
            is Handler.Immediate -> {
                val answer = handler.answer(suspension.args)
                environment.checkAnswerType(suspension, resolvedPins, answer)
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
    resolvedPins: ResolvedSurface,
    answer: Value,
) {
    val revision = resolvedPins.exposedRevisions.getValue(suspension.call)
    val declared = getCapabilityDeclaration(suspension.call, revision)!!.answerType
    if (!fitsDeclaredType(answer, declared, resolvedPins.ruleTypeEnv)) {
        throw KleinException(listOf(HandlerTypeMismatch(suspension.call, printType(answer, resolvedPins.ruleTypeEnv), declared)))
    }
}

private fun Environment.checkCallTypes(
    suspension: Execution.AwaitingHost,
    resolvedPins: ResolvedSurface,
) {
    val revision = resolvedPins.exposedRevisions.getValue(suspension.call)
    val declaration = getCapabilityDeclaration(suspension.call, revision)
    val declared = (declaration as ContractDeclaration.Function).parameterTypes
    if (suspension.args.size != declared.size) {
        val got = describeArgumentCount(suspension.args.size)
        throw KleinException(listOf(CallTypeMismatch(suspension.call, got, "${declared.size}")))
    }
    for ((argument, parameter) in suspension.args.zip(declared)) {
        if (!fitsDeclaredType(argument, parameter, resolvedPins.ruleTypeEnv)) {
            val got = printType(argument, resolvedPins.ruleTypeEnv)
            val wanted = Type.print(parameter)
            throw KleinException(listOf(CallTypeMismatch(suspension.call, got, wanted)))
        }
    }
}

private fun describeArgumentCount(count: Int) = if (count == 1) "1 argument" else "$count arguments"

private fun Environment.resolveHandler(
    name: String,
    pins: Map<String, RevisionNumber>,
    handlers: HandlerRegistry,
): Handler {
    val revision = pins.getValue(name)
    return handlers.registered[name to revision]
        ?: getHandler(name, revision)
        ?: throw IllegalStateException("no handler for '$name' although checkPins passed")
}
