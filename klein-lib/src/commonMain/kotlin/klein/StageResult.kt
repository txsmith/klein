package klein

/**
 * The uniform error surface of every pipeline stage: something went wrong at a source
 * location. Implemented by [LexerError], [ParseError], [klein.check.TypeError], and
 * [klein.interp.KleinRuntimeError] — so callers report diagnostics from any stage the
 * same way.
 */
interface KleinError {
    val message: String
    val span: SourceSpan
}

/**
 * The uniform result of a pipeline stage: an output (when the stage could produce one)
 * plus accumulated errors. Stages differ in which combinations they produce — the lexer
 * and parser abort on their first error (`output == null`, one error), the checker
 * synthesizes a type *and* accumulates errors, the interpreter evaluates or fails — but
 * callers handle all of them identically.
 */
data class StageResult<out T>(
    val output: T?,
    val errors: List<KleinError>,
) {
    val hasErrors: Boolean get() = errors.isNotEmpty()

    /**
     * Feed this stage's output to the next stage, but only if this stage fully succeeded;
     * otherwise carry the errors forward. Downstream stages assume clean input (the
     * interpreter only runs checked programs), so any error stops the pipeline.
     */
    fun <R> andThen(next: (T) -> StageResult<R>): StageResult<R> =
        if (output == null || hasErrors) StageResult(null, errors) else next(output)

    companion object {
        fun <T> success(output: T): StageResult<T> = StageResult(output, emptyList())

        fun <T> failure(error: KleinError): StageResult<T> = StageResult(null, listOf(error))
    }
}
