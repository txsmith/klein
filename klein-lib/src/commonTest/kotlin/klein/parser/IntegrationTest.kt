package klein.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class IntegrationTest {
    @Test
    fun loanEligibilityProgram() {
        val program =
            """
            # Calculate loan eligibility

            fun isEligible(score, amount) =
              minScore = 500
              maxAmount = 10000
              score >= minScore and amount <= maxAmount

            fun calculateRate(score) =
              if score >= 750 then
                baseRate = 5.0
                baseRate - 0.5
              else if score >= 650 then
                6.5
              else
                8.0

            fun assessRisk(customer) =
              score = customer.creditScore
              years = customer.yearsEmployed
              if score >= 700 and years >= 2 then
                "low"
              else if score >= 600 then
                "medium"
              else
                "high"

            fun process(application) =
              customer = application.customer
              amount = application.amount

              eligible = isEligible(customer.creditScore, amount)

              if not eligible then
                { approved = false, reason = "Not eligible" }
              else
                rate = calculateRate(customer.creditScore)
                risk = assessRisk(customer)
                { approved = true, rate, risk }

            # Using lambdas
            filterHighValue = |.amount > 5000|
            getScore = |.customer.creditScore|
            applyDiscount = |x -> x * 0.95|

            # Nested lambda
            hasGoodCustomer = |.customer.creditScore > 700|
            """.trimIndent()

        val parsed = parseProgram(program)
        assertEquals(8, parsed.stmts.size)

        assertProgramEquals(
            parsed,
            listOf(
                isEligibleFun(),
                calculateRateFun(),
                assessRiskFun(),
                processFun(),
                filterHighValueBinding(),
                getScoreBinding(),
                applyDiscountBinding(),
                hasGoodCustomerBinding(),
            ),
        )
    }

    private fun isEligibleFun() =
        funDef(
            "isEligible",
            "score",
            "amount",
            body =
                block(
                    valStmt("minScore", int(500)),
                    valStmt("maxAmount", int(10000)),
                    and(
                        gte(id("score"), id("minScore")),
                        lte(id("amount"), id("maxAmount")),
                    ),
                ),
        )

    private fun calculateRateFun() =
        funDef(
            "calculateRate",
            "score",
            body =
                block(
                    ifThenElse(
                        gte(id("score"), int(750)),
                        block(
                            valStmt("baseRate", double(5.0)),
                            sub(id("baseRate"), double(0.5)),
                        ),
                        ifThenElse(
                            gte(id("score"), int(650)),
                            block(double(6.5)),
                            block(double(8.0)),
                        ),
                    ),
                ),
        )

    private fun assessRiskFun() =
        funDef(
            "assessRisk",
            "customer",
            body =
                block(
                    valStmt("score", fieldAccess(id("customer"), "creditScore")),
                    valStmt("years", fieldAccess(id("customer"), "yearsEmployed")),
                    ifThenElse(
                        and(gte(id("score"), int(700)), gte(id("years"), int(2))),
                        block(string("low")),
                        ifThenElse(
                            gte(id("score"), int(600)),
                            block(string("medium")),
                            block(string("high")),
                        ),
                    ),
                ),
        )

    private fun processFun() =
        funDef(
            "process",
            "application",
            body =
                block(
                    valStmt("customer", fieldAccess(id("application"), "customer")),
                    valStmt("amount", fieldAccess(id("application"), "amount")),
                    valStmt(
                        "eligible",
                        call(
                            id("isEligible"),
                            fieldAccess(id("customer"), "creditScore"),
                            id("amount"),
                        ),
                    ),
                    ifThenElse(
                        not(id("eligible")),
                        block(
                            record(
                                "approved" to bool(false),
                                "reason" to string("Not eligible"),
                            ),
                        ),
                        block(
                            valStmt(
                                "rate",
                                call(id("calculateRate"), fieldAccess(id("customer"), "creditScore")),
                            ),
                            valStmt("risk", call(id("assessRisk"), id("customer"))),
                            record(
                                "approved" to bool(true),
                                "rate" to id("rate"),
                                "risk" to id("risk"),
                            ),
                        ),
                    ),
                ),
        )

    private fun filterHighValueBinding() =
        valStmt(
            "filterHighValue",
            lambda(body = gt(fieldAccess(implicitParam(), "amount"), int(5000))),
        )

    private fun getScoreBinding() =
        valStmt(
            "getScore",
            lambda(
                body =
                    fieldAccess(
                        fieldAccess(implicitParam(), "customer"),
                        "creditScore",
                    ),
            ),
        )

    private fun applyDiscountBinding() =
        valStmt(
            "applyDiscount",
            lambda("x", body = mul(id("x"), double(0.95))),
        )

    private fun hasGoodCustomerBinding() =
        valStmt(
            "hasGoodCustomer",
            lambda(
                body =
                    gt(
                        fieldAccess(
                            fieldAccess(implicitParam(), "customer"),
                            "creditScore",
                        ),
                        int(700),
                    ),
            ),
        )
}
