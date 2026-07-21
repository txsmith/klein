package klein.interp

import klein.Klein
import klein.interp.Value.VBool
import klein.interp.Value.VNull
import klein.interp.Value.VNum
import klein.interp.Value.VStr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Evaluate [src] through the full pipeline; any stage error fails the test — except a
 * runtime error, which is rethrown so tests can assert on it. */
fun run(src: String): Value {
    val parsed = Klein.tokenize(src.trimIndent()).andThen(Klein::parse)
    check(!parsed.hasErrors) { "test program does not parse: ${parsed.errors}" }
    val program = parsed.output!!
    val checked = Klein.check(program)
    check(!checked.hasErrors) { "type errors in test program: ${checked.errors}" }
    return Interpreter().run(program)
}

fun assertEvaluatesTo(
    expected: Value,
    src: String,
) = assertEquals(expected, run(src))

class InterpreterTest {
    @Test
    fun literals() {
        assertEvaluatesTo(VNum(42.0), "42")
        assertEvaluatesTo(VNum(2.5), "2.5")
        assertEvaluatesTo(VStr("hello"), "\"hello\"")
        assertEvaluatesTo(VBool(true), "true")
        assertEvaluatesTo(VNull, "null")
    }

    @Test
    fun arithmetic() {
        assertEvaluatesTo(VNum(7.0), "1 + 2 * 3")
        assertEvaluatesTo(VNum(2.5), "10 / 4")
        assertEvaluatesTo(VNum(1.0), "7 % 3")
        assertEvaluatesTo(VNum(-5.0), "-(2 + 3)")
    }

    @Test
    fun comparisonAndLogic() {
        assertEvaluatesTo(VBool(true), "1 < 2 and 2 <= 2")
        assertEvaluatesTo(VBool(true), "3 > 2 or false")
        assertEvaluatesTo(VBool(false), "not (1 == 1)")
        assertEvaluatesTo(VBool(true), "1 != 2")
    }

    @Test
    fun equalityIsStructural() {
        assertEvaluatesTo(VBool(true), "\"a\" == \"a\"")
        assertEvaluatesTo(VBool(true), "{ x = 1 } == { x = 1 }")
        assertEvaluatesTo(VBool(false), "{ x = 1 } == { x = 2 }")
        assertEvaluatesTo(
            VBool(true),
            """
            type Color = Red | Green
            Red == Red
            """,
        )
    }

    @Test
    fun shortCircuit() {
        // The right operand would divide by zero; short-circuiting must skip it.
        assertEvaluatesTo(VBool(true), "true or (1 / 0 > 0)")
        assertEvaluatesTo(VBool(false), "false and (1 / 0 > 0)")
    }

    @Test
    fun divisionByZeroFailsFast() {
        val e = assertFailsWith<KleinRuntimeError> { run("1 / 0") }
        assertEquals("Division by zero", e.message)
        assertFailsWith<KleinRuntimeError> { run("1 % 0") }
    }

    @Test
    fun ifThenElse() {
        assertEvaluatesTo(VNum(1.0), "if 2 > 1 then 1 else 2")
        assertEvaluatesTo(VNum(2.0), "if 2 < 1 then 1 else 2")
        assertEvaluatesTo(VNull, "if false then 1")
    }

    @Test
    fun bindingsAndBlocks() {
        assertEvaluatesTo(
            VNum(30.0),
            """
            x = 10
            y = x * 2
            x + y
            """,
        )
    }

    @Test
    fun lambdasAndClosures() {
        assertEvaluatesTo(VNum(42.0), "|x: Num -> x + 1|(41)")
        assertEvaluatesTo(
            VNum(15.0),
            """
            fun makeAdder(n: Num): (Num) -> Num = |x -> x + n|
            addTen = makeAdder(10)
            addTen(5)
            """,
        )
    }

    @Test
    fun thunk() {
        assertEvaluatesTo(VNum(42.0), "|42|()")
    }

    @Test
    fun implicitParam() {
        assertEvaluatesTo(
            VBool(true),
            """
            big: (Num) -> Bool = |. > 100|
            big(101)
            """,
        )
        assertEvaluatesTo(
            VNum(3.0),
            """
            f: ({ x: Num, y: Num }) -> Num = |.x + .y|
            f({ x = 1, y = 2 })
            """,
        )
    }

