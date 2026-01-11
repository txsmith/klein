package klein.types

import klein.InferResult
import klein.Type
import klein.TypeEnv
import klein.TypeGen
import klein.TypePrinter
import klein.parser.parse
import kotlin.test.assertEquals

fun infer(
    source: String,
    env: TypeEnv = TypeEnv.empty(),
): Type = TypeGen.infer(parse(source), env).type

fun inferWithErrors(
    source: String,
    env: TypeEnv = TypeEnv.empty(),
): InferResult = TypeGen.infer(parse(source), env)

fun assertType(
    expected: String,
    actual: Type,
) = assertEquals(expected, TypePrinter.print(actual))
