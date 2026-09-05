package klein.parser

import klein.RevisionNumber
import klein.surface.FunDecl
import klein.surface.Abort
import klein.surface.TypeDefStmt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Revisions (`/N`) — contract syntax. A revision rides a declared name and every type reference in
 * a contract; a rule cannot write one anywhere, and the parser is what says so, because the rule
 * language's type expressions have no slot to put one in.
 */
class RevisionTest {
    // --- the three declaration positions ---

    @Test
    fun typeDefTakesARevisionNumber() {
        assertContractEquals(
            parseContract("type Customer/2 = Customer { id: Num, tier: String }"),
            types =
                listOf(
                    typeDef(
                        "Customer",
                        revision = RevisionNumber(2),
                        constructors =
                            arrayOf(
                                constructor("Customer", field("id", typeName("Num")), field("tier", typeName("String"))),
                            ),
                    ),
                ),
        )
    }

    @Test
    fun aTypeDefWithoutARevisionRecordsNone() {
        assertNull(parseContract("type Customer = Customer { id: Num }").types.single().revision)
    }

    @Test
    fun revisionOneWrittenOutIsRecorded() {
        assertEquals(RevisionNumber(1), parseContract("type Customer/1 = Customer { id: Num }").types.single().revision)
    }

    @Test
    fun aRevisedTypeDefStillTakesTypeParams() {
        assertContractEquals(
            parseContract("type Box/2<'A> = Box { value: 'A }"),
            types =
                listOf(
                    typeDef(
                        "Box",
                        typeParams = listOf("A"),
                        revision = RevisionNumber(2),
                        constructors = arrayOf(constructor("Box", field("value", typeVar("A")))),
                    ),
                ),
        )
    }

    @Test
    fun funDeclarationTakesARevisionNumber() {
        assertContractEquals(
            parseContract("fun creditScore/2(c: Customer/2): Num"),
            declarations =
                listOf(
                    funDecl(
                        "creditScore",
                        listOf(param("c", typeName("Customer", revision = RevisionNumber(2)))),
                        typeName("Num"),
                        revision = RevisionNumber(2),
                    ),
                ),
        )
    }

    @Test
    fun valDeclarationTakesARevisionNumber() {
        assertContractEquals(
            parseContract("maxRetries/2: Num"),
            declarations = listOf(valDecl("maxRetries", typeName("Num"), revision = RevisionNumber(2))),
        )
    }

    @Test
    fun aRevisedValDeclarationDoesNotSwallowTheNextStatement() {
        val source =
            """
            maxRetries/2: Num
            limit: Num
            """.trimIndent()
        assertContractEquals(
            parseContract(source),
            declarations =
                listOf(
                    valDecl("maxRetries", typeName("Num"), revision = RevisionNumber(2)),
                    valDecl("limit", typeName("Num")),
                ),
        )
    }

    @Test
    fun bothRevisionsOfACapabilityParseInOneFile() {
        val source =
            """
            type Customer = Customer { id: Num }
            type Customer/2 = Customer { id: Num, tier: String }

            fun creditScore(c: Customer): Num
            fun creditScore/2(c: Customer/2): Num
            """.trimIndent()
        val contract = parseContract(source)
        assertEquals(listOf(null, RevisionNumber(2)), contract.types.map { it.revision })
        assertEquals(listOf(null, RevisionNumber(2)), contract.declarations.map { it.revision })
    }

    // --- type references ---

    @Test
    fun aReturnTypeTakesARevisionNumber() {
        assertContractEquals(
            parseContract("fun latest(): Customer/2"),
            declarations = listOf(funDecl("latest", emptyList(), typeName("Customer", revision = RevisionNumber(2)))),
        )
    }

    @Test
    fun aConstructorFieldTypeTakesARevisionNumber() {
        assertContractEquals(
            parseContract("type Order = Order { buyer: Customer/2 }"),
            types =
                listOf(
                    typeDef(
                        "Order",
                        constructors = arrayOf(constructor("Order", field("buyer", typeName("Customer", revision = RevisionNumber(2))))),
                    ),
                ),
        )
    }

    @Test
    fun aTypeArgumentTakesARevisionNumber() {
        assertContractEquals(
            parseContract("fun all(): List<Customer/2>"),
            declarations = listOf(funDecl("all", emptyList(), appliedType("List", typeName("Customer", revision = RevisionNumber(2))))),
        )
    }

    @Test
    fun aRevisedTypeIsStillApplicable() {
        assertContractEquals(
            parseContract("fun boxed(): Box/2<Num>"),
            declarations = listOf(funDecl("boxed", emptyList(), appliedType("Box", typeName("Num"), revision = RevisionNumber(2)))),
        )
    }

    @Test
    fun aRevisedTypeMayBeOptional() {
        assertContractEquals(
            parseContract("lookup: Customer/2?"),
            declarations = listOf(valDecl("lookup", optionalType(typeName("Customer", revision = RevisionNumber(2))))),
        )
    }

    @Test
    fun aRevisedTypeMayAppearInAFunctionType() {
        assertContractEquals(
            parseContract("fun pick(): (Customer/2) -> Num"),
            declarations =
                listOf(
                    funDecl(
                        "pick",
                        emptyList(),
                        functionType(typeName("Customer", revision = RevisionNumber(2)), typeName("Num")),
                    ),
                ),
        )
    }

    @Test
    fun aRevisedTypeMayAppearInARecordType() {
        assertContractEquals(
            parseContract("order: { buyer: Customer/2 }"),
            declarations = listOf(valDecl("order", recordType("buyer" to typeName("Customer", revision = RevisionNumber(2))))),
        )
    }

