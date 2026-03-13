package klein.types

import klein.Klein
import klein.Type
import kotlin.test.assertEquals

data class InferResult(val type: Type, val leastUpperBound: Type)

/**
 * Infer the type of a Klein expression, asserting no errors.
 * Returns both the canonicalized type and the LUB-simplified type.
 */
fun infer(
    source: String,
    env: TypeEnv = TypeEnv.empty(),
): InferResult {
    val result = Klein.infer(source, env)
    check(result.errors.isEmpty()) { "Expected no type errors but got: ${result.errors}" }
    return InferResult(result.type, result.leastUpperBound)
}

fun inferLUB(
    source: String,
    env: TypeEnv = TypeEnv.empty(),
): Type = infer(source, env).leastUpperBound

fun inferErrors(
    source: String,
    env: TypeEnv = TypeEnv.empty(),
): List<TypeError> = Klein.infer(source, env).errors

/**
 * Assert that the type and LUB match expected values.
 * When expectedLub is not specified, the LUB is expected to match the regular type.
 */
fun assertType(
    expected: Type,
    actual: InferResult,
    expectedLub: String = Type.print(expected),
) {
    assertEquals(expected, actual.type, "type")
    assertEquals(expectedLub, Type.print(actual.leastUpperBound), "leastUpperBound")
}

fun assertType(
    expected: String,
    actual: InferResult,
    expectedLub: String = expected,
) {
    assertEquals(expected, Type.print(actual.type), "type")
    assertEquals(expectedLub, Type.print(actual.leastUpperBound), "leastUpperBound")
}

/**
 * Assert that a single Type prints to the expected string.
 * Used by LubGlbSimplificationTest which tests LUB output directly.
 */
fun assertType(
    expected: String,
    actual: Type,
) = assertEquals(expected, Type.print(actual))

fun assertDuplicateBinding(
    error: TypeError,
    name: String,
) {
    check(error is TypeError.DuplicateBinding) { "Expected DuplicateBinding but got ${error::class.simpleName}" }
    assertEquals(name, error.name, "name")
}

fun assertUnbound(
    error: TypeError,
    name: String,
) {
    check(error is TypeError.UnboundVariable) { "Expected UnboundVariable but got ${error::class.simpleName}" }
    assertEquals(name, error.name, "name")
}

fun assertMissingField(
    error: TypeError,
    field: String,
) {
    check(error is TypeError.MissingField) { "Expected MissingField but got ${error::class.simpleName}" }
    assertEquals(field, error.field, "field")
}

fun assertCallArityMismatch(
    error: TypeError,
    expected: Int,
    actual: Int,
) {
    check(error is TypeError.CallArityMismatch) { "Expected CallArityMismatch but got ${error::class.simpleName}" }
    assertEquals(expected, error.expected, "expected")
    assertEquals(actual, error.actual, "actual")
}

fun assertTypeArityMismatch(
    error: TypeError,
    typeName: String,
    expected: Int,
    actual: Int,
) {
    check(error is TypeError.TypeArityMismatch) { "Expected TypeArityMismatch but got ${error::class.simpleName}" }
    assertEquals(typeName, error.typeName, "typeName")
    assertEquals(expected, error.expected, "expected")
    assertEquals(actual, error.actual, "actual")
}

fun assertMismatch(
    error: TypeError,
    sub: String,
    sup: String,
) {
    check(error is TypeError.TypeMismatch) { "Expected TypeMismatch but got ${error::class.simpleName}" }
    assertEquals(sub, Type.print(error.subtype), "subtype")
    assertEquals(sup, Type.print(error.supertype), "supertype")
}
