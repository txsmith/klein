package klein.check

import klein.check.Type.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A declaration without a body only belongs in a capability contract. Inside an ordinary program
 * the checker rejects it — but still binds its declared type, so nothing downstream cascades into
 * spurious "unbound variable" errors.
 */
class DeclarationTypeCheckTest {
    @Test
    fun funDeclarationIsRejected() {
        val error = infer("fun creditCheck(c: Num): Num").errors.single()
        assertIs<TypeError.DeclarationWithoutBody>(error)
        assertEquals("creditCheck", error.name)
    }

    @Test
    fun valDeclarationIsRejected() {
        val error = infer("maxRetries: Num").errors.single()
        assertIs<TypeError.DeclarationWithoutBody>(error)
        assertEquals("maxRetries", error.name)
    }

    @Test
    fun errorMessageNamesTheBindingAndPointsAtContracts() {
        val error = infer("maxRetries: Num").errors.single()
        assertTrue("maxRetries" in error.message, error.message)
        assertTrue("contract" in error.message, error.message)
    }

    @Test
    fun declaredValueTypeIsStillBound() {
        val result =
            infer(
                """
                maxRetries: Num
                maxRetries + 1
                """.trimIndent(),
            )
        assertEquals(TNum, result.type)
        assertIs<TypeError.DeclarationWithoutBody>(result.errors.single())
    }

    @Test
    fun declaredFunctionTypeIsStillBound() {
        val result =
            infer(
                """
                fun creditCheck(c: Num): Num
                creditCheck(1)
                """.trimIndent(),
            )
        assertEquals(TNum, result.type)
        assertIs<TypeError.DeclarationWithoutBody>(result.errors.single())
    }

    @Test
    fun declaredFunctionStillChecksItsCallSites() {
        val result =
            infer(
                """
                fun creditCheck(c: Num): Num
                creditCheck("nope")
                """.trimIndent(),
            )
        assertEquals(2, result.errors.size)
        assertIs<TypeError.DeclarationWithoutBody>(result.errors[0])
        assertIs<TypeError.TypeMismatch>(result.errors[1])
    }

    @Test
    fun declarationCanReferToADeclaredType() {
        val result =
            infer(
                """
                type Customer = Customer { id: Num, name: String }
                fun creditCheck(c: Customer): Num
                creditCheck(Customer(1, "ada"))
                """.trimIndent(),
            )
        assertEquals(TNum, result.type)
        assertIs<TypeError.DeclarationWithoutBody>(result.errors.single())
    }

    @Test
    fun everyDeclarationIsReported() {
        val result =
            infer(
                """
                fun creditCheck(c: Num): Num
                maxRetries: Num
                1
                """.trimIndent(),
            )
        assertEquals(2, result.errors.size)
        assertTrue(result.errors.all { it is TypeError.DeclarationWithoutBody }, "${result.errors}")
    }

    @Test
    fun declarationInsideABlockIsRejected() {
        val result =
            infer(
                """
                result =
                    limit: Num
                    limit
                result
                """.trimIndent(),
            )
        assertEquals(TNum, result.type)
        assertIs<TypeError.DeclarationWithoutBody>(result.errors.single())
    }
}
