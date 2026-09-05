package klein.check.contract

import klein.HostError
import klein.RevisionNumber

class UnknownPin(
    val name: String,
    val revision: RevisionNumber,
) : HostError {
    override val message = "the edition pins '$name' revision ${revision.value}, which the contract does not declare"
}
