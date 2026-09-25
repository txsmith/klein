package klein.js

import klein.Klein
import klein.ReleaseNumber
import klein.RevisionNumber
import klein.check.Type
import klein.check.contract.ContractDeclaration
import klein.check.contract.EnvironmentContract
import klein.host.DecodedEdition
import klein.host.StaleReason as KleinStaleReason
import klein.host.codec.decodeEditionJson
import klein.host.implement
import kotlin.js.Promise
import kotlin.js.collections.JsReadonlyArray
import kotlin.js.collections.toList

@JsExport
fun checkContract(source: String): Contract = mapExceptions { Contract(Klein.checkContract(source)) }

@JsExport
class Declaration internal constructor(
    internal val declaration: ContractDeclaration,
) {
    val kind: String = if (declaration is ContractDeclaration.Function) "fun" else "value"
    val name: String = declaration.name
    val revision: Int = declaration.revision.value
    val type: ContractType = ContractType(declaration.type)
    val answerType: RuleType = RuleType(declaration.answerType)
}

@JsExport
class ContractType internal constructor(
    internal val type: klein.check.ContractType,
) {
    fun print(): String = Type.print(type)
}

@JsExport
class RuleType internal constructor(
    internal val type: klein.check.RuleType,
) {
    fun print(): String = Type.print(type)
}

@JsExport
class Contract internal constructor(
    internal val contract: EnvironmentContract,
) {
    val environment: String = contract.environment
    val releases: JsReadonlyArray<Int> = contract.releases.map { it.value }.frozen()
    val declarations: JsReadonlyArray<Declaration> = contract.declarations.map(::Declaration).frozen()

    fun check(
        rule: String,
        release: Int,
    ): Checked<String> =
        mapExceptions { toJs(contract.check(rule, ReleaseNumber(release))) { Type.print(it) } }

    fun compileRule(
        rule: String,
        release: Int,
    ): Checked<Edition> =
        mapExceptions { toJs(contract.compileRule(rule, ReleaseNumber(release)), ::Edition) }

    fun compileRuleAtPins(
        rule: String,
        pins: dynamic,
    ): Checked<Edition> = mapExceptions { toJs(contract.compileRule(rule, fromJsPins(pins)), ::Edition) }

    fun evaluateValue(
        source: String,
        release: Int,
        expected: RuleType,
    ): Checked<Any?> = mapExceptions { toJs(contract.evaluateValue(source, ReleaseNumber(release), expected.type), ::toJs) }

    fun decodeEditionJson(json: String): DecodedEditionJs =
        mapExceptions {
            when (val decoded = contract.decodeEditionJson(json)) {
                is DecodedEdition.Intact -> Intact(Edition(decoded.edition))
                is DecodedEdition.Stale -> Stale(decoded)
            }
        }

    fun implement(
        registrations: JsReadonlyArray<HandlerRegistration>,
        transact: ((block: () -> Promise<Unit>) -> Any?)? = null,
    ): Environment =
        mapExceptions {
            val handlers = registrations.toList().map { it.registration }.toTypedArray()
            val environment =
                if (transact == null) {
                    contract.implement(*handlers)
                } else {
                    contract.implement(*handlers, transact = { block -> awaitResult(transact { promise { block() } }) })
                }
            Environment(environment)
        }
}

@JsExport
@JsName("DecodedEdition")
abstract class DecodedEditionJs internal constructor() {
    abstract val kind: String
}

@JsExport
class Intact internal constructor(
    val edition: Edition,
) : DecodedEditionJs() {
    override val kind: String = "intact"
}

@JsExport
class Stale internal constructor(
    decoded: DecodedEdition.Stale,
) : DecodedEditionJs() {
    override val kind: String = "stale"
    val language: Int = decoded.language.value
    val source: String = decoded.source
    val pins: dynamic = toJsPins(decoded.pins)
    val reason: StaleReason = toJs(decoded.reason)
}

@JsExport
abstract class StaleReason internal constructor() {
    abstract val kind: String
}

@JsExport
class ChecksumMismatch internal constructor() : StaleReason() {
    override val kind: String = "checksumMismatch"
}

@JsExport
class LanguageChanged internal constructor() : StaleReason() {
    override val kind: String = "languageChanged"
}

@JsExport
class CompilerChanged internal constructor() : StaleReason() {
    override val kind: String = "compilerChanged"
}

@JsExport
class UnknownPins internal constructor(
    val pins: dynamic,
) : StaleReason() {
    override val kind: String = "unknownPins"
}

@JsExport
class DeclarationChanged internal constructor() : StaleReason() {
    override val kind: String = "declarationChanged"
}

private fun toJs(reason: KleinStaleReason): StaleReason =
    when (reason) {
        KleinStaleReason.ChecksumMismatch -> ChecksumMismatch()
        KleinStaleReason.LanguageChanged -> LanguageChanged()
        KleinStaleReason.CompilerChanged -> CompilerChanged()
        is KleinStaleReason.UnknownPins -> UnknownPins(toJsRevisions(reason.pins))
        KleinStaleReason.DeclarationChanged -> DeclarationChanged()
    }
