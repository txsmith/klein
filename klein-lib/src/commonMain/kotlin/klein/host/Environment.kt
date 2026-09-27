package klein.host

import klein.KleinException
import klein.MissingHandler
import klein.RegistrationError
import klein.RevisionNumber
import klein.WrongEnvironment
import klein.check.contract.ContractDeclaration
import klein.check.contract.Edition
import klein.check.contract.EnvironmentContract
import klein.interp.Value

internal sealed interface Handler {
    class Immediate(
        val answer: suspend (List<Value>) -> Value,
    ) : Handler

    class Deferred(
        val initiate: suspend (Call) -> Unit,
    ) : Handler
}

class HandlerRegistration internal constructor(
    val name: String,
    internal val handler: Handler?,
)

fun immediate(
    name: String,
    answer: suspend (List<Value>) -> Value,
) = HandlerRegistration(name, Handler.Immediate(answer))

fun perRun(name: String) = HandlerRegistration(name, null)

fun deferred(
    name: String,
    initiate: suspend (Call) -> Unit,
) = HandlerRegistration(name, Handler.Deferred(initiate))

internal class HandlerRegistry(
    private val declarations: List<ContractDeclaration>,
    private val entries: Map<Pair<String, RevisionNumber>, Handler?>,
) {
    companion object {
        fun fromRegistrations(
            declarations: List<ContractDeclaration>,
            registrations: List<HandlerRegistration>,
            perRunAllowed: Boolean,
            errors: MutableList<RegistrationError>,
        ): HandlerRegistry {
            val entries = mutableMapOf<Pair<String, RevisionNumber>, Handler?>()
            for (registration in registrations) {
                val parsed = parse(registration.name)
                if (parsed == null) {
                    errors.add(malformed(registration.name))
                    continue
                }
                val (name, revision) = parsed
                val declaration = declarations.firstOrNull { it.name == name && it.revision == revision }
                when {
                    declaration == null ->
                        errors.add(RegistrationError("'$name' revision ${revision.value} is registered but the contract does not declare it"))
                    registration.handler is Handler.Deferred && declaration is ContractDeclaration.Value ->
                        errors.add(RegistrationError("'$name' is a value, which is read at start and cannot be deferred"))
                    registration.handler == null && !perRunAllowed ->
                        errors.add(RegistrationError("'$name' is supplied per run, so the run must give an implementation for it"))
                    name to revision in entries ->
                        errors.add(RegistrationError("'$name' revision ${revision.value} is registered more than once"))
                    else -> entries[name to revision] = registration.handler
                }
            }
            return HandlerRegistry(declarations, entries)
        }

        private fun parse(name: String): Pair<String, RevisionNumber>? {
            val slash = name.indexOf('/')
            if (slash < 0) return name to RevisionNumber(1)
            val revision = name.substring(slash + 1).toIntOrNull()
            if (slash == 0 || revision == null || revision < 1) return null
            return name.substring(0, slash) to RevisionNumber(revision)
        }

        private fun malformed(name: String) =
            RegistrationError("'$name' is not a declared name: a revision suffix is '/' and a number, as the contract writes it")
    }

    operator fun plus(other: HandlerRegistry): HandlerRegistry = HandlerRegistry(declarations, entries + other.entries)

    fun getHandler(
        name: String,
        revision: RevisionNumber,
    ): Handler? = entries[name to revision]

    fun unregistered(): List<RegistrationError> =
        declarations
            .filter { (it.name to it.revision) !in entries }
            .map { RegistrationError("'${it.name}' revision ${it.revision.value} is declared by the contract but no implementation is registered") }

    private val perRun: Set<Pair<String, RevisionNumber>> = entries.filterValues { it == null }.keys

    fun missingHandlers(supplied: HandlerRegistry): List<MissingHandler> =
        (perRun - supplied.entries.keys).map { (name, revision) -> MissingHandler(name, revision) }
}

