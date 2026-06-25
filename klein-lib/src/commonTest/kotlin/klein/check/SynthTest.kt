package klein.check

import klein.Lexer
import klein.Parser
import klein.check.Type.*
import klein.types.TypeError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Result of type-checking a source string through the new [Checker]. */
data class InferResult(val type: Type, val errors: List<TypeError>)

/** Parse [src] and run the bidirectional checker over it. */
fun infer(src: String): InferResult {
    val tokens = Lexer(src).tokenize().toList()
    val program = Parser(tokens).parseProgram()
    val checker = Checker()
    val type = checker.synthProgram(program)
    return InferResult(type, checker.getErrors())
}

/**
 * Pure-synth tests: the subset of the checker that does not bottom out in `isSubtype`
 * (literals, ident lookup, record literals/field access, blocks, annotated lambdas).
 * Operators, `if`, application, ascriptions, and annotated `val`s are blocked on `isSubtype` (M3).
 */
class SynthTest {
    @Test
    fun intLiteral() = assertEquals(TNum, infer("42").type)

    @Test
    fun doubleLiteral() = assertEquals(TNum, infer("3.14").type)

    @Test
    fun stringLiteral() = assertEquals(TStr, infer("\"hi\"").type)

    @Test
    fun boolLiteral() = assertEquals(TBool, infer("true").type)

    @Test
    fun recordLiteral() =
        assertEquals(TRecord(mapOf("a" to TNum, "b" to TBool)), infer("{ a = 1, b = true }").type)

    @Test
    fun fieldAccess() = assertEquals(TNum, infer("{ a = 1 }.a").type)

    @Test
    fun bindingThenIdent() = assertEquals(TNum, infer("x = 1\nx").type)

    @Test
    fun annotatedLambda() =
        assertEquals(TFun(listOf(TNum), TNum, listOf("x")), infer("|x: Num -> x|").type)

    @Test
    fun validSynthHasNoErrors() = assertTrue(infer("{ a = 1 }.a").errors.isEmpty())
}
