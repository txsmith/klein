package klein.check.contract

import klein.RevisionNumber
import klein.check.ConstructorInfo
import klein.check.ContractEnv
import klein.check.RuleEnv
import klein.check.Type.TForall
import klein.check.Type.TFun
import klein.check.TypeDefInfo
import klein.check.TypeEnv
import klein.core.PreludeBinding

internal class ResolvedRelease(
    val types: RuleEnv,
    val revisions: Map<String, RevisionNumber>,
) {
    /** Null for a name that lowering erases: exposed types bind nothing, only their constructors do. */
    fun bindingFor(name: String): PreludeBinding? {
        types.lookupConstructor(name, null)?.let { return PreludeBinding.Ctor(name, it.fields.keys.toList()) }
        val type = types.lookup(name) ?: return null
        val body = if (type is TForall) type.body else type
        return if (body is TFun) PreludeBinding.Function(name, body.params.size) else PreludeBinding.Value(name)
    }
}

internal fun ContractEnv.resolveRelease(release: FlattenedReleaseBlock): ResolvedRelease {
    val projected = TypeEnv.empty<Nothing?>()
    val revisions = mutableMapOf<String, RevisionNumber>()
    for ((name, revision) in release.surface) {
        expose(name, revision, projected)
        revisions[name] = revision
        constructorsOf(name, revision).forEach {
            expose(it.name, revision, projected)
            revisions[it.name] = revision
        }
    }
    return ResolvedRelease(projected, revisions)
}

/** Turns the ContractEnv entries into RuleEnv entries for one name at one revision. */
private fun ContractEnv.expose(
    name: String,
    revision: RevisionNumber,
    projected: RuleEnv,
) {
    lookup(name, revision)?.let { projected.bind(name, it.strip()) }
    lookupTypeDef(name, revision)?.let { def ->
        projected.registerTypeDef(TypeDefInfo(def.name, null, def.typeParams, def.iface.stripRecord(), def.span))
    }
    lookupConstructor(name, revision)?.let { ctor ->
        projected.registerConstructor(
            ConstructorInfo(
                ctor.name,
                null,
                ctor.typeParams,
                ctor.fields.mapValues { it.value.strip() },
                ctor.parentType,
                ctor.span,
            ),
        )
    }
}
