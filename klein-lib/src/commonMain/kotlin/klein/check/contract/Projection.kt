package klein.check.contract

import klein.RevisionNumber
import klein.check.ContractType
import klein.check.RuleType
import klein.check.Type.TBool
import klein.check.Type.TBottom
import klein.check.Type.TForall
import klein.check.Type.TFun
import klein.check.Type.TNull
import klein.check.Type.TNum
import klein.check.Type.TOptional
import klein.check.Type.TRecord
import klein.check.Type.TRef
import klein.check.Type.TSkolem
import klein.check.Type.TStr
import klein.check.Type.TTop
import klein.check.Type.TUnit

/**
 * Projection: the crossing from what a contract declares to what a rule sees.
 *
 * [strip] is the **only** function in the system whose signature changes the revision witness, so a
 * revision reaching a rule is a compile error rather than a leak to be tested for. What the witness
 * does *not* prove is that the rewrite preserves the type — a function returning `Any` for every
 * input would satisfy the signature perfectly — which is why `ProjectionTest` exists beside it.
 */

/** Rewrite a contract-side type as a rule-side one: the same tree, with every revision dropped. */
internal fun ContractType.strip(): RuleType =
    when (this) {
        is TRef -> TRef<Nothing?>(name, typeArgs.map { it.strip() }, null)
        is TFun -> TFun(params.map { it.strip() }, result.strip(), paramNames)
        is TRecord -> stripRecord()
        is TOptional -> TOptional(type.strip())
        is TForall -> TForall(params, body.strip())
        is TSkolem -> this
        // Written out rather than cast: a data object is already at the bottom of the lattice, and
        // a cast here would be the invariant leaking back out as a runtime concern.
        TNum -> TNum
        TStr -> TStr
        TBool -> TBool
        TUnit -> TUnit
        TNull -> TNull
        TTop -> TTop
        TBottom -> TBottom
    }

/** [strip] at a record, keeping the result's shape in the signature so no caller has to cast. */
internal fun TRecord<RevisionNumber>.stripRecord(): TRecord<Nothing?> = TRecord(fields.mapValues { it.value.strip() })
