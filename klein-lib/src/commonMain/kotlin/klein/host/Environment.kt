package klein.host

import klein.KleinError
import klein.Revision
import klein.check.ContractEnv
import klein.check.ContractType
import klein.check.Type
import klein.check.TypeEnv
import klein.check.contract.ContractChecker
import klein.check.contract.ContractDeclaration
import klein.check.contract.DeclarationKind
import klein.interp.Value
import klein.surface.ContractExpr
import klein.surface.Lexer
import klein.surface.LexerError
import klein.surface.ParseError
import klein.surface.Parser

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

class EnvironmentError(
    val errors: List<KleinError>,
) : Exception(errors.joinToString("\n") { "${it.message} at ${it.span}" })

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

class Environment internal constructor(
    val typeEnv: ContractEnv,
    val capabilities: List<Capability>,
    private val implementations: Map<CapabilityId, Implementation>,
) {
    operator fun get(id: CapabilityId): Implementation? = implementations[id]

    fun capability(
        name: String,
        revision: Revision,
    ): Capability? = capabilities.firstOrNull { it.name == name && it.revision == revision }

    companion object {
        fun load(
            contractSource: String,
            register: Registry.() -> Unit = {},
        ): Environment {
            val contract = parseContract(contractSource)
            val checked = ContractChecker().check(contract)
            if (checked.errors.isNotEmpty()) throw EnvironmentError(checked.errors)
            val typeEnv = checked.env

            val declarations = checked.declarations
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
            if (registry.errors.isNotEmpty()) throw EnvironmentError(registry.errors)

            val capabilities =
                declarations.map { declaration ->
                    Capability(
                        declaration,
                        capabilityId(declaration.name, declaration.type, declaration.revision),
                    )
                }
            val implementations =
                capabilities.associate { capability ->
                    capability.id to registry.registered.getValue(capability.name to capability.revision)
                }
            return Environment(typeEnv, capabilities, implementations)
        }

        private fun parseContract(source: String): ContractExpr =
            try {
                Parser(Lexer(source).tokenize().toList()).parseContract()
            } catch (e: LexerError) {
                throw EnvironmentError(listOf(e))
            } catch (e: ParseError) {
                throw EnvironmentError(listOf(e))
            }
    }
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
