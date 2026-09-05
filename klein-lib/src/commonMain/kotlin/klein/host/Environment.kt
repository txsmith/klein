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

class HandlerRegistry internal constructor(
    val declarations: List<ContractDeclaration>,
) {
    internal val registered = mutableMapOf<Pair<String, RevisionNumber>, Handler?>()
    internal val errors = mutableListOf<RegistrationError>()

    fun immediate(
        name: String,
        answer: (List<Value>) -> Value,
    ) {
        val (parsedName, revision) = parse(name) ?: return
        register(parsedName, revision, Handler.Immediate(answer))
    }

    fun immediate(name: String) {
        val (parsedName, revision) = parse(name) ?: return
        register(parsedName, revision, null)
    }

    fun deferred(
        name: String,
        initiate: (Call) -> Unit,
    ) {
        val (parsedName, revision) = parse(name) ?: return
        if (declarations.any { it.name == parsedName && it.revision == revision && it is ContractDeclaration.Value }) {
            errors.add(RegistrationError("'$parsedName' is a value, which is read at start and cannot be deferred"))
            return
        }
        register(parsedName, revision, Handler.Deferred(initiate))
    }

    private fun parse(name: String): Pair<String, RevisionNumber>? {
        val slash = name.indexOf('/')
        if (slash < 0) return name to RevisionNumber(1)
        val revision = name.substring(slash + 1).toIntOrNull()
        if (slash == 0 || revision == null || revision < 1) {
            errors.add(RegistrationError("'$name' is not a declared name: a revision suffix is '/' and a number, as the contract writes it"))
            return null
        }
        return name.substring(0, slash) to RevisionNumber(revision)
    }

    private fun register(
        name: String,
        revision: RevisionNumber,
        handler: Handler?,
    ) {
        if (declarations.none { it.name == name && it.revision == revision }) {
            errors.add(
                RegistrationError("'$name' revision ${revision.value} is registered but the contract does not declare it"),
            )
            return
        }
        if (name to revision in registered) {
            errors.add(RegistrationError("'$name' revision ${revision.value} is registered more than once"))
            return
        }
        registered[name to revision] = handler
    }
}

/**
 * Bind a checked contract to a running host: run [register], require a registration for every
 * declared `(name, revision)` — an immediate implementation, a deferred one whose ask parks the run
 * after its initiation lambda has run, or the lambda-less marker whose implementation arrives with
 * each run. Throws [KleinException] if any
 * declaration is unregistered, any registration names something undeclared, or anything is
 * registered twice. [transact] wraps every unit of a run that pairs host work with a log write —
 * an ask's handler, answer check, and `persist` — so a DB host can commit both together. It must
 * run its block and let an exception from it propagate; catching one breaks the run.
 *
 * An extension declared in `klein.host` rather than a member of [EnvironmentContract]: `klein.host`
 * depends on `klein.check`, so a member returning an [Environment] would point that arrow both
 * ways. It reads identically at the call site and leaves the checker unaware that hosts exist.
 */
fun EnvironmentContract.implement(
    transact: (block: () -> Unit) -> Unit = { it() },
    register: HandlerRegistry.() -> Unit = {},
): Environment {
    val registry = HandlerRegistry(declarations).apply(register)
    declarations
        .filter { (it.name to it.revision) !in registry.registered }
        .forEach {
            registry.errors.add(
                RegistrationError(
                    "'${it.name}' revision ${it.revision.value} is declared by the contract " +
                        "but no implementation is registered",
                ),
            )
        }
    if (registry.errors.isNotEmpty()) throw KleinException(registry.errors)

    val handlers =
        declarations
            .mapNotNull { declaration ->
                registry.registered.getValue(declaration.name to declaration.revision)?.let { (declaration.name to declaration.revision) to it }
            }.toMap()
    return Environment(this, handlers, transact)
}

/** A contract and an implementation of it: one injection point, as `host-integration.md` §Environment
 *  has it. Checking rules needs none of this — that is [EnvironmentContract]'s job. */
class Environment internal constructor(
    internal val contract: EnvironmentContract,
    private val handlers: Map<Pair<String, RevisionNumber>, Handler>,
    internal val transact: (block: () -> Unit) -> Unit,
) {
    val capabilities: List<ContractDeclaration> get() = contract.declarations

    private val declarations = contract.declarations.associateBy { it.name to it.revision }

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
    fun run(
        edition: Edition,
        log: EffectLog? = null,
        persist: (LogEntry) -> Unit = {},
        registerHandlers: HandlerRegistry.() -> Unit = {},
    ): RunOutcome {
        val handlers = HandlerRegistry(contract.declarations).apply(registerHandlers)
        if (handlers.errors.isNotEmpty()) throw RunFailure(RunError.InvalidRegistration(handlers.errors))
        val pinProblems = checkPins(edition, handlers)
        if (pinProblems.isNotEmpty()) throw RunFailure(RunError.UnservablePins(pinProblems))
        if (log != null) {
            val logProblems = checkLog(edition, log)
            if (logProblems.isNotEmpty()) throw RunFailure(RunError.LogTypeMismatch(logProblems))
        }
        return Run(this, edition, handlers, persist, log).start()
    }

    internal fun getHandler(
        name: String,
        revision: RevisionNumber,
    ): Handler? = handlers[name to revision]

    internal fun getCapabilityDeclaration(
        name: String,
        revision: RevisionNumber,
    ): ContractDeclaration? = declarations[name to revision]
}

class RegistrationError internal constructor(
    override val message: String,
) : HostError
