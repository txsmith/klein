# Klein Language

**A tiny, safe expression language for embedding customizable business rules.**

Klein is designed to let tech-savvy business users write rules, validations, and simple programs that your application executes. Programs are pure expressions with suspendable effects—they can't access the network or modify state directly, only use what the host application provides.

## Documentation

### Core Documentation

- **[grammar.md](./docs/grammar.md)** - Complete formal grammar for Klein expressions and types, including indentation rules, operator precedence, and parser method mappings
- **[reference.md](./docs/reference.md)** - Complete language reference with syntax, examples, and usage patterns for all Klein features
- **[type-system.md](./docs/type-system.md)** - Type system design: structural vs nominal typing, subtyping, records, and the tilde operator (inference sections being rewritten for Operation Bidi)
- **[spec/bidirectional-checking.md](./docs/spec/bidirectional-checking.md)** - The current type-checking model (Operation Bidi M0 surface spec)
- **[calling-conventions.md](./docs/calling-conventions.md)** - Function definitions, positional arguments, records, tuples, extension methods, and the tilde operator

### Implementation Guides

- **[implementation-status.md](./docs/implementation-status.md)** - Current implementation status across parser, type system, and interpreter
- **[plans/operation-bidi-roadmap.md](./docs/plans/operation-bidi-roadmap.md)** - **Current roadmap** for the type-checker rewrite (Operation Bidi build + teardown, test strategy, doc updates)
- **[roadmap.md](./docs/roadmap.md)** - Older phase-based roadmap (predates Operation Bidi; being superseded)
- **[dsl-project-summary.md](./docs/dsl-project-summary.md)** - Original vision document for Klein as a cross-platform expression language with algebraic effects

### Design Decisions

See [docs/decisions/](./docs/decisions/) for the full set of ADRs. ADRs are immutable history; superseded ones carry a forward pointer to what replaced them.

**Current type-system direction:**

- **[2026-06-24-adopt-path-g.md](./docs/decisions/2026-06-24-adopt-path-g.md)** - **Current.** Local bidirectional checking — annotate signatures, infer interiors; drop global inference, keep subtyping.
- **[2026-06-23-polarity-wall-and-type-system-direction.md](./docs/decisions/2026-06-23-polarity-wall-and-type-system-direction.md)** - Why SimpleSub was abandoned: the polarity wall and the three ways out.

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

## Project Structure

```
klein-lang/
├── klein-lib/
│   ├── src/
│   │   ├── commonMain/kotlin/klein/
│   │   │   ├── Lexer.kt          # Tokenization
│   │   │   ├── Parser.kt         # Parsing
│   │   │   ├── Ast.kt            # AST definitions
│   │   │   ├── Token.kt          # Token types
│   │   │   ├── SourceSpan.kt     # Source location tracking
│   │   │   ├── PrettyPrint.kt    # AST pretty-printing
│   │   │   ├── Klein.kt          # Library entry (lex → parse → check)
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
│   │   │   └── check/
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

## Implementation Status

See **[implementation-status.md](./docs/implementation-status.md)** for current status across parser, type system, and interpreter.
