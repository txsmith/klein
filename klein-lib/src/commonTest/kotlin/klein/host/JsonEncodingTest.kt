package klein.host

import klein.Klein
import klein.KleinException
import klein.ReleaseNumber
import klein.SourceSpan
import klein.interp.Value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

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

private val gold = Value.VStruct("Customer", mapOf("id" to Value.VNum(1.0), "tier" to Value.VStr("gold")))

private val everyValueShape: List<Value> =
    listOf(
        Value.VNum(1.5),
        Value.VNum(0.0),
        Value.VNum(-0.0),
        Value.VNum(Double.NaN),
        Value.VNum(Double.fromBits(0x7ff8_dead_beef_0001L)),
        Value.VNum(Double.POSITIVE_INFINITY),
        Value.VNum(Double.NEGATIVE_INFINITY),
        Value.VNum(Double.MIN_VALUE),
        Value.VStr(""),
        Value.VStr("héllo wörld — 日本語 🚀   😀"),
        Value.VStr("quotes \" backslash \\ newline \n tab \t control \u0001"),
        Value.VBool(true),
        Value.VBool(false),
        Value.VNull,
        Value.VUnit,
        Value.VStruct(null, emptyMap()),
        Value.VStruct("None", emptyMap()),
        Value.VStruct(null, mapOf("z" to Value.VNum(1.0), "a" to Value.VNull, "m" to Value.VUnit)),
        Value.VStruct(
            "Outer",
            mapOf(
                "inner" to Value.VStruct("Inner", mapOf("list" to Value.VStruct(null, mapOf("x" to Value.VStr("ünï"))))),
                "flag" to Value.VBool(false),
            ),
        ),
    )

private val everyEntryKind: EffectLog =
    EffectLog(LogEntry.Start(mapOf("customer" to gold, "all" to Value.VStruct(null, everyValueShape.withIndex().associate { (i, v) -> "f$i" to v })))) +
        LogEntry.Reply(Call("creditScore", listOf(gold)), Value.VNum(700.0)) +
        LogEntry.Reply(Call("noArgs", emptyList()), Value.VStruct("Ok", emptyMap())) +
        LogEntry.Reply(Call("many", everyValueShape), Value.VNull) +
        LogEntry.Failure(
            listOf(
                Diagnostic("Division by zero", SourceSpan(3, 17)),
                Diagnostic("environment error — no span ✗", null),
            ),
        )

class JsonEncodingTest {
    private fun roundTrip(log: EffectLog) = decodeJson(encodeJson(log))

    private fun assertUnreadable(text: String): UnreadableLog {
        val thrown = assertFailsWith<KleinException> { decodeJson(text) }
        return assertIs<UnreadableLog>(thrown.errors.single())
    }

    private fun frame(vararg entries: String): String {
        val joined = entries.joinToString(",")
        return """{"format":"klein-effect-log","version":1,"entries":[$joined]}"""
    }

    private val startEntry = """{"entry":"start","inputs":{}}"""
    private val resultEntry = """{"entry":"result","value":1}"""

    @Test
    fun aStartOnlyLogRoundTrips() {
        val log = EffectLog(LogEntry.Start(emptyMap()))
        assertEquals(log, roundTrip(log))
    }

    @Test
    fun aDocumentWithNoEntriesIsRejected() {
        assertTrue(assertUnreadable(frame()).message.contains("start entry"))
    }

    @Test
    fun aDocumentWhoseFirstEntryIsNotAStartIsRejected() {
        assertTrue(assertUnreadable(frame(resultEntry)).message.contains("opens with its start entry"))
    }

    @Test
    fun aSecondStartEntryMidLogIsRejected() {
        assertTrue(assertUnreadable(frame(startEntry, startEntry)).message.contains("second start"))
    }

    @Test
    fun entriesAfterTheLogsEndingAreRejected() {
        assertTrue(assertUnreadable(frame(startEntry, resultEntry, resultEntry)).message.contains("follow the log's ending"))
    }

    @Test
    fun everyEntryKindAndValueShapeRoundTrips() {
        assertEquals(everyEntryKind, roundTrip(everyEntryKind))
    }

    @Test
    fun aResultTerminalRoundTrips() {
        val log = EffectLog(LogEntry.Start(emptyMap())) + LogEntry.Result(Value.VStr("done"))
        assertEquals(log, roundTrip(log))
    }

    @Test
    fun numberBitPatternsSurviveExactly() {
        val bits = listOf((-0.0).toRawBits(), Double.NaN.toRawBits(), 0x7ff8_dead_beef_0001L, 0xfff0_0000_0000_0000uL.toLong())
        val log = EffectLog(LogEntry.Start(bits.withIndex().associate { (i, b) -> "n$i" to Value.VNum(Double.fromBits(b)) }))
        val decoded = roundTrip(log).start
        assertEquals(bits, decoded.inputs.values.map { assertIs<Value.VNum>(it).value.toRawBits() })
    }

