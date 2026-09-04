package klein.example

import klein.ReleaseNumber
import klein.host.LogEntry
import klein.host.RunOutcome
import klein.interp.Value
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LendingHostTest {
    @Test
    fun theLendingRuleDecidesGoldCustomersAreApproved() {
        val host = LendingHost(File("examples/lending.contract").readText())
        val outcome = assertIs<RunOutcome.Completed>(host.decide(File("examples/lending-rule.klein").readText(), ReleaseNumber(2)))
        assertEquals(Value.VBool(true), outcome.value)
        assertIs<LogEntry.Start>(outcome.log.entries.first())
        assertEquals(LogEntry.Result(Value.VBool(true)), outcome.log.entries.last())
    }
}
