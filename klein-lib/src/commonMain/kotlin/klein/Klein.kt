package klein

import klein.surface.*
import klein.check.RuleEnv
import klein.check.RuleType
import klein.check.TypeEnv
import klein.check.checkProgram
import klein.check.contract.ContractChecker
import klein.check.contract.EnvironmentContract
import klein.core.CoreExpr
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
 * in the result. The underlying throwing implementations ([Lexer], [parseProgram]) remain
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
            StageResult.success(parseProgram(tokens))
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
        env: RuleEnv = TypeEnv.empty(),
    ): StageResult<RuleType> {
        val checked = checkProgram(program, env)
        return StageResult(checked.type, checked.errors)
    }

    /**
     * Read and check a capability contract, and answer the artifact rules are checked against:
     * `contract.check(ruleSource, ReleaseNumber(2))`, and `contract.implement { … }` when the host
     * also needs to run them.
     *
     * Throws [KleinException] carrying every diagnostic. There is no [StageResult] here because
     * there is no partial answer worth having: holding an [EnvironmentContract] means the contract
     * checked.
     */
    fun checkContract(contractSource: String): EnvironmentContract {
        val tokens =
            try {
                Lexer(contractSource).tokenize().toList()
            } catch (e: LexerError) {
                throw KleinException(listOf(e))
            }
        val contract =
            try {
                parseContract(tokens)
            } catch (e: ParseError) {
                throw KleinException(listOf(e))
            }
        val checked = ContractChecker().check(contract)
        if (checked.errors.isNotEmpty()) throw KleinException(checked.errors)
        return checked.contract
    }

    /**
     * Lower a program that passed [check] to the core IR. Assumes checked input: any failure
     * here is an internal invariant violation (a lowerer bug), not a user diagnostic, so this
     * stage carries no errors — it either produces IR or throws.
     */
    fun lower(program: Program): StageResult<CoreExpr> = StageResult.success(klein.core.lower(program))

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
