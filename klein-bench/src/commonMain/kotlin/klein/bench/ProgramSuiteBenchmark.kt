package klein.bench

import klein.Klein
import klein.core.CoreExpr
import klein.surface.Program
import klein.Checked
import klein.surface.Token
import klein.check.RuleType
import klein.interp.Value
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/**
 * Every program in [Programs.suite], measured at each pipeline stage in isolation (each
 * stage benchmark takes pre-computed input from the previous stage) plus end to end. The
 * interesting comparisons over time are per (program, stage) cell — e.g. the numeric-model
 * swap should move `eval` on `arith` and barely touch `parse` on anything.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MICROSECONDS)
class ProgramSuiteBenchmark {
    @Param("arith", "fib", "sumTo", "closures", "records", "rules")
    var name = ""

    private var source = ""
    private lateinit var tokens: List<Token>
    private lateinit var program: Program
    private lateinit var core: CoreExpr

    @Setup
    fun setup() {
        source =
            Programs.suite[name]
                ?: error("benchmark param '$name' has no entry in Programs.suite")
        tokens = Klein.tokenize(source).output ?: error("benchmark program '$name' does not lex")
        program = Klein.parse(tokens).output ?: error("benchmark program '$name' does not parse")
        val checked = Klein.check(program)
        check(checked.diagnostics.isEmpty()) { "benchmark program '$name' has type errors: ${checked.diagnostics}" }
        core = Klein.lower(program).output ?: error("benchmark program '$name' does not lower")
    }

    @Benchmark
    fun lex(): Checked<List<Token>> = Klein.tokenize(source)

    @Benchmark
    fun parse(): Checked<Program> = Klein.parse(tokens)

    @Benchmark
    fun typecheck(): Checked<RuleType> = Klein.check(program)

    @Benchmark
    fun lower(): Checked<CoreExpr> = Klein.lower(program)

    @Benchmark
    fun eval(): Checked<Value> = Klein.execute(core)

    @Benchmark
    fun endToEnd(): Checked<Value> =
        Klein
            .tokenize(source)
            .andThen(Klein::parse)
            .andThen { p -> Klein.check(p).andThen { Klein.lower(p) }.andThen(Klein::execute) }
}
