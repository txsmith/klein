package klein

import klein.types.SimpleType
import klein.types.TypeEnv
import klein.types.TypePrinter
import klein.types.TypeSimplifier
import klein.types.Typer
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

enum class TypeFormat { CANONICAL, PRE_CANONICAL, IR_COMPACT, IR_BOUNDS }

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printUsage()
        return
    }

    val command = args[0]
    val rawErrors = "--raw" in args
    val verbose = "--verbose" in args || "-v" in args
    val useStdin = "--stdin" in args
    val fileArg = args.drop(1).firstOrNull { !it.startsWith("--") }

    val typeFormat =
        when {
            "--canonical" in args -> TypeFormat.CANONICAL
            "--pre-canonical" in args -> TypeFormat.PRE_CANONICAL
            "--ir-compact" in args -> TypeFormat.IR_COMPACT
            "--ir-bounds" in args -> TypeFormat.IR_BOUNDS
            else -> TypeFormat.CANONICAL
        }

    when (command) {
        "tokens", "t" -> {
            val source = getSource(useStdin, fileArg) ?: return
            tokenize(source, rawErrors, verbose)
        }
        "parse", "p" -> {
            val source = getSource(useStdin, fileArg) ?: return
            parse(source, rawErrors, verbose)
        }
        "infer", "i" -> {
            val source = getSource(useStdin, fileArg) ?: return
            infer(source, rawErrors, typeFormat)
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

        Type output format (for infer):
          --canonical      Canonical type (default, merges recursive types)
          --pre-canonical  Simplified type before canonicalization
          --ir-compact     Internal CompactType representation
          --ir-bounds      Internal SimpleType with bounds
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

private fun infer(
    source: String,
    rawErrors: Boolean,
    format: TypeFormat,
) {
    try {
        val tokens = Lexer(source).tokenize().toList()
        val program = Parser(tokens).parseProgram()
        val result = Typer.infer(program, TypeEnv.empty())

        fun formatType(type: SimpleType): String =
            when (format) {
                TypeFormat.CANONICAL -> Type.print(TypeSimplifier.simplifyCanonical(type))
                TypeFormat.PRE_CANONICAL -> Type.print(TypeSimplifier.simplify(type))
                TypeFormat.IR_COMPACT -> TypeSimplifier.simplify(type).toString()
                TypeFormat.IR_BOUNDS -> TypePrinter.printRaw(type)
            }

        for (stmt in program.stmts) {
            when (stmt) {
                is Val -> {
                    val type = result.env.lookupAndInstantiate(stmt.name)!!
                    println("${stmt.name} : ${formatType(type)}")
                }
                is FunDef -> {
                    val type = result.env.lookupAndInstantiate(stmt.name)!!
                    println("${stmt.name} : ${formatType(type)}")
                }
                is TypeDef -> {
                    println("type ${stmt.name}")
                }
                is Expr -> {
                    val type = result.exprTypes[stmt.span] ?: result.type
                    val exprSource = source.substring(stmt.span.start, stmt.span.end)
                    println("$exprSource : ${formatType(type)}")
                }
            }
        }

        for (error in result.errors) {
            printError(source, error.span, error.message, rawErrors)
        }
    } catch (e: LexerError) {
        printError(source, e.span, e.message ?: "Lexer error", rawErrors)
    } catch (e: ParseError) {
        printError(source, e.span, e.message ?: "Parse error", rawErrors)
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
