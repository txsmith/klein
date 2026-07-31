# Klein Language

**A tiny, safe expression language for embedding customizable business rules.**

Klein is designed to let tech-savvy business users write rules, validations, and simple programs that your application executes. Programs are pure expressions with suspendable effects—they can't access the network or modify state directly, only use what the host application provides.

## Documentation

### Core Documentation

- **[grammar.md](./docs/grammar.md)** - Complete formal grammar for Klein expressions and types, including indentation rules, operator precedence, and parser method mappings
- **[reference.md](./docs/reference.md)** - Complete language reference with syntax, examples, and usage patterns for all Klein features
- **[type-system.md](./docs/type-system.md)** - Type system design: structural vs nominal typing, subtyping, records, and the tilde operator (inference sections being rewritten for Operation Bidi)
- **[spec/bidirectional-checking.md](./docs/spec/bidirectional-checking.md)** - The current type-checking model (the Operation Bidi surface spec)
- **[spec/pattern-matching.md](./docs/spec/pattern-matching.md)** - Pattern matching and destructuring bindings: pattern forms, match typing, exhaustiveness, refutability

Specs are **living contracts** — the current rules, updated in place as the language evolves, and what the test suites are written against. ADRs (below) are the immutable decision history.
- **[calling-conventions.md](./docs/calling-conventions.md)** - Function definitions, positional arguments, records, tuples, extension methods, and the tilde operator

### Implementation Guides

- **[implementation-status.md](./docs/implementation-status.md)** - Current implementation status across parser, type system, and execution
- **[performance-debt.md](./docs/performance-debt.md)** - Deliberate performance corners in the execution pipeline, each with its fix
- **[roadmap.md](./docs/roadmap.md)** - Phase-based roadmap for what comes after the type checker (pattern matching, syntax, execution)
- **[dsl-project-summary.md](./docs/dsl-project-summary.md)** - Original vision document for Klein as a cross-platform expression language with algebraic effects

### Design Decisions

See [docs/decisions/](./docs/decisions/) for the full set of ADRs. ADRs are immutable history; superseded ones carry a forward pointer to what replaced them.

**Current type-system direction:**

- **[2026-06-24-adopt-operation-bidi.md](./docs/decisions/2026-06-24-adopt-operation-bidi.md)** - **Current.** Local bidirectional checking — annotate signatures, infer interiors; drop global inference, keep subtyping.
- **[2026-06-23-polarity-wall-and-type-system-direction.md](./docs/decisions/2026-06-23-polarity-wall-and-type-system-direction.md)** - Why SimpleSub was abandoned: the polarity wall and the three ways out.

**Execution decisions (Core IR + CESK machine):**

- **[persist-the-log-replay-the-run.md](./docs/decisions/2026-07-31-persist-the-log-replay-the-run.md)** - **The persistence model.** Machine state is never serialized; the effect log is truth, replay rebuilds everything else
- **[own-machine-not-a-rented-vm.md](./docs/decisions/2026-07-20-own-machine-not-a-rented-vm.md)** - Why Klein runs its own machine instead of compiling to JVM/WASM/Lua (re-centered on embedding, walkable hot tier, fuel)
- **[source-is-truth-ir-is-a-cache.md](./docs/decisions/2026-07-20-source-is-truth-ir-is-a-cache.md)** - Stored IR integrity: version stamp + checksum, re-derive instead of migrate
- **[no-load-time-verifier.md](./docs/decisions/2026-07-20-no-load-time-verifier.md)** - Why per-op checks are just unboxing and a verifier was rejected

**Foundational language decisions (still current):**

- **[records-as-interfaces.md](./docs/decisions/2026-01-09-records-as-interfaces.md)** - Records with function fields as structural interfaces
- **[no-anonymous-unions.md](./docs/decisions/2026-01-09-no-anonymous-unions.md)** - Why unions are nominal sums, not anonymous `A | B`
- **[optional-types-null-safety.md](./docs/decisions/2026-01-14-optional-types-null-safety.md)** - `T?` and null safety
- **[type-definition-syntax.md](./docs/decisions/2026-01-14-type-definition-syntax.md)** - The `type` keyword, constructors, sum types
- **[positional-function-syntax.md](./docs/decisions/2026-01-09-positional-function-syntax.md)** - Positional arguments instead of record-based calling
- **[fail-fast-error-handling.md](./docs/decisions/2026-01-09-fail-fast-error-handling.md)** - Fail-fast by default with opt-in recovery via `.recover`
- **[modules-vs-records.md](./docs/decisions/2026-01-09-modules-vs-records.md)** - Module system design

**Superseded by Operation Bidi (kept as history):** `simplesub-type-inference`, `lub-glb-type-simplification`, `rigid-type-variables-in-annotations`, `constructor-type-options`.

### Other Resources

- **[README.md](./README.md)** - Project overview and examples

## Running the CLI

### Build the native binary

```bash
./gradlew :klein-lib:linkDebugExecutableMacosArm64
```

This automatically creates a `./klein` symlink to the binary for convenience.

The actual binary is at: `klein-lib/build/bin/macosArm64/debugExecutable/klein-lib.kexe`

For other platforms:
- Linux: `linkDebugExecutableLinuxX64`
- macOS Intel: `linkDebugExecutableMacosX64`

### Tokenize (Lex)

```bash
# From a file
./klein tokens example.klein

# From stdin
echo "x = 1 + 2" | ./klein tokens --stdin

# Short form
./klein t example.klein

# Raw output (just tokens, no formatting)
./klein tokens --raw example.klein

# Verbose output
./klein tokens -v example.klein
```

### Parse

```bash
# From a file
./klein parse example.klein

# From stdin
echo "f = |x -> x + 1|" | ./klein parse --stdin

# Short form
./klein p example.klein

# Raw output (AST only)
./klein parse --raw example.klein
```

