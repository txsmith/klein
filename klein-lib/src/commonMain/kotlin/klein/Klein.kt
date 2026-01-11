package klein

import klein.types.*

/**
 * Main entry point for the Klein type inference system.
 *
 * Provides a unified API for:
 * - Parsing Klein source code
 * - Type inference with automatic simplification
 * - Access to both simplified and raw type representations
 *
 * Example usage:
 * ```
 * val result = Klein.infer("|x -> x|(42)")
 * println(result.typeString)  // "Num"
 * ```
 */
object Klein {

    /**
     * Result of type inference on a Klein program.
     */
    data class InferenceResult(
        /** The inferred type (already simplified) */
        val type: SimpleType,
        /** All typed statements in the program */
        val statements: List<TypedStmt>,
        /** Type errors encountered during inference */
        val errors: List<TypeError>,
        /** The type environment after inference */
        val env: TypeEnv,
    ) {
        /** Whether any type errors were found */
        val hasErrors: Boolean get() = errors.isNotEmpty()

        /** The simplified type as a string */
        val typeString: String get() = TypePrinter.print(type)

        /** The raw (unsimplified) type as a string, useful for debugging */
        val rawTypeString: String get() = TypePrinter.printRaw(type)

        /** Get simplified type string for a specific type */
        fun simplify(t: SimpleType): String = TypePrinter.print(t)

        /** Get raw type string for a specific type */
        fun raw(t: SimpleType): String = TypePrinter.printRaw(t)
    }

    /**
     * Parse and infer types for a Klein source string.
     *
     * @param source The Klein source code
     * @param env Optional type environment with predefined bindings
     * @return InferenceResult with the inferred type and any errors
     * @throws LexerError if tokenization fails
     * @throws ParseError if parsing fails
     */
    fun infer(source: String, env: TypeEnv = TypeEnv.empty()): InferenceResult {
        val tokens = Lexer(source).tokenize().toList()
        val program = Parser(tokens).parseProgram()
        val result = Typer.infer(program, env)

        return InferenceResult(
            type = result.type,
            statements = result.stmts,
            errors = result.errors,
            env = result.env,
        )
    }

    /**
     * Parse and infer types, returning null on any error instead of throwing.
     *
     * @param source The Klein source code
     * @param env Optional type environment with predefined bindings
     * @return InferenceResult or null if parsing/inference fails
     */
    fun inferOrNull(source: String, env: TypeEnv = TypeEnv.empty()): InferenceResult? =
        try {
            infer(source, env)
        } catch (_: LexerError) {
            null
        } catch (_: ParseError) {
            null
        }

    /**
     * Infer type and return the simplified type string directly.
     *
     * @param source The Klein source code
     * @param env Optional type environment
     * @return The simplified type as a string
     * @throws LexerError if tokenization fails
     * @throws ParseError if parsing fails
     */
    fun inferType(source: String, env: TypeEnv = TypeEnv.empty()): String =
        infer(source, env).typeString

    /**
     * Parse Klein source code into an AST.
     *
     * @param source The Klein source code
     * @return The parsed Program AST
     * @throws LexerError if tokenization fails
     * @throws ParseError if parsing fails
     */
    fun parse(source: String): Program {
        val tokens = Lexer(source).tokenize().toList()
        return Parser(tokens).parseProgram()
    }

    /**
     * Tokenize Klein source code.
     *
     * @param source The Klein source code
     * @return List of tokens
     * @throws LexerError if tokenization fails
     */
    fun tokenize(source: String): List<Token> =
        Lexer(source).tokenize().toList()

    /**
     * Simplify a type for display.
     * Eliminates polar variables and reduces to minimal form.
     *
     * @param type The type to simplify
     * @return The simplified type
     */
    fun simplify(type: SimpleType): SimpleType =
        TypeSimplifier.simplify(type)

    /**
     * Print a type as a string (with simplification).
     *
     * @param type The type to print
     * @return The type as a readable string
     */
    fun printType(type: SimpleType): String =
        TypePrinter.print(type)

    /**
     * Print a type without simplification (for debugging).
     *
     * @param type The type to print
     * @return The raw type string showing all bounds
     */
    fun printRawType(type: SimpleType): String =
        TypePrinter.printRaw(type)
}
