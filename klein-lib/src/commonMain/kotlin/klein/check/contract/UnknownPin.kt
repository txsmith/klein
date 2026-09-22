package klein.check.contract

import klein.HostError
import klein.RevisionNumber

class UnknownPin(
    val name: String,
    val revision: RevisionNumber,
) : HostError {
    override val message = "pin '$name' revision ${revision.value} names a revision the contract does not declare"
}
