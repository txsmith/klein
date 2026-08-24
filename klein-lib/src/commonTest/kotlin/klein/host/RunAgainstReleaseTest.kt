package klein.host

import klein.Klein
import klein.KleinException
import klein.ReleaseNumber
import klein.RevisionNumber
import klein.interp.Value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

private const val STANDARD = "creditScore(customer) >= 620"

private val LENDING =
    """
    type Customer = Customer { id: Num, tier: String }

    customer: Customer
    fun creditScore(c: Customer): Num

    release 1
      Customer
      customer
      creditScore
    """.trimIndent()

private val gold = Value.VStruct("Customer", mapOf("id" to Value.VNum(1.0), "tier" to Value.VStr("gold")))
private val basic = Value.VStruct("Customer", mapOf("id" to Value.VNum(2.0), "tier" to Value.VStr("basic")))

private fun scoreByTier(args: List<Value>): Value {
    val customer = assertIs<Value.VStruct>(args.single())
    return Value.VNum(if (customer.fields["tier"] == Value.VStr("gold")) 700.0 else 500.0)
}

/** The execution narrative: the editions [LendingExampleTest] only checks, run against a live host. */
class RunAgainstReleaseTest {
    @Test
    fun aRuleCallingACapabilityRunsToAValue() {
        val contract = Klein.checkContract(LENDING)
        val env =
            contract.implement {
                immediate("customer") { gold }
                immediate("creditScore") { scoreByTier(it) }
            }
        assertEquals(Value.VBool(true), env.run(contract.compileRule(STANDARD, ReleaseNumber(1))))
    }

    @Test
    fun aNullaryCapabilityIsAskedExactlyOnce() {
        val contract = Klein.checkContract(LENDING)
        var asks = 0
        val env =
            contract.implement {
                immediate("customer") { asks++; gold }
                immediate("creditScore") { scoreByTier(it) }
            }
        val edition = contract.compileRule("creditScore(customer) + creditScore(customer)", ReleaseNumber(1))
        assertEquals(Value.VNum(1400.0), env.run(edition))
        assertEquals(1, asks)
    }

    @Test
    fun callingThroughABindingMatchesTheDirectCall() {
        val contract = Klein.checkContract(LENDING)
        val env =
            contract.implement {
                immediate("customer") { gold }
                immediate("creditScore") { scoreByTier(it) }
            }
        val direct = env.run(contract.compileRule(STANDARD, ReleaseNumber(1)))
        val indirect = env.run(contract.compileRule("f = creditScore\nf(customer) >= 620", ReleaseNumber(1)))
        assertEquals(direct, indirect)
        assertEquals(Value.VBool(true), indirect)
    }

    @Test
    fun twoEditionsOfTheSameRuleDispatchToTheirPinnedRevision() {
        val contract =
            Klein.checkContract(
                """
                fun creditScore(c: Num): Num
                fun creditScore/2(c: Num): Num

                release 1
                  creditScore

                release 2
                  creditScore/2
                """.trimIndent(),
            )
        val env =
            contract.implement {
                immediate("creditScore") { Value.VNum(600.0) }
                immediate("creditScore", revision = RevisionNumber(2)) { Value.VNum(700.0) }
            }
        assertEquals(Value.VBool(false), env.run(contract.compileRule("creditScore(1) >= 620", ReleaseNumber(1))))
        assertEquals(Value.VBool(true), env.run(contract.compileRule("creditScore(1) >= 620", ReleaseNumber(2))))
    }

    @Test
    fun anUnregisteredPinIsUnservedPinBeforeTheMachineStarts() {
        val compiling =
            Klein.checkContract(
                """
                fun creditScore(c: Num): Num
                fun creditScore/2(c: Num): Num

                release 1
                  creditScore

                release 2
                  creditScore/2
                """.trimIndent(),
            )
        val edition = compiling.compileRule("creditScore(1) >= 620", ReleaseNumber(2))

        var asked = false
        val drained =
            Klein.checkContract(
                """
                fun creditScore(c: Num): Num

                release 1
                  creditScore

                release 2
                  creditScore
                """.trimIndent(),
            ).implement { immediate("creditScore") { asked = true; Value.VNum(700.0) } }

        val error = assertFailsWith<KleinException> { drained.run(edition) }
        val pin = assertIs<UnservedPin>(error.errors.single())
        assertEquals("creditScore", pin.name)
        assertEquals(RevisionNumber(2), pin.revision)
        assertFalse(asked, "the pin check should reject the edition before any capability is asked")
    }

    @Test
    fun aRunThatForgetsASuppliedCapabilityIsMissingImplementation() {
        val contract = Klein.checkContract(LENDING)
        val env =
            contract.implement {
                immediate("customer")
                immediate("creditScore") { scoreByTier(it) }
            }
        val edition = contract.compileRule(STANDARD, ReleaseNumber(1))

        val error = assertFailsWith<KleinException> { env.run(edition) }
        val missing = assertIs<MissingImplementation>(error.errors.single())
        assertEquals("customer", missing.name)
        assertEquals(RevisionNumber(1), missing.declared)

        assertEquals(Value.VBool(true), env.run(edition) { immediate("customer") { gold } })
    }

    @Test
    fun aRunSuppliedImplementationWinsOverTheBootRegisteredOne() {
        val contract = Klein.checkContract(LENDING)
        val env =
            contract.implement {
                immediate("customer") { basic }
                immediate("creditScore") { scoreByTier(it) }
            }
        val edition = contract.compileRule(STANDARD, ReleaseNumber(1))
        assertEquals(Value.VBool(false), env.run(edition))
        assertEquals(Value.VBool(true), env.run(edition) { immediate("customer") { gold } })
    }

    @Test
    fun aConstructorPinIsNotMistakenForACapability() {
        val contract = Klein.checkContract(LENDING)
        val env =
            contract.implement {
                immediate("customer") { basic }
                immediate("creditScore") { scoreByTier(it) }
            }
        val edition = contract.compileRule("""creditScore(Customer(1, "gold")) >= 620""", ReleaseNumber(1))
        assertEquals(Value.VBool(true), env.run(edition))
    }

    @Test
    fun anEditionRunsAgainstAContractEditedInPlaceAtTheSameRevision() {
        val edition = Klein.checkContract(LENDING).compileRule(STANDARD, ReleaseNumber(1))
        val widened =
            Klein.checkContract(
                LENDING.replace("fun creditScore(c: Customer): Num", "fun creditScore(c: { id: Num, tier: String }): Num"),
            ).implement {
                immediate("customer") { gold }
                immediate("creditScore") { scoreByTier(it) }
            }
        assertEquals(Value.VBool(true), widened.run(edition))
    }
}
