package klein.interp

import klein.SourceSpan

internal typealias StoreAddr = Int
internal class Store {

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
            ?: runtimeError("'$name' used before its binding was evaluated", span)
}
