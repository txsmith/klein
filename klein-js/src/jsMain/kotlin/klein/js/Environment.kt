package klein.js

import klein.host.Call as KleinCall
import klein.host.Environment as KleinEnvironment
import klein.host.HandlerRegistration as KleinHandlerRegistration
import klein.host.RunOutcome as KleinRunOutcome
import klein.host.deferred as kleinDeferred
import klein.host.immediate as kleinImmediate
import klein.host.perRun as kleinPerRun
import kotlin.js.Promise
import kotlin.js.collections.JsReadonlyArray
import kotlin.js.collections.toList

@JsExport
class Call internal constructor(
    internal val call: KleinCall,
) {
    val name: String = call.name
    val args: JsReadonlyArray<Any?> = call.args.map(::toJs).frozen()

    fun print(): String = call.print()
}

@JsExport
class HandlerRegistration internal constructor(
    internal val registration: KleinHandlerRegistration,
) {
    val name: String = registration.name
}

@JsExport
fun immediate(
    name: String,
    answer: (args: JsReadonlyArray<Any?>) -> Any?,
): HandlerRegistration = HandlerRegistration(kleinImmediate(name) { args -> fromJs(awaitResult(answer(args.map(::toJs).frozen()))) })

@JsExport
fun deferred(
    name: String,
    initiate: (call: Call) -> Any?,
): HandlerRegistration = HandlerRegistration(kleinDeferred(name) { call -> awaitResult(initiate(Call(call))) })

@JsExport
fun perRun(name: String): HandlerRegistration = HandlerRegistration(kleinPerRun(name))

@JsExport
class Environment internal constructor(
    internal val environment: KleinEnvironment,
) {
    fun run(
        edition: Edition,
        registrations: JsReadonlyArray<HandlerRegistration> = emptyList<HandlerRegistration>().frozen(),
        log: EffectLog? = null,
        persist: ((entry: LogEntry) -> Any?)? = null,
    ): Promise<RunOutcome> =
        promise {
            mapExceptions {
                val handlers = registrations.toList().map { it.registration }.toTypedArray()
                val outcome =
                    if (persist == null) {
                        environment.run(edition.edition, *handlers, log = log?.log)
                    } else {
                        environment.run(edition.edition, *handlers, log = log?.log, persist = { awaitResult(persist(toJs(it))) })
                    }
                toJs(outcome)
            }
        }
}

@JsExport
abstract class RunOutcome internal constructor(
    val log: EffectLog,
) {
    abstract val kind: String
}

@JsExport
class Completed internal constructor(
    val value: Any?,
    log: EffectLog,
) : RunOutcome(log) {
    override val kind: String = "completed"
}

@JsExport
class Failed internal constructor(
    val diagnostics: JsReadonlyArray<Diagnostic>,
    log: EffectLog,
) : RunOutcome(log) {
    override val kind: String = "failed"
}

@JsExport
class Parked internal constructor(
    internal val parked: KleinRunOutcome.Parked,
    log: EffectLog,
) : RunOutcome(log) {
    override val kind: String = "parked"
    val call: Call = Call(parked.call)

    fun toReply(answer: Any?): ReplyEntry = ReplyEntry(parked.toReply(fromJs(answer)))
}

private fun toJs(outcome: KleinRunOutcome): RunOutcome =
    when (outcome) {
        is KleinRunOutcome.Completed -> Completed(toJs(outcome.value), EffectLog(outcome.log))
        is KleinRunOutcome.Failed -> Failed(toJs(outcome.diagnostics), EffectLog(outcome.log))
        is KleinRunOutcome.Parked -> Parked(outcome, EffectLog(outcome.log))
    }
