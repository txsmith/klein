package klein

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
    val rawOutput = "--raw" in args
    val verbose = "--verbose" in args || "-v" in args
    val useStdin = "--stdin" in args
    val fileArg = args.drop(1).firstOrNull { !it.startsWith("--") }

    when (command) {
        "tokens", "t" -> {
            val source = getSource(useStdin, fileArg) ?: return
            tokenize(source, rawOutput, verbose)
        }
        "parse", "p" -> {
            val source = getSource(useStdin, fileArg) ?: return
            parse(source, rawOutput, verbose)
        }
        "infer", "i" -> {
            val source = getSource(useStdin, fileArg) ?: return
            infer(source, rawOutput)
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
          infer, i     Infer and print types
          help         Show this help

        Options:
          --stdin      Read from stdin instead of file
          --raw        Print raw errors with SourceSpan (for tooling)
          --verbose    Show nesting stack on lexer errors
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
        for (stmt in program) {
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

private fun infer(
    source: String,
    rawOutput: Boolean,
) {
    try {
        val tokens = Lexer(source).tokenize().toList()
        val program = Parser(tokens).parseProgram()
        val typeGen = TypeGen()
        val env = TypeEnv.empty()

        // Infer types for each statement
        for (stmt in program) {
            when (stmt) {
                is Val -> {
                    val valueType = typeGen.infer(stmt.value, env)
                    env.bind(stmt.name, valueType)
                    println("${stmt.name} : ${TypePrinter.print(valueType)}")
                }
                is FunDef -> {
                    // For now, just report that function definitions need more phases
                    throw NotImplementedError("Phase 6: Function definitions")
                }
                is Expr -> {
                    val exprType = typeGen.infer(stmt, env)
                    println(": ${TypePrinter.print(exprType)}")
                }
            }
        }

        // Report any errors after inference
        if (typeGen.hasErrors()) {
            for (error in typeGen.getErrors()) {
                printError(source, error.span, error.message, rawOutput)
            }
        }
    } catch (e: LexerError) {
        printError(source, e.span, e.message ?: "Lexer error", rawOutput)
    } catch (e: ParseError) {
        printError(source, e.span, e.message ?: "Parse error", rawOutput)
    } catch (e: NotImplementedError) {
        println("Type inference not yet implemented: ${e.message}")
    }
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
