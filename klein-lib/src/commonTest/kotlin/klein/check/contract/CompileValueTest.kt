package klein.check.contract

import klein.Klein
import klein.KleinException
import klein.ReleaseNumber
import klein.check.TypeError
import klein.interp.Value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val CONTRACT =
    """
    type Customer = Customer { id: Num, tier: String }
    type Shape/2 = Circle { area: Num } | Square { area: Num }

    customer: Customer
    fun creditScore(c: Customer): Num

    release 1
      Customer
      customer
      creditScore
    """.trimIndent()

private val contract = Klein.checkContract(CONTRACT)

private fun answerTypeOf(name: String) = contract.declarations.first { it.name == name }.answerType

private fun evaluate(
    source: String,
    demandedBy: String,
): Value {
    val core = contract.compileValue(source, ReleaseNumber(1), answerTypeOf(demandedBy))
    val executed = Klein.execute(core)
    assertEquals(emptyList(), executed.errors)
    return executed.output!!
}

class CompileValueTest {
    @Test
    fun anAnswerOfTheDemandedTypeCompilesAndEvaluates() {
        assertEquals(Value.VNum(700.0), evaluate("700", demandedBy = "creditScore"))
    }

    @Test
    fun aConstructorAnswerWorks() {
        val answer = assertIs<Value.VStruct>(evaluate("""Customer(1, "gold")""", demandedBy = "customer"))
        assertEquals("Customer", answer.tag)
        assertEquals(Value.VStr("gold"), answer.fields["tier"])
    }

    @Test
    fun aWrongTypedAnswerCarriesTheCheckersMessageAtTheTypedSpan() {
        val errors =
            assertFailsWith<KleinException> {
                contract.compileValue("700", ReleaseNumber(1), answerTypeOf("customer"))
            }.errors
        val error = assertIs<TypeError.TypeMismatch>(errors.single())
        assertEquals(0, error.span.start)
        assertEquals(3, error.span.end)
        assertTrue("Customer" in error.message)
    }

    @Test
    fun aWrongTypedConstructorArgumentIsReportedAtItsOwnSpan() {
        val source = """Customer(1, 2)"""
        val errors =
            assertFailsWith<KleinException> {
                contract.compileValue(source, ReleaseNumber(1), answerTypeOf("customer"))
            }.errors
        assertEquals(source.indexOf("2"), assertIs<TypeError>(errors.first()).span.start)
    }

    @Test
    fun anAnswerWhoseLastStatementIsABindingEvaluatesToUnitAndIsRejected() {
        val errors =
            assertFailsWith<KleinException> {
                contract.compileValue("Customer(1, \"gold\")\nx = 2", ReleaseNumber(1), answerTypeOf("customer"))
            }.errors
        val error = assertIs<TypeError.TypeMismatch>(errors.single())
        assertTrue("Unit" in error.message)
    }

    @Test
    fun anAnswerNamingACapabilityIsRejected() {
        val errors =
            assertFailsWith<KleinException> {
                contract.compileValue("""creditScore(Customer(1, "gold"))""", ReleaseNumber(1), answerTypeOf("creditScore"))
            }.errors
        assertEquals("creditScore", assertIs<CapabilityInAnswer>(errors.single()).name)
    }

    @Test
    fun aValueCapabilityInAnAnswerIsRejectedToo() {
        val errors =
            assertFailsWith<KleinException> {
                contract.compileValue("customer", ReleaseNumber(1), answerTypeOf("customer"))
            }.errors
        assertEquals("customer", assertIs<CapabilityInAnswer>(errors.single()).name)
    }

    @Test
    fun anAnswerNamingAnUnexposedTypeIsUnboundVariable() {
        val errors =
            assertFailsWith<KleinException> {
                contract.compileValue("Circle(9).area", ReleaseNumber(1), answerTypeOf("creditScore"))
            }.errors
        assertEquals("Circle", assertIs<TypeError.UnboundVariable>(errors.first()).name)
    }
}
