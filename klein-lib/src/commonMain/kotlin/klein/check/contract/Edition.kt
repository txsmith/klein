package klein.check.contract

import klein.LanguageVersion
import klein.RevisionNumber
import klein.core.CoreExpr
import klein.hex16
import klein.parseHex16
import kotlin.jvm.JvmInline

data class Pin(
    val revision: RevisionNumber,
    val hash: DeclarationHash,
)

@JvmInline
value class DeclarationHash internal constructor(
    internal val bits: Long,
) {
    override fun toString(): String = hex16(bits)

    companion object {
        internal fun parse(text: String?): DeclarationHash? = parseHex16(text)?.let(::DeclarationHash)
    }
}

class Edition internal constructor(
    val environment: String,
    val language: LanguageVersion,
    val core: CoreExpr,
    val pinsWithHash: Map<String, Pin>,
    val source: String,
    internal val surface: ResolvedSurface,
) {
    val pins: Map<String, RevisionNumber> = pinsWithHash.mapValues { it.value.revision }
}
