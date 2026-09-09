package klein.check.contract

import klein.LanguageVersion
import klein.RevisionNumber
import klein.core.CoreExpr

class Edition internal constructor(
    val language: LanguageVersion,
    val core: CoreExpr,
    val pins: Map<String, RevisionNumber>,
    val source: String,
)
