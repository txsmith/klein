package klein.host.codec

import klein.Klein
import klein.KleinException
import klein.LanguageVersion
import klein.ReleaseNumber
import klein.RevisionNumber
import klein.check.contract.Edition
import klein.check.contract.EnvironmentContract
import klein.check.contract.UnknownPin
import klein.host.DecodedEdition
import klein.host.Environment
import klein.host.Rederivation
import klein.host.RunOutcome
import klein.host.UnreadableEdition
import klein.host.immediate
import klein.host.implement
import klein.interp.Value
import klein.orFail
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val CREDIT_RULE = "creditScore(customer) >= 620"

private val LENDING =
    """
    type Customer = Customer { id: Num, tier: String }

    customer: Customer
    fun creditScore(c: Customer): Num

    release 1
      Customer
      customer
      creditScore
    """.trimIndent()

private val contract = Klein.checkContract(LENDING)

private val gold = Value.VStruct("Customer", mapOf("id" to Value.VNum(1.0), "tier" to Value.VStr("gold")))

private fun pins(vararg pins: Pair<String, Int>): Map<String, RevisionNumber> = pins.associate { (name, revision) -> name to RevisionNumber(revision) }

private val creditPins = pins("creditScore" to 1, "customer" to 1)

private fun hex(checksum: Long): String = checksum.toULong().toString(16).padStart(16, '0')

@OptIn(ExperimentalEncodingApi::class)
private fun base64(bytes: ByteArray): String = Base64.encode(bytes)

private fun lendingHost(contract: EnvironmentContract): Environment =
    contract.implement(
        immediate("customer") { gold },
        immediate("creditScore") { args ->
            val customer = assertIs<Value.VStruct>(args.single())
            Value.VNum(if (customer.fields["tier"] == Value.VStr("gold")) 700.0 else 500.0)
        },
    )

private fun assertCreditCompiles(): Edition = contract.compileRule(CREDIT_RULE, ReleaseNumber(1)).orFail()

private fun assertStale(text: String): DecodedEdition.Stale = assertIs<DecodedEdition.Stale>(decodeEditionJson(text))

private fun assertFresh(text: String): Edition = assertIs<DecodedEdition.Fresh>(decodeEditionJson(text)).edition

private fun assertRederives(stale: DecodedEdition.Stale): Edition = contract.compileRule(stale.source, stale.pins).orFail()

private fun assertStaleCredit(): DecodedEdition.Stale {
    val foreign = creditCore.copyOf()
    foreign[0] = 2
    return assertStale(documentWithCore(foreign))
}

private fun assertSameEdition(
    expected: Edition,
    actual: Edition,
) {
    assertEquals(expected.language, actual.language)
    assertEquals(expected.core, actual.core)
    assertEquals(expected.pins, actual.pins)
    assertEquals(expected.source, actual.source)
}

private val creditCore = encodeCore(assertCreditCompiles().core)

private val creditChecksum = editionChecksum(LanguageVersion(1), CREDIT_RULE, creditPins, creditCore)

private val validFields: Map<String, String> =
    mapOf(
        "format" to "\"klein-edition\"",
        "version" to "1",
        "language" to "1",
        "pins" to """{"creditScore":1,"customer":1}""",
        "source" to "\"$CREDIT_RULE\"",
        "core" to "\"${base64(creditCore)}\"",
        "checksum" to "\"${hex(creditChecksum)}\"",
    )

private fun document(vararg overrides: Pair<String, String?>): String {
    val fields = validFields.toMutableMap()
    overrides.forEach { (name, value) -> if (value == null) fields.remove(name) else fields[name] = value }
    return fields.entries.joinToString(",", "{", "}") { (name, value) -> "\"$name\":$value" }
}

private fun documentWithCore(core: ByteArray): String =
    document(
        "core" to "\"${base64(core)}\"",
        "checksum" to "\"${hex(editionChecksum(LanguageVersion(1), CREDIT_RULE, creditPins, core))}\"",
    )

class EditionJsonEncodingTest {
    private fun assertUnreadable(text: String): UnreadableEdition {
        val thrown = assertFailsWith<KleinException> { decodeEditionJson(text) }
        return assertIs<UnreadableEdition>(thrown.errors.single())
    }

