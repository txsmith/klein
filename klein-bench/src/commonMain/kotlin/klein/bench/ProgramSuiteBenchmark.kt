package klein.bench

import klein.Klein
import klein.surface.Program
import klein.StageResult
import klein.surface.Token
import klein.check.Type
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

    @Setup
    fun setup() {
        source =
            Programs.suite[name]
                ?: error("benchmark param '$name' has no entry in Programs.suite")
        tokens = Klein.tokenize(source).output ?: error("benchmark program '$name' does not lex")
        program = Klein.parse(tokens).output ?: error("benchmark program '$name' does not parse")
        val checked = Klein.check(program)
        check(checked.errors.isEmpty()) { "benchmark program '$name' has type errors: ${checked.errors}" }
    }

    @Benchmark
    fun lex(): StageResult<List<Token>> = Klein.tokenize(source)

    @Benchmark
    fun parse(): StageResult<Program> = Klein.parse(tokens)

    @Benchmark
    fun typecheck(): StageResult<Type> = Klein.check(program)

    @Benchmark
    fun eval(): StageResult<Value> = Klein.interpret(program)

    @Benchmark
    fun endToEnd(): StageResult<Value> =
        Klein
            .tokenize(source)
            .andThen(Klein::parse)
            .andThen { p -> Klein.check(p).andThen { Klein.interpret(p) } }
}
