package klein.parser

import klein.ParseError
import kotlin.test.Test
import kotlin.test.assertFailsWith

class RecordTest {
    @Test
    fun emptyRecord() {
        assertExprEquals(
            parse("{}"),
            record(),
        )
    }

    @Test
    fun singleField() {
        assertExprEquals(
            parse("{ name = \"Alice\" }"),
            record("name" to string("Alice")),
        )
    }

    @Test
    fun multipleFields() {
        assertExprEquals(
            parse("{ name = \"Alice\", age = 30 }"),
            record(
                "name" to string("Alice"),
                "age" to int(30),
            ),
        )
    }

    @Test
    fun shorthandField() {
        assertExprEquals(
            parse("{ name, age }"),
            record(
                "name" to id("name"),
                "age" to id("age"),
            ),
        )
    }

    @Test
    fun mixedShorthandAndFull() {
        assertExprEquals(
            parse("{ name, age = 30 }"),
            record(
                "name" to id("name"),
                "age" to int(30),
            ),
        )
    }

    @Test
    fun nestedRecord() {
        assertExprEquals(
            parse("{ person = { name = \"Bob\" } }"),
            record(
                "person" to record("name" to string("Bob")),
            ),
        )
    }

    @Test
    fun recordWithExpressionValue() {
        assertExprEquals(
            parse("{ total = price + tax }"),
            record("total" to add(id("price"), id("tax"))),
        )
    }

    @Test
    fun recordAssignment() {
        assertStmtEquals(
            parseStmt("person = { name = \"Alice\" }"),
            valStmt("person", record("name" to string("Alice"))),
        )
    }

    @Test
    fun recordInIfThenElse() {
        assertExprEquals(
            parse("if x then { a = 1 } else { b = 2 }"),
            ifThenElse(
                id("x"),
                record("a" to int(1)),
                record("b" to int(2)),
            ),
        )
    }

    @Test
    fun recordAsLambdaBody() {
        assertExprEquals(
            parse("|x -> { value = x }|"),
            lambda("x", body = record("value" to id("x"))),
        )
    }

    @Test
    fun recordInFunctionCall() {
        assertExprEquals(
            parse("process({ name = \"test\" })"),
            call(id("process"), record("name" to string("test"))),
        )
    }

    @Test
    fun fieldAccessOnRecord() {
        assertExprEquals(
            parse("{ name = \"Alice\" }.name"),
            fieldAccess(record("name" to string("Alice")), "name"),
        )
    }

    @Test
    fun unclosedBrace() {
        assertFailsWith<ParseError> { parse("{ a = 1") }
    }

    @Test
    fun numberAsKey() {
        assertFailsWith<ParseError> { parse("{ 123 = 1 }") }
    }

    @Test
    fun stringAsKey() {
        assertFailsWith<ParseError> { parse("{ \"key\" = 1 }") }
    }

    @Test
    fun randomSymbolsInBraces() {
        assertFailsWith<ParseError> { parse("{ + - * }") }
    }

    @Test
    fun keywordsInBraces() {
        assertFailsWith<ParseError> { parse("{ if then else }") }
    }

    @Test
    fun missingValue() {
        assertFailsWith<ParseError> { parse("{ a = }") }
    }

    @Test
    fun missingEqualsOrComma() {
        assertFailsWith<ParseError> { parse("{ a 1 }") }
    }

    @Test
    fun trailingComma() {
        assertExprEquals(
            parse("{ a = 1, }"),
            record("a" to int(1)),
        )
    }
}
