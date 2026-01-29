# Klein Language

**A tiny, safe expression language for embedding customizable business rules.**

Klein is designed to let tech-savvy business users write rules, validations, and simple programs that your application executes. Programs are pure expressions with suspendable effects—they can't access the network or modify state directly, only use what the host application provides.

## Documentation

### Core Documentation

- **[grammar.md](./docs/grammar.md)** - Complete formal grammar for Klein expressions and types, including indentation rules, operator precedence, and parser method mappings
- **[reference.md](./docs/reference.md)** - Complete language reference with syntax, examples, and usage patterns for all Klein features
- **[type-system.md](./docs/type-system.md)** - Type system design: structural vs nominal typing, row polymorphism, Hindley-Milner inference, and the tilde operator
- **[calling-conventions.md](./docs/calling-conventions.md)** - Function definitions, positional arguments, records, tuples, extension methods, and the tilde operator

### Implementation Guides

- **[implementation-status.md](./docs/implementation-status.md)** - Current implementation status across parser, type system, and interpreter
- **[roadmap.md](./docs/roadmap.md)** - Development roadmap with work units organized by phase (expressions, types, advanced features, type system, execution)
- **[dsl-project-summary.md](./docs/dsl-project-summary.md)** - Original vision document for Klein as a cross-platform expression language with algebraic effects

### Design Decisions

See [docs/decisions/](./docs/decisions/) for architecture decision records:

- **[positional-function-syntax.md](./docs/decisions/2026-01-09-positional-function-syntax.md)** - Why Klein uses positional function arguments instead of record-based calling
- **[records-as-interfaces.md](./docs/decisions/2026-01-09-records-as-interfaces.md)** - How records with function fields serve as structural interfaces
- **[fail-fast-error-handling.md](./docs/decisions/2026-01-09-fail-fast-error-handling.md)** - Fail-fast by default with opt-in recovery via `.recover`
- **[modules-vs-records.md](./docs/decisions/2026-01-09-modules-vs-records.md)** - Design decisions around module system
- **[no-anonymous-unions.md](./docs/decisions/2026-01-09-no-anonymous-unions.md)** - Why Klein doesn't support anonymous union types
- **[simplesub-type-inference.md](./docs/decisions/2026-01-14-simplesub-type-inference.md)** - Direct port of SimpleSub reference implementation for type inference
- **[variance-inference.md](./docs/decisions/2026-01-27-variance-inference.md)** - Automatic variance inference for type parameters, phantom type handling
- **[inferred-interfaces.md](./docs/decisions/2026-01-27-inferred-interfaces.md)** - Sum types automatically expose common fields as structural interfaces
- **[nominal-structural-subtyping.md](./docs/decisions/2026-01-27-nominal-structural-subtyping.md)** - Asymmetric subtyping: nominal → structural but not the reverse

### Experimental Features

- **[kleene-types-experimental.md](./docs/kleene-types-experimental.md)** - Research feature: cardinality-aware types (T, T?, T+, T\*) with Hindley-Milner inference

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

### Infer Types

```bash
# From a file
./klein infer example.klein

# From stdin
echo "x = 1 + 2" | ./klein infer --stdin

# Short form
./klein i example.klein

# Raw output (for tooling)
./klein infer --raw example.klein

# Type format options (for debugging)
./klein infer --canonical example.klein      # default: canonicalized types
./klein infer --pre-canonical example.klein  # non-canonicalized types
./klein infer --ir-compact example.klein     # CompactTypeScheme representation
./klein infer --ir-bounds example.klein      # SimpleType with bounds
```

## Project Structure

```
klein-lang/
├── klein-lib/
│   ├── src/
│   │   ├── commonMain/kotlin/klein/
│   │   │   ├── Lexer.kt        # Tokenization
│   │   │   ├── Parser.kt       # Parsing
│   │   │   ├── Ast.kt          # AST definitions
│   │   │   ├── Token.kt        # Token types
│   │   │   └── SourceSpan.kt   # Source location tracking
│   │   ├── commonTest/kotlin/klein/
│   │   │   ├── lexer/
│   │   │   └── parser/
│   │   └── nativeMain/kotlin/klein/
│   │       └── Main.kt         # CLI entry point
│   └── build.gradle.kts
├── docs/                       # Design docs
├── README.md                   # Project overview
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
