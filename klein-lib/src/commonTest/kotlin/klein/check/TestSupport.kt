package klein.check

import klein.Lexer
import klein.Parser
import klein.types.TypeError

/** Result of type-checking a source string through the [Checker]. */
data class InferResult(val type: Type, val errors: List<TypeError>)

/** Parse [src] and run the bidirectional checker over it. Shared by all `klein.check` test suites. */
fun infer(
    src: String,
    env: TypeEnv = TypeEnv.empty(),
): InferResult {
    val tokens = Lexer(src).tokenize().toList()
    val program = Parser(tokens).parseProgram()
    val checker = Checker()
    return InferResult(checker.synthProgram(program, env), checker.getErrors())
}
