package klein

import klein.surface.*
import klein.check.Type
import klein.check.TypeEnv
import klein.interp.Value
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
            "check", "c", "run", "r" -> setOf("--stdin", "--raw")
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
        "run", "r" -> {
            val source = getSource(useStdin, fileArg) ?: return
            run(source, rawErrors)
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
          run, r       Type-check, then evaluate; print the program's value
          help         Show this help

        Options:
          --stdin      Read from stdin instead of file
          --raw        Print raw errors with SourceSpan (for tooling)
          --verbose    Show nesting stack on lexer errors (tokens, parse)
        """.trimIndent(),
    )
}

/**
 * Print every error from a stage result uniformly, plus any verbose stage-specific detail,
 * and exit non-zero. No-op when the result is clean.
 */
private fun exitOnErrors(
    result: StageResult<*>,
    source: String,
    rawErrors: Boolean,
    verbose: Boolean = false,
) {
    if (!result.hasErrors) return
    for (error in result.errors) {
        printError(source, error.span, error.message, rawErrors)
        if (verbose && error is LexerError && error.nestingStack.isNotEmpty()) {
            println("\nNesting stack:")
            error.nestingStack.forEach { println("  $it") }
        }
        if (verbose && error is ParseError) {
            println("\nCall stack:")
            printFormattedStackTrace(error)
        }
    }
    exitProcess(1)
}

private fun tokenize(
    source: String,
    rawOutput: Boolean,
    verbose: Boolean,
) {
    val result = Klein.tokenize(source)
    exitOnErrors(result, source, rawOutput, verbose)
    for (token in result.output!!) {
        println(token.prettyPrint())
    }
}

private fun parse(
    source: String,
    rawOutput: Boolean,
    verbose: Boolean,
) {
    val result = Klein.tokenize(source).andThen(Klein::parse)
    exitOnErrors(result, source, rawOutput, verbose)
    for (stmt in result.output!!.stmts) {
        println(stmt.prettyPrint())
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
    val parsed = Klein.tokenize(source).andThen(Klein::parse)
    exitOnErrors(parsed, source, rawErrors)
    val program = parsed.output!!

    val env = TypeEnv.empty()
    val checked = Klein.check(program, env)

    for (stmt in program.stmts) {
        when (stmt) {
            is Val -> env.lookup(stmt.name)?.let { println("${stmt.name} : ${Type.print(it)}") }
            is PatternVal ->
                stmt.pattern.boundNames.forEach { name ->
                    env.lookup(name)?.let { println("$name : ${Type.print(it)}") }
                }
            is FunDef -> env.lookup(stmt.name)?.let { println("${stmt.name} : ${Type.print(it)}") }
            is TypeDef -> println("type ${stmt.name}")
            is Expr -> {} // trailing expression handled below; interior ones carry no recorded type
        }
    }
    (program.stmts.lastOrNull() as? Expr)?.let { expr ->
        val exprSource = source.substring(expr.span.start, expr.span.end)
        println("$exprSource : ${Type.print(checked.output!!)}")
    }

    exitOnErrors(checked, source, rawErrors)
    println("✓ Type checks")
}

/**
 * The full pipeline: tokenize, parse, type-check, evaluate, print the resulting value.
 * Errors from any stage print uniformly and exit non-zero.
 */
private fun run(
    source: String,
    rawErrors: Boolean,
) {
    val result =
        Klein
            .tokenize(source)
            .andThen(Klein::parse)
            .andThen { program -> Klein.check(program).andThen { Klein.interpret(program) } }
    exitOnErrors(result, source, rawErrors)
    println(Value.print(result.output!!))
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
