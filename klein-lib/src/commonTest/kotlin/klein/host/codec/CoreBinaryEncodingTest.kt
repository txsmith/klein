package klein.host.codec

import klein.CompilerVersion
import klein.Klein
import klein.KleinException
import klein.ReleaseNumber
import klein.SourceSpan
import klein.check.contract.Edition
import klein.core.Apply
import klein.core.Bind
import klein.core.Constant
import klein.core.CoreExpr
import klein.core.EnterScope
import klein.core.FieldGet
import klein.core.HostCall
import klein.core.Lambda
import klein.core.Literal
import klein.core.MakeData
import klein.core.Match
import klein.core.PrimApp
import klein.core.PrimOp
import klein.core.Run
import klein.core.Var
import klein.core.app
import klein.core.bind
import klein.core.bool
import klein.core.ctorArm
import klein.core.default
import klein.core.get
import klein.core.host
import klein.core.lam
import klein.core.litArm
import klein.core.match
import klein.core.mk
import klein.core.nul
import klein.core.num
import klein.core.prim
import klein.core.recordArm
import klein.core.scope
import klein.core.stmt
import klein.core.str
import klein.core.unit
import klein.core.v
import klein.host.RunOutcome
import klein.host.immediate
import klein.host.implement
import klein.interp.Value
import klein.orFail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val everyNodeKind: CoreExpr =
    scope(
        bind(0, lam(2, prim(PrimOp.Add, v(0, 0, "a"), v(0, 1, "b")), "add"), "add"),
        bind(1, host("creditScore", mk("Customer", "id" to num(1.0), "tier" to str("gold"))), "score"),
        stmt(app(v(0, 0, "add"), num(1.0), num(2.0))),
        result =
            match(
                get(v(0, 1, "score"), "value"),
                ctorArm("Some", listOf("x"), v(0, 0, "x"), guard = prim(PrimOp.Gt, v(0, 0, "x"), num(0.0))),
                ctorArm("None", emptyList(), nul()),
                recordArm(listOf("p", "q"), mk(null, "p" to v(0, 0, "p"), "q" to v(0, 1, "q"))),
                litArm(Constant.CNum(2.0), bool(true)),
                litArm(Constant.CStr("s"), bool(false), guard = prim(PrimOp.Not, bool(false))),
                litArm(Constant.CBool(true), unit()),
                litArm(Constant.CNull, str("null")),
                litArm(Constant.CUnit, str("unit")),
                default(mk(null), guard = prim(PrimOp.Eq, nul(), nul())),
                default(host("noArgs")),
            ),
    )

private val everyPrimOp: CoreExpr = EnterScope(PrimOp.entries.map { Run(prim(it, num(1.0), num(2.0)), SourceSpan.zero) }, unit(), SourceSpan.zero)

private val everyNumberBitPattern: List<Double> =
    listOf(
        1.5,
        0.0,
        -0.0,
        Double.NaN,
        Double.fromBits(0x7ff8_dead_beef_0001L),
        Double.POSITIVE_INFINITY,
        Double.NEGATIVE_INFINITY,
        Double.MIN_VALUE,
        Double.MAX_VALUE,
    )

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

private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

private fun count(value: Int): ByteArray = bytes(value ushr 24, value ushr 16, value ushr 8, value)

private fun node(expr: CoreExpr): ByteArray = encodeCore(expr).let { it.copyOfRange(1, it.size) }

private fun blob(vararg parts: ByteArray): ByteArray = parts.fold(bytes(CompilerVersion.CURRENT.value)) { acc, part -> acc + part }

private val zeroSpan = count(0) + count(0)

class CoreBinaryEncodingTest {
    private fun roundTrip(core: CoreExpr): CoreExpr = decodeCore(encodeCore(core))

    private fun assertUnreadable(bytes: ByteArray): UnreadableEdition {
        val thrown = assertFailsWith<KleinException> { decodeCore(bytes) }
        return assertIs<UnreadableEdition>(thrown.errors.single())
    }

    @Test
    fun everyNodeStatementArmAndConstantKindRoundTrips() {
        assertEquals(everyNodeKind, roundTrip(everyNodeKind))
    }

    @Test
    fun everyPrimitiveOperationRoundTrips() {
        assertEquals(everyPrimOp, roundTrip(everyPrimOp))
    }

    @Test
    fun aChainOfSixHundredAdditionsRoundTrips() {
        val program = Klein.tokenize("1" + " + 1".repeat(600)).andThen(Klein::parse).orFail()
        val core = Klein.lower(program).orFail()
        assertEquals(core, roundTrip(core))
    }

