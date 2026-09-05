package klein.host

import klein.Klein
import klein.KleinException
import klein.ReleaseNumber
import klein.interp.Value
import klein.orFail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame

private const val STANDARD = "creditScore(customer) >= 620"

private val LENDING =
    """
    type Customer = Customer { id: Num, tier: String }

    customer: Customer
    threshold: Num
    fun creditScore(c: Customer): Num

    release 1
      Customer
      customer
      threshold
      creditScore
    """.trimIndent()

private val gold = Value.VStruct("Customer", mapOf("id" to Value.VNum(1.0), "tier" to Value.VStr("gold")))
private val basic = Value.VStruct("Customer", mapOf("id" to Value.VNum(2.0), "tier" to Value.VStr("basic")))

private fun scoreByTier(args: List<Value>): Value {
    val customer = assertIs<Value.VStruct>(args.single())
    return Value.VNum(if (customer.fields["tier"] == Value.VStr("gold")) 700.0 else 500.0)
}

private fun makeScore(customer: Value) = LogEntry.Reply(Call("creditScore", listOf(customer)), scoreByTier(listOf(customer)))

/** The log side of the lending runs: what every outcome carries, and what `persist` and `transact` see. */
class EffectLogTest {
    private val contract = Klein.checkContract(LENDING)

    private var asks = 0

    private fun compile(rule: String) = contract.compileRule(rule, ReleaseNumber(1)).orFail()

    private fun makeHost(
        transact: (() -> Unit) -> Unit = { it() },
        vararg overrides: Pair<String, (List<Value>) -> Value>,
    ) = contract.implement(transact) {
        val handlers =
            mapOf<String, (List<Value>) -> Value>(
                "customer" to { gold },
                "threshold" to { Value.VNum(620.0) },
                "creditScore" to { scoreByTier(it) },
            ) + overrides
        handlers.forEach { (name, answer) -> immediate(name) { asks++; answer(it) } }
    }

    private fun makeLog(vararg inputs: Pair<String, Value>) = EffectLog(LogEntry.Start(mapOf(*inputs)))

    private fun assertDiverges(block: () -> RunOutcome): RunError.Diverged {
        val failure = assertFailsWith<RunFailure> { block() }
        return assertIs<RunError.Diverged>(failure.error)
    }

    @Test
    fun aCompletedRunCarriesStartRepliesAndResult() {
        val outcome = assertIs<RunOutcome.Completed>(makeHost().run(compile(STANDARD)))
        assertEquals(Value.VBool(true), outcome.value)
        assertEquals(
            listOf(
                LogEntry.Start(mapOf("customer" to gold)),
                makeScore(gold),
                LogEntry.Result(Value.VBool(true)),
            ),
            outcome.log.entries,
        )
    }

    @Test
    fun aRuleUsingNoValuesStartsWithAnEmptyStart() {
        val outcome = assertIs<RunOutcome.Completed>(makeHost().run(compile("1 + 1")))
        assertEquals(listOf(LogEntry.Start(emptyMap()), LogEntry.Result(Value.VNum(2.0))), outcome.log.entries)
    }

    @Test
    fun theInputPhasePersistsOneStartEntryForAllItsValues() {
        val persisted = mutableListOf<LogEntry>()
        val outcome = makeHost().run(compile("creditScore(customer) >= threshold"), persist = persisted::add)
        assertIs<RunOutcome.Completed>(outcome)
        assertEquals(LogEntry.Start(mapOf("customer" to gold, "threshold" to Value.VNum(620.0))), persisted.first())
        assertEquals(1, persisted.count { it is LogEntry.Start })
        assertEquals(outcome.log.entries, persisted)
    }

    @Test
    fun repliesAreRecordedInExecutionOrder() {
        val outcome = makeHost().run(compile("""creditScore(Customer(2, "basic")) + creditScore(customer)"""))
        assertIs<RunOutcome.Completed>(outcome)
        assertEquals(
            listOf(LogEntry.Start(mapOf("customer" to gold)), makeScore(basic), makeScore(gold), LogEntry.Result(Value.VNum(1200.0))),
            outcome.log.entries,
        )
    }

