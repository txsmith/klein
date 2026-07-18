package klein

import klein.check.Checker
import klein.check.Type
import klein.check.TypeEnv
import klein.check.TypeError

/**
 * Library entry point: lex → parse → type-check with the bidirectional checker.
 *
 * Example usage:
 * ```
 * val result = Klein.check("|x: Num -> x|(42)")
 * println(Type.print(result.type))  // "Num"
 * ```
 */
object Klein {
    data class CheckResult(
        val program: Program,
        val type: Type,
        val errors: List<TypeError>,
    ) {
        val hasErrors: Boolean get() = errors.isNotEmpty()
    }

    fun check(
        source: String,
        env: TypeEnv = TypeEnv.empty(),
    ): CheckResult {
        val program = parse(source)
        val checker = Checker()
        val type = checker.synthProgram(program, env)
        return CheckResult(
            program = program,
            type = type,
            errors = checker.getErrors(),
        )
    }

    fun parse(source: String): Program {
        val tokens = Lexer(source).tokenize().toList()
        return Parser(tokens).parseProgram()
    }

    fun tokenize(source: String): List<Token> = Lexer(source).tokenize().toList()
}
