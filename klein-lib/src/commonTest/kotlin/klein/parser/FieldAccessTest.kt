package klein.parser

import kotlin.test.Test

class FieldAccessTest {
    @Test
    fun simpleFieldAccess() {
        assertExprEquals(
            parse("user.name"),
            fieldAccess(id("user"), "name"),
        )
    }

    @Test
    fun chainedFieldAccess() {
        assertExprEquals(
            parse("user.address.city"),
            fieldAccess(fieldAccess(id("user"), "address"), "city"),
        )
    }

    @Test
    fun fieldAccessOnStringLiteral() {
        assertExprEquals(
            parse(""""hello".length"""),
            fieldAccess(string("hello"), "length"),
        )
    }

    @Test
    fun fieldAccessOnIntLiteral() {
        assertExprEquals(
            parse("42.toString"),
            fieldAccess(int(42), "toString"),
        )
    }

    @Test
    fun fieldAccessOnDoubleLiteral() {
        assertExprEquals(
            parse("3.14.toString"),
            fieldAccess(double(3.14), "toString"),
        )
    }

    @Test
    fun fieldAccessOnBoolLiteral() {
        assertExprEquals(
            parse("true.toString"),
            fieldAccess(bool(true), "toString"),
        )
    }

    @Test
    fun methodCallOnStringLiteral() {
        assertExprEquals(
            parse(""""hello".toUpperCase()"""),
            call(fieldAccess(string("hello"), "toUpperCase")),
        )
    }

    @Test
    fun methodCallOnIntLiteral() {
        assertExprEquals(
            parse("42.toString()"),
            call(fieldAccess(int(42), "toString")),
        )
    }

    @Test
    fun chainedFieldAccessOnLiteral() {
        assertExprEquals(
            parse(""""hello".chars.count"""),
            fieldAccess(fieldAccess(string("hello"), "chars"), "count"),
        )
    }

    @Test
    fun fieldAccessOnFunctionCall() {
        assertExprEquals(
            parse("getUser(1).name"),
            fieldAccess(call(id("getUser"), int(1)), "name"),
        )
    }

    @Test
    fun functionCallOnFieldAccess() {
        assertExprEquals(
            parse("user.getName()"),
            call(fieldAccess(id("user"), "getName")),
        )
    }

    @Test
    fun chainedAccessAndCalls() {
        assertExprEquals(
            parse("getUser(1).getName()"),
            call(
                fieldAccess(
                    call(id("getUser"), int(1)),
                    "getName",
                ),
            ),
        )
    }

    @Test
    fun fieldAccessWithBinaryOp() {
        assertExprEquals(
            parse("user.age + 1"),
            add(fieldAccess(id("user"), "age"), int(1)),
        )
    }

    @Test
    fun keywordAsFieldIsRejected() {
        kotlin.test.assertFailsWith<klein.ParseError> { parse("x.match") }
    }

    @Test
    fun underscoreAsFieldIsRejected() {
        kotlin.test.assertFailsWith<klein.ParseError> { parse("x._") }
    }
}