    @Test
    fun finiteNumbersReadAsPlainJsonNumbers() {
        val log = EffectLog(LogEntry.Start(mapOf("n" to Value.VNum(1.5), "z" to Value.VNum(-0.0))))
        val text = encodeJson(log)
        assertTrue(text.contains("\"n\":1.5"), text)
        assertTrue(text.contains("\"z\":-0.0"), text)
    }

    @Test
    fun nonFiniteNumbersCarryTheirRawBitsAsHex() {
        val payload = Value.VNum(Double.fromBits(0x7ff8_dead_beef_0001L))
        val log = EffectLog(LogEntry.Start(mapOf("nan" to payload, "inf" to Value.VNum(Double.POSITIVE_INFINITY))))
        val text = encodeJson(log)
        assertTrue(text.contains("\"nan\":{\"bits\":\"7ff8deadbeef0001\"}"), text)
        assertTrue(text.contains("\"inf\":{\"bits\":\"7ff0000000000000\"}"), text)
    }

    @Test
    fun structFieldOrderIsPreserved() {
        val struct = Value.VStruct(null, linkedMapOf("z" to Value.VNum(1.0), "a" to Value.VNum(2.0), "m" to Value.VNum(3.0)))
        val log = EffectLog(LogEntry.Start(mapOf("s" to struct)))
        val decoded = assertIs<Value.VStruct>(roundTrip(log).start.inputs["s"])
        assertEquals(listOf("z", "a", "m"), decoded.fields.keys.toList())
    }

    @Test
    fun aHandFormattedDocumentDecodes() {
        val text =
            """
            {
              "format": "klein-effect-log",
              "version": 1,
              "entries": [
                { "entry": "start", "inputs": { "n": 1.5, "u": { "unit": true } } },
                { "entry": "result", "value": { "tag": "Ok", "fields": {} } }
              ]
            }
            """.trimIndent()
        val expected =
            EffectLog(
                LogEntry.Start(mapOf("n" to Value.VNum(1.5), "u" to Value.VUnit)),
                ending = LogEntry.Result(Value.VStruct("Ok", emptyMap())),
            )
        assertEquals(expected, decodeJson(text))
    }

    @Test
    fun aDecodedLogReplaysIdenticallyToTheOriginal() {
        val contract = Klein.checkContract(LENDING)
        val rule = contract.compileRule("""creditScore(Customer(2, "basic")) + creditScore(customer)""", ReleaseNumber(1))
        var asks = 0
        fun makeHost() =
            contract.implement {
                immediate("customer") { asks++; gold }
                immediate("creditScore") { args ->
                    asks++
                    Value.VNum(if (assertIs<Value.VStruct>(args.single()).fields["tier"] == Value.VStr("gold")) 700.0 else 500.0)
                }
            }
        val live = assertIs<RunOutcome.Completed>(makeHost().run(rule))
        val fromOriginal = assertIs<RunOutcome.Completed>(makeHost().run(rule, log = live.log))
        asks = 0
        val fromDecoded = assertIs<RunOutcome.Completed>(makeHost().run(rule, log = roundTrip(live.log)))
        assertEquals(fromOriginal.value, fromDecoded.value)
        assertEquals(fromOriginal.log, fromDecoded.log)
        assertEquals(live.log, fromDecoded.log)
        assertEquals(0, asks)
    }

    @Test
    fun garbageTextIsUnreadable() {
        val diagnostic = assertUnreadable("hello")
        assertTrue(diagnostic.message.contains("unexpected character"), diagnostic.message)
    }

    @Test
    fun emptyTextIsUnreadable() {
        assertTrue(assertUnreadable("").message.contains("ends early"))
    }

    @Test
    fun aDocumentThatIsNotAJsonObjectIsUnreadable() {
        assertTrue(assertUnreadable("[1,2]").message.contains("not an effect log"))
    }

    @Test
    fun aMissingFormatMarkerIsUnreadable() {
        assertTrue(assertUnreadable("""{"version":1,"entries":[]}""").message.contains("not an effect log"))
    }

    @Test
    fun aWrongVersionStampIsUnreadable() {
        val text = """{"format":"klein-effect-log","version":99,"entries":[$startEntry]}"""
        val diagnostic = assertUnreadable(text)
        assertTrue(diagnostic.message.contains("unknown effect log version 99"), diagnostic.message)
    }

    @Test
    fun aVersionThatIsNotAWholeNumberIsUnreadable() {
        val text = """{"format":"klein-effect-log","version":"1","entries":[$startEntry]}"""
        assertTrue(assertUnreadable(text).message.contains("whole number"))
    }