    @Test
    fun aRuntimeErrorInsideTheRuleIsRecordedAsAFailure() {
        val outcome = makeHost().run(compile("creditScore(customer) / 0"))
        val failed = assertIs<RunOutcome.Failed>(outcome)
        val cause = failed.diagnostics.single()
        assertEquals("Division by zero", cause.message)
        val failure = assertIs<LogEntry.Failure>(failed.log.entries.last())
        assertEquals(listOf(cause), failure.errors)
        assertEquals(listOf(LogEntry.Start(mapOf("customer" to gold)), makeScore(gold)), failed.log.entries.dropLast(1))
    }

    @Test
    fun aWrongTypedAnswerThrowsWithNothingPersistedPastTheStart() {
        val env = makeHost(overrides = arrayOf("creditScore" to { Value.VStr("hi") }))
        val persisted = mutableListOf<LogEntry>()
        val failure = assertFailsWith<RunFailure> { env.run(compile(STANDARD), persist = persisted::add) }
        assertIs<RunError.HandlerTypeMismatch>(failure.error)
        assertEquals(listOf<LogEntry>(LogEntry.Start(mapOf("customer" to gold))), persisted)
    }

    @Test
    fun aRunRegistrationForAnUndeclaredNameThrows() {
        val failure =
            assertFailsWith<RunFailure> {
                makeHost().run(compile(STANDARD), registerHandlers = { immediate("nope") { Value.VUnit } })
            }
        val problem = assertIs<RunError.InvalidRegistration>(failure.error).problems.single()
        assertEquals("'nope' revision 1 is registered but the contract does not declare it", problem.message)
        assertEquals(0, asks)
    }

    @Test
    fun aRunRegistrationDeferringAValueThrows() {
        val failure =
            assertFailsWith<RunFailure> {
                makeHost().run(compile(STANDARD), registerHandlers = { deferred("customer") {} })
            }
        val problem = assertIs<RunError.InvalidRegistration>(failure.error).problems.single()
        assertEquals("'customer' is a value, which is read at start and cannot be deferred", problem.message)
        assertEquals(0, asks)
    }

    @Test
    fun aPreFlightErrorThrowsBeforeAnythingRuns() {
        var asked = false
        val env = contract.implement { immediate("customer"); immediate("threshold"); immediate("creditScore") { asked = true; scoreByTier(it) } }
        val failure = assertFailsWith<RunFailure> { env.run(compile(STANDARD)) }
        assertIs<MissingHandler>(assertIs<RunError.UnservablePins>(failure.error).problems.single())
        assertFalse(asked, "pre-flight should reject the run before any capability is asked")
    }

    @Test
    fun aHandlerExceptionEscapesUnwrapped() {
        val boom = IllegalStateException("db down")
        val env = makeHost(overrides = arrayOf("creditScore" to { throw boom }))
        val thrown = assertFailsWith<IllegalStateException> { env.run(compile(STANDARD)) }
        assertSame(boom, thrown)
    }

    @Test
    fun aPersistThatThrowsEscapesWithTheEntryUnpersisted() {
        val persisted = mutableListOf<LogEntry>()
        val boom = IllegalStateException("disk full")
        val thrown =
            assertFailsWith<IllegalStateException> {
                makeHost().run(compile(STANDARD), persist = {
                    if (it is LogEntry.Reply) throw boom
                    persisted.add(it)
                })
            }
        assertSame(boom, thrown)
        assertEquals(listOf<LogEntry>(LogEntry.Start(mapOf("customer" to gold))), persisted)
    }

    @Test
    fun transactWrapsEachAskWithItsPersistAndTerminalsAlone() {
        val trace = mutableListOf<String>()
        val env =
            makeHost(
                transact = { block -> trace.add("begin"); block(); trace.add("commit") },
                "customer" to { trace.add("ask customer"); gold },
                "creditScore" to { trace.add("ask creditScore"); scoreByTier(it) },
            )
        val outcome = env.run(compile(STANDARD), persist = { trace.add("persist ${it::class.simpleName}") })
        assertIs<RunOutcome.Completed>(outcome)
        assertEquals(
            listOf(
                "begin", "ask customer", "persist Start", "commit",
                "begin", "ask creditScore", "persist Reply", "commit",
                "begin", "persist Result", "commit",
            ),
            trace,
        )
    }

