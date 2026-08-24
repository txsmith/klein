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

// The deferred seam is commented out until the effect log lands (roadmap §Deferred host calls):
// the API shape is right — take the call, persist its token, answer via replay — but nothing
// drives it yet, and an unimplementable registration should not be writable.
//
// interface HostCall {
//     val capability: Capability
//     val args: List<Value>
//
//     fun resume(answer: Value)
// }

sealed interface Implementation {
    class Immediate(
        val answer: (List<Value>) -> Value,
    ) : Implementation

    // class Deferred(
    //     val take: (HostCall) -> Unit,
    // ) : Implementation
}

class Registry(
    val declarations: List<ContractDeclaration>,
) {
    val registered = mutableMapOf<Pair<String, RevisionNumber>, Implementation?>()
    val errors = mutableListOf<RegistrationError>()

    fun immediate(
        name: String,
        revision: RevisionNumber = RevisionNumber(1),
        answer: (List<Value>) -> Value,
    ) = register(name, revision, Implementation.Immediate(answer))

    fun immediate(
        name: String,
        revision: RevisionNumber = RevisionNumber(1),
    ) = register(name, revision, null)

    // fun deferred(
    //     name: String,
    //     revision: RevisionNumber = RevisionNumber(1),
    //     take: (HostCall) -> Unit,
    // ) = register(name, revision, Implementation.Deferred(take))

    private fun register(
        name: String,
        revision: RevisionNumber,
        implementation: Implementation?,
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
        registered[name to revision] = implementation
    }
}

/**
 * Bind a checked contract to a running host: run [register], require a registration for every
 * declared `(name, revision)` — an implementation, or the lambda-less marker whose implementation
 * arrives with each run — and mint a [Capability] per declaration. Throws [KleinException] if any
 * declaration is unregistered, any registration names something undeclared, or anything is
 * registered twice.
 *
 * An extension declared in `klein.host` rather than a member of [EnvironmentContract]: `klein.host`
 * depends on `klein.check`, so a member returning an [Environment] would point that arrow both
 * ways. It reads identically at the call site and leaves the checker unaware that hosts exist.
 */
fun EnvironmentContract.implement(register: Registry.() -> Unit = {}): Environment {
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
    val implementations =
        capabilities
            .mapNotNull { capability ->
                registry.registered.getValue(capability.name to capability.revision)?.let { capability.id to it }
            }.toMap()
    return Environment(capabilities, this, implementations)
}

/** A contract and an implementation of it: one injection point, as `host-integration.md` §Environment
 *  has it. Checking rules needs none of this — that is [EnvironmentContract]'s job. */
class Environment internal constructor(
    val capabilities: List<Capability>,
    internal val contract: EnvironmentContract,
    private val implementations: Map<CapabilityId, Implementation>,
) {
    operator fun get(id: CapabilityId): Implementation? = implementations[id]

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
