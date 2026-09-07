package klein.host

import klein.KleinException
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import kotlin.test.Test
import kotlin.test.assertTrue

class JsonTestSuiteTest {
    private val suite: List<File> =
        File(checkNotNull(javaClass.getResource("/json-test-suite/test_parsing")).toURI())
            .listFiles()
            .orEmpty()
            .sortedBy { it.name }

    private fun files(prefix: String): List<File> = suite.filter { it.name.startsWith(prefix) }

    private fun decodeUtf8(bytes: ByteArray): String? =
        try {
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (ignored: CharacterCodingException) {
            null
        }

    private fun read(text: String): Result<Json> =
        try {
            Result.success(JsonReader(text).readDocument())
        } catch (thrown: Throwable) {
            Result.failure(thrown)
        }

    private fun StringBuilder.writeJson(json: Json) {
        when (json) {
            is Json.JStr -> writeText(json.value)
            is Json.JNum -> writeNumber(json.value)
            is Json.JBool -> append(json.value)
            Json.JNull -> append("null")
            is Json.JArr -> {
                append('[')
                json.items.forEachIndexed { index, item ->
                    if (index > 0) append(',')
                    writeJson(item)
                }
                append(']')
            }
            is Json.JObj -> {
                append('{')
                var first = true
                json.fields.forEach { (name, field) ->
                    if (!first) append(',')
                    first = false
                    writeText(name)
                    append(':')
                    writeJson(field)
                }
                append('}')
            }
        }
    }

    private fun assertNoFailures(failures: List<String>) {
        assertTrue(failures.isEmpty(), failures.joinToString("\n", prefix = "${failures.size} suite files misbehaved:\n"))
    }

    @Test
    fun theSuiteIsPresent() {
        assertTrue(files("y_").size > 90, "expected the y_ files")
        assertTrue(files("n_").size > 180, "expected the n_ files")
        assertTrue(files("i_").size > 30, "expected the i_ files")
    }

    @Test
    fun everyAcceptedFileParsesAndRoundTrips() {
        val failures = mutableListOf<String>()
        for (file in files("y_")) {
            val text = decodeUtf8(file.readBytes())
            if (text == null) {
                failures.add("${file.name}: not valid UTF-8")
                continue
            }
            val parsed = read(text)
            val tree = parsed.getOrNull()
            if (tree == null) {
                failures.add("${file.name}: rejected with ${parsed.exceptionOrNull()}")
                continue
            }
            val written = StringBuilder().apply { writeJson(tree) }.toString()
            val reread = read(written).getOrNull()
            if (reread != tree) failures.add("${file.name}: written as $written, which read back as $reread instead of $tree")
        }
        assertNoFailures(failures)
    }

    @Test
    fun everyRejectedFileIsRefusedWithUnreadableLogAndNothingElse() {
        val failures = mutableListOf<String>()
        for (file in files("n_")) {
            val text = decodeUtf8(file.readBytes()) ?: continue
            val parsed = read(text)
            when (val thrown = parsed.exceptionOrNull()) {
                null -> failures.add("${file.name}: accepted as ${parsed.getOrNull()}")
                is MalformedJson -> {
                    val translated = runCatching { decodeJson(text) }.exceptionOrNull()
                    val error = (translated as? KleinException)?.errors?.singleOrNull()
                    if (error !is UnreadableLog || error.message != thrown.message) {
                        failures.add("${file.name}: the log codec turned ${thrown.message} into $translated")
                    }
                }
                else -> failures.add("${file.name}: escaped with ${thrown::class.simpleName}: ${thrown.message}")
            }
        }
        assertNoFailures(failures)
    }

    @Test
    fun everyImplementationDefinedFileParsesOrIsRejectedButNeverCrashes() {
        val failures = mutableListOf<String>()
        for (file in files("i_")) {
            val text = decodeUtf8(file.readBytes()) ?: continue
            val thrown = read(text).exceptionOrNull()
            if (thrown != null && thrown !is MalformedJson) {
                failures.add("${file.name}: escaped with ${thrown::class.simpleName}: ${thrown.message}")
            }
        }
        assertNoFailures(failures)
    }
}
