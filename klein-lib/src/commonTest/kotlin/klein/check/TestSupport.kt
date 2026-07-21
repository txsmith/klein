package klein.check

import klein.surface.Lexer
import klein.surface.Parser
import klein.check.Type.*
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Result of type-checking a source string through the [Checker]. */
data class InferResult(val type: Type, val errors: List<TypeError>)

/** Parse [src] and run the bidirectional checker over it. Shared by all `klein.check` test suites. */
fun infer(
    src: String,
    env: TypeEnv = TypeEnv.empty(),
): InferResult {
    val tokens = Lexer(src).tokenize().toList()
    val program = Parser(tokens).parseProgram()
    val checker = Checker()
    return InferResult(checker.synthProgram(program, env), checker.getErrors())
}

/** A type variable for expected types; alpha-equality compares skolems by name, ignoring the id. */
fun tv(name: String): TSkolem = TSkolem(name, 0)

/**
 * Structural type equality up to skolem renaming: skolems match by name (ignoring their id), a
 * `TForall` is compared through its body, and value-level `paramNames` are ignored.
 */
fun Type.alphaEquals(other: Type): Boolean {
    val a = if (this is TForall) body else this
    val b = if (other is TForall) other.body else other
    return when {
        a is TSkolem && b is TSkolem -> a.name == b.name
        a is TFun && b is TFun ->
            a.params.size == b.params.size &&
                a.params.zip(b.params).all { (p, q) -> p.alphaEquals(q) } &&
                a.result.alphaEquals(b.result)
        a is TRecord && b is TRecord ->
            a.fields.keys == b.fields.keys && a.fields.all { (k, v) -> v.alphaEquals(b.fields.getValue(k)) }
        a is TRef && b is TRef ->
            a.name == b.name &&
                a.typeArgs.size == b.typeArgs.size &&
                a.typeArgs.zip(b.typeArgs).all { (p, q) -> p.alphaEquals(q) }
        a is TOptional && b is TOptional -> a.type.alphaEquals(b.type)
        else -> a == b
    }
}

/** Assert [src] is rejected with exactly one mismatch of [subtype] against [supertype]. */
fun assertMismatch(
    subtype: String,
    supertype: String,
    src: String,
) {
    val e = infer(src).errors.single()
    assertIs<TypeError.TypeMismatch>(e)
    assertEquals(subtype, Type.print(e.subtype))
    assertEquals(supertype, Type.print(e.supertype))
}

/** Assert [src] checks cleanly and its type is [expected], up to skolem renaming. */
fun assertInfersType(
    expected: Type,
    src: String,
) {
    val r = infer(src)
    assertTrue(r.errors.isEmpty(), "unexpected errors: ${r.errors}")
    assertTrue(
        expected.alphaEquals(r.type),
        "expected ${Type.print(expected)}, got ${Type.print(r.type)}",
    )
}

