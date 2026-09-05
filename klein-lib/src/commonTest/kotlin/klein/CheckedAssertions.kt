package klein

import kotlin.test.assertTrue

fun <T> Checked<T>.orFail(): T {
    assertTrue(diagnostics.isEmpty(), "unexpected diagnostics: $diagnostics")
    return output!!
}
