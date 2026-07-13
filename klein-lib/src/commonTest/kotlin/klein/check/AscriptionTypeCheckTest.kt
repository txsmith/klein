package klein.check

import klein.check.Type.*
import klein.types.TypeError
import kotlin.test.Test
import kotlin.test.assertEquals

/** Ascription `(e : T)`: checks `e` against `T`, accepts subtypes, and can't introduce a type var. */
class AscriptionTypeCheckTest {
    @Test
    fun acceptsSubtype() =
        assertInfersType(
            TRef("Animal", emptyList()),
            """
            type Animal = Dog { name: String } | Cat { name: String }
            (Dog("Rex") : Animal)
            """.trimIndent(),
        )

    @Test
    fun mismatch() = assertMismatch("String", "Num", "(\"hello\" : Num)")

    @Test
    fun cannotIntroduceNewTypeVar() {
        val e =
            infer(
                """
                fun f(x: Num) = (x : 'B)
                f
                """.trimIndent(),
            ).errors.filterIsInstance<TypeError.UnboundVariable>().single()
        assertEquals("B", e.name)
    }
}
