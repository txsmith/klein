package klein.host

import klein.HostError
import klein.LanguageVersion
import klein.RevisionNumber
import klein.check.contract.Edition

class UnreadableEdition internal constructor(
    override val message: String,
) : HostError

enum class Rederivation { ChecksumMismatch, LowererChanged }

sealed interface DecodedEdition {
    class Fresh internal constructor(
        val edition: Edition,
    ) : DecodedEdition

    class Stale internal constructor(
        val language: LanguageVersion,
        val pins: Map<String, RevisionNumber>,
        val source: String,
        val reason: Rederivation,
    ) : DecodedEdition
}