    @Test
    fun aValueHandlerExceptionEscapesUnwrapped() {
        val boom = IllegalStateException("db down")
        val env = makeHost(overrides = arrayOf("customer" to { throw boom }))
        val thrown = assertFailsWith<IllegalStateException> { env.run(compile(STANDARD)) }
        assertSame(boom, thrown)
    }

    @Test
    fun aWrongTypedValueAnswerThrowsBeforeTheStartPersists() {
        val env = makeHost(overrides = arrayOf("customer" to { Value.VStr("nope") }))
        val persisted = mutableListOf<LogEntry>()
        val failure = assertFailsWith<RunFailure> { env.run(compile(STANDARD), persist = persisted::add) }
        assertIs<RunError.HandlerTypeMismatch>(failure.error)
        assertEquals(emptyList<LogEntry>(), persisted)
    }

    @Test
    fun aPersistThatThrowsOnTheStartEscapesWithNothingPersisted() {
        val boom = IllegalStateException("disk full")
        val thrown = assertFailsWith<IllegalStateException> { makeHost().run(compile(STANDARD), persist = { throw boom }) }
        assertSame(boom, thrown)
        assertEquals(1, asks)
    }

    @Test
    fun aPersistThatThrowsOnTheEndingEscapesWithTheEarlierEntriesPersisted() {
        val persisted = mutableListOf<LogEntry>()
        val boom = IllegalStateException("disk full")
        val thrown =
            assertFailsWith<IllegalStateException> {
                makeHost().run(compile(STANDARD), persist = {
                    if (it is LogEntry.Result) throw boom
                    persisted.add(it)
                })
            }
        assertSame(boom, thrown)
        assertEquals(listOf(LogEntry.Start(mapOf("customer" to gold)), makeScore(gold)), persisted)
    }

    @Test
    fun allInputReadsShareOneTransactionWithTheStartPersist() {
        val trace = mutableListOf<String>()
        val env =
            makeHost(
                transact = { block -> trace.add("begin"); block(); trace.add("commit") },
                "customer" to { trace.add("ask customer"); gold },
                "threshold" to { trace.add("ask threshold"); Value.VNum(620.0) },
            )
        val outcome = env.run(compile("creditScore(customer) >= threshold"), persist = { trace.add("persist ${it::class.simpleName}") })
        assertIs<RunOutcome.Completed>(outcome)
        assertEquals(
            listOf(
                "begin", "ask customer", "ask threshold", "persist Start", "commit",
                "begin", "persist Reply", "commit",
                "begin", "persist Result", "commit",
            ),
            trace,
        )
    }

    @Test
    fun aTransactThatNeverRunsItsBlockIsAnError() {
        val env = makeHost(transact = { })
        val thrown = assertFailsWith<IllegalStateException> { env.run(compile(STANDARD)) }
        assertEquals("transact returned without completing its block", thrown.message)
    }

    @Test
    fun aTransactThatThrowsEscapesUnwrapped() {
        var units = 0
        val boom = IllegalStateException("rollback")
        val env = makeHost(transact = { block -> block(); if (++units == 2) throw boom })
        val thrown = assertFailsWith<IllegalStateException> { env.run(compile(STANDARD)) }
        assertSame(boom, thrown)
    }

    @Test
    fun appendingPastTheLogsEndingIsRefused() {
        val log = EffectLog(LogEntry.Start(emptyMap())) + LogEntry.Result(Value.VNum(1.0))
        assertEquals(2, log.entries.size)
        assertFailsWith<IllegalArgumentException> { log + LogEntry.Result(Value.VNum(2.0)) }
    }

    @Test
    fun appendingASecondStartEntryIsRefused() {
        val log = EffectLog(LogEntry.Start(emptyMap()))
        assertFailsWith<IllegalArgumentException> { log + LogEntry.Start(emptyMap()) }
    }

