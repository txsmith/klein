package klein.interp

import klein.SourceSpan

/**
 * A persistent environment: a chain of scopes mapping names to store addresses. Extending
 * never mutates — closures capture their `Env` safely, and a captured environment can be
 * serialized without cycles because it holds only integer addresses (see [Store]).
 */
class Env internal constructor(
    private val bindings: Map<String, Int>,
    private val parent: Env?,
) {
    fun lookup(name: String): Int? = bindings[name] ?: parent?.lookup(name)

    internal fun child(bindings: Map<String, Int>): Env = Env(bindings, this)

    companion object {
        /** The name under which the implicit lambda parameter (`.`) is bound; not a legal identifier. */
        internal const val IMPLICIT_PARAM = "."

        internal fun root(bindings: Map<String, Int>): Env = Env(bindings, null)
    }
}

typealias StoreAddr = Int
class Store {

    private val cells = ArrayList<Value?>()

    internal val size: Int get() = cells.size

    internal fun copy(): Store {
        val snapshot = Store()
        snapshot.cells.addAll(cells)
        return snapshot
    }

    internal fun alloc(): StoreAddr {
        cells.add(null)
        return cells.size - 1
    }

    internal fun alloc(value: Value): StoreAddr {
        cells.add(value)
        return cells.size - 1
    }

    internal fun set(
        addr: StoreAddr,
        value: Value,
    ) {
        cells[addr] = value
    }

    internal fun get(
        addr: StoreAddr,
        name: String,
        span: SourceSpan,
    ): Value =
        cells[addr]
            ?: throw KleinRuntimeError("'$name' used before its binding was evaluated", span)
}
