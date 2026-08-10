package klein.host

import klein.KleinError
import klein.KleinException
import klein.Revision
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
    val revision: Revision get() = declaration.revision
    val kind: DeclarationKind get() = declaration.kind
    val type: ContractType get() = declaration.type
}

interface HostCall {
    val capability: Capability
    val args: List<Value>

    fun resume(answer: Value)
}

sealed interface Implementation {
    class Immediate(
        val answer: (List<Value>) -> Value,
    ) : Implementation

    class Deferred(
        val take: (HostCall) -> Unit,
    ) : Implementation
}

class Registry(
    val declarations: List<ContractDeclaration>,
) {
    val registered = mutableMapOf<Pair<String, Revision>, Implementation>()
    val errors = mutableListOf<RegistrationError>()

    fun immediate(
        name: String,
        revision: Revision = Revision(1),
        answer: (List<Value>) -> Value,
    ) = register(name, revision, Implementation.Immediate(answer))

    fun deferred(
        name: String,
        revision: Revision = Revision(1),
        take: (HostCall) -> Unit,
    ) = register(name, revision, Implementation.Deferred(take))

    private fun register(
        name: String,
        revision: Revision,
        implementation: Implementation,
    ) {
        if (declarations.none { it.name == name && it.revision == revision }) {
            errors.add(
                RegistrationError("'$name' revision ${revision.value} is registered but the contract does not declare it"),
            )
            return
        }
        if (registered.put(name to revision, implementation) != null) {
            errors.add(RegistrationError("'$name' revision ${revision.value} is registered more than once"))
        }
    }
}

/**
 * Bind a checked contract to a running host: run [register], require an implementation for every
 * declared `(name, revision)`, and mint a [Capability] per declaration. Throws [KleinException] if
 * any declaration is unimplemented, any registration names something undeclared, or anything is
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
        capabilities.associate { capability ->
            capability.id to registry.registered.getValue(capability.name to capability.revision)
        }
    return Environment(capabilities, implementations)
}

/** A contract and an implementation of it: one injection point, as `host-integration.md` §Environment
 *  has it. Checking rules needs none of this — that is [EnvironmentContract]'s job. */
class Environment(
    val capabilities: List<Capability>,
    private val implementations: Map<CapabilityId, Implementation>,
) {
    operator fun get(id: CapabilityId): Implementation? = implementations[id]

    fun capability(
        name: String,
        revision: Revision,
    ): Capability? = capabilities.firstOrNull { it.name == name && it.revision == revision }
}

class RegistrationError(
    override val message: String,
) : KleinError {
    override val span = klein.SourceSpan.zero
}

fun capabilityId(
    name: String,
    type: ContractType,
    revision: Revision,
): CapabilityId = CapabilityId(fingerprint("$name/${canonicalize(type)}/${revision.value}"))

fun canonicalize(type: Type<*>): String = Type.print(type)

private fun fingerprint(input: String): String {
    var hash = -0x340d631b7bdddcdbL
    for (char in input) {
        hash = hash xor char.code.toLong()
        hash *= 0x100000001b3L
    }
    return hash.toULong().toString(16).padStart(16, '0')
}