    @Test
    fun anEditionEncodedAndDecodedIsFreshAndRunsIdenticallyToTheOriginal() {
        val edition = assertCreditCompiles()
        val decoded = assertFresh(encodeEditionJson(edition))
        assertSameEdition(edition, decoded)
        val original = assertIs<RunOutcome.Completed>(lendingHost(contract).run(edition))
        val fromDecoded = assertIs<RunOutcome.Completed>(lendingHost(contract).run(decoded))
        assertEquals(Value.VBool(true), fromDecoded.value)
        assertEquals(original.value, fromDecoded.value)
        assertEquals(original.log, fromDecoded.log)
    }

    @Test
    fun aCompiledEditionCarriesTheCurrentLanguageVersion() {
        assertEquals(LanguageVersion.CURRENT, assertCreditCompiles().language)
    }

    @Test
    fun theDecodedInputsAreTheEditionsInputs() {
        val decoded = assertFresh(encodeEditionJson(assertCreditCompiles()))
        assertEquals(LanguageVersion.CURRENT, decoded.language)
        assertEquals(creditPins, decoded.pins)
        assertEquals(CREDIT_RULE, decoded.source)
    }

    @Test
    fun theJsonTextCarriesTheFieldsInOrderWithPinsSortedByName() {
        val rule =
            """
            score = creditScore(customer)
            score > 600
            """.trimIndent()
        val edition = contract.compileRule(rule, ReleaseNumber(1)).orFail()
        val text = encodeEditionJson(edition)
        val core = encodeCore(edition.core)
        val checksum = hex(editionChecksum(LanguageVersion(1), rule, creditPins, core))
        val expected =
            """{"format":"klein-edition","version":1,"language":1,"pins":{"creditScore":1,"customer":1},""" +
                """"source":"score = creditScore(customer)\nscore > 600","core":"${base64(core)}","checksum":"$checksum"}"""
        assertEquals(expected, text)
    }

