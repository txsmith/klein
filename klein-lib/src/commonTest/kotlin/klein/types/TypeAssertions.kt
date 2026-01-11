package klein.types

import klein.Klein
import klein.SourceSpan
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Infer the type of a Klein expression.
 * Returns the simplified type.
 */
fun infer(
    source: String,
    env: TypeEnv = TypeEnv.empty(),
): SimpleType = Klein.infer(source, env).type

/**
 * Infer types and return full result including errors.
 * Useful for testing error cases.
 */
fun inferWithErrors(
    source: String,
    env: TypeEnv = TypeEnv.empty(),
): Klein.InferenceResult = Klein.infer(source, env)

/**
 * Assert that a type prints to the expected string.
 * Uses simplified type printing.
 */
fun assertType(
    expected: String,
    actual: SimpleType,
) = assertEquals(expected, Klein.printType(actual))

/**
 * Assert that actual is a subtype of expected.
 */
fun assertSubtypeOf(
    actual: SimpleType,
    expected: SimpleType,
) {
    val subtyping = Subtyping()
    subtyping.constrain(actual, expected, SourceSpan.zero)
    assertTrue(
        subtyping.getErrors().isEmpty(),
        "Expected ${Klein.printType(actual)} <: ${Klein.printType(expected)}",
    )
}
