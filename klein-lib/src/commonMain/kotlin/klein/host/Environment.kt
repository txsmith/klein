package klein.host

import klein.HostError
import klein.KleinException
import klein.RevisionNumber
import klein.check.contract.ContractDeclaration
import klein.check.contract.Edition
import klein.check.contract.EnvironmentContract
import klein.interp.Value

internal sealed interface Handler {
    class Immediate(
        val answer: (List<Value>) -> Value,
    ) : Handler

    class Deferred(
        val initiate: (Call) -> Unit,
    ) : Handler
}

class HandlerRegistration internal constructor(
    val name: String,
    internal val handler: Handler?,
)

fun immediate(
    name: String,
    answer: (List<Value>) -> Value,
) = HandlerRegistration(name, Handler.Immediate(answer))

fun immediate(name: String) = HandlerRegistration(name, null)

fun deferred(
    name: String,
    initiate: (Call) -> Unit,
) = HandlerRegistration(name, Handler.Deferred(initiate))

class HandlerRegistry internal constructor(
    val declarations: List<ContractDeclaration>,
    private val entries: Map<Pair<String, RevisionNumber>, Handler?>,
) {
    companion object {
        internal fun fromRegistrations(
            declarations: List<ContractDeclaration>,
            registrations: List<HandlerRegistration>,
        ): HandlerRegistry {
            val entries = mutableMapOf<Pair<String, RevisionNumber>, Handler?>()
            val errors = mutableListOf<RegistrationError>()
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
                    name to revision in entries ->
                        errors.add(RegistrationError("'$name' revision ${revision.value} is registered more than once"))
                    else -> entries[name to revision] = registration.handler
                }
            }
            if (errors.isNotEmpty()) throw KleinException(errors)
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

    internal fun getHandler(
        name: String,
        revision: RevisionNumber,
    ): Handler? = entries[name to revision]

    internal fun unregistered(): List<RegistrationError> =
        declarations
            .filter { (it.name to it.revision) !in entries }
            .map { RegistrationError("'${it.name}' revision ${it.revision.value} is declared by the contract but no implementation is registered") }

    internal fun missingHandlers(): List<MissingHandler> =
        declarations
            .filter { entries[it.name to it.revision] == null }
            .map { MissingHandler(it.name, it.revision) }
}

/**
 * Bind a checked contract to a running host: require a registration for every declared
 * `(name, revision)` — an immediate implementation, a deferred one whose ask parks the run after its
 * initiation lambda has run, or the lambda-less marker whose implementation arrives with each run.
 * Throws [KleinException] if any declaration is unregistered, any registration names something
 * undeclared, or anything is registered twice. [transact] wraps every unit of a run that pairs host
 * work with a log write — an ask's handler, answer check, and `persist` — so a DB host can commit
 * both together. It must run its block and let an exception from it propagate; catching one breaks
 * the run.
 *
 * An extension declared in `klein.host` rather than a member of [EnvironmentContract]: `klein.host`
 * depends on `klein.check`, so a member returning an [Environment] would point that arrow both
 * ways. It reads identically at the call site and leaves the checker unaware that hosts exist.
 */
fun EnvironmentContract.implement(
    vararg registrations: HandlerRegistration,
    transact: (block: () -> Unit) -> Unit = { it() },
): Environment {
    val registry = HandlerRegistry.fromRegistrations(declarations, registrations.toList())
    val unregistered = registry.unregistered()
    if (unregistered.isNotEmpty()) throw KleinException(unregistered)
    return Environment(this, registry, transact)
}

/** A contract and an implementation of it: one injection point, as `host-integration.md` §Environment
 *  has it. Checking rules needs none of this — that is [EnvironmentContract]'s job. */
class Environment internal constructor(
    val contract: EnvironmentContract,
    internal val registry: HandlerRegistry,
    internal val transact: (block: () -> Unit) -> Unit,
) {
    val capabilities: List<ContractDeclaration> get() = contract.declarations

    private val declarations = contract.declarations.associateBy { it.name to it.revision }

    /**
     * Start, resume, and replay are this one call. A null [log] starts fresh; otherwise the log is
     * replayed first (start values by name, replies by position) and every call past the
     * end of the log is answered by the run's [registrations], or failing that by the environment's
     * own.
     *
     * A rule that fails at runtime is a normal result: [RunOutcome.Failed] carries its diagnostics and
     * the log so far. Everything else Klein detects is the host's fault and throws [KleinException]
     * carrying one error per fault: [RegistrationError], [klein.check.contract.UnknownPin],
     * [MissingHandler], [LogTypeMismatch], [Diverged], [CallTypeMismatch], [HandlerTypeMismatch].
     * An exception from the host's own code (a handler, an initiation, [persist], `transact`)
     * escapes unwrapped. A call to a deferred capability runs its initiation,
     * records nothing, and returns [RunOutcome.Parked]; resume by calling run again with
     * `parked.toReply(answer)` appended to the log.
     *
     * [persist] is called with each newly recorded entry before execution continues, inside the same
     * `transact` as the handler work that produced it. Replayed entries are not persisted.
     */
    fun run(
        edition: Edition,
        vararg registrations: HandlerRegistration,
        log: EffectLog? = null,
        persist: (LogEntry) -> Unit = {},
    ): RunOutcome {
        val handlers = registry + HandlerRegistry.fromRegistrations(contract.declarations, registrations.toList())
        contract.resolvePins(edition.pins)
        val missing = handlers.missingHandlers()
        if (missing.isNotEmpty()) throw KleinException(missing)
        if (log != null) {
            val logProblems = checkLog(edition, log)
            if (logProblems.isNotEmpty()) throw KleinException(logProblems)
        }
        return Run(this, edition, handlers, persist, log).start()
    }

    internal fun getCapabilityDeclaration(
        name: String,
        revision: RevisionNumber,
    ): ContractDeclaration? = declarations[name to revision]
}

class RegistrationError internal constructor(
    override val message: String,
) : HostError
