package klein.host

import klein.LanguageVersion
import klein.RevisionNumber
import klein.check.contract.Edition
import klein.check.contract.Pin

sealed interface StaleReason {
    data object ChecksumMismatch : StaleReason

    data object LanguageChanged : StaleReason

    data object CompilerChanged : StaleReason

    class UnknownPins internal constructor(
        val pins: Map<String, RevisionNumber>,
    ) : StaleReason

    data object DeclarationChanged : StaleReason
}

sealed interface DecodedEdition {
    class Intact internal constructor(
        val edition: Edition,
    ) : DecodedEdition

    class Stale internal constructor(
        val language: LanguageVersion,
        val pins: Map<String, Pin>,
        val source: String,
        val reason: StaleReason,
    ) : DecodedEdition
}
