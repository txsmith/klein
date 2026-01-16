package klein.parser

import kotlin.test.Test

class SafeFieldAccessTest {
    @Test
    fun simpleSafeFieldAccess() {
        assertExprEquals(
            parse("user?.name"),
            safeFieldAccess(id("user"), "name"),
        )
    }

    @Test
    fun chainedSafeFieldAccess() {
        assertExprEquals(
            parse("user?.address?.city"),
            safeFieldAccess(safeFieldAccess(id("user"), "address"), "city"),
        )
    }

    @Test
    fun safeFieldAccessOnStringLiteral() {
        assertExprEquals(
            parse(""""hello"?.length"""),
            safeFieldAccess(string("hello"), "length"),
        )
    }

    @Test
    fun safeFieldAccessOnIntLiteral() {
        assertExprEquals(
            parse("42?.toString"),
            safeFieldAccess(int(42), "toString"),
        )
    }

    @Test
    fun safeFieldAccessOnDoubleLiteral() {
        assertExprEquals(
            parse("3.14?.toString"),
            safeFieldAccess(double(3.14), "toString"),
        )
    }

    @Test
    fun safeFieldAccessOnBoolLiteral() {
        assertExprEquals(
            parse("true?.toString"),
            safeFieldAccess(bool(true), "toString"),
        )
    }

    @Test
    fun methodCallOnSafeFieldAccess() {
        assertExprEquals(
            parse(""""hello"?.toUpperCase()"""),
            call(safeFieldAccess(string("hello"), "toUpperCase")),
        )
    }

    @Test
    fun methodCallOnIntLiteralSafe() {
        assertExprEquals(
            parse("42?.toString()"),
            call(safeFieldAccess(int(42), "toString")),
        )
    }

    @Test
    fun chainedSafeFieldAccessOnLiteral() {
        assertExprEquals(
            parse(""""hello"?.chars?.count"""),
            safeFieldAccess(safeFieldAccess(string("hello"), "chars"), "count"),
        )
    }

    @Test
    fun safeFieldAccessOnFunctionCall() {
        assertExprEquals(
            parse("getUser(1)?.name"),
            safeFieldAccess(call(id("getUser"), int(1)), "name"),
        )
    }

    @Test
    fun functionCallOnSafeFieldAccess() {
        assertExprEquals(
            parse("user?.getName()"),
            call(safeFieldAccess(id("user"), "getName")),
        )
    }

    @Test
    fun chainedSafeAccessAndCalls() {
        assertExprEquals(
            parse("getUser(1)?.getName()"),
            call(
                safeFieldAccess(
                    call(id("getUser"), int(1)),
                    "getName",
                ),
            ),
        )
    }

    @Test
    fun safeFieldAccessWithBinaryOp() {
        assertExprEquals(
            parse("user?.age + 1"),
            add(safeFieldAccess(id("user"), "age"), int(1)),
        )
    }

    @Test
    fun mixedAccessSafeThenRegular() {
        assertExprEquals(
            parse("user?.address.city"),
            fieldAccess(safeFieldAccess(id("user"), "address"), "city"),
        )
    }

    @Test
    fun mixedAccessRegularThenSafe() {
        assertExprEquals(
            parse("user.address?.city"),
            safeFieldAccess(fieldAccess(id("user"), "address"), "city"),
        )
    }

    @Test
    fun mixedAccessThreeLevel() {
        assertExprEquals(
            parse("a?.b.c?.d"),
            safeFieldAccess(
                fieldAccess(
                    safeFieldAccess(id("a"), "b"),
                    "c",
                ),
                "d",
            ),
        )
    }

    @Test
    fun safeFieldAccessAfterMethodCall() {
        assertExprEquals(
            parse("getUser()?.address?.city"),
            safeFieldAccess(
                safeFieldAccess(
                    call(id("getUser")),
                    "address",
                ),
                "city",
            ),
        )
    }

    @Test
    fun methodCallAfterSafeFieldAccess() {
        assertExprEquals(
            parse("user?.address?.getCity()"),
            call(
                safeFieldAccess(
                    safeFieldAccess(id("user"), "address"),
                    "getCity",
                ),
            ),
        )
    }

    @Test
    fun safeFieldAccessOnRecord() {
        assertExprEquals(
            parse("{ x = 1 }?.x"),
            safeFieldAccess(record("x" to int(1)), "x"),
        )
    }

    @Test
    fun safeFieldAccessOnParenthesizedExpr() {
        assertExprEquals(
            parse("(a + b)?.x"),
            safeFieldAccess(add(id("a"), id("b")), "x"),
        )
    }

    @Test
    fun safeFieldAccessOnNull() {
        assertExprEquals(
            parse("null?.x"),
            safeFieldAccess(nullLit(), "x"),
        )
    }
}
