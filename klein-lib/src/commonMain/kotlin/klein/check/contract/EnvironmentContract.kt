package klein.check.contract

import klein.KleinException
import klein.ReleaseNumber
import klein.Revision
import klein.check.Checker
import klein.check.ContractEnv
import klein.check.ContractType
import klein.check.RuleEnv
import klein.check.RuleType
import klein.surface.Lexer
import klein.surface.LexerError
import klein.surface.ParseError
import klein.surface.Parser

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
    val revision: Revision,
    val kind: DeclarationKind,
    val type: ContractType,
)

/**
 * A checked capability contract: the compile-time artifact a rule is checked against, needing no
 * implementations. There is no `errors` field, so holding one means the contract checked, and [env]
 * is private, so the contract environment cannot reach a rule.
 *
 * [releases] comes from the written blocks only; there is no implicit release 1.
 */
class EnvironmentContract(
    val declarations: List<ContractDeclaration>,
    val releases: List<ReleaseNumber>,
    private val env: ContractEnv,
    private val surfaces: Map<ReleaseNumber, Release>,
) {
    private val environments = mutableMapOf<ReleaseNumber, RuleEnv>()

    /**
     * Type-check [ruleSource] against exactly [release], and answer its type. Throws
     * [UnknownRelease] for a number this contract does not have, and [KleinException] for anything
     * wrong with the rule.
     */
    fun check(
        ruleSource: String,
        release: ReleaseNumber,
    ): RuleType {
        val ruleEnv = ruleEnvironment(release)
        val program =
            try {
                Parser(Lexer(ruleSource).tokenize().toList()).parseProgram()
            } catch (e: LexerError) {
                throw KleinException(listOf(e))
            } catch (e: ParseError) {
                throw KleinException(listOf(e))
            }
        val checked = Checker().checkProgram(program, ruleEnv)
        if (checked.errors.isNotEmpty()) throw KleinException(checked.errors)
        return checked.type
    }

    /** Memoised per release: a host recompiles many rules against the same one. */
    private fun ruleEnvironment(release: ReleaseNumber): RuleEnv =
        environments.getOrPut(release) {
            env.environmentFor(surfaces[release] ?: throw UnknownRelease(release, releases))
        }
}
