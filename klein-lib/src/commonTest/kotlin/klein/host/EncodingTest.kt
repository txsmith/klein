package klein.host

import klein.Klein
import klein.KleinException
import klein.ReleaseNumber
import klein.SourceSpan
import klein.interp.RuntimeError
import klein.interp.Value
import klein.orFail
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
        Value.VStr("héllo wörld — 日本語 🚀 \u0000 😀"),
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
                RuntimeError("Division by zero", SourceSpan(3, 17)),
                RuntimeError("'ünï' used before its binding was evaluated ✗", SourceSpan(0, 3)),
            ),
        )

class EncodingTest {
    private fun roundTrip(log: EffectLog) = decode(encode(log))

    private fun assertUnreadable(bytes: ByteArray): UnreadableLog {
        val thrown = assertFailsWith<KleinException> { decode(bytes) }
        return assertIs<UnreadableLog>(thrown.errors.single())
    }

    private val startOnly = encode(EffectLog(LogEntry.Start(emptyMap())))
    private val startBytes = startOnly.copyOfRange(9, startOnly.size)
    private val endedLog = encode(EffectLog(LogEntry.Start(emptyMap()), ending = LogEntry.Result(Value.VNum(1.0))))
    private val resultBytes = endedLog.copyOfRange(startOnly.size, endedLog.size)

    private fun frame(vararg entries: ByteArray): ByteArray {
        val count = entries.size
        val countBytes = byteArrayOf((count ushr 24).toByte(), (count ushr 16).toByte(), (count ushr 8).toByte(), count.toByte())
        return startOnly.copyOfRange(0, 5) + countBytes + entries.fold(byteArrayOf()) { acc, e -> acc + e }
    }

    @Test
    fun aStartOnlyLogRoundTrips() {
        val log = EffectLog(LogEntry.Start(emptyMap()))
        assertEquals(log, roundTrip(log))
    }

    @Test
    fun bytesWithNoEntriesAreRejected() {
        assertTrue(assertUnreadable(frame()).message.contains("start entry"))
    }

    @Test
    fun bytesWhoseFirstEntryIsNotAStartAreRejected() {
        assertTrue(assertUnreadable(frame(resultBytes)).message.contains("opens with its start entry"))
    }

    @Test
    fun aSecondStartEntryMidLogIsRejected() {
        assertTrue(assertUnreadable(frame(startBytes, startBytes)).message.contains("second start"))
    }

    @Test
    fun entriesAfterTheLogsEndingAreRejected() {
        assertTrue(assertUnreadable(frame(startBytes, resultBytes, resultBytes)).message.contains("follow the log's ending"))
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
    fun aLogLargerThanTheWritersStartingBufferRoundTrips() {
        val wide = Value.VStruct(null, (1..300).associate { "field$it" to Value.VNum(it.toDouble()) })
        val log = EffectLog(LogEntry.Start(mapOf("long" to Value.VStr("k".repeat(5000)), "wide" to wide)))
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
    fun structFieldOrderIsPreserved() {
        val struct = Value.VStruct(null, linkedMapOf("z" to Value.VNum(1.0), "a" to Value.VNum(2.0), "m" to Value.VNum(3.0)))
        val log = EffectLog(LogEntry.Start(mapOf("s" to struct)))
        val decoded = assertIs<Value.VStruct>(roundTrip(log).start.inputs["s"])
        assertEquals(listOf("z", "a", "m"), decoded.fields.keys.toList())
    }

    @Test
    fun aDecodedLogReplaysIdenticallyToTheOriginal() {
        val contract = Klein.checkContract(LENDING)
        val rule = contract.compileRule("""creditScore(Customer(2, "basic")) + creditScore(customer)""", ReleaseNumber(1)).orFail()
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
    fun garbageBytesAreUnreadable() {
        val diagnostic = assertUnreadable(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9))
        assertTrue(diagnostic.message.contains("not an effect log"), diagnostic.message)
    }

    @Test
    fun emptyBytesAreUnreadable() {
        assertTrue(assertUnreadable(byteArrayOf()).message.contains("not an effect log"))
    }

    @Test
    fun exactlyTheMagicBytesWithNothingAfterAreRejectedAsNotAnEffectLog() {
        assertTrue(assertUnreadable(startOnly.copyOf(4)).message.contains("not an effect log"))
    }

    @Test
    fun theMagicAndVersionWithNothingAfterAreRejectedAsEndingEarly() {
        assertTrue(assertUnreadable(startOnly.copyOf(5)).message.contains("ends early"))
    }

    @Test
    fun aWrongVersionStampIsUnreadable() {
        val bytes = encode(EffectLog(LogEntry.Start(emptyMap())))
        bytes[4] = 99
        val diagnostic = assertUnreadable(bytes)
        assertTrue(diagnostic.message.contains("unknown effect log version 99"), diagnostic.message)
    }

    @Test
    fun aTruncatedLogIsUnreadable() {
        val bytes = encode(everyEntryKind)
        val diagnostic = assertUnreadable(bytes.copyOf(bytes.size / 2))
        assertTrue(diagnostic.message.contains("ends early"), diagnostic.message)
    }

    @Test
    fun everyTruncatedPrefixIsUnreadableNeverACrashOrAWrongLog() {
        val bytes = encode(everyEntryKind)
        for (length in 0 until bytes.size) {
            assertUnreadable(bytes.copyOf(length))
        }
    }

    @Test
    fun anUnknownEntryKindIsUnreadable() {
        assertTrue(assertUnreadable(frame(byteArrayOf(9))).message.contains("unknown log entry kind 9"))
    }

    @Test
    fun anUnknownValueKindIsUnreadable() {
        assertTrue(assertUnreadable(frame(byteArrayOf(2, 99))).message.contains("unknown value kind 99"))
    }

    @Test
    fun aByteThatIsNotABooleanIsUnreadable() {
        val resultWithBadBool = byteArrayOf(2, 2, 7)
        assertTrue(assertUnreadable(frame(resultWithBadBool)).message.contains("expected a boolean byte, found 7"))
    }

    @Test
    fun malformedUtf8IsUnreadable() {
        val resultWithBadString = byteArrayOf(2, 1, 0, 0, 0, 1, 0xFF.toByte())
        assertTrue(assertUnreadable(frame(resultWithBadString)).message.contains("malformed UTF-8"))
    }

    @Test
    fun anImplausibleCountIsUnreadable() {
        val resultWithHugeStringLength = byteArrayOf(2, 1, 0x7F, -1, -1, -1)
        assertTrue(assertUnreadable(frame(resultWithHugeStringLength)).message.contains("implausible count"))
    }

    @Test
    fun aStringLengthOneMoreThanTheBytesLeftIsAnImplausibleCount() {
        val resultWithOverlongStringLength = byteArrayOf(2, 1, 0, 0, 0, 3, 'a'.code.toByte(), 'b'.code.toByte())
        assertTrue(assertUnreadable(frame(resultWithOverlongStringLength)).message.contains("implausible count"))
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
        assertFailsWith<IllegalArgumentException> { encode(EffectLog(LogEntry.Start(mapOf("f" to closure)))) }
    }

    @Test
    fun trailingBytesAreUnreadable() {
        val diagnostic = assertUnreadable(encode(EffectLog(LogEntry.Start(emptyMap()))) + byteArrayOf(0))
        assertTrue(diagnostic.message.contains("trailing"), diagnostic.message)
    }
}
