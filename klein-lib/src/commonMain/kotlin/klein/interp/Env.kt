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

/**
 * The store: addresses to values. The indirection through addresses is what makes recursive
 * closures acyclic — a closure's environment holds the address of the binding, and the store
 * holds the closure, so `closure → env → address → store → closure` never forms an object
 * cycle. Cells are allocated for a whole scope up front and filled as bindings evaluate
 * (SCC order); reading an unfilled cell is a runtime error rather than undefined behavior.
 */
class Store {
    private val cells = ArrayList<Value?>()

    internal fun alloc(): Int {
        cells.add(null)
        return cells.size - 1
    }

    internal fun set(
        addr: Int,
        value: Value,
    ) {
        cells[addr] = value
    }

    internal fun get(
        addr: Int,
        name: String,
        span: SourceSpan,
    ): Value =
        cells[addr]
            ?: throw KleinRuntimeError("'$name' used before its binding was evaluated", span)
}