    @Test
    fun entriesThatAreNotAnArrayAreUnreadable() {
        val text = """{"format":"klein-effect-log","version":1,"entries":true}"""
        assertTrue(assertUnreadable(text).message.contains("entries"))
    }

    @Test
    fun aTruncatedDocumentIsUnreadable() {
        val text = encodeJson(everyEntryKind)
        val truncated = text.substring(0, text.length - 1)
        assertTrue(assertUnreadable(truncated).message.contains("ends early"))
    }

    @Test
    fun everyTruncatedPrefixIsUnreadableNeverACrashOrAWrongLog() {
        val text = encodeJson(everyEntryKind)
        for (length in 0 until text.length) {
            assertUnreadable(text.substring(0, length))
        }
    }

    @Test
    fun anUnknownEntryKindIsUnreadable() {
        val diagnostic = assertUnreadable(frame("""{"entry":"snapshot"}"""))
        assertTrue(diagnostic.message.contains("unknown log entry kind \"snapshot\""), diagnostic.message)
    }

    @Test
    fun anUnknownValueShapeIsUnreadable() {
        val diagnostic = assertUnreadable(frame("""{"entry":"result","value":{"weird":1}}"""))
        assertTrue(diagnostic.message.contains("weird"), diagnostic.message)
    }

    @Test
    fun anUnexpectedFieldInAnEntryIsUnreadable() {
        val diagnostic = assertUnreadable(frame("""{"entry":"result","value":1,"extra":true}"""))
        assertTrue(diagnostic.message.contains("unexpected field \"extra\""), diagnostic.message)
    }

    @Test
    fun aDuplicateFieldIsUnreadable() {
        val diagnostic = assertUnreadable(frame("""{"entry":"start","inputs":{"a":1,"a":2}}"""))
        assertTrue(diagnostic.message.contains("duplicate field \"a\""), diagnostic.message)
    }

    @Test
    fun anUnknownStringEscapeIsUnreadable() {
        val diagnostic = assertUnreadable(frame("""{"entry":"result","value":"\x"}"""))
        assertTrue(diagnostic.message.contains("unknown escape"), diagnostic.message)
    }

    @Test
    fun anUnterminatedStringIsUnreadable() {
        assertTrue(assertUnreadable("""{"format":"klein-effect-""").message.contains("ends early inside a string"))
    }

    @Test
    fun aUnitValueThatIsNotTrueIsUnreadable() {
        val diagnostic = assertUnreadable(frame("""{"entry":"result","value":{"unit":1}}"""))
        assertTrue(diagnostic.message.contains("unit"), diagnostic.message)
    }

    @Test
    fun aDiagnosticWithHalfASpanIsUnreadable() {
        val diagnostic = assertUnreadable(frame("""{"entry":"failure","errors":[{"message":"m","start":1}]}"""))
        assertTrue(diagnostic.message.contains("both \"start\" and \"end\""), diagnostic.message)
    }

    @Test
    fun malformedNumberBitsAreUnreadable() {
        val diagnostic = assertUnreadable(frame("""{"entry":"result","value":{"bits":"xyz"}}"""))
        assertTrue(diagnostic.message.contains("hex"), diagnostic.message)
    }

    @Test
    fun aClosureInALogCannotBeEncoded() {
        val closure =
            Klein
                .tokenize("|x -> x|")
                .andThen(Klein::parse)
                .andThen(Klein::lower)
                .andThen(Klein::execute)
                .output!!
        assertFailsWith<IllegalArgumentException> { encodeJson(EffectLog(LogEntry.Start(mapOf("f" to closure)))) }
    }

    @Test
    fun trailingCharactersAreUnreadable() {
        val text = encodeJson(EffectLog(LogEntry.Start(emptyMap()))) + "x"
        val diagnostic = assertUnreadable(text)
        assertTrue(diagnostic.message.contains("trailing"), diagnostic.message)
    }

    @Test
    fun anUnexpectedFieldInEveryOwnerIsUnreadable() {
        val documents =
            listOf(
                """{"format":"klein-effect-log","version":1,"entries":[$startEntry],"x":1}""",
                frame("""{"entry":"start","inputs":{},"x":1}"""),
                frame("""{"entry":"reply","call":{"name":"f","args":[]},"answer":1,"x":1}"""),
                frame("""{"entry":"failure","errors":[],"x":1}"""),
                frame("""{"entry":"reply","call":{"name":"f","args":[],"x":1},"answer":1}"""),
                frame("""{"entry":"failure","errors":[{"message":"m","x":1}]}"""),
                frame("""{"entry":"result","value":{"bits":"7ff8000000000000","x":1}}"""),
                frame("""{"entry":"result","value":{"unit":true,"x":1}}"""),
                frame("""{"entry":"result","value":{"tag":"T","fields":{},"x":1}}"""),
            )
        documents.forEach { document ->
            val diagnostic = assertUnreadable(document)
            assertTrue(diagnostic.message.contains("unexpected field \"x\""), diagnostic.message)
        }
    }

