package klein.types

import klein.Klein
import klein.Type
import kotlin.test.assertEquals

/**
 * Infer the type of a Klein expression with canonicalization.
 */
fun infer(
    source: String,
    env: TypeEnv = TypeEnv.empty(),
): Type = Klein.infer(source, env).type

/**
 * Infer types and return full result including errors.
 * Useful for testing error cases.
 */
fun inferWithErrors(
    source: String,
    env: TypeEnv = TypeEnv.empty(),
): Klein.InferenceResult = Klein.infer(source, env)

/**
 * Assert that two Types are equal.
 */
fun assertType(
    expected: Type,
    actual: Type,
) = assertEquals(expected, actual)

/**
 * Assert that a type prints to the expected string.
 * Convenience for simple type assertions.
 */
fun assertType(
    expected: String,
    actual: Type,
) = assertEquals(expected, Type.print(actual))
