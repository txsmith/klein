package klein.check.contract

import klein.Revision
import klein.check.ConstructorInfo
import klein.check.ContractEnv
import klein.check.ContractType
import klein.check.RuleEnv
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
import klein.check.TypeDefInfo
import klein.check.TypeEnv

/**
 * Projection: the crossing from what a contract declares to what a rule sees.
 *
 * [strip] is the **only** function in the system whose signature changes the revision witness, so a
 * revision reaching a rule is a compile error rather than a leak to be tested for. What the witness
 * does *not* prove is that the rewrite preserves the type — a function returning `Any` for every
 * input would satisfy the signature perfectly — which is why `ProjectionTest` exists beside it.
 */

/** Rewrite a contract-side type as a rule-side one: the same tree, with every revision dropped. */
fun strip(type: ContractType): RuleType =
    when (type) {
        is TRef -> TRef<Nothing?>(type.name, type.typeArgs.map(::strip), null)
        is TFun -> TFun(type.params.map(::strip), strip(type.result), type.paramNames)
        is TRecord -> stripRecord(type)
        is TOptional -> TOptional(strip(type.type))
        is TForall -> TForall(type.params, strip(type.body))
        is TSkolem -> type
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
private fun stripRecord(rec: TRecord<Revision>): TRecord<Nothing?> = TRecord(rec.fields.mapValues { strip(it.value) })

/**
 * Build the environment [release] exposes: every name it names, copied out of [env] under its plain
 * name with its type stripped.
 *
 * A name the release does not expose is *absent* rather than merely unrevisioned — visibility is a
 * decision the release makes, not an accident of which keys a rule can spell. Constructors are
 * never pointed at individually: they travel with their type, so exposing `Shape/2` carries
 * `Circle` and `Square` along with it.
 *
 * [strip] never follows a name into [env], so a recursive type stays a reference rather than an
 * expansion and the walk cannot cycle.
 */
fun environmentFor(
    release: Release,
    env: ContractEnv,
): RuleEnv {
    val projected = TypeEnv.empty<Nothing?>()
    for ((name, revision) in release.surface) {
        expose(name, revision, env, projected)
        env.allConstructors()
            .filter { it.parentType == name && it.revision == revision }
            .forEach { expose(it.name, revision, env, projected) }
    }
    return projected
}

/** Copy everything stored under `(name, revision)` — a value binding, a type definition, a
 *  constructor — into [projected] under the plain [name]. */
private fun expose(
    name: String,
    revision: Revision,
    env: ContractEnv,
    projected: RuleEnv,
) {
    env.lookup(name, revision)?.let { projected.bind(name, strip(it)) }
    env.lookupTypeDef(name, revision)?.let { def ->
        projected.registerTypeDef(
            TypeDefInfo(def.name, null, def.typeParams, stripRecord(def.iface), def.span),
        )
    }
    env.lookupConstructor(name, revision)?.let { ctor ->
        projected.registerConstructor(
            ConstructorInfo(ctor.name, null, ctor.typeParams, ctor.fields, ctor.parentType, ctor.span),
        )
    }
}
