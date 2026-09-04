package klein

import klein.surface.*
import klein.check.Type
import klein.check.RuleEnv
import klein.check.TypeEnv
import klein.check.contract.ContractDeclaration
import klein.check.contract.EnvironmentContract
import klein.check.contract.UnknownRelease
import klein.core.CorePrinter
import klein.host.RunFailure
import klein.host.RunOutcome
import klein.host.implement
import klein.interp.KleinRuntimeError
import klein.interp.Value
import kotlin.system.exitProcess
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.SEEK_END
import platform.posix.STDIN_FILENO
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fgets
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.isatty
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
            "check", "c", "run", "r" -> setOf("--stdin", "--raw", "--contract", "--release")
            "core" -> setOf("--stdin", "--raw")
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
    val contractIndex = args.indexOf("--contract")
    val releaseIndex = args.indexOf("--release")
    val contractPath = if (contractIndex >= 0) args.getOrNull(contractIndex + 1) else null
    val releaseArg = if (releaseIndex >= 0) args.getOrNull(releaseIndex + 1) else null
    // Values consumed by --contract/--release must not also be read as the file argument.
    val consumedValues = setOfNotNull(contractIndex.takeIf { it >= 0 }?.plus(1), releaseIndex.takeIf { it >= 0 }?.plus(1))
    val fileArg = args.withIndex().drop(1).firstOrNull { (i, a) -> i !in consumedValues && !a.startsWith("-") }?.value

    when (command) {
        "tokens", "t" -> {
            val source = getSource(useStdin, fileArg) ?: return
            tokenize(source, rawErrors, verbose)
        }
        "parse", "p" -> {
            val source = getSource(useStdin, fileArg) ?: return
            parse(source, rawErrors, verbose)
        }
        "check", "c" -> checkCmd(useStdin, fileArg, contractPath, releaseArg, rawErrors)
        "run", "r" -> runCmd(useStdin, fileArg, contractPath, releaseArg, rawErrors)
        "core" -> {
            val source = getSource(useStdin, fileArg) ?: return
            core(source, rawErrors)
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
          core         Type-check, then lower to core IR and print it
          run, r       Type-check, then evaluate; print the program's value
          help         Show this help

        Options:
          --stdin           Read from stdin instead of file
          --raw             Print raw errors with SourceSpan (for tooling)
          --verbose         Show nesting stack on lexer errors (tokens, parse)
          --contract FILE   Check against a capability contract (check, run)
          --release N       Release to check the rule against (needs --contract)
        """.trimIndent(),
    )
}

/**
 * `check` with `--contract`: no rule file means check the contract alone and print its
 * declarations and releases; a rule file checks it against one release.
 */
private fun checkCmd(
    useStdin: Boolean,
    fileArg: String?,
    contractPath: String?,
    releaseArg: String?,
    rawErrors: Boolean,
) {
    if (contractPath == null) {
        if (releaseArg != null) {
            println("Error: --release requires --contract")
            exitProcess(1)
        }
        val source = getSource(useStdin, fileArg) ?: return
        check(source, rawErrors)
        return
    }
    val contract = loadContract(contractPath, rawErrors)
    if (fileArg == null && !useStdin) {
        printContractSummary(contract)
        return
    }
    val ruleSource = getSource(useStdin, fileArg) ?: return
    val release = parseReleaseNumber(contract, releaseArg)
    val type = withRuleDiagnostics(ruleSource, rawErrors) { contract.check(ruleSource, release) }
    println("rule : ${Type.print(type)}")
    println("✓ Type checks against release ${release.value}")
}

/**
 * `run` with `--contract`: compile the rule against the release into an edition, then run it with
 * the CLI itself as the host — every capability is answered by prompting the person at the
 * terminal for a Klein expression of the declared type. Without a terminal (piped input, or
 * `--stdin` consumed by the rule source), a suspension is an error naming the capability, so
 * scripts fail loudly instead of hanging on a read.
 **/
private fun runCmd(
    useStdin: Boolean,
    fileArg: String?,
    contractPath: String?,
    releaseArg: String?,
    rawErrors: Boolean,
) {
    if (contractPath == null) {
        if (releaseArg != null) {
            println("Error: --release requires --contract")
            exitProcess(1)
        }
        val source = getSource(useStdin, fileArg) ?: return
        run(source, rawErrors)
        return
    }
    val contract = loadContract(contractPath, rawErrors)
    val ruleSource = getSource(useStdin, fileArg) ?: return
    val release = parseReleaseNumber(contract, releaseArg)
    val edition = withRuleDiagnostics(ruleSource, rawErrors) { contract.compileRule(ruleSource, release) }
    val canPrompt = !useStdin && isatty(STDIN_FILENO) == 1
    val answers = mutableMapOf<String, Value>()
    val environment =
        contract.implement {
            declarations.forEach { d ->
                immediate("${d.name}/${d.revision.value}") { args -> prompt(contract, release, d, args, answers, canPrompt, rawErrors) }
            }
        }
    val outcome =
        try {
            withRuleDiagnostics(ruleSource, rawErrors) { environment.run(edition) }
        } catch (e: RunFailure) {
            printError(ruleSource, null, e.error.message, rawErrors)
            exitProcess(1)
        }
    when (outcome) {
        is RunOutcome.Completed -> println(Value.print(outcome.value))
        is RunOutcome.Failed -> {
            outcome.diagnostics.forEach { printError(ruleSource, it.span, it.message, rawErrors) }
            exitProcess(1)
        }
        is RunOutcome.Parked -> {
            printError(ruleSource, null, "parked at ${outcome.call.print()}", rawErrors)
            exitProcess(1)
        }
    }
}

private class UnanswerableCapability(
    call: String,
) : KleinError {
    override val message = "cannot answer '$call': interactive run needs a terminal, and stdin is not one"
    override val span: SourceSpan? = null
}

/**
 * Ask the person at the terminal to be the host for one capability call: print the call, read a
 * Klein expression, compile it against the declared answer type, evaluate it, and re-prompt with
 * the diagnostics on anything wrong. Answers are remembered per printed call, so a distinct
 * question is asked once.
 */
@OptIn(ExperimentalForeignApi::class)
private fun prompt(
    contract: EnvironmentContract,
    release: ReleaseNumber,
    declaration: ContractDeclaration,
    args: List<Value>,
    answers: MutableMap<String, Value>,
    canPrompt: Boolean,
    rawErrors: Boolean,
): Value {
    val call =
        if (declaration is ContractDeclaration.Function) {
            "${declaration.name}(${args.joinToString(", ") { Value.print(it) }})"
        } else {
            declaration.name
        }
    answers[call]?.let { return it }
    if (!canPrompt) throw KleinException(listOf(UnanswerableCapability(call)))
    while (true) {
        print("$call = ? ")
        fflush(null)
        val line = readLine() ?: throw KleinException(listOf(UnanswerableCapability(call)))
        try {
            val executed = Klein.execute(contract.compileValue(line, release, declaration.answerType))
            if (executed.hasErrors) {
                executed.errors.forEach { printError(line, it.span, it.message, rawErrors) }
                continue
            }
            val value = executed.output!!
            answers[call] = value
            return value
        } catch (e: KleinException) {
            e.errors.forEach { printError(line, it.span, it.message, rawErrors) }
        }
    }
}

/** Read and check a contract file, exiting non-zero with every diagnostic on failure. */
private fun loadContract(
    path: String,
    rawErrors: Boolean,
): EnvironmentContract {
    val source = readFile(path) ?: exitProcess(1)
    return try {
        Klein.checkContract(source)
    } catch (e: KleinException) {
        e.errors.forEach { printError(source, it.span, it.message, rawErrors) }
        exitProcess(1)
    }
}

/** [releaseArg] if given; otherwise the contract's one release, or an error if it has none or several. */
private fun parseReleaseNumber(
    contract: EnvironmentContract,
    releaseArg: String?,
): ReleaseNumber {
    if (releaseArg != null) {
        val n = releaseArg.toIntOrNull()
        if (n == null) {
            println("Error: --release must be a number, got '$releaseArg'")
            exitProcess(1)
        }
        return ReleaseNumber(n)
    }
    return when (contract.releases.size) {
        1 -> contract.releases.single()
        0 -> {
            println("Error: this contract has no releases; there is nothing to check a rule against")
            exitProcess(1)
        }
        else -> {
            println(
                "Error: --release required; this contract has releases " +
                    contract.releases.joinToString(", ") { it.value.toString() },
            )
            exitProcess(1)
        }
    }
}

/** Run a contract operation on [ruleSource], exiting non-zero with every diagnostic on failure. */
private fun <T> withRuleDiagnostics(
    ruleSource: String,
    rawErrors: Boolean,
    operation: () -> T,
): T = try {
    operation()
} catch (e: UnknownRelease) {
    println("Error: ${e.message}")
    exitProcess(1)
} catch (e: KleinException) {
    e.errors.forEach { printError(ruleSource, it.span, it.message, rawErrors) }
    exitProcess(1)
} catch (e: KleinRuntimeError) {
    printError(ruleSource, e.span, e.message, rawErrors)
    exitProcess(1)
}

private fun printContractSummary(contract: EnvironmentContract) {
    contract.declarations.forEach { d ->
        val kind = if (d is ContractDeclaration.Function) "fun" else "val"
        println("$kind ${revisioned(d.name, d.revision)} : ${Type.print(d.type)}")
    }
    println(
        if (contract.releases.isEmpty()) {
            "releases: none"
        } else {
            "releases: ${contract.releases.joinToString(", ") { it.value.toString() }}"
        },
    )
}

private fun revisioned(
    name: String,
    revision: RevisionNumber,
): String = if (revision.value == 1) name else "$name/${revision.value}"

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
 * Run the type checker: print the type of each top-level binding (and the trailing
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

    val env: RuleEnv = TypeEnv.empty()
    val checked = Klein.check(program, env)

    for (stmt in program.stmts) {
        when (stmt) {
            is Val -> env.lookup(stmt.name)?.let { println("${stmt.name} : ${Type.print(it)}") }
            is PatternVal ->
                stmt.pattern.boundNames.forEach { name ->
                    env.lookup(name)?.let { println("$name : ${Type.print(it)}") }
                }
            is FunDef -> env.lookup(stmt.name)?.let { println("${stmt.name} : ${Type.print(it)}") }
            is TypeDefStmt -> println("type ${stmt.typeDef.name}")
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
 * Lower a type-checked program to core IR and print it with [CorePrinter] — a "core dump" for
 * inspecting what the surface lowers to. Checks first (the lowerer assumes checked input), so type
 * errors gate the dump and print uniformly.
 */
private fun core(
    source: String,
    rawErrors: Boolean,
) {
    val result =
        Klein
            .tokenize(source)
            .andThen(Klein::parse)
            .andThen { program -> Klein.check(program).andThen { Klein.lower(program) } }
    exitOnErrors(result, source, rawErrors)
    println(CorePrinter.print(result.output!!))
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
            .andThen { program -> Klein.check(program).andThen { Klein.lower(program) } }
            .andThen(Klein::execute)
    exitOnErrors(result, source, rawErrors)
    println(Value.print(result.output!!))
}

private fun printError(
    source: String,
    span: SourceSpan?,
    message: String,
    rawOutput: Boolean,
) {
    if (span == null) {
        println("Error: $message")
    } else if (rawOutput) {
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
