package klein.parser

import klein.ReleaseNumber
import klein.Revision
import klein.surface.ParseError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Release blocks — contract syntax. A block's *contents* are syntax; everything about what a block
 * means is checker semantics, and lives in `klein.check.contract.ReleaseTypeCheckTest`.
 */
class ReleaseTest {
    // ── The header ───────────────────────────────────────────────────────────

    @Test
    fun aBlockTakesItsNumberAndItsEntries() {
        assertContractEquals(
            parseContract(
                """
                release 2
                  Customer/2
                  creditScore/2
                """.trimIndent(),
            ),
            releases =
                listOf(
                    releaseBlock(
                        2,
                        releaseEntry("Customer", Revision(2)),
                        releaseEntry("creditScore", Revision(2)),
                    ),
                ),
        )
    }

    @Test
    fun anEmptyBlockParses() {
        val block = parseContract("release 2").releases.single()
        assertEquals(ReleaseNumber(2), block.number)
        assertTrue(block.entries.isEmpty())
    }

    @Test
    fun twoBlocksParseInOneFile() {
        assertContractEquals(
            parseContract(
                """
                release 1
                  Customer

                release 2
                  Customer/2
                """.trimIndent(),
            ),
            releases =
                listOf(
                    releaseBlock(1, releaseEntry("Customer")),
                    releaseBlock(2, releaseEntry("Customer", Revision(2))),
                ),
        )
    }

    @Test
    fun aBlockSitsBesideTypesAndDeclarations() {
        assertContractEquals(
            parseContract(
                """
                type Customer = Customer { id: Num }

                fun creditScore(c: Customer): Num

                release 1
                  Customer
                  creditScore
                """.trimIndent(),
            ),
            types = listOf(typeDef("Customer", constructors = arrayOf(constructor("Customer", field("id", typeName("Num")))))),
            declarations = listOf(funDecl("creditScore", listOf(param("c", typeName("Customer"))), typeName("Num"))),
            releases = listOf(releaseBlock(1, releaseEntry("Customer"), releaseEntry("creditScore"))),
        )
    }

    // ── Entries ──────────────────────────────────────────────────────────────

    @Test
    fun anEntryWithoutARevisionRecordsNone() {
        assertNull(parseContract("release 1\n  Customer").releases.single().entries.single().revision)
    }

    @Test
    fun aLowercaseNameIsAnEntry() {
        assertContractEquals(
            parseContract("release 1\n  maxRetries/2"),
            releases = listOf(releaseBlock(1, releaseEntry("maxRetries", Revision(2)))),
        )
    }

    @Test
    fun removeTakesTheNameOutOfTheRelease() {
        assertContractEquals(
            parseContract(
                """
                release 4
                  Customer/3
                  remove creditScore
                """.trimIndent(),
            ),
            releases =
                listOf(
                    releaseBlock(
                        4,
                        releaseEntry("Customer", Revision(3)),
                        releaseEntry("creditScore", remove = true),
                    ),
                ),
        )
    }

    @Test
    fun removeTakesARevisionedNameToo() {
        assertContractEquals(
            parseContract("release 4\n  remove Customer/2"),
            releases = listOf(releaseBlock(4, releaseEntry("Customer", Revision(2), remove = true))),
        )
    }

    // `remove` is contextual too, so a declaration may itself be named `remove`.
    @Test
    fun anEntryMayItselfBeNamedRemove() {
        assertContractEquals(
            parseContract("release 1\n  remove"),
            releases = listOf(releaseBlock(1, releaseEntry("remove"))),
        )
    }

    @Test
    fun anEntryNamedRemoveMayItselfBeRemoved() {
        assertContractEquals(
            parseContract("release 2\n  remove remove"),
            releases = listOf(releaseBlock(2, releaseEntry("remove", remove = true))),
        )
    }

    // ── Indentation ──────────────────────────────────────────────────────────

    @Test
    fun aBlockDoesNotSwallowTheDeclarationAfterIt() {
        val contract =
            parseContract(
                """
                release 1
                  Customer
                maxRetries: Num
                """.trimIndent(),
            )
        assertEquals(listOf("Customer"), contract.releases.single().entries.map { it.name })
        assertEquals(listOf("maxRetries"), contract.declarations.map { it.name })
    }

    @Test
    fun aBlockDoesNotSwallowTheNextBlock() {
        val contract =
            parseContract(
                """
                release 1
                  Customer
                release 2
                  Customer/2
                """.trimIndent(),
            )
        assertEquals(listOf(ReleaseNumber(1), ReleaseNumber(2)), contract.releases.map { it.number })
        assertEquals(listOf(1, 1), contract.releases.map { it.entries.size })
    }

    @Test
    fun entriesMayBeIndentedFurtherThanTheFirst() {
        val block =
            parseContract(
                """
                release 1
                  Customer
                      creditScore
                """.trimIndent(),
            ).releases.single()
        assertEquals(listOf("Customer", "creditScore"), block.entries.map { it.name })
    }

    // ── `release` is not a reserved word ─────────────────────────────────────

    @Test
    fun releaseIsStillAnOrdinaryBindingName() {
        assertProgramEquals(parseProgram("release = 3"), listOf(valStmt("release", int(3))))
    }

    @Test
    fun releaseIsStillAnOrdinaryOperand() {
        assertProgramEquals(parseProgram("release + 1"), listOf(add(id("release"), int(1))))
    }

    @Test
    fun releaseIsStillAnOrdinaryDeclarationName() {
        assertContractEquals(parseContract("release: Num"), declarations = listOf(valDecl("release", typeName("Num"))))
    }

    // The lookahead is `release` and an INT *on the same line*; a number on the next line is a
    // statement of its own.
    @Test
    fun aBareReleaseAndANumberOnTheNextLineAreTwoStatements() {
        assertProgramEquals(parseProgram("release\n3"), listOf(id("release"), int(3)))
    }

    // ── Rejected shapes ──────────────────────────────────────────────────────

    @Test
    fun releaseZeroIsAParseError() {
        assertFailsWith<ParseError> { parseContract("release 0\n  Customer") }
    }

    @Test
    fun anEntryWithAValueIsAParseError() {
        assertFailsWith<ParseError> { parseContract("release 1\n  Customer = 3") }
    }

    @Test
    fun anEntryThatIsACallIsAParseError() {
        assertFailsWith<ParseError> { parseContract("release 1\n  Customer(1)") }
    }

    @Test
    fun twoNamesOnOneEntryLineIsAParseError() {
        assertFailsWith<ParseError> { parseContract("release 1\n  Customer creditScore") }
    }

    @Test
    fun anEntryWithANonNumericRevisionIsAParseError() {
        assertFailsWith<ParseError> { parseContract("release 1\n  Customer/next") }
    }

    @Test
    fun anEntryThatIsNotANameIsAParseError() {
        assertFailsWith<ParseError> { parseContract("release 1\n  42") }
    }

    // ── A release in a rule ──────────────────────────────────────────────────

    @Test
    fun aReleaseBlockInAProgramIsAParseError() {
        val error = assertFailsWith<ParseError> { parseProgram("release 1\n  Customer") }
        assertTrue("contract" in error.message, error.message)
    }
}