    @Test
    fun replayingACompletedLogReproducesTheOutcomeWithoutAskingTheHost() {
        val rule = compile("""creditScore(Customer(2, "basic")) + creditScore(customer) - threshold""")
        val live = assertIs<RunOutcome.Completed>(makeHost().run(rule))
        asks = 0
        val persisted = mutableListOf<LogEntry>()
        val replayed = assertIs<RunOutcome.Completed>(makeHost().run(rule, log = live.log, persist = persisted::add))
        assertEquals(live.value, replayed.value)
        assertEquals(live.log, replayed.log)
        assertEquals(0, asks)
        assertEquals(emptyList<LogEntry>(), persisted)
    }

    @Test
    fun replayingAFailedLogReproducesTheFailureWithoutPersisting() {
        val rule = compile("creditScore(customer) / 0")
        val live = assertIs<RunOutcome.Failed>(makeHost().run(rule))
        asks = 0
        val persisted = mutableListOf<LogEntry>()
        val replayed = assertIs<RunOutcome.Failed>(makeHost().run(rule, log = live.log, persist = persisted::add))
        assertEquals(live.diagnostics, replayed.diagnostics)
        assertEquals(live.log, replayed.log)
        assertEquals(0, asks)
        assertEquals(emptyList<LogEntry>(), persisted)
    }

    @Test
    fun aPartialLogContinuesLiveAndPersistsOnlyTheNewEntries() {
        val persisted = mutableListOf<LogEntry>()
        val outcome = makeHost().run(compile(STANDARD), log = makeLog("customer" to gold), persist = persisted::add)
        assertIs<RunOutcome.Completed>(outcome)
        assertEquals(1, asks)
        assertEquals(listOf(makeScore(gold), LogEntry.Result(Value.VBool(true))), persisted)
        assertEquals(listOf(LogEntry.Start(mapOf("customer" to gold))) + persisted, outcome.log.entries)
    }

    @Test
    fun aRecordedFailureTheRunDoesNotReproduceDiverges() {
        val log = makeLog("customer" to gold) + makeScore(gold) + LogEntry.Failure(listOf(Diagnostic("Division by zero", null)))
        val diverged = assertDiverges { makeHost().run(compile(STANDARD), log = log) }
        assertEquals(2, diverged.at)
        assertEquals(0, asks)
    }

    @Test
    fun aValueMissingFromTheStartEntryDiverges() {
        val diverged = assertDiverges { makeHost().run(compile(STANDARD), log = makeLog()) }
        assertEquals(0, diverged.at)
        assertEquals(Call("customer", emptyList()), diverged.call)
        assertEquals("the start entry to hold 'customer'", diverged.expected)
        assertEquals("a start entry without it", diverged.got)
        assertEquals(0, asks)
    }

    @Test
    fun aCallNameMismatchDiverges() {
        val log = makeLog("customer" to gold) + LogEntry.Reply(Call("threshold", emptyList()), Value.VNum(700.0))
        val diverged = assertDiverges { makeHost().run(compile(STANDARD), log = log) }
        assertEquals(1, diverged.at)
        assertEquals(Call("creditScore", listOf(gold)), diverged.call)
        assertEquals("a call to threshold()", diverged.expected)
        assertEquals("""a call to creditScore(Customer(1, "gold"))""", diverged.got)
        assertEquals(0, asks)
    }

    @Test
    fun anArgumentMismatchDiverges() {
        val log = makeLog("customer" to gold) + makeScore(basic)
        val diverged = assertDiverges { makeHost().run(compile(STANDARD), log = log) }
        assertEquals(1, diverged.at)
        assertEquals(Call("creditScore", listOf(gold)), diverged.call)
        assertEquals(0, asks)
    }

    @Test
    fun anOutcomeReachedWithUnconsumedRepliesDiverges() {
        val log = makeLog("customer" to gold) + makeScore(gold) + makeScore(basic)
        val diverged = assertDiverges { makeHost().run(compile(STANDARD), log = log) }
        assertEquals(2, diverged.at)
        assertEquals(null, diverged.call)
        assertEquals("the result true", diverged.got)
        assertEquals(0, asks)
    }

