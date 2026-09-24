package klein.check

import klein.Checked
import klein.Klein
import klein.assertRejected
import klein.core.parseProgram
import klein.orFail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private fun printedBindings(source: String): Map<String, String> =
    Klein
        .checkBindings(parseProgram(source.trimIndent()))
        .orFail()
        .mapValues { Type.print(it.value) }

class CheckBindingsTest {
    @Test
    fun aValueBindingHasItsType() {
        assertEquals(mapOf("x" to "Num"), printedBindings("x = 1"))
    }

    @Test
    fun aFunctionHasItsFunctionType() {
        assertEquals(mapOf("double" to "(Num) -> Num"), printedBindings("fun double(n: Num): Num = n * 2"))
    }

    @Test
    fun aPatternBindingNamesEachBoundName() {
        assertEquals(
            mapOf("name" to "String", "age" to "Num"),
            printedBindings("""{ name, age } = { name = "Ada", age = 36 }"""),
        )
    }

    @Test
    fun aTypeDefinitionAndATrailingExpressionBindNoName() {
        val bindings =
            printedBindings(
                """
                type Money = Money { value: Num }
                price = Money(3)
                price.value
                """,
            )
        assertEquals(mapOf("price" to "Money"), bindings)
    }

    @Test
    fun namesComeInProgramOrder() {
        val bindings =
            printedBindings(
                """
                fun b(): Num = a() + 1
                fun a(): Num = 1
                c = b()
                """,
            )
        assertEquals(listOf("b", "a", "c"), bindings.keys.toList())
    }

    @Test
    fun aProgramWithATypeErrorIsRejected() {
        val result = Klein.checkBindings(parseProgram("x = 1 + \"a\""))
        assertIs<TypeError>(result.assertRejected().single())
    }

    @Test
    fun theGivenEnvironmentIsLeftUnchanged() {
        val env: RuleEnv = TypeEnv.empty()
        assertIs<Checked.Accepted<*>>(Klein.checkBindings(parseProgram("x = 1"), env))
        assertNull(env.lookup("x"))
    }
}
