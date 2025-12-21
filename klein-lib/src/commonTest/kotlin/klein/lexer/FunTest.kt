@file:Suppress("ktlint")

package klein.lexer

import klein.TokenKind.*
import kotlin.test.Test

class FunTest {
    @Test
    fun simpleFunctionDefinition() {
        assertTokens(
            "fun double(x) = x * 2",
            kw(FUN), ident("double"), sym('('), ident("x"), sym(')'),
            sym('='), ident("x"), sym('*'), num("2"),
            eof
        )
    }

    @Test
    fun functionWithMultipleParams() {
        assertTokens(
            "fun add(x, y) = x + y",
            kw(FUN), ident("add"), sym('('), ident("x"), sym(','), ident("y"), sym(')'),
            sym('='), ident("x"), sym('+'), ident("y"),
            eof
        )
    }

    @Test
    fun functionWithNoParams() {
        assertTokens(
            "fun thunk() = 42",
            kw(FUN), ident("thunk"), sym('('), sym(')'),
            sym('='), num("42"),
            eof
        )
    }

    @Test
    fun functionWithBlockBody() {
        val program = """
            fun calculate(a, b) =
              temp = a * 2
              temp + b
        """.trimIndent()
        assertTokens(
            program,
            kw(FUN), ident("calculate"), sym('('), ident("a"), sym(','), ident("b"), sym(')'),
            sym('='),
            blockStart,
            ident("temp"), sym('='), ident("a"), sym('*'), num("2"),
            stmtEnd,
            ident("temp"), sym('+'), ident("b"),
            eof
        )
    }

    @Test
    fun multipleFunctionDefinitions() {
        val program = """
            fun add(x, y) = x + y
            fun mul(x, y) = x * y
        """.trimIndent()
        assertTokens(
            program,
            kw(FUN), ident("add"), sym('('), ident("x"), sym(','), ident("y"), sym(')'),
            sym('='), ident("x"), sym('+'), ident("y"),
            stmtEnd,
            kw(FUN), ident("mul"), sym('('), ident("x"), sym(','), ident("y"), sym(')'),
            sym('='), ident("x"), sym('*'), ident("y"),
            eof
        )
    }

    @Test
    fun functionWithRecordBody() {
        assertTokens(
            "fun makePerson(name) = { name = name }",
            kw(FUN), ident("makePerson"), sym('('), ident("name"), sym(')'),
            sym('='),
            sym('{'), ident("name"), sym('='), ident("name"), sym('}'),
            eof
        )
    }

    @Test
    fun functionWithLambdaBody() {
        assertTokens(
            "fun curry(f) = |x -> |y -> f(x, y)||",
            kw(FUN), ident("curry"), sym('('), ident("f"), sym(')'),
            sym('='),
            pipeOpen, ident("x"), sym("->"),
            pipeOpen, ident("y"), sym("->"),
            ident("f"), sym('('), ident("x"), sym(','), ident("y"), sym(')'),
            pipeClose, pipeClose,
            eof
        )
    }

    @Test
    fun functionWithIfBody() {
        assertTokens(
            "fun abs(x) = if x < 0 then -x else x",
            kw(FUN), ident("abs"), sym('('), ident("x"), sym(')'),
            sym('='),
            kw(IF), ident("x"), sym('<'), num("0"),
            kw(THEN), sym('-'), ident("x"),
            kw(ELSE), ident("x"),
            eof
        )
    }
}
