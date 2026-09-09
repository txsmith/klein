package klein.host.codec

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonPrimitivesTest {
    private fun read(text: String): Json = JsonReader(text).readDocument()

    private fun assertMalformed(text: String, fragment: String) {
        val thrown = assertFailsWith<MalformedJson> { read(text) }
        assertTrue(thrown.message.contains(fragment), thrown.message)
    }

    @Test
    fun nestingAtTheLimitParses() {
        val text = "[".repeat(JSON_MAX_DEPTH) + "]".repeat(JSON_MAX_DEPTH)
        var tree = read(text)
        var depth = 0
        while (tree is Json.JArr) {
            depth++
            tree = tree.items.singleOrNull() ?: break
        }
        assertEquals(JSON_MAX_DEPTH, depth)
    }

    @Test
    fun arraysNestedBeyondTheLimitAreRejectedInsteadOfOverflowingTheStack() {
        assertMalformed("[".repeat(100_000), "nesting deeper than $JSON_MAX_DEPTH levels")
    }

    @Test
    fun objectsNestedBeyondTheLimitAreRejectedInsteadOfOverflowingTheStack() {
        assertMalformed("{\"\":".repeat(50_000), "nesting deeper than $JSON_MAX_DEPTH levels")
    }

    @Test
    fun aSurrogatePairEscapeReadsAsOneCodePoint() {
        assertEquals(Json.JStr("\uD834\uDD1E"), read("\"\\uD834\\udd1e\""))
    }

    @Test
    fun aLoneHighSurrogateEscapeIsRejected() {
        assertMalformed("[\"\\uD800\"]", "not followed by a low surrogate")
    }

    @Test
    fun aHighSurrogateEscapeFollowedByANonSurrogateIsRejected() {
        assertMalformed("[\"\\uD800\\u0041\"]", "not followed by a low surrogate")
    }

    @Test
    fun aLoneLowSurrogateEscapeIsRejected() {
        assertMalformed("[\"\\uDFAA\"]", "lone low surrogate")
    }

    @Test
    fun aSignedHexEscapeIsRejected() {
        assertMalformed("[\"\\u+041\"]", "four hex digits")
        assertMalformed("[\"\\u-041\"]", "four hex digits")
    }

    @Test
    fun numbersBeyondDoubleRangeAreRejectedInsteadOfReadAsInfinity() {
        assertMalformed("[1e400]", "outside the range of a double")
        assertMalformed("[-1e400]", "outside the range of a double")
        assertMalformed("[123123e100000]", "outside the range of a double")
    }

    @Test
    fun aDuplicateKeyInAnObjectIsRejected() {
        assertMalformed("""{"a":1,"b":2,"a":3}""", "duplicate field \"a\"")
    }

    @Test
    fun numbersThatUnderflowReadAsZero() {
        assertEquals(Json.JArr(listOf(Json.JNum(0.0))), read("[1e-400]"))
    }
}
