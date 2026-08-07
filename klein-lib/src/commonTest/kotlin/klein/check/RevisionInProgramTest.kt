package klein.check

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Revision syntax is contract-only. A program that writes `/N` — in a type position or anywhere
 * else — is rejected with a dedicated error, and a written `/1` is just as illegal as `/2`: the
 * rejection is about the syntax, not the number.
 */
class RevisionInProgramTest {
    private fun assertRevisionRejected(
        src: String,
        name: String,
        revision: Int,
        env: TypeEnv = TypeEnv.empty(),
    ) {
        val errors = infer(src, env).errors.filterIsInstance<TypeError.RevisionInProgram>()
        assertTrue(errors.isNotEmpty(), "expected RevisionInProgram for $name/$revision in: $src")
        assertEquals(name, errors.first().name)
        assertEquals(revision, errors.first().revision)
    }

    // --- type references ---

    @Test
    fun aRevisionedBindingAnnotationIsRejected() {
        assertRevisionRejected("x: Customer/2 = 1", "Customer", 2)
    }

    @Test
    fun aRevisionedFunParamAnnotationIsRejected() {
        assertRevisionRejected("fun f(c: Customer/2): Num = 1", "Customer", 2)
    }

    @Test
    fun aRevisionedFunReturnAnnotationIsRejected() {
        assertRevisionRejected("fun f(x: Num): Customer/2 = x", "Customer", 2)
    }

    @Test
    fun aRevisionedLambdaParamAnnotationIsRejected() {
        assertRevisionRejected("f = |c: Customer/2 -> 1|", "Customer", 2)
    }

    @Test
    fun aRevisionedAscriptionIsRejected() {
        assertRevisionRejected("(1 : Num/2)", "Num", 2)
    }

    @Test
    fun aRevisionedTypeArgumentIsRejected() {
        val src =
            """
            type Box<'A> = Box { value: 'A }
            fun f(b: Box<Num/2>): Num = 1
            """.trimIndent()
        assertRevisionRejected(src, "Num", 2)
    }

    @Test
    fun aRevisionedAppliedTypeHeadIsRejected() {
        val src =
            """
            type Box<'A> = Box { value: 'A }
            fun f(b: Box/2<Num>): Num = 1
            """.trimIndent()
        assertRevisionRejected(src, "Box", 2)
    }

    @Test
    fun aRevisionedOptionalCoreIsRejected() {
        assertRevisionRejected("fun f(c: Customer/2?): Num = 1", "Customer", 2)
    }

    @Test
    fun aRevisionedConstructorFieldTypeIsRejected() {
        assertRevisionRejected("type Order = Order { buyer: Customer/2 }", "Customer", 2)
    }

    // --- declaration statements ---

    @Test
    fun aRevisionedFunDeclIsRejectedNotJustBodiless() {
        val errors = infer("fun creditScore/2(c: Num): Num").errors
        val error = errors.filterIsInstance<TypeError.RevisionInProgram>().single()
        assertEquals("creditScore", error.name)
        assertEquals(2, error.revision)
        assertTrue(errors.none { it is TypeError.DeclarationWithoutBody }, "$errors")
    }

    @Test
    fun aRevisionedValDeclIsRejectedNotJustBodiless() {
        val errors = infer("maxRetries/2: Num").errors
        val error = errors.filterIsInstance<TypeError.RevisionInProgram>().single()
        assertEquals("maxRetries", error.name)
        assertEquals(2, error.revision)
        assertTrue(errors.none { it is TypeError.DeclarationWithoutBody }, "$errors")
    }

    @Test
    fun aRevisionedTypeDefIsRejected() {
        assertRevisionRejected("type Customer/2 = Customer { id: Num }", "Customer", 2)
    }

    // --- a written /1 is the same offence ---

    @Test
    fun revisionOneWrittenOutIsRejectedToo() {
        assertRevisionRejected("x: Customer/1 = 1", "Customer", 1)
        assertRevisionRejected("maxRetries/1: Num", "maxRetries", 1)
        assertRevisionRejected("type Customer/1 = Customer { id: Num }", "Customer", 1)
    }

    // --- what stays legal ---

    @Test
    fun aBareDeclarationStillReportsDeclarationWithoutBody() {
        val errors = infer("fun f(c: Num): Num").errors
        assertTrue(errors.any { it is TypeError.DeclarationWithoutBody }, "$errors")
        assertTrue(errors.none { it is TypeError.RevisionInProgram }, "$errors")
    }

    @Test
    fun aRuleStillReachesBareContractNames() {
        val contract =
            checkContract(
                """
                type Customer = Customer { id: Num }
                type Customer/2 = Customer { id: Num, tier: String }

                fun creditScore(c: Customer): Num
                """.trimIndent(),
            )
        val program = infer("creditScore(Customer(1))", contract.env)
        assertTrue(program.errors.isEmpty(), "unexpected errors: ${program.errors}")
    }
}
