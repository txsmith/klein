package klein.host

import klein.LanguageVersion
import klein.check.contract.Edition
import klein.check.contract.Pin

enum class StaleReason { ChecksumMismatch, LanguageChanged, CompilerChanged }

sealed interface DecodedEdition {
    class Fresh internal constructor(
        val edition: Edition,
    ) : DecodedEdition

    class Stale internal constructor(
        val language: LanguageVersion,
        val pins: Map<String, Pin>,
        val source: String,
        val reason: StaleReason,
    ) : DecodedEdition
}
