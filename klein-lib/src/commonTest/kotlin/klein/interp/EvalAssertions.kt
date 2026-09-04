package klein.interp

import klein.Klein
import klein.core.lower
import klein.core.parseProgram
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Run [source] through the full pipeline — parse -> check -> lower -> [Interpreter] — and surface
 * the interpreter's own outcome, so callers can assert on an [Execution.Failure]. A parse or type
 * error fails the test: eval tests must be well-typed programs.
 */
internal fun execSource(source: String): Execution {
    val program = parseProgram(source.trimIndent().trim())
    val checked = Klein.check(program)
    check(!checked.hasErrors) { "type errors in test program: ${checked.errors}" }
    return Interpreter.start(lower(program))
}

fun runSource(source: String): Value = assertIs<Execution.Done>(execSource(source)).value

/**
 * End-to-end evaluation assertion over the full pipeline — the surface -> interpreter seam that
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
    val e = assertIs<Execution.Failure>(execSource(source)).error
    assertTrue(
        e.message.orEmpty().contains(messagePart),
        "expected message containing '$messagePart', got '${e.message}'",
    )
}
