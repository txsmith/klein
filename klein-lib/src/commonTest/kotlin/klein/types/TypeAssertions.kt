package klein.types

import klein.Expr
import klein.Lexer
import klein.Parser
import klein.Type
import klein.TypeEnv
import klein.TypeError
import klein.TypeGen
import klein.TypePrinter
import kotlin.test.assertEquals

/**
 * Parse a source string into an expression.
 */
fun parse(source: String): Expr {
    val tokens = Lexer(source).tokenize().toList()
    return Parser(tokens).parseExpr()
}

/**
 * Infer the type of an expression.
 */
fun inferExpr(source: String, env: TypeEnv = TypeEnv.empty()): Type {
    val expr = parse(source)
    return TypeGen().infer(expr, env)
}

/**
 * Infer expression and return both type and errors.
 */
data class InferResult(
    val type: Type,
    val errors: List<TypeError>,
) {
    val hasErrors: Boolean get() = errors.isNotEmpty()
}

fun inferExprWithErrors(source: String, env: TypeEnv = TypeEnv.empty()): InferResult {
    val typeGen = TypeGen()
    val expr = parse(source)
    val type = typeGen.infer(expr, env)
    return InferResult(type, typeGen.getErrors())
}

/**
 * Assert type matches expected display string.
 */
fun assertType(expected: String, actual: Type) {
    assertEquals(expected, TypePrinter.print(actual))
}