/**
 * Bind a checked contract to a running host: require a registration for every declared
 * `(name, revision)` — an immediate implementation, a deferred one whose ask parks the run after its
 * initiation lambda has run, or a per-run entry whose implementation arrives with each run.
 * Throws [KleinException] if any declaration is unregistered, any registration names something
 * undeclared, or anything is registered twice.
 *
 * An extension declared in `klein.host` rather than a member of [EnvironmentContract]: `klein.host`
 * depends on `klein.check`, so a member returning an [Environment] would point that arrow both
 * ways. It reads identically at the call site and leaves the checker unaware that hosts exist.
 */
fun EnvironmentContract.implement(vararg registrations: HandlerRegistration): Environment {
    val errors = mutableListOf<RegistrationError>()
    val registry = HandlerRegistry.fromRegistrations(declarations, registrations.toList(), perRunAllowed = true, errors)
    errors += registry.unregistered()
    if (errors.isNotEmpty()) throw KleinException(errors)
    return Environment(this, registry, WithoutTransactions, {})
}

interface Transactor {
    suspend fun <T> transact(block: suspend () -> T): T
}

private object WithoutTransactions : Transactor {
    override suspend fun <T> transact(block: suspend () -> T): T = block()
}

/** A contract and an implementation of it: one injection point, as `host-integration.md` §Environment
 *  has it. Checking rules needs none of this — that is [EnvironmentContract]'s job. */
class Environment internal constructor(
    internal val contract: EnvironmentContract,
    internal val registry: HandlerRegistry,
    internal val transactor: Transactor,
    internal val persist: suspend (LogEntry) -> Unit,
) {
    private val declarations = contract.declarations.associateBy { it.name to it.revision }

    fun withTransactor(transactor: Transactor): Environment = Environment(contract, registry, transactor, persist)

    fun withPersister(persist: suspend (LogEntry) -> Unit): Environment = Environment(contract, registry, transactor, persist)

    suspend fun run(
        edition: Edition,
        vararg registrations: HandlerRegistration,
    ): RunOutcome = start(edition, null, registrations)

    /**
     * Start, resume, and replay are this one call. Without a log the run starts fresh; with one, the
     * log is replayed first (start values by name, replies by position) and every call past the
     * end of the log is answered by the run's [registrations], or failing that by the environment's
     * own.
     *
     * A rule that fails at runtime is a normal result: [RunOutcome.Failed] carries its diagnostics and
     * the log so far. Everything else Klein detects is the host's fault and throws [KleinException]
     * carrying one error per fault: [RegistrationError], [klein.check.contract.UnknownPin],
     * [MissingHandler], [LogTypeMismatch], [Diverged], [CallTypeMismatch], [HandlerTypeMismatch].
     * An exception from the host's own code (a handler, an initiation, the persister, the transactor)
     * escapes unwrapped. A call to a deferred capability runs its initiation,
     * records nothing, and returns [RunOutcome.Parked]; resume by calling run again with
     * `parked.toReply(answer)` appended to the log.
     *
     * The persister ([withPersister]) is called with each newly recorded entry before execution
     * continues, inside the same transaction ([withTransactor]) as the handler work that produced it.
     * Replayed entries are not persisted.
     */
    suspend fun run(
        edition: Edition,
        log: EffectLog,
        vararg registrations: HandlerRegistration,
    ): RunOutcome = start(edition, log, registrations)

    private suspend fun start(
        edition: Edition,
        log: EffectLog?,
        registrations: Array<out HandlerRegistration>,
    ): RunOutcome {
        if (edition.environment != contract.environment) {
            throw KleinException(listOf(WrongEnvironment(edition.environment, contract.environment)))
        }
        val errors = mutableListOf<RegistrationError>()
        val supplied = HandlerRegistry.fromRegistrations(contract.declarations, registrations.toList(), perRunAllowed = false, errors)
        if (errors.isNotEmpty()) throw KleinException(errors)
        val missing = registry.missingHandlers(supplied)
        if (missing.isNotEmpty()) throw KleinException(missing)
        if (log != null) {
            val logProblems = checkLog(edition, log)
            if (logProblems.isNotEmpty()) throw KleinException(logProblems)
        }
        return Run(this, edition, registry + supplied, log).start()
    }

    internal fun getCapabilityDeclaration(
        name: String,
        revision: RevisionNumber,
    ): ContractDeclaration? = declarations[name to revision]
}
