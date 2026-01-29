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
): Type {
    val result = Klein.infer(source, env)
    check(result.errors.isEmpty()) { "Expected no type errors but got: ${result.errors}" }
    return result.type
}

fun inferErrors(
    source: String,
    env: TypeEnv = TypeEnv.empty(),
): List<TypeError> = Klein.infer(source, env).errors

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

fun assertMismatch(
    error: TypeError,
    sub: String,
    sup: String,
) {
    check(error is TypeError.TypeMismatch) { "Expected TypeMismatch but got ${error::class.simpleName}" }
    assertEquals(sub, Type.print(error.subtype), "subtype")
    assertEquals(sup, Type.print(error.supertype), "supertype")
}
