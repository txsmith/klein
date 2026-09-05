package klein

interface KleinError {
    val message: String
    val span: SourceSpan?
}

data class Checked<out T>(
    val output: T?,
    val diagnostics: List<KleinError>,
) {
    val hasErrors: Boolean get() = diagnostics.isNotEmpty()

    fun <R> map(transform: (T) -> R): Checked<R> = Checked(output?.let(transform), diagnostics)

    fun <R> andThen(next: (T) -> Checked<R>): Checked<R> =
        if (output == null || hasErrors) Checked(null, diagnostics) else next(output)

    companion object {
        fun <T> success(output: T): Checked<T> = Checked(output, emptyList())

        fun <T> failure(error: KleinError): Checked<T> = Checked(null, listOf(error))
    }
}
