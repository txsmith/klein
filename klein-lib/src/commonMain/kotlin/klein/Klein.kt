package klein

import klein.types.TypeEnv
import klein.types.TypeError
import klein.types.TypeSimplifier
import klein.types.Typer

/**
 * Main entry point for the Klein type inference system.
 *
 * Provides a unified API for:
 * - Parsing Klein source code
 * - Type inference with automatic simplification
 * - Access to simplified type representations
 *
 * Example usage:
 * ```
 * val result = Klein.infer("|x -> x|(42)")
 * println(result.type)  // "Num"
 * ```
 */
object Klein {
    data class InferenceResult(
        val program: Program,
        val type: Type,
        val leastUpperBound: Type,
        val errors: List<TypeError>,
    ) {
        val hasErrors: Boolean get() = errors.isNotEmpty()
    }

    fun infer(
        source: String,
        env: TypeEnv = TypeEnv.empty(),
    ): InferenceResult {
        val tokens = Lexer(source).tokenize().toList()
        val program = Parser(tokens).parseProgram()
        val result = Typer.infer(program, env)

        val type = TypeSimplifier.simplifyCanonical(result.type, result.env)
        // TODO: produce leastUpperBound from tightBound once Component is implemented
        val leastUpperBound = type

        return InferenceResult(
            program = program,
            type = type,
            leastUpperBound = leastUpperBound,
            errors = result.errors,
        )
    }

    fun inferOrNull(
        source: String,
        env: TypeEnv = TypeEnv.empty(),
    ): InferenceResult? =
        try {
            infer(source, env)
        } catch (_: LexerError) {
            null
        } catch (_: ParseError) {
            null
        }

    fun parse(source: String): Program {
        val tokens = Lexer(source).tokenize().toList()
        return Parser(tokens).parseProgram()
    }

    fun tokenize(source: String): List<Token> = Lexer(source).tokenize().toList()
}