    // --- '/' is still division ---

    @Test
    fun divisionStillParses() {
        assertExprEquals(parse("6 / 2"), div(int(6), int(2)))
        assertExprEquals(parse("total / count"), div(id("total"), id("count")))
    }

    @Test
    fun divisionAtStatementLevelStillParses() {
        val source =
            """
            total = 10
            total / 2
            """.trimIndent()
        assertProgramEquals(
            parseProgram(source),
            listOf(valStmt("total", int(10)), div(id("total"), int(2))),
        )
    }

    @Test
    fun divisionInABindingStillParses() {
        assertProgramEquals(
            parseProgram("half = x / 2"),
            listOf(valStmt("half", div(id("x"), int(2)))),
        )
    }

    // --- rejected shapes ---

    @Test
    fun revisionZeroIsAParseError() {
        assertFailsWith<Abort> { parseContract("fun creditScore/0(c: Num): Num") }
    }

    @Test
    fun aNonNumericRevisionIsAParseError() {
        assertFailsWith<Abort> { parseContract("fun creditScore/next(c: Num): Num") }
        assertFailsWith<Abort> { parseContract("type Customer/next = Customer { id: Num }") }
    }

    @Test
    fun aRevisionOnAFunctionDefinitionIsAParseError() {
        assertFailsWith<Abort> { parseContract("fun creditScore/2(c: Num): Num = c") }
        assertFailsWith<Abort> { parseContract("fun creditScore/1(c: Num): Num = c") }
    }

    @Test
    fun aRevisionOnABindingIsAParseError() {
        assertFailsWith<Abort> { parseContract("maxRetries/2: Num = 3") }
        assertFailsWith<Abort> { parseContract("maxRetries/2 = 3") }
        assertFailsWith<Abort> { parseContract("maxRetries/1 = 3") }
    }

    // --- a revision in a program is a parse error, in every position ---

    private fun assertRevisionRejectedInProgram(
        src: String,
        name: String,
        revision: Int,
    ) {
        val error = assertFailsWith<Abort>("expected a revision rejection in: $src") { parseProgram(src) }
        assertTrue("$name/$revision" in error.message, error.message)
        assertTrue("contract" in error.message, error.message)
    }

    @Test
    fun aRevisionedBindingAnnotationIsRejected() {
        assertRevisionRejectedInProgram("x: Customer/2 = 1", "Customer", 2)
    }

    @Test
    fun aRevisionedFunParamAnnotationIsRejected() {
        assertRevisionRejectedInProgram("fun f(c: Customer/2): Num = 1", "Customer", 2)
    }

    @Test
    fun aRevisionedFunReturnAnnotationIsRejected() {
        assertRevisionRejectedInProgram("fun f(x: Num): Customer/2 = x", "Customer", 2)
    }

    @Test
    fun aRevisionedLambdaParamAnnotationIsRejected() {
        assertRevisionRejectedInProgram("f = |c: Customer/2 -> 1|", "Customer", 2)
    }

    @Test
    fun aRevisionedAscriptionIsRejected() {
        assertRevisionRejectedInProgram("(1 : Num/2)", "Num", 2)
    }

    @Test
    fun aRevisionedTypeArgumentIsRejected() {
        val src =
            """
            type Box<'A> = Box { value: 'A }
            fun f(b: Box<Num/2>): Num = 1
            """.trimIndent()
        assertRevisionRejectedInProgram(src, "Num", 2)
    }

    @Test
    fun aRevisionedAppliedTypeHeadIsRejected() {
        val src =
            """
            type Box<'A> = Box { value: 'A }
            fun f(b: Box/2<Num>): Num = 1
            """.trimIndent()
        assertRevisionRejectedInProgram(src, "Box", 2)
    }

    @Test
    fun aRevisionedOptionalCoreIsRejected() {
        assertRevisionRejectedInProgram("fun f(c: Customer/2?): Num = 1", "Customer", 2)
    }

    @Test
    fun aRevisionedConstructorFieldTypeIsRejected() {
        assertRevisionRejectedInProgram("type Order = Order { buyer: Customer/2 }", "Customer", 2)
    }

    @Test
    fun aRevisionedTypeDefNameIsRejected() {
        assertRevisionRejectedInProgram("type Customer/2 = Customer { id: Num }", "Customer", 2)
    }

    @Test
    fun aRevisionedFunDeclIsRejected() {
        assertRevisionRejectedInProgram("fun creditScore/2(c: Num): Num", "creditScore", 2)
    }

    @Test
    fun aRevisionedValDeclIsRejected() {
        assertRevisionRejectedInProgram("maxRetries/2: Num", "maxRetries", 2)
    }

    // A written `/1` is the same offence: the offence is the syntax, not the number.
    @Test
    fun revisionOneWrittenOutIsRejectedInAProgramToo() {
        assertRevisionRejectedInProgram("x: Customer/1 = 1", "Customer", 1)
        assertRevisionRejectedInProgram("maxRetries/1: Num", "maxRetries", 1)
        assertRevisionRejectedInProgram("type Customer/1 = Customer { id: Num }", "Customer", 1)
    }

    // The rule language has no revision slot at all: what the parser builds is unrevisionable.
    @Test
    fun aProgramsTypeExpressionsCarryNoRevisionNumber() {
        val typeDef = assertIs<TypeDefStmt>(parseProgram("type Customer = Customer { id: Num }").stmts.single()).typeDef
        assertNull(typeDef.revision)
        assertNull(assertIs<klein.surface.TypeName<*>>(typeDef.constructors.single().fields.single().type).revision)
    }
}
