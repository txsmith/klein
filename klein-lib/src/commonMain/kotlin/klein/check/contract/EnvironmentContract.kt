package klein.check.contract

import klein.KleinException
import klein.ReleaseNumber
import klein.RevisionNumber
import klein.check.ContractEnv
import klein.check.ContractType
import klein.check.RuleType
import klein.check.Type.TForall
import klein.check.Type.TFun
import klein.check.checkProgram
import klein.core.CoreExpr
import klein.core.PreludeBinding
import klein.core.lowerWithPrelude
import klein.surface.Lexer
import klein.surface.LexerError
import klein.surface.ParseError
import klein.surface.Program
import klein.surface.parseProgram

enum class DeclarationKind { Function, Value }

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
data class ContractDeclaration(
    val name: String,
    val revision: RevisionNumber,
    val kind: DeclarationKind,
    val type: ContractType,
) {
    /** What an answer to this capability must be: a fun's result, a value's whole type. */
    val answerType: RuleType
        get() {
            if (kind == DeclarationKind.Value) return type.strip()
            val stripped = type.strip()
            val fn = if (stripped is TForall) stripped.body else stripped
            return (fn as TFun).result
        }

    val parameterTypes: List<RuleType>
        get() {
            if (kind == DeclarationKind.Value) return emptyList()
            val stripped = type.strip()
            val fn = if (stripped is TForall) stripped.body else stripped
            return (fn as TFun).params
        }
}

/**
 * A checked capability contract: the compile-time artifact a rule is checked against, needing no
 * implementations. There is no `errors` field, so holding one means the contract checked, and [env]
 * is private, so the contract environment cannot reach a rule.
 *
 * [releases] comes from the written blocks only; there is no implicit release 1.
 */
class EnvironmentContract internal constructor(
    val declarations: List<ContractDeclaration>,
    val releases: List<ReleaseNumber>,
    private val env: ContractEnv,
    private val surfaces: Map<ReleaseNumber, FlattenedReleaseBlock>,
) {
    private val resolved = mutableMapOf<ReleaseNumber, ResolvedRelease>()

    /**
     * Type-check [ruleSource] against exactly [release], and answer its type. Throws
     * [UnknownRelease] for a number this contract does not have, and [KleinException] for anything
     * wrong with the rule.
     */
    fun check(
        ruleSource: String,
        release: ReleaseNumber,
    ): RuleType = parseAndCheck(ruleSource, resolve(release)).second

    fun compileRule(
        ruleSource: String,
        release: ReleaseNumber,
    ): Edition {
        val resolvedRelease = resolve(release)
        val (program, _) = parseAndCheck(ruleSource, resolvedRelease)
        val used = usedCapabilities(program, resolvedRelease.revisions.keys)
        val pins = used.associateWith { resolvedRelease.revisions.getValue(it) }
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
        val resolvedRelease = resolve(release)
        val (program, _) = parseAndCheck(source, resolvedRelease, expected)
        val used = usedCapabilities(program, resolvedRelease.revisions.keys)
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
        release: ResolvedRelease,
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
        val checked = checkProgram(program, release.types, expected)
        if (checked.errors.isNotEmpty()) throw KleinException(checked.errors)
        return program to checked.type
    }

    internal fun resolve(release: ReleaseNumber): ResolvedRelease =
        resolved.getOrPut(release) {
            env.resolveRelease(surfaces[release] ?: throw UnknownRelease(release, releases))
        }
}
