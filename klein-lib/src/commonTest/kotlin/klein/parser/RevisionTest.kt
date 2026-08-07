package klein.parser

import klein.surface.FunDecl
import klein.surface.ParseError
import klein.surface.TypeDef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Revisions (`/N`) — contract syntax. A revision rides a declared name and every type reference;
 * it never appears in an expression.
 */
class RevisionTest {
    // --- the three declaration positions ---

    @Test
    fun typeDefTakesARevision() {
        assertProgramEquals(
            parseProgram("type Customer/2 = Customer { id: Num, tier: String }"),
            listOf(
                typeDef(
                    "Customer",
                    revision = 2,
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
        val typeDef = assertIs<TypeDef>(parseProgram("type Customer = Customer { id: Num }").stmts.single())
        assertNull(typeDef.revision)
    }

    @Test
    fun revisionOneWrittenOutIsRecorded() {
        val typeDef = assertIs<TypeDef>(parseProgram("type Customer/1 = Customer { id: Num }").stmts.single())
        assertEquals(1, typeDef.revision)
    }

    @Test
    fun aRevisedTypeDefStillTakesTypeParams() {
        assertProgramEquals(
            parseProgram("type Box/2<'A> = Box { value: 'A }"),
            listOf(
                typeDef(
                    "Box",
                    typeParams = listOf("A"),
                    revision = 2,
                    constructors = arrayOf(constructor("Box", field("value", typeVar("A")))),
                ),
            ),
        )
    }

    @Test
    fun funDeclarationTakesARevision() {
        assertProgramEquals(
            parseProgram("fun creditScore/2(c: Customer/2): Num"),
            listOf(
                funDecl(
                    "creditScore",
                    listOf(param("c", typeName("Customer", revision = 2))),
                    typeName("Num"),
                    revision = 2,
                ),
            ),
        )
    }

    @Test
    fun valDeclarationTakesARevision() {
        assertProgramEquals(
            parseProgram("maxRetries/2: Num"),
            listOf(valDecl("maxRetries", typeName("Num"), revision = 2)),
        )
    }

    @Test
    fun aRevisedValDeclarationDoesNotSwallowTheNextStatement() {
        val source =
            """
            maxRetries/2: Num
            limit: Num
            """.trimIndent()
        assertProgramEquals(
            parseProgram(source),
            listOf(valDecl("maxRetries", typeName("Num"), revision = 2), valDecl("limit", typeName("Num"))),
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
        val stmts = parseProgram(source).stmts
        assertEquals(4, stmts.size)
        assertEquals(listOf(null, 2), stmts.filterIsInstance<TypeDef>().map { it.revision })
        assertEquals(listOf(null, 2), stmts.filterIsInstance<FunDecl>().map { it.revision })
    }

    // --- type references ---

    @Test
    fun aReturnTypeTakesARevision() {
        assertProgramEquals(
            parseProgram("fun latest(): Customer/2"),
            listOf(funDecl("latest", emptyList(), typeName("Customer", revision = 2))),
        )
    }

    @Test
    fun aConstructorFieldTypeTakesARevision() {
        assertProgramEquals(
            parseProgram("type Order = Order { buyer: Customer/2 }"),
            listOf(typeDef("Order", constructors = arrayOf(constructor("Order", field("buyer", typeName("Customer", revision = 2)))))),
        )
    }

    @Test
    fun aTypeArgumentTakesARevision() {
        assertProgramEquals(
            parseProgram("fun all(): List<Customer/2>"),
            listOf(funDecl("all", emptyList(), appliedType("List", typeName("Customer", revision = 2)))),
        )
    }

    @Test
    fun aRevisedTypeIsStillApplicable() {
        assertProgramEquals(
            parseProgram("fun boxed(): Box/2<Num>"),
            listOf(funDecl("boxed", emptyList(), appliedType("Box", typeName("Num"), revision = 2))),
        )
    }

    @Test
    fun aRevisedTypeMayBeOptional() {
        assertProgramEquals(
            parseProgram("lookup: Customer/2?"),
            listOf(valDecl("lookup", optionalType(typeName("Customer", revision = 2)))),
        )
    }

    @Test
    fun aRevisedTypeMayAppearInAFunctionType() {
        assertProgramEquals(
            parseProgram("fun pick(): (Customer/2) -> Num"),
            listOf(
                funDecl(
                    "pick",
                    emptyList(),
                    functionType(typeName("Customer", revision = 2), typeName("Num")),
                ),
            ),
        )
    }

    @Test
    fun aRevisedTypeMayAppearInARecordType() {
        assertProgramEquals(
            parseProgram("order: { buyer: Customer/2 }"),
            listOf(valDecl("order", recordType("buyer" to typeName("Customer", revision = 2)))),
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
        assertFailsWith<ParseError> { parseProgram("fun creditScore/0(c: Num): Num") }
    }

    @Test
    fun aNonNumericRevisionIsAParseError() {
        assertFailsWith<ParseError> { parseProgram("fun creditScore/next(c: Num): Num") }
        assertFailsWith<ParseError> { parseProgram("type Customer/next = Customer { id: Num }") }
    }

    @Test
    fun aRevisionOnAFunctionDefinitionIsAParseError() {
        assertFailsWith<ParseError> { parseProgram("fun creditScore/2(c: Num): Num = c") }
        assertFailsWith<ParseError> { parseProgram("fun creditScore/1(c: Num): Num = c") }
    }

    @Test
    fun aRevisionOnABindingIsAParseError() {
        assertFailsWith<ParseError> { parseProgram("maxRetries/2: Num = 3") }
        assertFailsWith<ParseError> { parseProgram("maxRetries/2 = 3") }
        assertFailsWith<ParseError> { parseProgram("maxRetries/1 = 3") }
    }
}
