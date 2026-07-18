package klein.check

/**
 * Convert a checker [Type] to the surface [klein.Type] — the printable representation used in
 * error payloads and CLI output. A [Type.TForall] flattens to its body: surface types have no
 * quantifiers, so its skolems print by name (`(A) -> A`).
 */
internal fun Type.toSurface(): klein.Type =
    when (this) {
        Type.TNum -> klein.Type.Num
        Type.TStr -> klein.Type.Str
        Type.TBool -> klein.Type.Bool
        Type.TUnit -> klein.Type.Unit
        Type.TNull -> klein.Type.Null
        Type.TTop -> klein.Type.Top
        Type.TBottom -> klein.Type.Bottom
        is Type.TFun -> klein.Type.Fun(params.map { it.toSurface() }, result.toSurface())
        is Type.TRecord -> klein.Type.Record(fields.mapValues { it.value.toSurface() })
        is Type.TOptional -> klein.Type.Optional(type.toSurface())
        is Type.TRef -> klein.Type.Ref(name, typeArgs.map { it.toSurface() })
        is Type.TSkolem -> klein.Type.Var(name)
        is Type.TForall -> body.toSurface()
    }
