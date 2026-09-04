package klein.check.contract

import klein.KleinException
import klein.ReleaseNumber
import klein.RevisionNumber
import klein.check.ConstructorInfo
import klein.check.ContractEnv
import klein.check.ContractType
import klein.check.RuleEnv
import klein.check.RuleType
import klein.check.Type
import klein.check.Type.TForall
import klein.check.Type.TFun
import klein.check.TypeDefInfo
import klein.check.TypeEnv
import klein.check.checkProgram
import klein.core.CoreExpr
import klein.core.PreludeBinding
import klein.core.lowerWithPrelude
import klein.surface.Lexer
import klein.surface.LexerError
import klein.surface.ParseError
import klein.surface.Program
import klein.surface.parseProgram

/**
 * A request naming a release the contract does not have — retired last week, or never written.
 * Not a diagnostic: it has no span to point at, and neither file is wrong; the *request* is.
 */
class UnknownRelease(
    val number: ReleaseNumber,
    val available: List<ReleaseNumber>,
) : Exception(
        "release ${number.value} is not in this contract; " +
            if (available.isEmpty()) "it has none" else "it has ${available.joinToString { it.value.toString() }}",
    )

/** One accepted declaration: what the host must implement, and what a release may point at. */
sealed class ContractDeclaration {
    abstract val name: String
    abstract val revision: RevisionNumber
    abstract val type: ContractType

    /** What an answer to this capability must be: a fun's result, a value's whole type. */
    abstract val answerType: RuleType

    data class Function(
        override val name: String,
        override val revision: RevisionNumber,
        override val type: ContractType,
    ) : ContractDeclaration() {
        private val signature =
            run {
                val stripped = type.strip()
                val fn = if (stripped is TForall) stripped.body else stripped
                require(fn is TFun) { "'$name' is a fun declaration but its type is not a function type" }
                fn
            }

        val parameterTypes: List<RuleType> get() = signature.params

        override val answerType: RuleType get() = signature.result
    }

    data class Value(
        override val name: String,
        override val revision: RevisionNumber,
        override val type: ContractType,
    ) : ContractDeclaration() {
        override val answerType: RuleType get() = type.strip()
    }
}

/**
 * A checked capability contract: the compile-time artifact a rule is checked against, needing no
 * implementations. There is no `errors` field, so holding one means the contract checked, and
 * [contractTypeEnv] is private, so the contract environment cannot reach a rule.
 *
 * [releases] comes from the written blocks only; there is no implicit release 1.
 */
