package klein.host

import klein.Klein
import klein.ReleaseNumber
import klein.interp.Value
import klein.orFail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

private const val CREDIT_RULE = "creditScore(customer) >= 620"

private val LENDING_CONTRACT =
    """
    environment acme

    type Customer = Customer { id: Num, tier: String }

    customer: Customer
    fun creditScore(c: Customer): Num

    release 1
      Customer
      customer
      creditScore
    """.trimIndent()

private val gold = Value.VStruct("Customer", mapOf("id" to Value.VNum(1.0), "tier" to Value.VStr("gold")))

class SuspendingHandlerTest {
    private val contract = Klein.checkContract(LENDING_CONTRACT)

    private val edition = contract.compileRule(CREDIT_RULE, ReleaseNumber(1)).orFail()

    @Test
    fun aRunWaitsForAHandlerThatSuspendsAndRecordsItsAnswer() = runTest {
        val score = CompletableDeferred<Value>()
        val env = contract.implement(immediate("customer") { gold }, immediate("creditScore") { score.await() })
        val persisted = mutableListOf<LogEntry>()
        val run = async(start = CoroutineStart.UNDISPATCHED) { env.run(edition, persist = persisted::add) }
        assertFalse(run.isCompleted, "the run should wait while the handler waits")
        assertEquals(listOf<LogEntry>(LogEntry.Start(mapOf("customer" to gold))), persisted)
        score.complete(Value.VNum(700.0))
        val outcome = assertIs<RunOutcome.Completed>(run.await())
        assertEquals(Value.VBool(true), outcome.value)
        assertEquals(LogEntry.Reply(Call("creditScore", listOf(gold)), Value.VNum(700.0)), outcome.log.replies.single())
        assertEquals(outcome.log.entries, persisted)
    }

    @Test
    fun aHandlerThatNeverSuspendsStillAnswersTheRun() = runTest {
        val env = contract.implement(immediate("customer") { gold }, immediate("creditScore") { Value.VNum(700.0) })
        assertEquals(Value.VBool(true), assertIs<RunOutcome.Completed>(env.run(edition)).value)
    }

    @Test
    fun aHandlerWhoseWaitFailsEscapesWithNothingPersistedPastTheStart() = runTest {
        val score = CompletableDeferred<Value>()
        val env = contract.implement(immediate("customer") { gold }, immediate("creditScore") { score.await() })
        val persisted = mutableListOf<LogEntry>()
        val run = async(start = CoroutineStart.UNDISPATCHED) { runCatching { env.run(edition, persist = persisted::add) } }
        score.completeExceptionally(IllegalStateException("score service down"))
        val thrown = assertFailsWith<IllegalStateException> { run.await().getOrThrow() }
        assertEquals("score service down", thrown.message)
        assertEquals(listOf<LogEntry>(LogEntry.Start(mapOf("customer" to gold))), persisted)
    }
}
