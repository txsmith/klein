package klein.host

import klein.Klein
import klein.ReleaseNumber
import klein.RevisionNumber
import klein.check.contract.Edition
import klein.interp.Value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

private const val CREDIT_RULE = "creditScore(customer) >= 620"

private val LENDING_CONTRACT =
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

private fun Environment.runToValue(
    edition: Edition,
    registerHandlers: Registry.() -> Unit = {},
): Value = assertIs<RunOutcome.Completed>(run(edition, registerHandlers = registerHandlers)).value

private fun Environment.runToFailure(edition: Edition): RunError = assertFailsWith<RunFailure> { run(edition) }.error

/** The execution narrative: the editions [LendingExampleTest] only checks, run against a live host. */
class RunAgainstReleaseTest {
    @Test
    fun aRuleCallingACapabilityRunsToAValue() {
        val contract = Klein.checkContract(LENDING_CONTRACT)
        val env =
            contract.implement {
                immediate("customer") { gold }
                immediate("creditScore") { scoreByTier(it) }
            }
        assertEquals(Value.VBool(true), env.runToValue(contract.compileRule(CREDIT_RULE, ReleaseNumber(1))))
    }

    @Test
    fun aNullaryCapabilityIsAskedExactlyOnce() {
        val contract = Klein.checkContract(LENDING_CONTRACT)
        var asks = 0
        val env =
            contract.implement {
                immediate("customer") { asks++; gold }
                immediate("creditScore") { scoreByTier(it) }
            }
        val edition = contract.compileRule("creditScore(customer) + creditScore(customer)", ReleaseNumber(1))
        assertEquals(Value.VNum(1400.0), env.runToValue(edition))
        assertEquals(1, asks)
    }

    @Test
    fun callingThroughABindingMatchesTheDirectCall() {
        val contract = Klein.checkContract(LENDING_CONTRACT)
        val env =
            contract.implement {
                immediate("customer") { gold }
                immediate("creditScore") { scoreByTier(it) }
            }
        val direct = env.runToValue(contract.compileRule(CREDIT_RULE, ReleaseNumber(1)))
        val indirect = env.runToValue(contract.compileRule("f = creditScore\nf(customer) >= 620", ReleaseNumber(1)))
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
        assertEquals(Value.VBool(false), env.runToValue(contract.compileRule("creditScore(1) >= 620", ReleaseNumber(1))))
        assertEquals(Value.VBool(true), env.runToValue(contract.compileRule("creditScore(1) >= 620", ReleaseNumber(2))))
    }

    @Test
    fun anUnregisteredPinIsUnservedPinBeforeTheInterpreterStarts() {
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

        val pin = assertIs<UnservedPin>(assertIs<RunError.UnservablePins>(drained.runToFailure(edition)).problems.single())
        assertEquals("creditScore", pin.name)
        assertEquals(RevisionNumber(2), pin.revision)
        assertFalse(asked, "the pin check should reject the edition before any capability is asked")
    }

    @Test
    fun aRunThatForgetsASuppliedCapabilityIsMissingImplementation() {
        val contract = Klein.checkContract(LENDING_CONTRACT)
        val env =
            contract.implement {
                immediate("customer")
                immediate("creditScore") { scoreByTier(it) }
            }
        val edition = contract.compileRule(CREDIT_RULE, ReleaseNumber(1))

        val missing = assertIs<MissingHandler>(assertIs<RunError.UnservablePins>(env.runToFailure(edition)).problems.single())
        assertEquals("customer", missing.name)
        assertEquals(RevisionNumber(1), missing.revision)

        assertEquals(Value.VBool(true), env.runToValue(edition) { immediate("customer") { gold } })
    }

    @Test
    fun aRunSuppliedImplementationWinsOverTheBootRegisteredOne() {
        val contract = Klein.checkContract(LENDING_CONTRACT)
        val env =
            contract.implement {
                immediate("customer") { basic }
                immediate("creditScore") { scoreByTier(it) }
            }
        val edition = contract.compileRule(CREDIT_RULE, ReleaseNumber(1))
        assertEquals(Value.VBool(false), env.runToValue(edition))
        assertEquals(Value.VBool(true), env.runToValue(edition) { immediate("customer") { gold } })
    }

