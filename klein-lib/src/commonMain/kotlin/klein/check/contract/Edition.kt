package klein.check.contract

import klein.RevisionNumber
import klein.core.CoreExpr

class Edition internal constructor(
    val core: CoreExpr,
    val pins: Map<String, RevisionNumber>,
    val source: String,
)
