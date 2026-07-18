package klein

import klein.check.Checker
import klein.check.Type
import klein.check.TypeEnv
import kotlin.system.exitProcess
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.SEEK_END
import platform.posix.fclose
import platform.posix.fgets
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.rewind
import platform.posix.stdin

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printUsage()
        return
    }

    val command = args[0]
    // Each command accepts its own flags; help/unknown commands (null) skip flag validation.
    val knownFlags: Set<String>? =
        when (command) {
            "tokens", "t", "parse", "p" -> setOf("--stdin", "--raw", "--verbose", "-v")
            "check", "c" -> setOf("--stdin", "--raw")
            else -> null
        }
    if (knownFlags != null) {
        val unknownFlags = args.drop(1).filter { it.startsWith("-") && it !in knownFlags }
        if (unknownFlags.isNotEmpty()) {
            println("Unknown option(s) for '$command': ${unknownFlags.joinToString(", ")}")
            printUsage()
            return
        }
    }

    val rawErrors = "--raw" in args
    val verbose = "--verbose" in args || "-v" in args
    val useStdin = "--stdin" in args
    val fileArg = args.drop(1).firstOrNull { !it.startsWith("-") }

    when (command) {
        "tokens", "t" -> {
            val source = getSource(useStdin, fileArg) ?: return
            tokenize(source, rawErrors, verbose)
        }
        "parse", "p" -> {
            val source = getSource(useStdin, fileArg) ?: return
            parse(source, rawErrors, verbose)
        }
        "check", "c" -> {
            val source = getSource(useStdin, fileArg) ?: return
            check(source, rawErrors)
        }
        "help", "-h", "--help" -> printUsage()
        else -> {
            println("Unknown command: $command")
            printUsage()
        }
    }
}

private fun getSource(
    useStdin: Boolean,
    fileArg: String?,
): String? =
    if (useStdin) {
        readStdin()
    } else if (fileArg != null) {
        readFile(fileArg)
    } else {
        println("Error: missing file argument or --stdin")
        printUsage()
        null
    }

private fun printUsage() {
    println(
        """
        Usage: klein <command> <file|--stdin> [options]

        Commands:
          tokens, t    Tokenize and print tokens
          parse, p     Parse and print AST
          check, c     Type-check with the Operation Bidi checker; print types and pass/fail
          help         Show this help

        Options:
          --stdin      Read from stdin instead of file
          --raw        Print raw errors with SourceSpan (for tooling)
          --verbose    Show nesting stack on lexer errors (tokens, parse)
        """.trimIndent(),
    )
}

private fun tokenize(
    source: String,
    rawOutput: Boolean,
    verbose: Boolean,
) {
    try {
        val tokens = Lexer(source).tokenize()
        for (token in tokens) {
            println(token.prettyPrint())
        }
    } catch (e: LexerError) {
        printError(source, e.span, e.message ?: "Lexer error", rawOutput)
        if (verbose && e.nestingStack.isNotEmpty()) {
            println("\nNesting stack:")
            e.nestingStack.forEach { println("  $it") }
        }
    }
}

private fun parse(
    source: String,
    rawOutput: Boolean,
    verbose: Boolean,
) {
    try {
        val tokens = Lexer(source).tokenize().toList()
        val program = Parser(tokens).parseProgram()
        for (stmt in program.stmts) {
            println(stmt.prettyPrint())
        }
    } catch (e: LexerError) {
        printError(source, e.span, e.message ?: "Lexer error", rawOutput)
        if (verbose && e.nestingStack.isNotEmpty()) {
            println("\nNesting stack:")
            e.nestingStack.forEach { println("  $it") }
        }
    } catch (e: ParseError) {
        printError(source, e.span, e.message ?: "Parse error", rawOutput)
        if (verbose) {
            println("\nCall stack:")
            printFormattedStackTrace(e)
        }
    }
}

/**
 * Run the Operation Bidi bidirectional checker: print the type of each top-level binding (and the trailing
 * expression), then a pass/fail verdict. Exits non-zero when the program has type errors, so `check`
 * is usable as a gate in scripts.
 */
private fun check(
    source: String,
    rawErrors: Boolean,
) {
    val tokens: List<Token>
    val program: Program
    try {
        tokens = Lexer(source).tokenize().toList()
        program = Parser(tokens).parseProgram()
    } catch (e: LexerError) {
        printError(source, e.span, e.message ?: "Lexer error", rawErrors)
        exitProcess(1)
    } catch (e: ParseError) {
        printError(source, e.span, e.message ?: "Parse error", rawErrors)
        exitProcess(1)
    }

    val checker = Checker()
    val env = TypeEnv.empty()
    val lastType = checker.synthProgram(program, env)

    for (stmt in program.stmts) {
        when (stmt) {
            is Val -> env.lookup(stmt.name)?.let { println("${stmt.name} : ${Type.print(it)}") }
            is FunDef -> env.lookup(stmt.name)?.let { println("${stmt.name} : ${Type.print(it)}") }
            is TypeDef -> println("type ${stmt.name}")
            is Expr -> {} // trailing expression handled below; interior ones carry no recorded type
        }
    }
    (program.stmts.lastOrNull() as? Expr)?.let { expr ->
        val exprSource = source.substring(expr.span.start, expr.span.end)
        println("$exprSource : ${Type.print(lastType)}")
    }

    val errors = checker.getErrors()
    if (errors.isEmpty()) {
        println("✓ Type checks")
        return
    }
    for (error in errors) {
        printError(source, error.span, error.message, rawErrors)
    }
    exitProcess(1)
}

private fun printError(
    source: String,
    span: SourceSpan,
    message: String,
    rawOutput: Boolean,
) {
    if (rawOutput) {
        println("Error: $message at $span")
    } else {
        print(span.formatInSource(source, contextLines = 5, message = message))
    }
}

private fun printFormattedStackTrace(e: Throwable) {
    val pattern = Regex("""kfun:klein\.([^+]+).+/klein/(\w+\.kt):(\d+)""")
    val frames =
        e
            .stackTraceToString()
            .lines()
            .mapNotNull { line ->
                pattern.find(line)?.let { match ->
                    val (func, file, lineNum) = match.destructured
                    val funcName =
                        func
                            .replace(Regex("""Parser[.#]"""), "")
                            .replace("#internal", "")
                            .substringBefore("(")
                            .substringBefore("{")
                            .trim()
                    "$funcName:$lineNum"
                }
            }.filter { !it.startsWith("ParseError") }
            .reversed()

    println(frames.joinToString(" -> "))
}

@OptIn(ExperimentalForeignApi::class)
private fun readFile(path: String): String? {
    val file = fopen(path, "r")
    if (file == null) {
        println("Error: cannot open file '$path'")
        return null
    }

    fseek(file, 0, SEEK_END)
    val size = ftell(file).toInt()
    rewind(file)

    val buffer = ByteArray(size)
    fread(buffer.refTo(0), 1u, size.toULong(), file)
    fclose(file)

    return buffer.toKString()
}

@OptIn(ExperimentalForeignApi::class)
private fun readStdin(): String {
    val result = StringBuilder()
    val buffer = ByteArray(4096)
    while (true) {
        val line = fgets(buffer.refTo(0), buffer.size, stdin) ?: break
        result.append(line.toKString())
    }
    return result.toString()
}
