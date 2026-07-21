package klein.parser

import klein.Match
import klein.ParseError
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class MatchTest {
    @Test
    fun bareConstructorArms() {
        val expr =
            parse(
                """
                match a
                  Dog -> 1
                  Cat -> 2
                """.trimIndent(),
            )
        assertExprEquals(
            expr,
            matchExpr(
                id("a"),
                arm(ctorP("Dog"), int(1)),
                arm(ctorP("Cat"), int(2)),
            ),
        )
    }

    @Test
    fun constructorRecordPatternWithPun() {
        val expr =
            parse(
                """
                match s
                  Circle { radius } -> radius
                """.trimIndent(),
            )
        assertExprEquals(
            expr,
            matchExpr(id("s"), arm(ctorP("Circle", fieldP("radius")), id("radius"))),
        )
    }

    @Test
    fun renamedFieldPattern() {
        val expr =
            parse(
                """
                match r
                  Ok { value = v } -> v
                """.trimIndent(),
            )
        assertExprEquals(
            expr,
            matchExpr(id("r"), arm(ctorP("Ok", fieldP("value", "v")), id("v"))),
        )
    }

    @Test
    fun multiFieldPatternWithTrailingComma() {
        val expr =
            parse(
                """
                match s
                  Tri { base, height, } -> base * height
                """.trimIndent(),
            )
        assertExprEquals(
            expr,
            matchExpr(
                id("s"),
                arm(ctorP("Tri", fieldP("base"), fieldP("height")), mul(id("base"), id("height"))),
            ),
        )
    }

    @Test
    fun bareRecordPattern() {
        val expr =
            parse(
                """
                match p
                  { name, age } -> name
                """.trimIndent(),
            )
        assertExprEquals(
            expr,
            matchExpr(id("p"), arm(recordP(fieldP("name"), fieldP("age")), id("name"))),
        )
    }

    @Test
    fun wildcardAndVariableArms() {
        val expr =
            parse(
                """
                match s
                  Circle -> 1
                  other -> 2
                  _ -> 3
                """.trimIndent(),
            )
        assertExprEquals(
            expr,
            matchExpr(
                id("s"),
                arm(ctorP("Circle"), int(1)),
                arm(varP("other"), int(2)),
                arm(wildcardP(), int(3)),
            ),
        )
    }

    @Test
    fun literalPatterns() {
        val expr =
            parse(
                """
                match x
                  42 -> 1
                  -1 -> 2
                  2.5 -> 3
                  "yes" -> 4
                  true -> 5
                  false -> 6
                  null -> 7
                """.trimIndent(),
            )
        assertExprEquals(
            expr,
            matchExpr(
                id("x"),
                arm(litP(int(42)), int(1)),
                arm(litP(int(-1)), int(2)),
                arm(litP(double(2.5)), int(3)),
                arm(litP(string("yes")), int(4)),
                arm(litP(bool(true)), int(5)),
                arm(litP(bool(false)), int(6)),
                arm(litP(nullLit()), int(7)),
            ),
        )
    }

    @Test
    fun guardedArm() {
        val expr =
            parse(
                """
                match s
                  Circle { radius } if radius > 10 -> 1
                  _ -> 2
                """.trimIndent(),
            )
        assertExprEquals(
            expr,
            matchExpr(
                id("s"),
                arm(ctorP("Circle", fieldP("radius")), int(1), guard = gt(id("radius"), int(10))),
                arm(wildcardP(), int(2)),
            ),
        )
    }

    @Test
    fun blockBodyArm() {
        val expr =
            parse(
                """
                match s
                  Circle ->
                    x = 1
                    x + 1
                  _ -> 2
                """.trimIndent(),
            )
        assertExprEquals(
            expr,
            matchExpr(
                id("s"),
                arm(ctorP("Circle"), block(valStmt("x", int(1)), add(id("x"), int(1)))),
                arm(wildcardP(), int(2)),
            ),
        )
    }

    @Test
    fun scrutineeCanBeACall() {
        val expr =
            parse(
                """
                match f(x)
                  _ -> 1
                """.trimIndent(),
            )
        assertExprEquals(
            expr,
            matchExpr(call(id("f"), id("x")), arm(wildcardP(), int(1))),
        )
    }

    @Test
    fun matchAsBindingValue() {
        val stmt =
            parseStmt(
                """
                x = match s
                  Circle -> 1
                  _ -> 2
                """.trimIndent(),
            )
        assertStmtEquals(
            stmt,
            valStmt(
                "x",
                matchExpr(id("s"), arm(ctorP("Circle"), int(1)), arm(wildcardP(), int(2))),
            ),
        )
    }

    @Test
    fun nestedMatchInArmBody() {
        val expr =
            parse(
                """
                match a
                  Dog -> match b
                    Cat -> 1
                    _ -> 2
                  _ -> 3
                """.trimIndent(),
            )
        assertExprEquals(
            expr,
            matchExpr(
                id("a"),
                arm(
                    ctorP("Dog"),
                    matchExpr(id("b"), arm(ctorP("Cat"), int(1)), arm(wildcardP(), int(2))),
                ),
                arm(wildcardP(), int(3)),
            ),
        )
    }

    @Test
    fun elseTerminatesMatchArms() {
        val expr =
            parse(
                """
                if c then
                  match s
                    Circle -> 1
                    _ -> 2
                else 3
                """.trimIndent(),
            )
        assertExprEquals(
            expr,
            ifThenElse(
                id("c"),
                block(matchExpr(id("s"), arm(ctorP("Circle"), int(1)), arm(wildcardP(), int(2)))),
                int(3),
            ),
        )
    }

    @Test
    fun closingPipeTerminatesMatchArmsInLambda() {
        val expr =
            parse(
                """
                |x -> match x
                  Circle -> 1
                  _ -> 2
                |
                """.trimIndent(),
            )
        assertExprEquals(
            expr,
            lambda(
                "x",
                body = matchExpr(id("x"), arm(ctorP("Circle"), int(1)), arm(wildcardP(), int(2))),
            ),
        )
    }

    @Test
    fun dedentTerminatesMatch() {
        val program =
            parseProgram(
                """
                y = match s
                  Circle -> 1
                  _ -> 2
                z = 3
                """.trimIndent(),
            )
        assertProgramEquals(
            program,
            listOf(
                valStmt("y", matchExpr(id("s"), arm(ctorP("Circle"), int(1)), arm(wildcardP(), int(2)))),
                valStmt("z", int(3)),
            ),
        )
    }

    @Test
    fun armsOnTheMatchLineAreRejected() {
        val e = assertFailsWith<ParseError> { parse("match s Circle -> 1") }
        assertIs<ParseError>(e)
    }

    @Test
    fun matchWithoutArmsIsRejected() {
        assertFailsWith<ParseError> { parse("match s") }
    }

    @Test
    fun emptyRecordPatternIsRejected() {
        assertFailsWith<ParseError> {
            parse(
                """
                match s
                  Circle { } -> 1
                """.trimIndent(),
            )
        }
    }

    @Test
    fun duplicateFieldInPatternIsRejected() {
        assertFailsWith<ParseError> {
            parse(
                """
                match s
                  Circle { radius, radius } -> 1
                """.trimIndent(),
            )
        }
    }

    @Test
    fun constructorBinderPattern() {
        val expr =
            parse(
                """
                match a
                  Dog d -> d
                  _ -> a
                """.trimIndent(),
            )
        assertExprEquals(
            expr,
            matchExpr(
                id("a"),
                arm(ctorBindP("Dog", "d"), id("d")),
                arm(wildcardP(), id("a")),
            ),
        )
    }

    @Test
    fun positionalPatternIsRejected() {
        assertFailsWith<ParseError> {
            parse(
                """
                match r
                  Some(x) -> x
                """.trimIndent(),
            )
        }
    }

    @Test
    fun matchIsNoLongerAnIdentifier() {
        assertFailsWith<ParseError> { parseStmt("match = 1") }
    }

    @Test
    fun wildcardFieldTestsWithoutBinding() {
        val expr =
            parse(
                """
                match p
                  { name = _, age } -> age
                """.trimIndent(),
            )
        assertExprEquals(
            expr,
            matchExpr(id("p"), arm(recordP(fieldP("name", null), fieldP("age")), id("age"))),
        )
    }

    @Test
    fun wildcardConstructorBinderMeansBareConstructor() {
        val expr =
            parse(
                """
                match a
                  Dog _ -> 1
                """.trimIndent(),
            )
        assertExprEquals(expr, matchExpr(id("a"), arm(ctorP("Dog"), int(1))))
    }

    @Test
    fun closingParenTerminatesMatchArms() {
        val expr =
            parse(
                """
                f(match s
                  Circle -> 1
                  _ -> 2)
                """.trimIndent(),
            )
        assertExprEquals(
            expr,
            call(id("f"), matchExpr(id("s"), arm(ctorP("Circle"), int(1)), arm(wildcardP(), int(2)))),
        )
    }

    @Test
    fun nonIdentifierAfterConstructorIsRejected() {
        assertFailsWith<ParseError> {
            parse(
                """
                match a
                  Dog true -> 1
                """.trimIndent(),
            )
        }
    }

    @Test
    fun matchParsesAsAMatchNode() {
        val expr =
            parse(
                """
                match s
                  _ -> 1
                """.trimIndent(),
            )
        assertIs<Match>(expr)
    }
}