    @Test
    fun aConstructorPinIsNotMistakenForACapability() {
        val contract = Klein.checkContract(LENDING_CONTRACT)
        val env =
            contract.implement {
                immediate("customer") { basic }
                immediate("creditScore") { scoreByTier(it) }
            }
        val edition = contract.compileRule("""creditScore(Customer(1, "gold")) >= 620""", ReleaseNumber(1))
        assertEquals(Value.VBool(true), env.runToValue(edition))
    }

    @Test
    fun aHandlerAnsweringTheWrongTypeIsCaughtAtTheCallSite() {
        val contract = Klein.checkContract(LENDING_CONTRACT)
        val env =
            contract.implement {
                immediate("customer") { gold }
                immediate("creditScore") { Value.VStr("hi") }
            }
        val error = env.runToFailure(contract.compileRule("1 + $CREDIT_RULE", ReleaseNumber(1)))
        val mismatch = assertIs<RunError.HandlerTypeMismatch>(error)
        assertEquals("creditScore", mismatch.call)
        assertEquals("'creditScore' answered with String where the contract declares Num", mismatch.message)
    }

    @Test
    fun aCustomerWithAWrongFieldTypeIsCaughtAtTheBoundary() {
        val contract = Klein.checkContract(LENDING_CONTRACT)
        val env =
            contract.implement {
                immediate("customer") { Value.VStruct("Customer", mapOf("id" to Value.VStr("one"), "tier" to Value.VStr("gold"))) }
                immediate("creditScore") { scoreByTier(it) }
            }
        val error = env.runToFailure(contract.compileRule(CREDIT_RULE, ReleaseNumber(1)))
        val mismatch = assertIs<RunError.HandlerTypeMismatch>(error)
        assertEquals("customer", mismatch.call)
        assertEquals("'customer' answered with { id: String, tier: String } where the contract declares Customer", mismatch.message)
    }

    @Test
    fun aRecordAnswerWithExtraFieldsPasses() {
        val contract =
            Klein.checkContract(
                """
                customer: { id: Num, tier: String }

                release 1
                  customer
                """.trimIndent(),
            )
        val env =
            contract.implement {
                immediate("customer") {
                    Value.VStruct(null, mapOf("id" to Value.VNum(1.0), "tier" to Value.VStr("gold"), "region" to Value.VStr("EU")))
                }
            }
        assertEquals(Value.VStr("gold"), env.runToValue(contract.compileRule("customer.tier", ReleaseNumber(1))))
    }

    @Test
    fun aHandlerAnsweringAClosureIsRejectedAsAFunction() {
        val closure =
            Klein
                .tokenize("|x -> x|")
                .andThen(Klein::parse)
                .andThen(Klein::lower)
                .andThen(Klein::execute)
                .output!!
        val contract = Klein.checkContract(LENDING_CONTRACT)
        val env =
            contract.implement {
                immediate("customer") { gold }
                immediate("creditScore") { closure }
            }
        val error = env.runToFailure(contract.compileRule(CREDIT_RULE, ReleaseNumber(1)))
        val mismatch = assertIs<RunError.HandlerTypeMismatch>(error)
        assertEquals("'creditScore' answered with a function where the contract declares Num", mismatch.message)
    }

    @Test
    fun anEditionRunsAgainstAContractEditedInPlaceAtTheSameRevision() {
        val edition = Klein.checkContract(LENDING_CONTRACT).compileRule(CREDIT_RULE, ReleaseNumber(1))
        val widened =
            Klein.checkContract(
                LENDING_CONTRACT.replace("fun creditScore(c: Customer): Num", "fun creditScore(c: { id: Num, tier: String }): Num"),
            ).implement {
                immediate("customer") { gold }
                immediate("creditScore") { scoreByTier(it) }
            }
        assertEquals(Value.VBool(true), widened.runToValue(edition))
    }