    @Test
    fun aBareFieldsObjectDecodesToAnUntaggedStruct() {
        val text = frame(startEntry, """{"entry":"result","value":{"fields":{}}}""")
        val expected = EffectLog(LogEntry.Start(emptyMap()), ending = LogEntry.Result(Value.VStruct(null, emptyMap())))
        assertEquals(expected, decodeJson(text))
    }

    @Test
    fun aStructWithATagButNoFieldsIsUnreadable() {
        val diagnostic = assertUnreadable(frame("""{"entry":"result","value":{"tag":"T"}}"""))
        assertTrue(diagnostic.message.contains("fields"), diagnostic.message)
    }

    @Test
    fun everyHexDigitIsAcceptedInRawBits() {
        val text = frame(startEntry, """{"entry":"result","value":{"bits":"0123456789abcdef"}}""")
        val result = assertIs<LogEntry.Result>(decodeJson(text).ending)
        assertEquals(0x0123456789abcdefL, assertIs<Value.VNum>(result.value).value.toRawBits())
    }

    @Test
    fun aNonHexDigitInRawBitsIsUnreadable() {
        val diagnostic = assertUnreadable(frame("""{"entry":"result","value":{"bits":"0123456789abcdeg"}}"""))
        assertTrue(diagnostic.message.contains("hex"), diagnostic.message)
    }

    @Test
    fun uppercaseRawBitsAreUnreadable() {
        val diagnostic = assertUnreadable(frame("""{"entry":"result","value":{"bits":"0123456789ABCDEF"}}"""))
        assertTrue(diagnostic.message.contains("lowercase"), diagnostic.message)
    }

    @Test
    fun whitespaceInEveryStructuralPositionDecodes() {
        val text = """{ "format" : "klein-effect-log" , "version" : 1 , "entries" : [ { "entry" : "start" , "inputs" : { } } ] }""" + "   "
        assertEquals(EffectLog(LogEntry.Start(emptyMap())), decodeJson(text))
    }

    @Test
    fun handWrittenNumberFormsDecodeToTheirValues() {
        val text = frame("""{"entry":"start","inputs":{"a":0,"b":9,"c":90,"d":109,"e":1e5,"f":1E+5,"g":2.5e-3,"h":-0.0}}""")
        val expected =
            mapOf(
                "a" to Value.VNum(0.0),
                "b" to Value.VNum(9.0),
                "c" to Value.VNum(90.0),
                "d" to Value.VNum(109.0),
                "e" to Value.VNum(1e5),
                "f" to Value.VNum(1e5),
                "g" to Value.VNum(2.5e-3),
                "h" to Value.VNum(-0.0),
            )
        assertEquals(expected, decodeJson(text).start.inputs)
    }

    @Test
    fun aLeadingZeroFollowedByADigitIsUnreadable() {
        assertUnreadable(frame("""{"entry":"start","inputs":{"a":01}}"""))
    }

    @Test
    fun anExponentWithoutDigitsIsUnreadable() {
        val diagnostic = assertUnreadable(frame("""{"entry":"start","inputs":{"a":1e}}"""))
        assertTrue(diagnostic.message.contains("digit"), diagnostic.message)
    }

    @Test
    fun aSpaceIsWrittenRawAndAControlCharacterAsItsEscape() {
        val text = encodeJson(EffectLog(LogEntry.Start(mapOf("s" to Value.VStr("a b\u0001")))))
        assertTrue(text.contains("\"a b\\u0001\""), text)
    }

    @Test
    fun aRawControlCharacterInsideAStringIsUnreadable() {
        val diagnostic = assertUnreadable(frame("{\"entry\":\"result\",\"value\":\"a\tb\"}"))
        assertTrue(diagnostic.message.contains("raw control character"), diagnostic.message)
    }

    @Test
    fun aUnicodeEscapeCutOffByTheEndOfTextIsUnreadable() {
        val text = """{"format":"klein-effect-log","version":1,"entries":[{"entry":"result","value":"\u12"""
        assertTrue(assertUnreadable(text).message.contains("ends early"))
    }

    @Test
    fun aUnicodeEscapeWithNonHexDigitsIsUnreadable() {
        val diagnostic = assertUnreadable(frame("""{"entry":"result","value":"\uzzzz"}"""))
        assertTrue(diagnostic.message.contains("four hex digits"), diagnostic.message)
    }
}
