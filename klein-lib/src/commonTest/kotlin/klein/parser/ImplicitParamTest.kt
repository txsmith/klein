package klein.parser

import kotlin.test.Test

class ImplicitParamTest {
    @Test
    fun bareImplicitParam() {
        assertExprEquals(
            parse("|.|"),
            lambda(body = implicitParam()),
        )
    }

    @Test
    fun implicitParamFieldAccess() {
        assertExprEquals(
            parse(".price"),
            fieldAccess(implicitParam(), "price"),
        )
    }

    @Test
    fun implicitParamChainedFieldAccess() {
        assertExprEquals(
            parse(".user.name"),
            fieldAccess(fieldAccess(implicitParam(), "user"), "name"),
        )
    }

    @Test
    fun lambdaWithImplicitParam() {
        assertExprEquals(
            parse("|.price|"),
            lambda(body = fieldAccess(implicitParam(), "price")),
        )
    }

    @Test
    fun lambdaWithImplicitParamInExpression() {
        assertExprEquals(
            parse("|.price > 100|"),
            lambda(body = gt(fieldAccess(implicitParam(), "price"), int(100))),
        )
    }

    @Test
    fun nestedLambdasWithImplicitParams() {
        assertExprEquals(
            parse("|.orders.any(|.price > 100|)|"),
            lambda(
                body =
                    call(
                        fieldAccess(fieldAccess(implicitParam(), "orders"), "any"),
                        lambda(body = gt(fieldAccess(implicitParam(), "price"), int(100))),
                    ),
            ),
        )
    }

    @Test
    fun implicitParamWithBinaryOp() {
        assertExprEquals(
            parse("|. + 1|"),
            lambda(body = add(implicitParam(), int(1))),
        )
    }

    @Test
    fun implicitParamWithUnaryNeg() {
        assertExprEquals(
            parse("|-.|"),
            lambda(body = neg(implicitParam())),
        )
    }

    @Test
    fun implicitParamWithUnaryNot() {
        assertExprEquals(
            parse("|not .|"),
            lambda(body = not(implicitParam())),
        )
    }

    @Test
    fun implicitParamFieldWithUnaryOp() {
        assertExprEquals(
            parse("|-.price|"),
            lambda(body = neg(fieldAccess(implicitParam(), "price"))),
        )
    }

    @Test
    fun implicitParamWithFunctionCall() {
        assertExprEquals(
            parse("|.toString()|"),
            lambda(body = call(fieldAccess(implicitParam(), "toString"))),
        )
    }

    @Test
    fun implicitParamFieldWithFunctionCall() {
        assertExprEquals(
            parse("|.name.toUpperCase()|"),
            lambda(
                body =
                    call(
                        fieldAccess(
                            fieldAccess(implicitParam(), "name"),
                            "toUpperCase",
                        ),
                    ),
            ),
        )
    }

    @Test
    fun implicitParamInComparison() {
        assertExprEquals(
            parse("|. == 0|"),
            lambda(body = eq(implicitParam(), int(0))),
        )
    }

    @Test
    fun implicitParamFieldInComparison() {
        assertExprEquals(
            parse("|.age >= 18|"),
            lambda(body = gte(fieldAccess(implicitParam(), "age"), int(18))),
        )
    }

    @Test
    fun implicitParamInLogicalExpression() {
        assertExprEquals(
            parse("|.active and .verified|"),
            lambda(
                body =
                    and(
                        fieldAccess(implicitParam(), "active"),
                        fieldAccess(implicitParam(), "verified"),
                    ),
            ),
        )
    }

    @Test
    fun multipleImplicitParamsInExpression() {
        assertExprEquals(
            parse("|. + . * 2|"),
            lambda(
                body =
                    add(
                        implicitParam(),
                        mul(implicitParam(), int(2)),
                    ),
            ),
        )
    }

    @Test
    fun implicitParamAsArgument() {
        assertExprEquals(
            parse("|print(.)|"),
            lambda(body = call(id("print"), implicitParam())),
        )
    }

    @Test
    fun underscoreAsImplicitFieldIsRejected() {
        kotlin.test.assertFailsWith<klein.ParseError> { parse("._") }
    }
}
