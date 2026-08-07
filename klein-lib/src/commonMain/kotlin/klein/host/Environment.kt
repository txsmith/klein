package klein.host

import klein.KleinError
import klein.check.DeclarationKind
import klein.check.Type
import klein.check.TypeEnv
import klein.interp.Value
import klein.surface.Lexer
import klein.surface.LexerError
import klein.surface.ParseError
import klein.surface.Parser
import klein.surface.Program

enum class CapabilityKind { Function, Value }

class CapabilityId internal constructor(
    private val key: String,
) {
    override fun equals(other: Any?) = other is CapabilityId && key == other.key

    override fun hashCode() = key.hashCode()

    override fun toString() = key
}

data class Declaration(
    val name: String,
    val revision: Int,
    val kind: CapabilityKind,
    val type: Type,
)

data class Capability(
    val name: String,
    val revision: Int,
    val id: CapabilityId,
    val kind: CapabilityKind,
    val type: Type,
)

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

class Registry internal constructor(
    val declarations: List<Declaration>,
) {
    internal val registered = mutableMapOf<Pair<String, Int>, Implementation>()
    internal val errors = mutableListOf<RegistrationError>()

    fun immediate(
        name: String,
        revision: Int = 1,
        answer: (List<Value>) -> Value,
    ) = register(name, revision, Implementation.Immediate(answer))

    fun deferred(
        name: String,
        revision: Int = 1,
        take: (HostCall) -> Unit,
    ) = register(name, revision, Implementation.Deferred(take))

    private fun register(
        name: String,
        revision: Int,
        implementation: Implementation,
    ) {
        if (declarations.none { it.name == name && it.revision == revision }) {
            errors.add(
                RegistrationError("'$name' revision $revision is registered but the contract does not declare it"),
            )
            return
        }
        if (registered.put(name to revision, implementation) != null) {
            errors.add(RegistrationError("'$name' revision $revision is registered more than once"))
        }
    }
}

class Environment internal constructor(
    val typeEnv: TypeEnv,
    val capabilities: List<Capability>,
    private val implementations: Map<CapabilityId, Implementation>,
) {
    operator fun get(id: CapabilityId): Implementation? = implementations[id]

    fun capability(
        name: String,
        revision: Int,
    ): Capability? = capabilities.firstOrNull { it.name == name && it.revision == revision }

    companion object {
        fun load(
            contractSource: String,
            register: Registry.() -> Unit = {},
        ): Environment {
            val program = parseContract(contractSource)
            val checked = klein.check.Checker().checkContract(program)
            if (checked.errors.isNotEmpty()) throw EnvironmentError(checked.errors)
            val typeEnv = checked.env

            val declarations =
                checked.declarations.map { decl ->
                    Declaration(
                        decl.name,
                        decl.revision,
                        when (decl.kind) {
                            DeclarationKind.Function -> CapabilityKind.Function
                            DeclarationKind.Value -> CapabilityKind.Value
                        },
                        decl.type,
                    )
                }
            val registry = Registry(declarations).apply(register)
            declarations
                .filter { (it.name to it.revision) !in registry.registered }
                .forEach {
                    registry.errors.add(
                        RegistrationError(
                            "'${it.name}' revision ${it.revision} is declared by the contract " +
                                "but no implementation is registered",
                        ),
                    )
                }
            if (registry.errors.isNotEmpty()) throw EnvironmentError(registry.errors)

            val capabilities =
                declarations.map { declaration ->
                    Capability(
                        declaration.name,
                        declaration.revision,
                        capabilityId(declaration.name, declaration.type, declaration.revision),
                        declaration.kind,
                        declaration.type,
                    )
                }
            val implementations =
                capabilities.associate { capability ->
                    capability.id to registry.registered.getValue(capability.name to capability.revision)
                }
            return Environment(typeEnv, capabilities, implementations)
        }

        private fun parseContract(source: String): Program =
            try {
                Parser(Lexer(source).tokenize().toList()).parseProgram()
            } catch (e: LexerError) {
                throw EnvironmentError(listOf(e))
            } catch (e: ParseError) {
                throw EnvironmentError(listOf(e))
            }
    }
}

internal class RegistrationError(
    override val message: String,
) : KleinError {
    override val span = klein.SourceSpan.zero
}

internal fun capabilityId(
    name: String,
    type: Type,
    revision: Int,
): CapabilityId = CapabilityId(fingerprint("$name/${canonicalize(type)}/$revision"))

internal fun canonicalize(type: Type): String = Type.print(type)

private fun fingerprint(input: String): String {
    var hash = -0x340d631b7bdddcdbL
    for (char in input) {
        hash = hash xor char.code.toLong()
        hash *= 0x100000001b3L
    }
    return hash.toULong().toString(16).padStart(16, '0')
}
