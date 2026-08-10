package klein.check.contract

import klein.ReleaseNumber
import klein.Revision
import klein.check.ContractType

/** One accepted declaration: what the host must implement, and what a release may point at. */
data class ContractDeclaration(
    val name: String,
    val revision: Revision,
    val kind: DeclarationKind,
    val type: ContractType,
)