### Check Types

The primary command under Operation Bidi: run the `klein.check` bidirectional checker. Prints the type of
each top-level binding (and the trailing expression), then a pass/fail verdict. Exits non-zero on any
type error, so it works as a gate in scripts.

```bash
# From a file
./klein check example.klein

# Short form
./klein c example.klein

# From stdin
echo "x = 1 + 2" | ./klein check --stdin

# Machine-readable errors (Error: <msg> at <span>)
./klein check --raw example.klein
```

`check` has no IR/format flags — the Operation Bidi type is a plain structural tree with nothing to dump.

### Run

Execute a program on the Core machine (parse → check → lower → run) and print the final value.
Host calls are not reachable from source yet, so programs must be pure.

```bash
# From a file
./klein run example.klein

# Short form
./klein r example.klein

# From stdin
echo "1 + 2 * 3" | ./klein run --stdin
```

### Dump the Core IR

Print the lowered Core IR of a program — slot-addressed `name[depth;slot]` refs, hoisted-first
scopes, desugared `match`es.

```bash
./klein core example.klein
echo "if x > 1 then x else 0" | ./klein core --stdin
```

## Project Structure

```
klein-lang/
├── klein-lib/
│   ├── src/
│   │   ├── commonMain/kotlin/klein/
│   │   │   ├── SourceSpan.kt     # Source location tracking (cross-cutting; stays at root)
│   │   │   ├── Klein.kt          # Library entry: pipeline stages (tokenize → parse → check → lower → execute)
│   │   │   ├── StageResult.kt    # Uniform stage result + KleinError; compose stages with andThen
│   │   │   ├── surface/          # Surface syntax: what the parser produces, the checker consumes
│   │   │   │   ├── Lexer.kt        # Tokenization
│   │   │   │   ├── Parser.kt       # Parsing
│   │   │   │   ├── Ast.kt          # Surface AST definitions
│   │   │   │   ├── Token.kt        # Token types
│   │   │   │   └── PrettyPrint.kt  # AST pretty-printing
│   │   │   ├── core/             # The compile-time half: Core IR + lowering
│   │   │   │   ├── Core.kt         # IR nodes (Control, CoreExpr, Match arms, constants)
│   │   │   │   ├── Lowering.kt     # Surface → Core: slot resolution, hoisting, desugaring
│   │   │   │   ├── Invariant.kt    # invariant() + InvariantViolation (malformed-IR errors)
│   │   │   │   └── PrettyPrint.kt  # Core IR printer (backs the `core` CLI command)
│   │   │   ├── interp/           # The run-time half: the CESK machine over Core
│   │   │   │   ├── Machine.kt      # Two-stack machine + Execution (Done | AwaitingHost), one-shot resume/clone
│   │   │   │   ├── Store.kt        # The store: write-once cells behind integer addresses
│   │   │   │   ├── Value.kt        # Runtime values (VStruct for records and data, VClos closures)
│   │   │   │   └── KleinRuntimeError.kt
│   │   │   └── check/            # The Operation Bidi bidirectional checker
│   │   │       ├── Checker.kt              # synth / check driver
│   │   │       ├── Type.kt                 # The type tree (skolems, foralls) + printer
│   │   │       ├── TypeError.kt            # Typed error hierarchy
│   │   │       ├── Subtyping.kt            # Ground subtyping, lub/glb
│   │   │       ├── Constraint.kt           # Instantiation constraint solving
│   │   │       ├── TypeEnv.kt              # Environment / scopes
│   │   │       ├── ScopeGraph.kt           # Top-level dependency SCCs
│   │   │       ├── TypeDefPreprocessor.kt  # Variance inference, nominal setup
│   │   │       └── Variance.kt             # Variance lattice
│   │   ├── commonTest/kotlin/klein/
│   │   │   ├── lexer/
│   │   │   ├── parser/
│   │   │   ├── check/
│   │   │   ├── core/             # Lowering golden tests + IR printer tests
│   │   │   └── interp/           # Machine unit tests + per-feature eval suites (full pipeline)
│   │   └── nativeMain/kotlin/klein/
│   │       └── Main.kt           # CLI entry point
│   └── build.gradle.kts
├── docs/                         # Design docs, ADRs, spec, roadmap
├── examples/                     # Sample .klein programs
└── README.md                     # Project overview
```

## Running Tests

```bash
# All tests
./gradlew :klein-lib:allTests

# JVM only (faster for development)
./gradlew :klein-lib:jvmTest

# Specific test class
./gradlew :klein-lib:jvmTest --tests "klein.parser.LambdaTest"

# Lexer tests only
./gradlew :klein-lib:jvmTest --tests "klein.lexer.*"

# Parser tests only
./gradlew :klein-lib:jvmTest --tests "klein.parser.*"
```

## Running Benchmarks

The benchmark corpus lives in `klein-bench` (kotlinx-benchmark / JMH): a suite of named
sample Klein programs (`Programs.kt`), each measured at every pipeline stage
(`parse`, `typecheck`, `eval`, `endToEnd`). Compare per (program, stage) cell before and
after a change to see whether it actually made things faster.

```bash
# Full statistical run (use this for real before/after comparisons; takes minutes)
./gradlew :klein-bench:benchmark

# Quick smoke pass (sanity only, high variance)
./gradlew :klein-bench:smokeBenchmark
```

JSON reports land in `klein-bench/build/reports/benchmarks/`. To track a new program, add
an entry to `Programs.suite` and its name to the `@Param` list in `ProgramSuiteBenchmark`.

## Implementation Status

See **[implementation-status.md](./docs/implementation-status.md)** for current status across parser, type system, and interpreter.