    @Test
    fun aCallWhoseArgumentNoLongerFitsTheEditedContractIsCallTypeMismatch() {
        val edition = Klein.checkContract(LENDING_CONTRACT).compileRule(CREDIT_RULE, ReleaseNumber(1))
        var asked = false
        val narrowed =
            Klein.checkContract(
                LENDING_CONTRACT.replace("fun creditScore(c: Customer): Num", "fun creditScore(c: String): Num"),
            ).implement {
                immediate("customer") { gold }
                immediate("creditScore") { asked = true; Value.VNum(700.0) }
            }
        val persisted = mutableListOf<LogEntry>()
        val failure = assertFailsWith<RunFailure> { narrowed.run(edition, persist = persisted::add) }
        val mismatch = assertIs<RunError.CallTypeMismatch>(failure.error)
        assertEquals("'creditScore' was called with Customer where the contract declares String", mismatch.message)
        assertFalse(asked, "the argument check should reject the call before the handler runs")
        assertEquals(listOf<LogEntry>(LogEntry.Start(mapOf("customer" to gold))), persisted)
    }

    @Test
    fun aCallWhoseArityNoLongerMatchesTheEditedContractIsCallTypeMismatch() {
        val edition = Klein.checkContract(LENDING_CONTRACT).compileRule(CREDIT_RULE, ReleaseNumber(1))
        var asked = false
        val widened =
            Klein.checkContract(
                LENDING_CONTRACT.replace("fun creditScore(c: Customer): Num", "fun creditScore(c: Customer, floor: Num): Num"),
            ).implement {
                immediate("customer") { gold }
                immediate("creditScore") { asked = true; Value.VNum(700.0) }
            }
        val persisted = mutableListOf<LogEntry>()
        val failure = assertFailsWith<RunFailure> { widened.run(edition, persist = persisted::add) }
        val mismatch = assertIs<RunError.CallTypeMismatch>(failure.error)
        assertEquals("'creditScore' was called with 1 argument where the contract declares 2", mismatch.message)
        assertFalse(asked, "the arity check should reject the call before the handler runs")
        assertEquals(listOf<LogEntry>(LogEntry.Start(mapOf("customer" to gold))), persisted)
    }

    @Test
    fun aDeferredInitiationIsNotRunWhenTheArgumentNoLongerFits() {
        val edition = Klein.checkContract(LENDING_CONTRACT).compileRule(CREDIT_RULE, ReleaseNumber(1))
        var initiated = false
        val narrowed =
            Klein.checkContract(
                LENDING_CONTRACT.replace("fun creditScore(c: Customer): Num", "fun creditScore(c: String): Num"),
            ).implement {
                immediate("customer") { gold }
                deferred("creditScore") { initiated = true }
            }
        val failure = assertFailsWith<RunFailure> { narrowed.run(edition) }
        assertIs<RunError.CallTypeMismatch>(failure.error)
        assertFalse(initiated, "the argument check should reject the call before the initiation runs")
    }

    @Test
    fun replayedCallsAreNotArgumentCheckedAgainstTheEditedContract() {
        val original = Klein.checkContract(LENDING_CONTRACT)
        val edition = original.compileRule(CREDIT_RULE, ReleaseNumber(1))
        val recording =
            original.implement {
                immediate("customer") { gold }
                immediate("creditScore") { scoreByTier(it) }
            }
        val live = assertIs<RunOutcome.Completed>(recording.run(edition))
        var asked = false
        val narrowed =
            Klein.checkContract(
                LENDING_CONTRACT.replace("fun creditScore(c: Customer): Num", "fun creditScore(c: String): Num"),
            ).implement {
                immediate("customer") { gold }
                immediate("creditScore") { asked = true; Value.VNum(700.0) }
            }
        val replayed = assertIs<RunOutcome.Completed>(narrowed.run(edition, log = live.log))
        assertEquals(live.value, replayed.value)
        assertFalse(asked, "replay should answer from the log without asking the host")
    }
}