    @Test
    fun theCoreIsWrittenAsBase64OfTheCoreBlob() {
        val text = encodeEditionJson(assertCreditCompiles())
        val written = Regex("\"core\":\"([^\"]*)\"").find(text)?.groupValues?.get(1)
        assertEquals(base64(creditCore), written)
        assertTrue(written!!.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' || it == '=' })
    }

    @Test
    fun theChecksumIsWrittenAsSixteenLowercaseHexDigits() {
        val text = encodeEditionJson(assertCreditCompiles())
        val written = Regex("\"checksum\":\"([^\"]*)\"").find(text)?.groupValues?.get(1)
        assertEquals(hex(creditChecksum), written)
        assertEquals(16, written?.length)
        assertTrue(written!!.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun aHandWrittenArtifactDecodes() {
        val text =
            """
            {
              "format": "klein-edition",
              "version": 1,
              "language": 1,
              "pins": { "creditScore": 1, "customer": 1 },
              "source": "creditScore(customer) >= 620",
              "core": "${base64(creditCore)}",
              "checksum": "${hex(creditChecksum)}"
            }
            """.trimIndent()
        assertSameEdition(assertCreditCompiles(), assertFresh(text))
    }

    @Test
    fun reformattedTextDecodesToTheSameEdition() {
        val reformatted =
            """
            {
              "checksum" : "${hex(creditChecksum)}",
              "core": "${base64(creditCore)}",
              "pins": {"customer": 1.0, "creditScore": 1e0},
              "source": "creditScore(customer) >= 620",
              "version": 1.0,
              "language": 1,
              "format": "klein-edition"
            }
            """.trimIndent()
        assertSameEdition(assertFresh(encodeEditionJson(assertCreditCompiles())), assertFresh(reformatted))
    }

    @Test
    fun theTextIsIdenticalAcrossTwoIndependentlyBuiltEditions() {
        val first = encodeEditionJson(Klein.checkContract(LENDING).compileRule(CREDIT_RULE, ReleaseNumber(1)).orFail())
        val second = encodeEditionJson(Klein.checkContract(LENDING).compileRule(CREDIT_RULE, ReleaseNumber(1)).orFail())
        assertEquals(first, second)
    }

    @Test
    fun theChecksumDependsOnLanguageSourcePinsAndCore() {
        val base = editionChecksum(LanguageVersion(1), CREDIT_RULE, creditPins, creditCore)
        assertTrue(base != editionChecksum(LanguageVersion(2), CREDIT_RULE, creditPins, creditCore))
        assertTrue(base != editionChecksum(LanguageVersion(1), "$CREDIT_RULE ", creditPins, creditCore))
        assertTrue(base != editionChecksum(LanguageVersion(1), CREDIT_RULE, pins("creditScore" to 2, "customer" to 1), creditCore))
        assertTrue(base != editionChecksum(LanguageVersion(1), CREDIT_RULE, pins("customer" to 1), creditCore))
        assertTrue(base != editionChecksum(LanguageVersion(1), CREDIT_RULE, creditPins, creditCore.copyOf(creditCore.size - 1)))
        assertTrue(base != editionChecksum(LanguageVersion(1), CREDIT_RULE, creditPins, creditCore + byteArrayOf(0)))
        assertTrue(base != editionChecksum(LanguageVersion(1), CREDIT_RULE, creditPins, byteArrayOf(2) + creditCore.copyOfRange(1, creditCore.size)))
    }

    @Test
    fun theChecksumIgnoresTheOrderThePinsAreGivenIn() {
        val forward = editionChecksum(LanguageVersion(1), CREDIT_RULE, pins("creditScore" to 1, "customer" to 1), creditCore)
        val backward = editionChecksum(LanguageVersion(1), CREDIT_RULE, pins("customer" to 1, "creditScore" to 1), creditCore)
        assertEquals(forward, backward)
    }

    @Test
    fun aFlippedBlobByteIsAChecksumMismatchAndRederivesFromTheRecordedInputs() {
        val damaged = creditCore.copyOf()
        damaged[damaged.size - 1] = (damaged[damaged.size - 1].toInt() xor 0x01).toByte()
        val decoded = assertStale(document("core" to "\"${base64(damaged)}\""))
        assertEquals(Rederivation.ChecksumMismatch, decoded.reason)
        assertEquals(CREDIT_RULE, decoded.source)
        assertEquals(creditPins, decoded.pins)
        val rederived = assertRederives(decoded)
        assertSameEdition(assertCreditCompiles(), rederived)
        assertEquals(Value.VBool(true), assertIs<RunOutcome.Completed>(lendingHost(contract).run(rederived)).value)
    }

    @Test
    fun aForeignLowererVersionBehindAMatchingChecksumIsLowererChanged() {
        val foreign = creditCore.copyOf()
        foreign[0] = 2
        val decoded = assertStale(documentWithCore(foreign))
        assertEquals(Rederivation.LowererChanged, decoded.reason)
        assertEquals(CREDIT_RULE, decoded.source)
        assertEquals(creditPins, decoded.pins)
        val rederived = assertRederives(decoded)
        assertSameEdition(assertCreditCompiles(), rederived)
        assertEquals(Value.VBool(true), assertIs<RunOutcome.Completed>(lendingHost(contract).run(rederived)).value)
    }

    @Test
    fun aForeignLowererVersionWithAStaleChecksumIsAChecksumMismatch() {
        val foreign = creditCore.copyOf()
        foreign[0] = 2
        assertEquals(Rederivation.ChecksumMismatch, assertStale(document("core" to "\"${base64(foreign)}\"")).reason)
    }

    @Test
    fun aDamagedBlobBehindAMatchingChecksumIsUnreadable() {
        val truncated = creditCore.copyOf(creditCore.size - 1)
        assertTrue(assertUnreadable(documentWithCore(truncated)).message.contains("ends early"))
        val trailing = creditCore + byteArrayOf(0)
        assertTrue(assertUnreadable(documentWithCore(trailing)).message.contains("trailing"))
        val unknownNode = creditCore.copyOf()
        unknownNode[1] = 42
        assertTrue(assertUnreadable(documentWithCore(unknownNode)).message.contains("unknown Core node tag 42"))
        assertTrue(assertUnreadable(documentWithCore(byteArrayOf(1))).message.contains("ends early"))
    }

    @Test
    fun anEmptyBlobBehindAMatchingChecksumIsUnreadable() {
        assertTrue(assertUnreadable(documentWithCore(byteArrayOf())).message.contains("empty"))
    }

    @Test
    fun malformedBase64InTheCoreIsUnreadable() {
        listOf("\"!!!!\"", "\"AQ*\"", "\"${base64(creditCore)}#\"").forEach { text ->
            val message = assertUnreadable(document("core" to text)).message
            assertTrue(message.contains("base64"), message)
        }
    }

    @Test
    fun anEditedSourceIsAChecksumMismatchAndRederivesFromTheEditedSource() {
        val decoded = assertStale(document("source" to "\"creditScore(customer) >= 800\""))
        assertEquals(Rederivation.ChecksumMismatch, decoded.reason)
        val rederived = assertRederives(decoded)
        assertEquals("creditScore(customer) >= 800", rederived.source)
        assertEquals(Value.VBool(false), assertIs<RunOutcome.Completed>(lendingHost(contract).run(rederived)).value)
    }

    @Test
    fun anEditedPinIsAChecksumMismatchAndRederivesAgainstTheEditedPins() {
        val decoded = assertStale(document("pins" to """{"Customer":1,"creditScore":1,"customer":1}"""))
        assertEquals(Rederivation.ChecksumMismatch, decoded.reason)
        assertEquals(creditPins, assertRederives(decoded).pins)
    }

    @Test
    fun aPinEditedToARevisionTheContractLacksIsAnUnknownPin() {
        val decoded = assertStale(document("pins" to """{"creditScore":2,"customer":1}"""))
        assertEquals(Rederivation.ChecksumMismatch, decoded.reason)
        val errors = assertFailsWith<KleinException> { contract.compileRule(decoded.source, decoded.pins) }.errors
        val unknown = assertIs<UnknownPin>(errors.single())
        assertEquals("creditScore" to RevisionNumber(2), unknown.name to unknown.revision)
    }

    @Test
    fun aWellFormedChecksumThatDoesNotMatchIsAMismatchNotUnreadable() {
        val decoded = assertStale(document("checksum" to "\"fedcba9876543210\""))
        assertEquals(Rederivation.ChecksumMismatch, decoded.reason)
        assertEquals(CREDIT_RULE, decoded.source)
    }

    @Test
    fun anUnknownLanguageVersionIsUnreadableNamingBothVersions() {
        val checksum = hex(editionChecksum(LanguageVersion(7), CREDIT_RULE, creditPins, creditCore))
        val message = assertUnreadable(document("language" to "7", "checksum" to "\"$checksum\"")).message
        assertTrue(message.contains("7"), message)
        assertTrue(message.contains("1"), message)
        assertTrue(message.contains("language"), message)
    }

    @Test
    fun anUnknownLanguageVersionIsUnreadableEvenWhenTheChecksumDoesNotMatch() {
        assertTrue(assertUnreadable(document("language" to "7")).message.contains("language"))
    }

    @Test
    fun aPinnedRevisionRemovedFromTheContractIsAnUnknownPinAndNoResult() {
        val decoded = assertStaleCredit()
        val withoutRevision1 =
            Klein.checkContract(
                """
                type Customer/2 = Customer { id: Num, name: String, tier: String }

                customer/2: Customer/2
                fun creditScore/2(c: Customer/2): Num

                release 2
                  Customer/2
                  customer/2
                  creditScore/2
                """.trimIndent(),
            )
        val errors = assertFailsWith<KleinException> { withoutRevision1.compileRule(decoded.source, decoded.pins) }.errors
        assertEquals(
            setOf("creditScore" to RevisionNumber(1), "customer" to RevisionNumber(1)),
            errors.map { assertIs<UnknownPin>(it) }.map { it.name to it.revision }.toSet(),
        )
    }

    @Test
    fun aDeclarationEditedInPlaceComesBackAsDiagnosticsAndTheInputsStayReadable() {
        val decoded = assertStaleCredit()
        val edited =
            Klein.checkContract(
                """
                type Customer = Customer { id: Num, tier: String }

                customer: Customer
                fun creditScore(c: Customer): String

                release 1
                  Customer
                  customer
                  creditScore
                """.trimIndent(),
            )
        val checked = edited.compileRule(decoded.source, decoded.pins)
        assertNull(checked.output)
        assertTrue(checked.diagnostics.isNotEmpty())
        assertEquals(CREDIT_RULE, decoded.source)
        assertEquals(creditPins, decoded.pins)
    }

    @Test
    fun aWrongFormatMarkerIsUnreadable() {
        assertTrue(assertUnreadable(document("format" to "\"klein-effect-log\"")).message.contains("not a Klein edition"))
    }

    @Test
    fun aMissingFormatMarkerIsUnreadable() {
        assertTrue(assertUnreadable(document("format" to null)).message.contains("not a Klein edition"))
    }

    @Test
    fun aDocumentThatIsNotAJsonObjectIsUnreadable() {
        assertTrue(assertUnreadable("[1]").message.contains("not a Klein edition"))
    }

    @Test
    fun garbageTextIsUnreadable() {
        assertTrue(assertUnreadable("hello").message.contains("unexpected character"))
    }

    @Test
    fun aWrongVersionStampIsUnreadable() {
        val message = assertUnreadable(document("version" to "99")).message
        assertTrue(message.contains("unknown edition version 99"), message)
    }

    @Test
    fun eachMissingFieldIsUnreadable() {
        listOf("version", "language", "pins", "source", "core", "checksum").forEach { name ->
            val message = assertUnreadable(document(name to null)).message
            assertTrue(message.contains("missing its \"$name\" field"), message)
        }
    }

    @Test
    fun anUnknownFieldIsUnreadable() {
        val message = assertUnreadable(document("release" to "1")).message
        assertTrue(message.contains("unexpected field \"release\""), message)
    }

    @Test
    fun aDuplicateFieldIsUnreadable() {
        val text = document().dropLast(1) + ""","source":"creditScore(customer) >= 620"}"""
        val message = assertUnreadable(text).message
        assertTrue(message.contains("duplicate"), message)
        assertTrue(message.contains("source"), message)
    }

    @Test
    fun aDuplicatePinIsUnreadable() {
        val message = assertUnreadable(document("pins" to """{"creditScore":1,"creditScore":1,"customer":1}""")).message
        assertTrue(message.contains("duplicate"), message)
    }

    @Test
    fun eachWronglyTypedFieldIsUnreadable() {
        assertTrue(assertUnreadable(document("version" to "\"1\"")).message.contains("whole number"))
        assertTrue(assertUnreadable(document("version" to "1.5")).message.contains("whole number"))
        assertTrue(assertUnreadable(document("language" to "true")).message.contains("whole number"))
        assertTrue(assertUnreadable(document("pins" to "[]")).message.contains("pins"))
        assertTrue(assertUnreadable(document("pins" to """{"creditScore":"1"}""")).message.contains("whole number"))
        assertTrue(assertUnreadable(document("pins" to """{"creditScore":0}""")).message.contains("1 or more"))
        assertTrue(assertUnreadable(document("source" to "1")).message.contains("source"))
        assertTrue(assertUnreadable(document("core" to "1")).message.contains("core"))
        assertTrue(assertUnreadable(document("checksum" to "1")).message.contains("checksum"))
    }

    @Test
    fun malformedHexInTheChecksumIsUnreadable() {
        listOf("\"\"", "\"abc\"", "\"0123456789abcdeg\"", "\"0123456789ABCDEF\"", "\"00123456789abcdef\"").forEach { text ->
            val message = assertUnreadable(document("checksum" to text)).message
            assertTrue(message.contains("16 lowercase hex digits"), message)
        }
    }

    @Test
    fun trailingCharactersAreUnreadable() {
        assertTrue(assertUnreadable(document() + "x").message.contains("trailing"))
    }

    @Test
    fun everyTruncatedPrefixIsUnreadable() {
        val text = encodeEditionJson(assertCreditCompiles())
        for (length in 0 until text.length) {
            assertUnreadable(text.substring(0, length))
        }
    }

    @Test
    fun anEmptyPinMapRoundTrips() {
        val edition = contract.compileRule("1 + 2", ReleaseNumber(1)).orFail()
        val decoded = assertFresh(encodeEditionJson(edition))
        assertEquals(emptyMap(), decoded.pins)
        assertSameEdition(edition, decoded)
    }

    @Test
    fun sourceTextWithEscapesRoundTrips() {
        val rule = "s = \"quote \\\" tab \\t héllo 日本語\"\ncreditScore(customer) >= 620"
        val edition = contract.compileRule(rule, ReleaseNumber(1)).orFail()
        val decoded = assertFresh(encodeEditionJson(edition))
        assertEquals(rule, decoded.source)
        assertSameEdition(edition, decoded)
    }
}
