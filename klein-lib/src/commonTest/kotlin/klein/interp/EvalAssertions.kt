package klein.interp

import klein.Klein
import klein.core.lower
import klein.core.parseProgram
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Run [source] through the full pipeline: parse -> check -> lower -> [Machine]. A parse or type
 * error fails the test (eval tests must be well-typed programs); a runtime error propagates so
 * callers can assert on it.
 */
fun runSource(source: String): Value {
    val program = parseProgram(source.trimIndent().trim())
    val checked = Klein.check(program)
    check(!checked.hasErrors) { "type errors in test program: ${checked.errors}" }
    val exec = Machine.start(lower(program))
    assertIs<Execution.Done>(exec)
    return exec.value
}

/**
 * End-to-end evaluation assertion over the full pipeline — the surface -> machine seam that
 * golden lowering tests (surface -> printed IR) can't exercise: the layer where an arm's runtime
 * scope depth has to match the depth the lowerer assigned.
 */
fun assertEvaluatesTo(
    expected: Value,
    source: String,
) = assertEquals(expected, runSource(source))

fun assertRunFails(
    source: String,
    messagePart: String,
) {
    val e = assertFailsWith<KleinRuntimeError> { runSource(source) }
    assertTrue(
        e.message.orEmpty().contains(messagePart),
        "expected message containing '$messagePart', got '${e.message}'",
    )
}
