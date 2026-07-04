package klein.check

/**
 * Temporary bridge from a [Type] to the legacy [klein.Type], so the new checker can reuse
 * `klein.types.TypeError` (whose payloads embed `klein.Type`) until `klein.check` grows its
 * own error type. Drop this once the error-type decision is made.
 */
internal fun Type.toLegacy(): klein.Type =
    when (this) {
        Type.TNum -> klein.Type.Num
        Type.TStr -> klein.Type.Str
        Type.TBool -> klein.Type.Bool
        Type.TUnit -> klein.Type.Unit
        Type.TNull -> klein.Type.Null
        Type.TTop -> klein.Type.Top
        Type.TBottom -> klein.Type.Bottom
        is Type.TFun -> klein.Type.Fun(params.map { it.toLegacy() }, result.toLegacy())
        is Type.TRecord -> klein.Type.Record(fields.mapValues { it.value.toLegacy() })
        is Type.TOptional -> klein.Type.Optional(type.toLegacy())
        is Type.TRef -> klein.Type.Ref(name, typeArgs.map { it.toLegacy() })
        is Type.TSkolem -> klein.Type.Var(name)
        is Type.TForall -> body.toLegacy()
    }
