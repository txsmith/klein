package klein.jsbinding

@JsExport
class Type internal constructor(
    internal val type: klein.check.Type<*>,
) {
    fun print(): String = klein.check.Type.print(type)
}