class EnvironmentContract internal constructor(
    // What the host needs to implement
    val declarations: List<ContractDeclaration>,
    // Encapsulates all types defined in the contract
    private val contractTypeEnv: ContractEnv,
    // The full set of revisioned names per release
    private val releaseSurfaces: Map<ReleaseNumber, Map<String, RevisionNumber>>,
) {
    val releases: List<ReleaseNumber> get() = releaseSurfaces.keys.toList()

    private val resolved = mutableMapOf<ReleaseNumber, ResolvedSurface>()

    // TODO: this is where hashing a pin set would come in handy. A Map as Map keys is not great...
    private val resolvedPins = mutableMapOf<Map<String, RevisionNumber>, ResolvedSurface>()

    /**
     * Type-check [ruleSource] against exactly [release], and answer its type. Throws
     * [UnknownRelease] for a number this contract does not have, and [KleinException] for anything
     * wrong with the rule.
     */
    fun check(
        ruleSource: String,
        release: ReleaseNumber,
    ): RuleType = parseAndCheck(ruleSource, resolveRelease(release)).second

    fun compileRule(
        ruleSource: String,
        release: ReleaseNumber,
    ): Edition {
        val resolvedRelease = resolveRelease(release)
        val (program, _) = parseAndCheck(ruleSource, resolvedRelease)
        val used = usedCapabilities(program, resolvedRelease.exposedRevisions.keys)
        val pins = used.associateWith { resolvedRelease.exposedRevisions.getValue(it) }
        val prelude = used.mapNotNull { resolvedRelease.bindingFor(it) }
        return Edition(lowerWithPrelude(program, prelude), release, pins)
    }

    /**
     * Compile [source] as a pure expression of type [expected] against [release] — a host answering
     * a capability call in Klein rather than in its own language. Unlike an [Edition], the result
     * needs no pins and no environment: an answer may use the release's types but not its
     * capabilities, so `Klein.execute` is enough to evaluate it. Throws [KleinException] carrying
     * the checker's diagnostics for a mismatch, and [CapabilityInAnswer] for each capability named.
     */
    fun compileValue(
        source: String,
        release: ReleaseNumber,
        expected: RuleType,
    ): CoreExpr {
        val resolvedRelease = resolveRelease(release)
        val (program, _) = parseAndCheck(source, resolvedRelease, expected)
        val used = usedCapabilities(program, resolvedRelease.exposedRevisions.keys)
        val capabilities =
            used.sorted().filter {
                when (resolvedRelease.bindingFor(it)) {
                    is PreludeBinding.Function, is PreludeBinding.Value -> true
                    is PreludeBinding.Ctor, null -> false
                }
            }
        if (capabilities.isNotEmpty()) throw KleinException(capabilities.map { CapabilityInAnswer(it) })
        return lowerWithPrelude(program, used.mapNotNull { resolvedRelease.bindingFor(it) })
    }

    private fun parseAndCheck(
        ruleSource: String,
        surface: ResolvedSurface,
        expected: RuleType? = null,
    ): Pair<Program, RuleType> {
        val program =
            try {
                parseProgram(Lexer(ruleSource).tokenize().toList())
            } catch (e: LexerError) {
                throw KleinException(listOf(e))
            } catch (e: ParseError) {
                throw KleinException(listOf(e))
            }
        val checked = checkProgram(program, surface.ruleTypeEnv, expected)
        if (checked.errors.isNotEmpty()) throw KleinException(checked.errors)
        return program to checked.type
    }

    internal fun resolveRelease(release: ReleaseNumber): ResolvedSurface =
        resolved.getOrPut(release) {
            val revisions = releaseSurfaces[release] ?: throw UnknownRelease(release, releases)
            resolveSurface(revisions)
        }

    internal fun resolvePins(pins: Map<String, RevisionNumber>): ResolvedSurface =
        resolvedPins.getOrPut(pins) {
            // Editions only pin things explicitly mentioned in their source,
            // but those pinned things can lead (implictly) to more pins being part of the actual full surface.
            // Therefore we compute the transitive closure of exposed pins here.
            // `resolveRelease` doesn't need this because each release as written in a contract file is demanded to be transitive closure already.
            val surface = mutableMapOf<String, RevisionNumber>()
            val roots = mutableListOf<ContractType>()
            for ((name, revision) in pins) {
                val declaration = declarations.firstOrNull { it.name == name && it.revision == revision }
                if (declaration != null) {
                    surface[name] = revision
                    roots.add(declaration.type)
                    continue
                }
                val typeName = contractTypeEnv.lookupConstructor(name, revision)?.parentType ?: name
                if (contractTypeEnv.lookupTypeDef(typeName, revision) != null) {
                    surface[typeName] = revision
                    roots.addAll(contractTypeEnv.declaredFields(typeName, revision))
                }
            }
            for (reached in roots.reachableTypes(contractTypeEnv)) {
                if (reached is Type.TRef) surface[reached.name] = reached.revision
            }
            resolveSurface(surface)
        }

    internal fun declaresVocabulary(
        name: String,
        revision: RevisionNumber,
    ): Boolean = contractTypeEnv.lookupTypeDef(name, revision) != null || contractTypeEnv.lookupConstructor(name, revision) != null

    private fun resolveSurface(surface: Map<String, RevisionNumber>): ResolvedSurface {
        val projected = TypeEnv.empty<Nothing?>()
        val exposedRevisions = mutableMapOf<String, RevisionNumber>()
        for ((name, revision) in surface) {
            expose(name, revision, projected)
            exposedRevisions[name] = revision
            contractTypeEnv.constructorsOf(name, revision).forEach {
                expose(it.name, revision, projected)
                exposedRevisions[it.name] = revision
            }
        }
        return ResolvedSurface(projected, exposedRevisions)
    }

    /** Turns the ContractEnv entries into RuleEnv entries for one name at one revision. */
    private fun expose(
        name: String,
        revision: RevisionNumber,
        projected: RuleEnv,
    ) {
        contractTypeEnv.lookup(name, revision)?.let { projected.bind(name, it.strip()) }
        contractTypeEnv.lookupTypeDef(name, revision)?.let { def ->
            projected.registerTypeDef(TypeDefInfo(def.name, null, def.typeParams, def.iface.stripRecord(), def.span))
        }
        contractTypeEnv.lookupConstructor(name, revision)?.let { ctor ->
            projected.registerConstructor(
                ConstructorInfo(
                    ctor.name,
                    null,
                    ctor.typeParams,
                    ctor.fields.mapValues { it.value.strip() },
                    ctor.parentType,
                    ctor.span,
                ),
            )
        }
    }
}

/** Each release or set of pins from an edition forms a surface of what the release exposes or what the edition demands */
internal class ResolvedSurface(
    // The typing environment a rule checks against: each exposed name at a determined revision
    val ruleTypeEnv: RuleEnv,
    val exposedRevisions: Map<String, RevisionNumber>,
) {
    /** Null for a name that lowering erases: exposed types bind nothing, only their constructors do. */
    fun bindingFor(name: String): PreludeBinding? {
        ruleTypeEnv.lookupConstructor(name, null)?.let { return PreludeBinding.Ctor(name, it.fields.keys.toList()) }
        val type = ruleTypeEnv.lookup(name) ?: return null
        val body = if (type is TForall) type.body else type
        return if (body is TFun) PreludeBinding.Function(name, body.params.size) else PreludeBinding.Value(name)
    }
}
