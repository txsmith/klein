package klein

import klein.surface.*
import klein.check.ProgramTypes
import klein.check.RuleEnv
import klein.check.RuleType
import klein.check.TypeEnv
import klein.check.checkProgram
import klein.check.contract.ContractChecker
import klein.check.contract.EnvironmentContract
import klein.check.contract.InvalidContract
import klein.core.CoreExpr
import klein.interp.Execution
import klein.interp.Interpreter
import klein.interp.RuntimeError
import klein.interp.Value

/**
 * Library entry point: the pipeline stages, each a total function with the uniform
 * [Checked] error surface. Stages take the previous stage's output — composition is
 * the caller's, via [Checked.andThen]:
 *
 * ```
 * val result: Checked<Value> =
 *     Klein
 *         .tokenize(source)
 *         .andThen(Klein::parse)
 *         .andThen { p -> Klein.check(p).andThen { Klein.lower(p) }.andThen(Klein::execute) }
 * ```
 *
 * Exceptions never escape these functions; stage-internal aborts are converted to errors
 * in the result.
 */
object Klein {
    fun tokenize(source: String): Checked<List<Token>> =
        try {
            Checked.Accepted(Lexer(source).tokenize().toList())
        } catch (e: Abort) {
            Checked.Rejected(e.diagnostic)
        }

    fun parse(tokens: List<Token>): Checked<Program> =
        try {
            Checked.Accepted(parseProgram(tokens))
        } catch (e: Abort) {
            Checked.Rejected(e.diagnostic)
        }

    /**
     * [env] pre-binds names the program may use, such as host types; checking leaves it unchanged.
     * [checkBindings] returns the types of the program's own top-level names.
     */
    fun check(
        program: Program,
        env: RuleEnv = TypeEnv.empty(),
    ): Checked<RuleType> {
        val checked = checkProgram(program, env)
        return if (checked.errors.isEmpty()) Checked.Accepted(checked.type) else Checked.Rejected(checked.errors)
    }

    fun checkBindings(
        program: Program,
        env: RuleEnv = TypeEnv.empty(),
    ): Checked<ProgramTypes> {
        val checked = checkProgram(program, env)
        return if (checked.errors.isEmpty()) Checked.Accepted(checked.types) else Checked.Rejected(checked.errors)
    }

    /**
     * Read and check a capability contract, and answer the artifact rules are checked against:
     * `contract.check(ruleSource, ReleaseNumber(2))`, and `contract.implement { … }` when the host
     * also needs to run them.
     *
     * The contract is the host's own document, so a contract that does not check is the host's
     * fault: this throws [KleinException] carrying one [InvalidContract] with every diagnostic,
     * rather than returning a [Checked]. Holding an [EnvironmentContract] means the contract checked.
     */
    fun checkContract(contractSource: String): EnvironmentContract {
        val contract =
            try {
                parseContract(Lexer(contractSource).tokenize().toList())
            } catch (e: Abort) {
                throw KleinException(listOf(InvalidContract(listOf(e.diagnostic))))
            }
        val checked = ContractChecker().check(contract)
        if (checked.errors.isNotEmpty()) throw KleinException(listOf(InvalidContract(checked.errors)))
        return checked.contract
    }

    /**
     * Lower a program that passed [check] to the core IR. Assumes checked input: any failure
     * here is an internal invariant violation (a lowerer bug), not a user diagnostic, so this
     * stage carries no errors — it either produces IR or throws.
     */
    fun lower(program: Program): Checked<CoreExpr> = Checked.Accepted(klein.core.lower(program))

    /**
     * Run lowered [CoreExpr] on the [Interpreter] to completion. This entry point runs without an
     * environment — `klein.host` owns that seam — so a suspension on a host call is reported as an
     * error rather than driven; a failed run (division by zero, etc.) becomes a stage failure.
     * Invariant violations propagate: they are interpreter/lowerer bugs, not user diagnostics.
     */
    fun execute(program: CoreExpr): Checked<Value> =
        when (val exec = Interpreter.start(program)) {
            is Execution.Done -> Checked.Accepted(exec.value)
            is Execution.Failure -> Checked.Rejected(exec.error)
            is Execution.AwaitingHost ->
                Checked.Rejected(RuntimeError("unhandled host call '${exec.call}'", exec.span))
        }
}
