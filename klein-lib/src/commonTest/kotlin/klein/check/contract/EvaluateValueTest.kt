package klein.check.contract

import klein.Klein
import klein.ReleaseNumber
import klein.check.TypeError
import klein.interp.RuntimeError
import klein.interp.Value
import klein.assertRejected
import klein.orFail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val CONTRACT =
    """
    environment acme

    type Customer = Customer { id: Num, tier: String }
    type Shape/2 = Circle { area: Num } | Square { area: Num }

    customer: Customer
    fun creditScore(c: Customer): Num

    release 1
      Customer
      customer
      creditScore

    release 2
      Shape/2
    """.trimIndent()

private val contract = Klein.checkContract(CONTRACT)

private fun answerTypeOf(name: String) = contract.declarations.first { it.name == name }.answerType

private fun evaluate(
    source: String,
    demandedBy: String,
    release: Int = 1,
) = contract.evaluateValue(source, ReleaseNumber(release), answerTypeOf(demandedBy))

class EvaluateValueTest {
    @Test
    fun anAnswerOfTheDemandedTypeEvaluates() {
        assertEquals(Value.VNum(700.0), evaluate("700", demandedBy = "creditScore").orFail())
    }

    @Test
    fun aConstructorAnswerWorks() {
        val answer = assertIs<Value.VStruct>(evaluate("""Customer(1, "gold")""", demandedBy = "customer").orFail())
        assertEquals("Customer", answer.tag)
        assertEquals(Value.VStr("gold"), answer.fields["tier"])
    }

    @Test
    fun aWrongTypedAnswerCarriesTheCheckersMessageAtTheTypedSpan() {
        val errors = evaluate("700", demandedBy = "customer").assertRejected()
        val error = assertIs<TypeError.TypeMismatch>(errors.single())
        assertEquals(0, error.span.start)
        assertEquals(3, error.span.end)
        assertTrue("Customer" in error.message)
    }

    @Test
    fun aWrongTypedConstructorArgumentIsReportedAtItsOwnSpan() {
        val source = """Customer(1, 2)"""
        val errors = evaluate(source, demandedBy = "customer").assertRejected()
        assertEquals(source.indexOf("2"), assertIs<TypeError>(errors.first()).span.start)
    }

    @Test
    fun anAnswerWhoseLastStatementIsABindingEvaluatesToUnitAndIsRejected() {
        val errors = evaluate("Customer(1, \"gold\")\nx = 2", demandedBy = "customer").assertRejected()
        val error = assertIs<TypeError.TypeMismatch>(errors.single())
        assertTrue("Unit" in error.message)
    }

    @Test
    fun anAnswerNamingACapabilityIsRejected() {
        val errors = evaluate("""creditScore(Customer(1, "gold"))""", demandedBy = "creditScore").assertRejected()
        assertEquals("creditScore", assertIs<CapabilityInAnswer>(errors.single()).name)
    }

    @Test
    fun aValueCapabilityInAnAnswerIsRejectedToo() {
        val errors = evaluate("customer", demandedBy = "customer").assertRejected()
        assertEquals("customer", assertIs<CapabilityInAnswer>(errors.single()).name)
    }

    @Test
    fun anAnswerMentioningATypeThatBindsNoTermEvaluates() {
        val source =
            """
            s: Shape = Circle(9)
            s.area
            """.trimIndent()
        assertEquals(Value.VNum(9.0), evaluate(source, demandedBy = "creditScore", release = 2).orFail())
    }

    @Test
    fun anAnswerNamingAnUnexposedTypeIsUnboundVariable() {
        val errors = evaluate("Circle(9).area", demandedBy = "creditScore").assertRejected()
        assertEquals("Circle", assertIs<TypeError.UnboundVariable>(errors.first()).name)
    }

    @Test
    fun anAnswerThatFailsWhileEvaluatingIsRejectedWithTheRuntimeError() {
        val errors = evaluate("1 / 0", demandedBy = "creditScore").assertRejected()
        assertEquals("Division by zero", assertIs<RuntimeError>(errors.single()).message)
    }
}
