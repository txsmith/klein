# Klein Language

**A tiny, safe expression language for embedding customizable business rules.**

Klein is designed to let tech-savvy business users write rules, validations, and simple programs that your application executes. Programs are pure expressions with suspendable effects—they can't access the network or modify state directly, only use what the host application provides.

## Documentation

See [docs/](./docs/) for design docs and specifications.

Also see:
- [README.md](./README.md) - Project overview and examples

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
## Implemented Features

### Fully Working
- ✅ Function definitions (`fun add(x, y) = x + y`)
- ✅ If-then-else expressions
- ✅ Lambdas (nested, multi-param, with blocks)
- ✅ Function application
- ✅ Field access and implicit parameters (`user.name`, `|.price|`, `|.|`)
- ✅ Record literals (`{ name = 'Alice', age = 30 }`, shorthand `{ name }`, trailing comma)
- ✅ Binary operators (+, -, \*, /, %, ==, !=, <, <=, >, >=, and, or)
- ✅ Unary operators (-, not)
- ✅ Val bindings
- ✅ Blocks with statements
- ✅ String literals with escape sequences
- ✅ Integer and double literals
- ✅ Comments
- ✅ Significant indentation
