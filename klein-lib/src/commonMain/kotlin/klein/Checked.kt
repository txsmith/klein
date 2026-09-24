package klein

sealed class Checked<out T> {
    data class Accepted<out T>(
        val value: T,
    ) : Checked<T>()

    data class Rejected(
        val diagnostics: List<Diagnostic>,
    ) : Checked<Nothing>() {
        constructor(diagnostic: Diagnostic) : this(listOf(diagnostic))

        init {
            require(diagnostics.isNotEmpty()) { "a rejected result needs at least one diagnostic" }
        }
    }

    fun <R> map(transform: (T) -> R): Checked<R> =
        when (this) {
            is Accepted -> Accepted(transform(value))
            is Rejected -> this
        }

    fun <R> andThen(next: (T) -> Checked<R>): Checked<R> =
        when (this) {
            is Accepted -> next(value)
            is Rejected -> this
        }

    fun getOrThrow(): T =
        when (this) {
            is Accepted -> value
            is Rejected -> throw RejectedException(diagnostics)
        }
}

class RejectedException(
    val diagnostics: List<Diagnostic>,
) : RuntimeException(diagnostics.joinToString("\n") { it.message })