    @Test
    fun spansSurviveOnEveryNodeStatementAndArm() {
        val core =
            EnterScope(
                listOf(
                    Bind(0, "f", Lambda(1, Var(0, 0, "x", SourceSpan(4, 5)), "f", SourceSpan(3, 6)), SourceSpan(1, 7)),
                    Run(HostCall("ping", emptyList(), SourceSpan(8, 12)), SourceSpan(8, 13)),
                ),
                Match(
                    FieldGet(Apply(Var(1, 0, "f", SourceSpan(14, 15)), listOf(Literal(Constant.CNum(1.0), SourceSpan(16, 17))), SourceSpan(14, 18)), "v", SourceSpan(14, 20)),
                    listOf(
                        Match.DataArm("Some", listOf("x"), PrimApp(PrimOp.Not, listOf(Literal(Constant.CBool(false), SourceSpan(22, 27))), SourceSpan(21, 27)), MakeData("Ok", listOf("a"), listOf(Literal(Constant.CUnit, SourceSpan(30, 32))), SourceSpan(28, 33)), SourceSpan(21, 33)),
                        Match.LitArm(Constant.CStr("s"), null, Literal(Constant.CNull, SourceSpan(36, 40)), SourceSpan(34, 40)),
                        Match.Default(null, Literal(Constant.CUnit, SourceSpan(41, 43)), SourceSpan(41, 43)),
                    ),
                    SourceSpan(14, 44),
                ),
                SourceSpan(0, 45),
            )
        assertEquals(core, roundTrip(core))
    }

    @Test
    fun numberBitPatternsSurviveExactly() {
        everyNumberBitPattern.forEach { value ->
            val decoded = assertIs<Literal>(roundTrip(num(value)))
            val constant = assertIs<Constant.CNum>(decoded.value)
            assertEquals(value.toRawBits(), constant.value.toRawBits(), "bits of $value")
        }
        val inArm = assertIs<Match>(roundTrip(match(unit(), litArm(Constant.CNum(-0.0), unit()))))
        assertEquals((-0.0).toRawBits(), assertIs<Constant.CNum>(assertIs<Match.LitArm>(inArm.arms.single()).lit).value.toRawBits())
    }

    @Test
    fun stringsWithEveryKindOfCharacterRoundTrip() {
        val text = "héllo wörld — 日本語 🚀 \u0000 😀 \"quoted\" \\ tab\t"
        val core = scope(bind(0, str(text), text), result = get(host(text, mk(text, text to Var(0, 0, text, SourceSpan.zero))), text))
        assertEquals(core, roundTrip(core))
    }

    @Test
    fun aTreeLargerThanTheWritersStartingBufferRoundTrips() {
        val wide = MakeData(null, (1..300).map { "field$it" }, (1..300).map { num(it.toDouble()) }, SourceSpan.zero)
        val core = scope(bind(0, str("k".repeat(5000)), "long"), result = wide)
        assertEquals(core, roundTrip(core))
    }

    @Test
    fun aDecodedCoreRunsIdenticallyToTheOriginal() {
        val contract = Klein.checkContract(LENDING)
        val edition =
            contract
                .compileRule(
                    """
                    score = creditScore(customer)
                    verdict = match customer
                      Customer { tier } -> if tier == "gold" then score > 600 else false
                    if verdict then "approve" else "decline"
                    """.trimIndent(),
                    ReleaseNumber(1),
                ).orFail()
        val decoded = roundTrip(edition.core)
        assertEquals(edition.core, decoded)

        fun makeHost() =
            contract.implement(
                immediate("customer") { gold },
                immediate("creditScore") { Value.VNum(700.0) },
            )
        val rederived = Edition(edition.language, decoded, edition.pinsWithHash, edition.source)
        val original = assertIs<RunOutcome.Completed>(makeHost().run(edition))
        val fromDecoded = assertIs<RunOutcome.Completed>(makeHost().run(rederived))
        assertEquals(Value.VStr("approve"), fromDecoded.value)
        assertEquals(original.value, fromDecoded.value)
        assertEquals(original.log, fromDecoded.log)
    }

    @Test
    fun theBlobOpensWithTheCurrentCompilerVersion() {
        val encoded = encodeCore(unit())
        assertEquals(CompilerVersion.CURRENT.value, encoded[0].toInt())
        assertEquals(CompilerVersion.CURRENT, readCoreVersion(encoded))
    }