    @Test
    fun recursion() {
        assertEvaluatesTo(
            VNum(120.0),
            """
            fun fact(n: Num): Num = if n <= 1 then 1 else n * fact(n - 1)
            fact(5)
            """,
        )
    }

    @Test
    fun mutualRecursion() {
        assertEvaluatesTo(
            VBool(true),
            """
            fun isEven(n: Num): Bool = if n == 0 then true else isOdd(n - 1)
            fun isOdd(n: Num): Bool = if n == 0 then false else isEven(n - 1)
            isEven(10)
            """,
        )
    }

    @Test
    fun valMayReferenceLaterFunction() {
        // Bindings resolve in dependency order, not textual order, mirroring the checker.
        assertEvaluatesTo(
            VNum(6.0),
            """
            x = double(3)
            fun double(n: Num): Num = n * 2
            x
            """,
        )
    }

    @Test
    fun recordsAndFieldAccess() {
        assertEvaluatesTo(
            VNum(3.0),
            """
            p = { x = 1, y = 2 }
            p.x + p.y
            """,
        )
    }

    @Test
    fun safeFieldAccess() {
        assertEvaluatesTo(
            VNull,
            """
            fun pick(r: { x: Num }?): Num? = r?.x
            pick(null)
            """,
        )
        assertEvaluatesTo(
            VNum(1.0),
            """
            fun pick(r: { x: Num }?): Num? = r?.x
            pick({ x = 1 })
            """,
        )
    }

    @Test
    fun constructors() {
        val v =
            run(
                """
                type Shape = Circle { radius: Num } | Point
                Circle(3)
                """,
            )
        assertIs<Value.VData>(v)
        assertEquals("Circle", v.constructorName)
        assertEquals("Shape", v.typeName)
        assertEquals(mapOf("radius" to VNum(3.0)), v.fields)

        assertEvaluatesTo(
            VNum(3.0),
            """
            type Shape = Circle { radius: Num } | Point
            Circle(3).radius
            """,
        )
    }

    @Test
    fun nullaryConstructorIsAValue() {
        val v =
            run(
                """
                type Shape = Circle { radius: Num } | Point
                Point
                """,
            )
        assertIs<Value.VData>(v)
        assertTrue(v.fields.isEmpty())
    }

    @Test
    fun genericConstructors() {
        assertEvaluatesTo(
            VNum(1.0),
            """
            type List<'A> = Cons { head: 'A, tail: List<'A> } | Nil
            Cons(1, Nil).head
            """,
        )
    }

    @Test
    fun ascriptionIsTransparent() {
        assertEvaluatesTo(VNum(1.0), "(1 : Num)")
    }

    @Test
    fun blockValueIsLastExpression() {
        assertEvaluatesTo(
            VNum(9.0),
            """
            fun f(n: Num): Num =
                m = n * n
                m
            f(3)
            """,
        )
    }

    @Test
    fun hostNativeFunctions() {
        // The host seam: a native bound in the value env, typed in the type env.
        val typeEnv = klein.check.TypeEnv.empty()
        typeEnv.bind("twice", klein.check.Type.TFun(listOf(klein.check.Type.TNum), klein.check.Type.TNum))
        val bindings = mapOf("twice" to Value.VNative("twice", 1))
        val result =
            Klein
                .tokenize("twice(21)")
                .andThen(Klein::parse)
                .andThen { program ->
                    Klein.check(program, typeEnv).andThen {
                        Klein.interpret(program, bindings) { call -> VNum((call.args[0] as VNum).value * 2) }
                    }
                }
        assertEquals(emptyList(), result.errors)
        assertEquals(VNum(42.0), result.output)
    }

    @Test
    fun valuePrinting() {
        assertEquals("42", Value.print(run("42")))
        assertEquals("2.5", Value.print(run("2.5")))
        assertEquals("\"hi\"", Value.print(run("\"hi\"")))
        assertEquals("{ x = 1 }", Value.print(run("{ x = 1 }")))
        assertEquals(
            "Circle(1)",
            Value.print(
                run(
                    """
                    type Shape = Circle { radius: Num } | Point
                    Circle(1)
                    """,
                ),
            ),
        )
    }
}
