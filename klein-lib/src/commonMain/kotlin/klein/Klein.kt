package klein

import klein.surface.*
import klein.check.Checker
import klein.check.Type
import klein.check.TypeEnv
import klein.interp.HostCall
import klein.interp.Interpreter
import klein.interp.Value

/**
 * Library entry point: the pipeline stages, each a total function with the uniform
 * [StageResult] error surface. Stages take the previous stage's output — composition is
 * the caller's, via [StageResult.andThen]:
 *
 * ```
 * val result: StageResult<Value> =
 *     Klein
 *         .tokenize(source)
 *         .andThen(Klein::parse)
 *         .andThen { program -> Klein.check(program).andThen { Klein.interpret(program) } }
 * ```
 *
 * Exceptions never escape these functions; stage-internal aborts are converted to errors
 * in the result. The underlying throwing implementations ([Lexer], [Parser],
 * [Interpreter]) remain available for tools that want them raw.
 */
object Klein {
    fun tokenize(source: String): StageResult<List<Token>> =
        try {
            StageResult.success(Lexer(source).tokenize().toList())
        } catch (e: LexerError) {
            StageResult.failure(e)
        }

    fun parse(tokens: List<Token>): StageResult<Program> =
        try {
            StageResult.success(Parser(tokens).parseProgram())
        } catch (e: ParseError) {
            StageResult.failure(e)
        }

    /**
     * The checker synthesizes a type even for ill-typed programs, so the result can carry
     * both an output and errors; [StageResult.andThen] still refuses to continue past
     * errors. [env] is mutated with the program's bindings — pass your own to inspect
     * them afterwards, or to pre-bind host types.
     */
    fun check(
        program: Program,
        env: TypeEnv = TypeEnv.empty(),
    ): StageResult<Type> {
        val checker = Checker()
        val type = checker.synthProgram(program, env)
        return StageResult(type, checker.getErrors())
    }

    /**
     * Evaluate a program that passed [check] — the interpreter assumes checked input — driving
     * every host call through [onHostCall] synchronously. By default a host call is an error.
     *
     * [bindings] is the host's seam: bind [Value.VNative] declarations or data there (with
     * matching types in the [check] env) to expose them to the program. Hosts that want to
     * control scheduling themselves — suspend on a host call, resume later — use
     * [Interpreter.begin] and step the returned [klein.interp.Execution] directly.
     */
    fun interpret(
        program: Program,
        bindings: Map<String, Value> = emptyMap(),
        onHostCall: ((HostCall) -> Value)? = null,
    ): StageResult<Value> =
        try {
            val interpreter = Interpreter()
            val value =
                if (onHostCall == null) {
                    interpreter.run(program, bindings)
                } else {
                    interpreter.run(program, bindings, onHostCall)
                }
            StageResult.success(value)
        } catch (e: klein.interp.KleinRuntimeError) {
            StageResult.failure(e)
        }
}
