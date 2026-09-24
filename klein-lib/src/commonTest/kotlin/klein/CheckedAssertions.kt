package klein

import kotlin.test.assertIs
import kotlin.test.fail

fun <T> Checked<T>.orFail(): T =
    when (this) {
        is Checked.Accepted -> value
        is Checked.Rejected -> fail("unexpected diagnostics: $diagnostics")
    }

fun Checked<*>.assertRejected(): List<Diagnostic> = assertIs<Checked.Rejected>(this).diagnostics

fun Checked<*>.diagnosticsOrEmpty(): List<Diagnostic> =
    when (this) {
        is Checked.Accepted -> emptyList()
        is Checked.Rejected -> diagnostics
    }
