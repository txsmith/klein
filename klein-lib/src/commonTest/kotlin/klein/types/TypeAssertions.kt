package klein.types

import klein.SourceSpan
import klein.parser.parse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun infer(
    source: String,
    env: TypeEnv = TypeEnv.empty(),
): SimpleType = Typer.infer(parse(source), env).type

fun inferWithErrors(
    source: String,
    env: TypeEnv = TypeEnv.empty(),
): InferResult = Typer.infer(parse(source), env)

fun assertType(
    expected: String,
    actual: SimpleType,
) = assertEquals(expected, TypePrinter.print(actual))

fun assertSubtypeOf(
    actual: SimpleType,
    expected: SimpleType,
) {
    val subtyping = Subtyping()
    subtyping.constrain(actual, expected, SourceSpan.zero)
    assertTrue(
        subtyping.getErrors().isEmpty(),
        "Expected ${TypePrinter.print(actual)} <: ${TypePrinter.print(expected)}",
    )
}
