package klein.interp

import klein.interp.Value.VBool
import klein.interp.Value.VNum
import klein.interp.Value.VStr
import klein.interp.Value.VStruct
import kotlin.test.Test
import kotlin.test.assertEquals

private fun program(trailing: String) =
    """
    fun isEligible(score: Num, amount: Num) =
      minScore = 500
      maxAmount = 10000
      score >= minScore and amount <= maxAmount

    fun calculateRate(score: Num): Num =
      if score >= 750 then
        baseRate = 5.0
        baseRate - 0.5
      else if score >= 650 then
        6.5
      else
        8.0

    fun assessRisk(customer: { creditScore: Num, yearsEmployed: Num }) =
      score = customer.creditScore
      years = customer.yearsEmployed
      if score >= 700 and years >= 2 then
        "low"
      else if score >= 600 then
        "medium"
      else
        "high"

    fun process(application: { customer: { creditScore: Num, yearsEmployed: Num }, amount: Num }) =
      customer = application.customer
      amount = application.amount

      eligible = isEligible(customer.creditScore, amount)

      if not eligible then
        { approved = false, reason = "Not eligible" }
      else
        rate = calculateRate(customer.creditScore)
        risk = assessRisk(customer)
        { approved = true, rate, risk }

    filterHighValue: ({ amount: Num }) -> Bool = |.amount > 5000|
    applyDiscount: (Num) -> Num = |x -> x * 0.95|

    $trailing
    """

class LoanEligibilityEvalTest {
    @Test
    fun strongApplicationIsApprovedAtTheDiscountedRate() =
        assertEquals(
            VStruct(
                null,
                mapOf(
                    "approved" to VBool(true),
                    "rate" to VNum(4.5),
                    "risk" to VStr("low"),
                ),
            ),
            runSource(program("process({ customer = { creditScore = 780, yearsEmployed = 3 }, amount = 4000 })")),
        )

    @Test
    fun midScoreApplicationGetsTheMidRateAndMediumRisk() =
        assertEquals(
            VStruct(
                null,
                mapOf(
                    "approved" to VBool(true),
                    "rate" to VNum(6.5),
                    "risk" to VStr("medium"),
                ),
            ),
            runSource(program("process({ customer = { creditScore = 660, yearsEmployed = 1 }, amount = 9000 })")),
        )

    @Test
    fun ineligibleApplicationIsRejectedWithAReason() =
        assertEquals(
            VStruct(
                null,
                mapOf(
                    "approved" to VBool(false),
                    "reason" to VStr("Not eligible"),
                ),
            ),
            runSource(program("process({ customer = { creditScore = 480, yearsEmployed = 5 }, amount = 4000 })")),
        )

    @Test
    fun helperLambdasRunAgainstApplications() {
        assertEvaluatesTo(
            VBool(true),
            program("filterHighValue({ amount = 6000 })"),
        )
        assertEvaluatesTo(
            VBool(false),
            program("filterHighValue({ amount = 4000 })"),
        )
        assertEvaluatesTo(
            VNum(95.0),
            program("applyDiscount(100)"),
        )
    }
}
