package klein.check.contract

import klein.LanguageVersion
import klein.RevisionNumber
import klein.core.CoreExpr

data class Pin(
    val revision: RevisionNumber,
    val hash: Long,
)

class Edition internal constructor(
    val language: LanguageVersion,
    val core: CoreExpr,
    val pinsWithHash: Map<String, Pin>,
    val source: String,
    internal val surface: ResolvedSurface,
) {
    val pins: Map<String, RevisionNumber> = pinsWithHash.mapValues { it.value.revision }
}