    @Test
    fun aStartValueTheRunNeverAsksForIsIgnored() {
        val log = makeLog("customer" to gold, "threshold" to Value.VNum(620.0)) + makeScore(gold)
        val outcome = assertIs<RunOutcome.Completed>(makeHost().run(compile(STANDARD), log = log))
        assertEquals(Value.VBool(true), outcome.value)
        assertEquals(0, asks)
    }

    @Test
    fun aRunThatFailsWhereTheLogExpectsACallDiverges() {
        val log = makeLog("customer" to gold) + makeScore(gold)
        val diverged = assertDiverges { makeHost().run(compile("1 / 0 + creditScore(customer)"), log = log) }
        assertEquals(1, diverged.at)
        assertEquals(null, diverged.call)
        assertEquals("the failure 'Division by zero'", diverged.got)
        assertEquals(0, asks)
    }

    @Test
    fun aResultDifferentFromTheRecordedOneDiverges() {
        val log = makeLog("customer" to gold) + makeScore(gold) + LogEntry.Result(Value.VBool(false))
        val diverged = assertDiverges { makeHost().run(compile(STANDARD), log = log) }
        assertEquals(2, diverged.at)
        assertEquals(null, diverged.call)
        assertEquals("the recorded result false", diverged.expected)
        assertEquals("the result true", diverged.got)
        assertEquals(0, asks)
    }

    @Test
    fun aStartValueOfTheWrongTypeFailsPreFlight() {
        val failure = assertFailsWith<RunFailure> { makeHost().run(compile(STANDARD), log = makeLog("customer" to Value.VStr("x"))) }
        val mismatch = assertIs<RunError.LogTypeMismatch>(failure.error).problems.single()
        assertEquals(0, mismatch.at)
        assertEquals("customer", mismatch.name)
        assertEquals(0, asks)
    }

    @Test
    fun aReplyAnswerOfTheWrongTypeFailsPreFlight() {
        val log = makeLog("customer" to gold) + LogEntry.Reply(Call("creditScore", listOf(gold)), Value.VStr("hi"))
        val failure = assertFailsWith<RunFailure> { makeHost().run(compile(STANDARD), log = log) }
        val mismatch = assertIs<RunError.LogTypeMismatch>(failure.error).problems.single()
        assertEquals(1, mismatch.at)
        assertEquals("creditScore", mismatch.name)
        assertEquals(0, asks)
    }

    @Test
    fun anEntryForAnUnpinnedNamePassesPreFlightAndDivergesPositionally() {
        val log = makeLog("customer" to gold) + LogEntry.Reply(Call("somethingElse", emptyList()), Value.VStr("whatever"))
        val diverged = assertDiverges { makeHost().run(compile(STANDARD), log = log) }
        assertEquals(1, diverged.at)
        assertEquals(0, asks)
    }

    @Test
    fun recordedArgumentsAreNotTypeCheckedOnlyCompared() {
        val log = makeLog("customer" to gold) + LogEntry.Reply(Call("creditScore", listOf(Value.VStr("junk"))), Value.VNum(700.0))
        assertEquals(1, assertDiverges { makeHost().run(compile(STANDARD), log = log) }.at)
    }

    private var initiations = 0

    private fun makeParkingHost(
        transact: (() -> Unit) -> Unit = { it() },
        initiate: (Call) -> Unit = {},
    ) = contract.implement(transact) {
        immediate("customer") { asks++; gold }
        immediate("threshold") { asks++; Value.VNum(620.0) }
        deferred("creditScore") { initiations++; initiate(it) }
    }

    @Test
    fun aDeferredAskParksTheRunWithTheCallAndTheLogSoFar() {
        val parked = assertIs<RunOutcome.Parked>(makeParkingHost().run(compile(STANDARD)))
        assertEquals(Call("creditScore", listOf(gold)), parked.call)
        assertEquals(listOf(LogEntry.Start(mapOf("customer" to gold))), parked.log.entries)
        assertEquals(1, initiations)
    }

    @Test
    fun parkingInvokesInitiationInsideTransactAndRecordsNothing() {
        val trace = mutableListOf<String>()
        val env =
            makeParkingHost(
                transact = { block -> trace.add("begin"); block(); trace.add("commit") },
                initiate = { trace.add("initiate ${it.print()}") },
            )
        val outcome = env.run(compile(STANDARD), persist = { trace.add("persist ${it::class.simpleName}") })
        assertIs<RunOutcome.Parked>(outcome)
        assertEquals(
            listOf(
                "begin", "persist Start", "commit",
                "begin", "initiate creditScore(Customer(1, \"gold\"))", "commit",
            ),
            trace,
        )
    }