    @Test
    fun theEncodingIsIdenticalAcrossTwoIndependentlyBuiltTrees() {
        assertTrue(encodeCore(everyNodeKind).contentEquals(encodeCore(roundTrip(everyNodeKind))))
    }

    @Test
    fun readCoreVersionReadsOnlyTheHeaderByte() {
        assertEquals(CompilerVersion(7), readCoreVersion(bytes(7)))
        assertEquals(CompilerVersion(7), readCoreVersion(bytes(7, 99, 99, 99)))
        assertEquals(CompilerVersion(255), readCoreVersion(bytes(0xFF)))
    }

    @Test
    fun anEmptyBlobIsUnreadableToBoth() {
        assertTrue(assertUnreadable(bytes()).message.contains("empty"))
        val thrown = assertFailsWith<KleinException> { readCoreVersion(bytes()) }
        assertTrue(assertIs<UnreadableEdition>(thrown.errors.single()).message.contains("empty"))
    }

    @Test
    fun aForeignCompilerVersionIsUnreadableToDecodeCore() {
        val bytes = encodeCore(unit())
        bytes[0] = 2
        val message = assertUnreadable(bytes).message
        assertTrue(message.contains("compiler version 2"), message)
        assertTrue(message.contains("compiler version 1"), message)
    }

    @Test
    fun everyTruncatedPrefixIsUnreadableNeverACrashOrAWrongTree() {
        val bytes = encodeCore(everyNodeKind)
        for (length in 0 until bytes.size) {
            assertUnreadable(bytes.copyOf(length))
        }
    }

    @Test
    fun aHeaderWithNothingAfterItIsUnreadableAsEndingEarly() {
        assertTrue(assertUnreadable(bytes(1)).message.contains("ends early"))
    }

    @Test
    fun trailingBytesAreUnreadable() {
        val message = assertUnreadable(encodeCore(unit()) + bytes(0)).message
        assertTrue(message.contains("1 unexpected trailing bytes"), message)
    }

    @Test
    fun anUnknownNodeTagIsUnreadable() {
        assertTrue(assertUnreadable(blob(bytes(42))).message.contains("unknown Core node tag 42"))
    }

    @Test
    fun anUnknownStatementTagIsUnreadable() {
        val message = assertUnreadable(blob(bytes(8), count(1), bytes(7))).message
        assertTrue(message.contains("unknown Core statement tag 7"), message)
    }

    @Test
    fun anUnknownArmTagIsUnreadable() {
        val message = assertUnreadable(blob(bytes(9), node(unit()), count(1), bytes(9))).message
        assertTrue(message.contains("unknown Core arm tag 9"), message)
    }

    @Test
    fun anUnknownConstantTagIsUnreadable() {
        assertTrue(assertUnreadable(blob(bytes(0, 9))).message.contains("unknown Core constant tag 9"))
        val inArm = assertUnreadable(blob(bytes(9), node(unit()), count(1), bytes(1, 9))).message
        assertTrue(inArm.contains("unknown Core constant tag 9"), inArm)
    }

    @Test
    fun anUnknownPrimitiveOperationIsUnreadable() {
        val message = assertUnreadable(blob(bytes(4, 200))).message
        assertTrue(message.contains("unknown primitive operation 200"), message)
    }

    @Test
    fun aDataNodeWhoseFieldNamesAndArgumentsDisagreeIsUnreadable() {
        val oneNameNoArgs = blob(bytes(5, 0), count(1), count(1), bytes('a'.code), count(0), zeroSpan)
        val message = assertUnreadable(oneNameNoArgs).message
        assertTrue(message.contains("names 1 fields but carries 0 arguments"), message)
    }

    @Test
    fun aByteThatIsNotABooleanIsUnreadable() {
        val message = assertUnreadable(blob(bytes(2), count(1), node(unit()), bytes(7))).message
        assertTrue(message.contains("expected a boolean byte, found 7"), message)
    }

    @Test
    fun anImplausibleCountIsUnreadable() {
        val message = assertUnreadable(blob(bytes(3), node(unit()), bytes(0x7F, 0xFF, 0xFF, 0xFF))).message
        assertTrue(message.contains("implausible count"), message)
    }

    @Test
    fun malformedUtf8IsUnreadable() {
        val message = assertUnreadable(blob(bytes(6), node(unit()), count(1), bytes(0xFF), zeroSpan)).message
        assertTrue(message.contains("malformed UTF-8"), message)
    }
}
