package klein

/**
 * Every diagnostic the failed operation produced, as one exception.
 *
 * [Checked] accumulates diagnostics so no degenerate statement goes unreported; that is a
 * checker-internal job. The accumulated list crosses the public boundary here, thrown at the point
 * of failure and never rewrapped on the way out — so a host writes one catch for bad contract
 * syntax, a contract that fails to check, a missing implementation, and a rule that fails to check.
 */
class KleinException(
    val errors: List<KleinError>,
) : Exception(errors.joinToString("\n") { it.span?.let { span -> "${it.message} at $span" } ?: it.message })
