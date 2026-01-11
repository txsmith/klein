package klein.types

import klein.Klein
import klein.SourceSpan
import klein.types.DisplayType.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Infer the type of a Klein expression and simplify it.
 * Returns the simplified DisplayType.
 */
fun infer(
    source: String,
    env: TypeEnv = TypeEnv.empty(),
): DisplayType = Klein.simplify(Klein.infer(source, env).type)

/**
 * Infer types and return full result including errors.
 * Useful for testing error cases.
 */
fun inferWithErrors(
    source: String,
    env: TypeEnv = TypeEnv.empty(),
): Klein.InferenceResult = Klein.infer(source, env)

/**
 * Assert that two DisplayTypes are equal.
 */
fun assertType(
    expected: DisplayType,
    actual: DisplayType,
) = assertEquals(expected, actual)

/**
 * Assert that a type prints to the expected string.
 * Convenience for simple type assertions.
 */
fun assertType(
    expected: String,
    actual: DisplayType,
) = assertEquals(expected, TypePrinter.print(actual))

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
