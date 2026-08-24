package klein.check

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
import klein.check.Type.TUnit
import klein.interp.Value

private val subtyping = Subtyping()

/** A tagged struct whose fields do not fit its constructor is inferred as the record it actually
 *  is, so the mismatch names what came back rather than a nominal type it never was. */
internal fun infer(
    value: Value,
    env: RuleEnv,
): RuleType =
    when (value) {
        is Value.VNum -> TNum
        is Value.VStr -> TStr
        is Value.VBool -> TBool
        Value.VNull -> TNull
        Value.VUnit -> TUnit
        is Value.VStruct -> {
            val fields = value.fields.mapValues { infer(it.value, env) }
            if (value.tag == null) recordOf(fields) else inferTagged(value.tag, fields, env)
        }
        is Value.VClos -> error("unreachable: a closure is rejected before inference runs")
    }

private fun inferTagged(
    tag: String,
    fields: Map<String, RuleType>,
    env: RuleEnv,
): RuleType {
    val ctor = env.lookupConstructor(tag, null) ?: return TBottom
    val typeArgs =
        ctor.typeParams.map { param ->
            ctor.fields.entries
                .firstOrNull { (_, declared) -> declared is TSkolem && declared.name == param }
                ?.let { fields.getValue(it.key) } ?: TBottom
        }
    val subst =
        ctor.fields.values
            .flatMap { skolemsIn(it) }
            .associateWith { skolem -> typeArgs.getOrElse(ctor.typeParams.indexOf(skolem.name)) { TBottom } }
    val expected = ctor.fields.mapValues { substitute(it.value, subst) }
    val fits =
        fields.keys == expected.keys &&
            fields.all { (name, got) -> subtyping.isSubtype(got, expected.getValue(name), env) }
    return if (fits) TRef(ctor.name, typeArgs, null) else recordOf(fields)
}

private fun skolemsIn(type: RuleType): List<TSkolem> =
    when (type) {
        is TSkolem -> listOf(type)
        is TFun -> type.params.flatMap { skolemsIn(it) } + skolemsIn(type.result)
        is TRecord -> type.fields.values.flatMap { skolemsIn(it) }
        is TOptional -> skolemsIn(type.type)
        is TRef -> type.typeArgs.flatMap { skolemsIn(it) }
        is TForall -> skolemsIn(type.body)
        TNum, TStr, TBool, TUnit, TNull, Type.TTop, TBottom -> emptyList()
    }
