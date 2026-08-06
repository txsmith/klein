package klein

import klein.surface.*
import klein.check.Checker
import klein.check.Type
import klein.check.TypeEnv
import klein.core.CoreExpr
import klein.core.Lowering
import klein.interp.Execution
import klein.interp.KleinRuntimeError
import klein.interp.Machine
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
 *         .andThen { p -> Klein.check(p).andThen { Klein.lower(p) }.andThen(Klein::execute) }
 * ```
 *
 * Exceptions never escape these functions; stage-internal aborts are converted to errors
 * in the result. The underlying throwing implementations ([Lexer], [Parser]) remain
 * available for tools that want them raw.
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
        val checked = Checker().checkProgram(program, env)
        return StageResult(checked.type, checked.errors)
    }

    /**
     * Check a parsed [Program] as a capability contract instead of as a program: type definitions
     * and declarations only. The output is the environment the contract declares, so a program can
     * be checked against a contract by composition:
     *
     * ```
     * Klein.checkContract(contract).andThen { env -> Klein.check(program, env) }
     * ```
     *
     * Which capabilities a host actually implements, and how a contract is located, versioned, or
     * bound to an implementation, are all the host's business — this stage only reads the source.
     */
    fun checkContract(
        program: Program,
        env: TypeEnv = TypeEnv.empty(),
    ): StageResult<TypeEnv> {
        val checked = Checker().checkContract(program, env)
        return StageResult(checked.env, checked.errors)
    }

    /**
     * Lower a program that passed [check] to the core IR. Assumes checked input: any failure
     * here is an internal invariant violation (a lowerer bug), not a user diagnostic, so this
     * stage carries no errors — it either produces IR or throws.
     */
    fun lower(program: Program): StageResult<CoreExpr> = StageResult.success(Lowering().lower(program))

    /**
     * Run lowered [CoreExpr] on the [Machine] to completion. v1 has no host seam, so a suspension
     * on a host call is reported as an error rather than driven; a [KleinRuntimeError] from the
     * machine (division by zero, etc.) becomes a stage failure. Invariant violations propagate:
     * they are machine/lowerer bugs, not user diagnostics.
     */
    fun execute(program: CoreExpr): StageResult<Value> =
        try {
            when (val exec = Machine.start(program)) {
                is Execution.Done -> StageResult.success(exec.value)
                is Execution.AwaitingHost ->
                    StageResult.failure(KleinRuntimeError("unhandled host call '${exec.call}'", exec.span))
            }
        } catch (e: KleinRuntimeError) {
            StageResult.failure(e)
        }
}
