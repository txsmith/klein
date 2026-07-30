package klein.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CorePrettyPrintTest {
    @Test
    fun numLiteralPrintsAsWholeWhenIntegral() = assertEquals("42", CorePrinter.print(num(42.0)))

    @Test
    fun numLiteralKeepsFraction() = assertEquals("3.5", CorePrinter.print(num(3.5)))

    @Test
    fun stringLiteralIsQuoted() = assertEquals("\"hi\"", CorePrinter.print(str("hi")))

    @Test
    fun boolLiteral() = assertEquals("true", CorePrinter.print(bool(true)))

    @Test
    fun nullLiteral() = assertEquals("null", CorePrinter.print(nul()))

    @Test
    fun unitLiteral() = assertEquals("unit", CorePrinter.print(unit()))

    @Test
    fun variableShowsNameDepthAndSlot() = assertEquals("x[1;2]", CorePrinter.print(v(1, 2, "x")))

    @Test
    fun namedLambda() = assertEquals("fun id/1 -> x[0;0]", CorePrinter.print(lam(1, v(0, 0, "x"), "id")))

    @Test
    fun anonymousLambda() = assertEquals("fun/2 -> x[0;0]", CorePrinter.print(lam(2, v(0, 0, "x"))))

    @Test
    fun application() = assertEquals("f[0;0](1, 2)", CorePrinter.print(app(v(0, 0, "f"), num(1.0), num(2.0))))

    @Test
    fun lambdaCalleeIsParenthesized() =
        assertEquals("(fun/1 -> x[0;0])(1)", CorePrinter.print(app(lam(1, v(0, 0, "x")), num(1.0))))

    @Test
    fun primApplicationIsInfix() = assertEquals("(1 + 2)", CorePrinter.print(prim(PrimOp.Add, num(1.0), num(2.0))))

    @Test
    fun taggedData() =
        assertEquals(
            "Cons{head: 1, tail: nil[0;0]}",
            CorePrinter.print(mk("Cons", "head" to num(1.0), "tail" to v(0, 0, "nil"))),
        )

    @Test
    fun recordDataHasNoTag() =
        assertEquals(
            "{x: 1, y: 2}",
            CorePrinter.print(mk(null, "x" to num(1.0), "y" to num(2.0))),
        )

    @Test
    fun fieldGet() = assertEquals("p[0;0].x", CorePrinter.print(get(v(0, 0, "p"), "x")))

    @Test
    fun hostCall() = assertEquals("host log(\"hi\")", CorePrinter.print(host("log", str("hi"))))

    @Test
    fun scopeIsMultiline() =
        assertEquals(
            """
            scope
              bind x#0 = 1
              run host log()
              x[0;0]
            """.trimIndent(),
            CorePrinter.print(scope(bind(0, num(1.0), "x"), stmt(host("log")), result = v(0, 0, "x"))),
        )

    @Test
    fun matchIsMultiline() =
        assertEquals(
            """
            match s[0;0]
              Some{v} -> v[0;1]
              _ -> 0
            """.trimIndent(),
            CorePrinter.print(
                match(
                    v(0, 0, "s"),
                    ctorArm("Some", listOf("v"), v(0, 1, "v")),
                    default(num(0.0)),
                ),
            ),
        )

    @Test
    fun litArmWithGuard() =
        assertEquals(
            """
            match n[0;0]
              lit 0 if (n[0;0] < 1) -> "zero"
            """.trimIndent(),
            CorePrinter.print(
                match(
                    v(0, 0, "n"),
                    litArm(Constant.CNum(0.0), str("zero"), prim(PrimOp.Lt, v(0, 0, "n"), num(1.0))),
                ),
            ),
        )

    @Test
    fun nestedScopeIndentsFurther() =
        assertEquals(
            """
            scope
              bind outer#0 = scope
                bind a#0 = 1
                a[0;0]
              r[0;0]
            """.trimIndent(),
            CorePrinter.print(
                scope(
                    bind(0, scope(bind(0, num(1.0), "a"), result = v(0, 0, "a")), "outer"),
                    result = v(0, 0, "r"),
                ),
            ),
        )
}
