package klein.check.contract

import klein.ReleaseNumber
import klein.RevisionNumber
import klein.core.CoreExpr

class Edition internal constructor(
    val core: CoreExpr,
    val release: ReleaseNumber,
    val pins: Map<String, RevisionNumber>,
)
