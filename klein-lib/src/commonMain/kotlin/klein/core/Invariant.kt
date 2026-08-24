package klein.core

import klein.SourceSpan
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
internal inline fun invariant(
    condition: Boolean,
    span: SourceSpan? = null,
    message: () -> String,
) {
    contract { returns() implies condition }
    if (!condition) throw InvariantViolation(message(), span)
}

internal class InvariantViolation(
    message: String,
    val span: SourceSpan? = null,
) : IllegalStateException(if (span != null) "$message at $span" else message)
