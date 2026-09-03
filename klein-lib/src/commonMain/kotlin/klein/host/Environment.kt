package klein.host

import klein.KleinError
import klein.KleinException
import klein.RevisionNumber
import klein.check.ContractType
import klein.check.Type
import klein.check.contract.ContractDeclaration
import klein.check.contract.DeclarationKind
import klein.check.contract.EnvironmentContract
import klein.interp.Value

class CapabilityId(
    private val key: String,
) {
    override fun equals(other: Any?) = other is CapabilityId && key == other.key

    override fun hashCode() = key.hashCode()

    override fun toString() = key
}

data class Capability(
    val declaration: ContractDeclaration,
    val id: CapabilityId,
) {
    val name: String get() = declaration.name
    val revision: RevisionNumber get() = declaration.revision
    val kind: DeclarationKind get() = declaration.kind
    val type: ContractType get() = declaration.type
}

sealed interface Handler {
    class Immediate(
        val answer: (List<Value>) -> Value,
    ) : Handler

    class Deferred(
        val initiate: (Call) -> Unit,
    ) : Handler
}

class Registry(
    val declarations: List<ContractDeclaration>,
) {
    internal val registered = mutableMapOf<Pair<String, RevisionNumber>, Handler?>()
    internal val errors = mutableListOf<RegistrationError>()

    fun immediate(
        name: String,
        revision: RevisionNumber = RevisionNumber(1),
        answer: (List<Value>) -> Value,
    ) = register(name, revision, Handler.Immediate(answer))

    fun immediate(
        name: String,
        revision: RevisionNumber = RevisionNumber(1),
    ) = register(name, revision, null)

    fun deferred(
        name: String,
        revision: RevisionNumber = RevisionNumber(1),
        initiate: (Call) -> Unit,
    ) {
        if (declarations.any { it.name == name && it.revision == revision && it.kind == DeclarationKind.Value }) {
            errors.add(RegistrationError("'$name' is a value, which is read at start and cannot be deferred"))
            return
        }
        register(name, revision, Handler.Deferred(initiate))
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
 * each run — and mint a [Capability] per declaration. Throws [KleinException] if any
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
    register: Registry.() -> Unit = {},
): Environment {
    val registry = Registry(declarations).apply(register)
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

    val capabilities =
        declarations.map { declaration ->
            Capability(declaration, capabilityId(declaration.name, declaration.type, declaration.revision))
        }
    val handlers =
        capabilities
            .mapNotNull { capability ->
                registry.registered.getValue(capability.name to capability.revision)?.let { capability.id to it }
            }.toMap()
    return Environment(capabilities, this, handlers, transact)
}

/** A contract and an implementation of it: one injection point, as `host-integration.md` §Environment
 *  has it. Checking rules needs none of this — that is [EnvironmentContract]'s job. */
class Environment internal constructor(
    val capabilities: List<Capability>,
    internal val contract: EnvironmentContract,
    private val handlers: Map<CapabilityId, Handler>,
    internal val transact: (block: () -> Unit) -> Unit,
) {
    operator fun get(id: CapabilityId): Handler? = handlers[id]

    fun capability(
        name: String,
        revision: RevisionNumber,
    ): Capability? = capabilities.firstOrNull { it.name == name && it.revision == revision }
}

class RegistrationError(
    override val message: String,
) : KleinError {
    override val span: klein.SourceSpan? = null
}

internal fun capabilityId(
    name: String,
    type: ContractType,
    revision: RevisionNumber,
): CapabilityId = CapabilityId(fingerprint("$name/${canonicalize(type)}/${revision.value}"))

internal fun canonicalize(type: Type<*>): String = Type.print(type)

private fun fingerprint(input: String): String {
    var hash = -0x340d631b7bdddcdbL
    for (char in input) {
        hash = hash xor char.code.toLong()
        hash *= 0x100000001b3L
    }
    return hash.toULong().toString(16).padStart(16, '0')
}
