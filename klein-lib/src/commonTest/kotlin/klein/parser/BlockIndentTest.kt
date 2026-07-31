package klein.parser

import klein.surface.ParseError
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Characterization tests for the indentation contract of blocks. These pin the subtle behaviors
 * that the block-boundary logic must preserve — they should stay green regardless of how the
 * indent bookkeeping is implemented.
 */
class BlockIndentTest {
    // A statement may span a deeper continuation indent; a following line back at the block indent
    // is a *sibling* statement, not a dedent that ends the block.
    @Test
    fun deeperStatementThenSiblingStayInSameBlock() {
        val program =
            """
            |
              a = foo
                bar
              b = 2
              b
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda(
                body =
                    block(
                        valStmt("a", id("foo")),
                        id("bar"),
                        valStmt("b", int(2)),
                        id("b"),
                    ),
            ),
        )
    }

    // A block may *open* with a bare `|`-lambda whose `|` sits at the block indent: the first
    // boundary check is made against the enclosing line, so the `|` reads as content, not a terminator.
    @Test
    fun blockMayOpenWithBareLambda() {
        val program =
            """
            f =
              |x -> x|
            f
            """.trimIndent()
        assertProgramEquals(
            parseProgram(program),
            listOf(
                valStmt("f", block(lambda("x", body = id("x")))),
                id("f"),
            ),
        )
    }

    // ...but a bare `|`-lambda in a non-first position is read as a block terminator (the counterpart
    // to the rule above), so it cannot appear as a later statement without being bound.
    @Test
    fun bareLambdaAsNonFirstStatementTerminatesBlock() {
        val program =
            """
            |
              a = 1
              |x -> x|
            |
            """.trimIndent()
        assertFailsWith<ParseError> { parseProgram(program) }
    }

    // Binding a lambda works in any position (its `|` is mid-line after `=`, never a line start).
    @Test
    fun boundLambdaWorksInAnyPosition() {
        val program =
            """
            |
              a = 1
              g = |x -> x|
              g
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda(
                body =
                    block(
                        valStmt("a", int(1)),
                        valStmt("g", lambda("x", body = id("x"))),
                        id("g"),
                    ),
            ),
        )
    }
}
