package klein.parser

import klein.surface.FunDecl
import klein.surface.Ident
import klein.surface.ParseError
import klein.surface.TypeDef
import klein.surface.ValDecl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Revisions (`/N`) and the `review` marker — contract syntax. A revision rides a declared name and
 * every type reference; `review` rides a declaration. Neither ever appears in an expression.
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
    fun aTypeDefWithoutARevisionIsRevisionOne() {
        val typeDef = assertIs<TypeDef>(parseProgram("type Customer = Customer { id: Num }").stmts.single())
        assertEquals(1, typeDef.revision)
    }

    @Test
    fun revisionOneWrittenOutIsTheSameNodeAsBare() {
        assertProgramEquals(
            parseProgram("type Customer/1 = Customer { id: Num }"),
            parseProgram("type Customer = Customer { id: Num }").stmts.map { it.stripSpan() },
        )
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
        assertEquals(listOf(1, 2), stmts.filterIsInstance<TypeDef>().map { it.revision })
        assertEquals(listOf(1, 2), stmts.filterIsInstance<FunDecl>().map { it.revision })
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

    // --- the review marker ---

    @Test
    fun funDeclarationCarriesTheReviewMarker() {
        assertProgramEquals(
            parseProgram("fun underwrite(a: Application): Decision review"),
            listOf(
                funDecl(
                    "underwrite",
                    listOf(param("a", typeName("Application"))),
                    typeName("Decision"),
                    review = true,
                ),
            ),
        )
    }

    @Test
    fun valDeclarationCarriesTheReviewMarker() {
        assertProgramEquals(
            parseProgram("maxRetries: Num review"),
            listOf(valDecl("maxRetries", typeName("Num"), review = true)),
        )
    }

    @Test
    fun reviewCombinesWithARevision() {
        assertProgramEquals(
            parseProgram("fun underwrite/2(a: Application): Decision review"),
            listOf(
                funDecl(
                    "underwrite",
                    listOf(param("a", typeName("Application"))),
                    typeName("Decision"),
                    revision = 2,
                    review = true,
                ),
            ),
        )
    }

    @Test
    fun anUnmarkedDeclarationIsNotUnderReview() {
        val decl = assertIs<FunDecl>(parseProgram("fun underwrite(a: Num): Num").stmts.single())
        assertFalse(decl.review)
        val value = assertIs<ValDecl>(parseProgram("maxRetries: Num").stmts.single())
        assertFalse(value.review)
    }

    @Test
    fun aMarkedDeclarationDoesNotSwallowTheNextStatement() {
        val source =
            """
            fun underwrite(a: Num): Num review
            maxRetries: Num
            """.trimIndent()
        val stmts = parseProgram(source).stmts
        assertEquals(2, stmts.size)
        assertTrue(assertIs<FunDecl>(stmts[0]).review)
        assertIs<ValDecl>(stmts[1])
    }

    // `review` is contextual: everywhere else it is an ordinary identifier.

    @Test
    fun reviewIsStillAnOrdinaryBindingName() {
        assertProgramEquals(parseProgram("review = 1"), listOf(valStmt("review", int(1))))
    }

    @Test
    fun reviewIsStillAnOrdinaryFunctionName() {
        assertProgramEquals(
            parseProgram("fun review(a: Num): Num = a"),
            listOf(funDef("review", listOf(param("a", typeName("Num"))), id("a"), typeName("Num"))),
        )
    }

    @Test
    fun reviewIsStillAnOrdinaryParameterName() {
        assertProgramEquals(
            parseProgram("fun score(review: Num): Num"),
            listOf(funDecl("score", listOf(param("review", typeName("Num"))), typeName("Num"))),
        )
    }

    @Test
    fun reviewIsStillAnOrdinaryFieldName() {
        assertProgramEquals(
            parseProgram("type Case = Case { review: Bool }"),
            listOf(typeDef("Case", constructors = arrayOf(constructor("Case", field("review", typeName("Bool")))))),
        )
    }

    @Test
    fun reviewIsStillAnOrdinaryDeclaredName() {
        assertProgramEquals(
            parseProgram("review: Num"),
            listOf(valDecl("review", typeName("Num"))),
        )
    }

    // The marker must be on the declaration's own line — a `review` below it is a new statement.
    @Test
    fun reviewOnTheNextLineIsItsOwnExpression() {
        val source =
            """
            maxRetries: Num
            review
            """.trimIndent()
        val stmts = parseProgram(source).stmts
        assertEquals(2, stmts.size)
        assertFalse(assertIs<ValDecl>(stmts[0]).review)
        assertEquals("review", assertIs<Ident>(stmts[1]).name)
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
    }

    @Test
    fun aRevisionOnABindingIsAParseError() {
        assertFailsWith<ParseError> { parseProgram("maxRetries/2: Num = 3") }
        assertFailsWith<ParseError> { parseProgram("maxRetries/2 = 3") }
    }

    @Test
    fun reviewOnADefinitionIsAParseError() {
        assertFailsWith<ParseError> { parseProgram("fun underwrite(a: Num): Num review = a") }
        assertFailsWith<ParseError> { parseProgram("maxRetries: Num review = 3") }
    }
}
