package klein.types

import klein.parser.parse
import kotlin.test.assertEquals

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
