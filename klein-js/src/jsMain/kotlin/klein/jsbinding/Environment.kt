package klein.jsbinding

import klein.host.Transactor
import kotlin.js.Promise
import kotlin.js.collections.JsReadonlyArray
import kotlin.js.collections.toList

@JsExport
class HandlerRegistration internal constructor(
    internal val registration: klein.host.HandlerRegistration,
) {
    val name: String get() = registration.name
}

@JsExport
fun immediate(
    name: String,
    answer: (JsReadonlyArray<Any?>) -> Promise<Any?>,
): HandlerRegistration = HandlerRegistration(klein.host.immediate(name) { args -> fromJs(answer(toJs(args)).await()) })

@JsExport
fun deferred(
    name: String,
    initiate: (Call) -> Promise<Unit>,
): HandlerRegistration = HandlerRegistration(klein.host.deferred(name) { call -> initiate(Call(call)).await() })

@JsExport
fun perRun(name: String): HandlerRegistration = HandlerRegistration(klein.host.perRun(name))

@JsExport
class Environment internal constructor(
    internal val environment: klein.host.Environment,
) {
    fun withTransactor(transactor: (() -> Promise<Any?>) -> Promise<Any?>): Environment =
        Environment(
            environment.withTransactor(
                object : Transactor {
                    @Suppress("UNCHECKED_CAST")
                    override suspend fun <T> transact(block: suspend () -> T): T = transactor { promise { block() } }.await() as T
                },
            ),
        )

    fun withPersister(persist: (LogEntry) -> Promise<Unit>): Environment =
        Environment(environment.withPersister { persist(LogEntry(it)).await() })

    suspend fun run(
        edition: Edition,
        log: EffectLog?,
        registrations: JsReadonlyArray<HandlerRegistration>,
    ): RunOutcome {
        val handlers = registrations.toList().map { it.registration }.toTypedArray()
        val outcome =
            if (log == null) {
                environment.run(edition.edition, *handlers)
            } else {
                environment.run(edition.edition, log.log, *handlers)
            }
        return RunOutcome(outcome)
    }
}

@JsExport
class RunOutcome internal constructor(
    internal val outcome: klein.host.RunOutcome,
) {
    val kind: String =
        when (outcome) {
            is klein.host.RunOutcome.Completed -> "Completed"
            is klein.host.RunOutcome.Failed -> "Failed"
            is klein.host.RunOutcome.Parked -> "Parked"
        }
    val log: EffectLog = EffectLog(outcome.log)
    val value: Any? = (outcome as? klein.host.RunOutcome.Completed)?.value?.let(::toJs)
    val diagnostics: JsReadonlyArray<Diagnostic>? = (outcome as? klein.host.RunOutcome.Failed)?.diagnostics?.let(::toJs)
    val call: Call? = (outcome as? klein.host.RunOutcome.Parked)?.call?.let(::Call)
}