    @Test
    fun resumingAParkedRunWithToReplyCompletes() {
        val env = makeParkingHost()
        val parked = assertIs<RunOutcome.Parked>(env.run(compile(STANDARD)))
        val reply = parked.toReply(Value.VNum(700.0))
        assertEquals(LogEntry.Reply(parked.call, Value.VNum(700.0)), reply)
        val persisted = mutableListOf<LogEntry>()
        val resumed = assertIs<RunOutcome.Completed>(env.run(compile(STANDARD), log = parked.log + reply, persist = persisted::add))
        assertEquals(Value.VBool(true), resumed.value)
        assertEquals(parked.log.entries + reply + LogEntry.Result(Value.VBool(true)), resumed.log.entries)
        assertEquals(listOf<LogEntry>(LogEntry.Result(Value.VBool(true))), persisted)
        assertEquals(1, initiations)
    }

    @Test
    fun resumingOnAFreshEnvironmentBuiltFromTheSameContractCompletes() {
        val parked = assertIs<RunOutcome.Parked>(makeParkingHost().run(compile(STANDARD)))
        val fresh = Klein.checkContract(LENDING)
        val resumed =
            fresh.implement { immediate("customer") { gold }; immediate("threshold") { Value.VNum(620.0) }; deferred("creditScore") {} }
                .run(fresh.compileRule(STANDARD, ReleaseNumber(1)).orFail(), log = parked.log + parked.toReply(Value.VNum(500.0)))
        assertEquals(Value.VBool(false), assertIs<RunOutcome.Completed>(resumed).value)
    }

    @Test
    fun reRunningAnUnresumedParkedLogParksAndInitiatesAgain() {
        val env = makeParkingHost()
        val parked = assertIs<RunOutcome.Parked>(env.run(compile(STANDARD)))
        val again = assertIs<RunOutcome.Parked>(env.run(compile(STANDARD), log = parked.log))
        assertEquals(parked.call, again.call)
        assertEquals(parked.log, again.log)
        assertEquals(2, initiations)
    }

    @Test
    fun aDeferredCapabilityAnsweredByReplayDoesNotInitiate() {
        val outcome = makeParkingHost().run(compile(STANDARD), log = makeLog("customer" to gold) + makeScore(gold))
        assertEquals(Value.VBool(true), assertIs<RunOutcome.Completed>(outcome).value)
        assertEquals(0, initiations)
    }

    @Test
    fun aDeferredRegistrationSatisfiesCompletenessAndCountsAsAHandlerForThePinCheck() {
        val env = contract.implement { immediate("customer"); immediate("threshold"); deferred("creditScore") {} }
        val outcome = env.run(compile(STANDARD), registerHandlers = { immediate("customer") { gold } })
        assertIs<RunOutcome.Parked>(outcome)
    }

    @Test
    fun aValueCannotBeDeferred() {
        val thrown =
            assertFailsWith<KleinException> {
                contract.implement { deferred("customer") {}; immediate("threshold"); immediate("creditScore") }
            }
        assertEquals("'customer' is a value, which is read at start and cannot be deferred", thrown.errors.first().message)
    }

    @Test
    fun anInitiationExceptionEscapesUnwrapped() {
        val boom = IllegalStateException("queue down")
        val thrown = assertFailsWith<IllegalStateException> { makeParkingHost(initiate = { throw boom }).run(compile(STANDARD)) }
        assertSame(boom, thrown)
    }

    @Test
    fun aWellTypedLogPassesPreFlightAndReplays() {
        val log = makeLog("customer" to gold) + LogEntry.Reply(Call("creditScore", listOf(gold)), Value.VNum(650.0))
        val outcome = assertIs<RunOutcome.Completed>(makeHost().run(compile(STANDARD), log = log))
        assertEquals(Value.VBool(true), outcome.value)
        assertEquals(0, asks)
    }
}
