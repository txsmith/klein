package klein.example

import klein.ReleaseNumber
import klein.interp.Value
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class LendingHostTest {
    @Test
    fun theLendingRuleDecidesGoldCustomersAreApproved() {
        val host = LendingHost(File("examples/lending.contract").readText())
        val result = host.decide(File("examples/lending-rule.klein").readText(), ReleaseNumber(2))
        assertEquals(Value.VBool(true), result)
    }
}
