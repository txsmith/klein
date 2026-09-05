package klein.example

import klein.Klein
import klein.ReleaseNumber
import klein.check.contract.EnvironmentContract
import klein.host.Environment
import klein.host.RunOutcome
import klein.host.implement
import klein.interp.Value
import java.io.File

private fun customer(vararg fields: Pair<String, Value>) = Value.VStruct("Customer", mapOf(*fields))

private fun scoreByTier(args: List<Value>): Value {
    val tier = (args.single() as Value.VStruct).fields["tier"]
    return Value.VNum(if (tier == Value.VStr("gold")) 720.0 else 580.0)
}

class LendingHost(
    contractSource: String,
) {
    private val contract: EnvironmentContract = Klein.checkContract(contractSource)

    private val environment: Environment =
        contract.implement {
            immediate("creditScore") { Value.VNum(650.0) }
            immediate("creditScore/2") { scoreByTier(it) }
            immediate("customer")
            immediate("customer/2")
        }

    fun decide(
        ruleSource: String,
        release: ReleaseNumber,
    ): RunOutcome {
        val compiled = contract.compileRule(ruleSource, release)
        val edition =
            compiled.output
                ?: throw IllegalArgumentException("the rule does not compile:\n" + compiled.diagnostics.joinToString("\n") { it.message })
        return environment.run(edition) {
            immediate("customer") {
                customer("id" to Value.VNum(1.0), "name" to Value.VStr("Acme"))
            }
            immediate("customer/2") {
                customer("id" to Value.VNum(1.0), "name" to Value.VStr("Acme"), "tier" to Value.VStr("gold"))
            }
        }
    }
}

fun main(args: Array<String>) {
    if (args.size != 3) {
        System.err.println("usage: klein-example-host <contract> <rule> <release>")
        System.exit(2)
    }
    val host = LendingHost(File(args[0]).readText())
    when (val outcome = host.decide(File(args[1]).readText(), ReleaseNumber(args[2].toInt()))) {
        is RunOutcome.Completed -> println(Value.print(outcome.value))
        is RunOutcome.Failed -> {
            outcome.diagnostics.forEach { System.err.println(it.message) }
            System.exit(1)
        }
        is RunOutcome.Parked -> {
            System.err.println("parked at ${outcome.call.print()}")
            System.exit(1)
        }
    }
}
