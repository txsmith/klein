package klein.jsbinding

import klein.ReleaseNumber
import klein.RevisionNumber
import klein.check.RuleType
import klein.check.contract.ContractDeclaration as KleinContractDeclaration
import klein.host.codec.decodeEditionJson
import klein.host.implement
import kotlin.js.collections.JsReadonlyArray
import kotlin.js.collections.toList

@JsExport
class EnvironmentContract internal constructor(
    internal val contract: klein.check.contract.EnvironmentContract,
) {
    val environment: String get() = contract.environment
    val releases: JsReadonlyArray<Int> get() = contract.releases.map { it.value }.toJs()
    val declarations: JsReadonlyArray<ContractDeclaration> get() = contract.declarations.map(::ContractDeclaration).toJs()

    fun check(
        ruleSource: String,
        release: Int,
    ): Checked<Type> = toJs(contract.check(ruleSource, ReleaseNumber(release)), ::Type)

    fun compileRule(
        ruleSource: String,
        release: Int,
    ): Checked<Edition> = toJs(contract.compileRule(ruleSource, ReleaseNumber(release)), ::Edition)

    fun compileRuleAtRevisions(
        source: String,
        names: JsReadonlyArray<String>,
        revisions: JsReadonlyArray<Int>,
    ): Checked<Edition> {
        val pins = names.toList().zip(revisions.toList().map(::RevisionNumber)).toMap()
        return toJs(contract.compileRule(source, pins), ::Edition)
    }

    @Suppress("UNCHECKED_CAST")
    fun evaluateValue(
        source: String,
        release: Int,
        expected: Type,
    ): Checked<Any?> = toJs(contract.evaluateValue(source, ReleaseNumber(release), expected.type as RuleType), ::toJs)

    fun decodeEditionJson(text: String): DecodedEdition = toJs(contract.decodeEditionJson(text))

    fun implement(
        registrations: JsReadonlyArray<HandlerRegistration>,
    ): Environment = Environment(contract.implement(*registrations.toList().map { it.registration }.toTypedArray()))
}

@JsExport
class ContractDeclaration internal constructor(
    declaration: KleinContractDeclaration,
) {
    val isFunction: Boolean = declaration is KleinContractDeclaration.Function
    val name: String = declaration.name
    val revision: Int = declaration.revision.value
    val type: Type = Type(declaration.type)
    val answerType: Type = Type(declaration.answerType)
    val parameterTypes: JsReadonlyArray<Type>? = (declaration as? KleinContractDeclaration.Function)?.parameterTypes?.map(::Type)?.toJs()
}
