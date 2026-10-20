package org.fungalsentinel.app.i18n

import java.io.File
import java.util.IllegalFormatException
import java.util.Locale
import java.util.MissingFormatArgumentException
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Format-string gate (Gate A1): the value of every `<string>` must be parsable by the Java Formatter.
 *
 * Background: on a real device, uploading a DNG in Step 1 once crashed with
 * `java.util.UnknownFormatConversionException: Conversion = '1'`. The root cause was an
 * intermediate i18n state that wrote the template as `%+1$.2f` -- Java format-string syntax is
 * `%[index$][flags][width][.precision]conversion`, so **the index must come before the flags**;
 * writing `%+1$…` makes `1` be taken as the conversion character. That string never entered git
 * (it came from an uncommitted working tree that Android Studio built and installed directly),
 * so it only ever surfaced on the phone.
 *
 * This gate turns "can the template be parsed by the Java Formatter" into an assertion in
 * `testDebugUnitTest`. The criterion is simple because, with no arguments passed, a legal
 * template only throws [MissingFormatArgumentException].
 */
class StringResourceFormatTest {

    private companion object {
        val QUALIFIERS = listOf("values", "values-zh", "values-fr", "values-es", "values-ny")

        /**
         * Fewer parsed entries than this counts as a regression: the smallest language directory
         * in a normal build still has dozens of entries, so dropping to 0 means the resource path
         * was not found and the gate would silently go "all green" -- which must be blocked.
         */
        const val MIN_ENTRIES = 50

        /** %[index$][flags][width][.precision]conversion -- the index group comes before the flags. */
        val PLACEHOLDER = Regex("""%(?:(\d+)\$)?[-#+ 0,(<]*\d*(?:\.\d+)?([a-zA-Z%])""")
    }

    // ── 1. Main gate ──────────────────────────────────────────────────────
    @Test
    fun everyStringResourceIsAParsableJavaFormatTemplate() {
        val failures = mutableListOf<String>()

        QUALIFIERS.forEach { qualifier ->
            val entries = readStrings(qualifier)
            assertTrue(
                "$qualifier only resolved ${entries.size} <string> entries, suspected resource path / parsing regression",
                entries.size >= MIN_ENTRIES
            )
            entries.forEach { (name, value) ->
                val error = illegalFormatError(value)
                if (error != null) {
                    failures += buildString {
                        append("[$qualifier] $name")
                        append("\n    raw       : $value")
                        append("\n    exception : ${error.javaClass.simpleName}: ${error.message}")
                    }
                }
            }
        }

        if (failures.isNotEmpty()) {
            fail(
                "${failures.size} strings are not legal Java format templates" +
                    " (the index must come before the flags, and a literal percent sign must be written %%):\n" +
                    failures.joinToString("\n")
            )
        }
    }

    // ── 2. Gate self-check: keep the rule from being rewritten into a tautology ──────────
    @Test
    fun detectorRejectsIllegalTemplatesAndKeepsLegalOnes() {
        // Note: `$` must be escaped as `\$` in Kotlin source, otherwise it starts a string template
        val mustFail = mapOf(
            "%+1\$.2f" to "index written after the flag",
            "%-1\$d" to "index written after the flag",
            "%q" to "unknown conversion",
            "%1\$" to "truncated format string",
            "Progress: 50%" to "bare percent sign (should be %%)"
        )
        mustFail.forEach { (template, why) ->
            val error = illegalFormatError(template)
            assertTrue(
                "gate broken: `$template` ($why) should have been judged an illegal template",
                error != null && error !is MissingFormatArgumentException
            )
        }

        val mustPass = listOf(
            "Hello",                    // no format string
            "100%%",                    // escaped literal percent sign
            "%1\$d%%",                   // the usage in half_preview_covered_pct
            "%1\$s — %2\$s %3\$s",
            "%1\$.4f",
            "%1\$+.2f",                  // legal index+flag ordering
            "%.1f"
        )
        mustPass.forEach { template ->
            val error = illegalFormatError(template)
            assertTrue(
                "false positive: legal template `$template` was judged illegal (${error?.javaClass?.simpleName})",
                error == null
            )
        }
    }

    // ── 3. Placeholders must not be lost or deformed in translation ──────────────────────
    @Test
    fun translationsKeepTheEnglishPlaceholders() {
        val base = readStrings("values").toMap()
        val mismatches = mutableListOf<String>()

        QUALIFIERS.drop(1).forEach { qualifier ->
            readStrings(qualifier).forEach { (name, value) ->
                val source = base[name] ?: return@forEach
                val expected = placeholderSignature(source)
                val actual = placeholderSignature(value)
                if (expected != actual) {
                    mismatches += buildString {
                        append("[$qualifier] $name")
                        append("\n    English     : $source  →  $expected")
                        append("\n    translation : $value  →  $actual")
                    }
                }
            }
        }

        if (mismatches.isNotEmpty()) {
            fail(
                "${mismatches.size} translations have placeholders that differ from the English" +
                    " (index/type must be preserved verbatim; word order may be adjusted via the index):\n" +
                    mismatches.joinToString("\n")
            )
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────
    /**
     * Run [String.format] once with no arguments. Returns null = the template is legal;
     * [MissingFormatArgumentException] means "legal but missing arguments" and is also allowed;
     * any other [IllegalFormatException] is treated as an illegal template.
     */
    private fun illegalFormatError(value: String): IllegalFormatException? =
        try {
            String.format(Locale.US, value)
            null
        } catch (e: MissingFormatArgumentException) {
            null
        } catch (e: IllegalFormatException) {
            e
        }

    /** Reduce the placeholders in one value to a comparable signature (a multiset of index → conversion). */
    private fun placeholderSignature(value: String): List<Pair<String, String>> {
        val signature = mutableListOf<Pair<String, String>>()
        var cursor = 0
        while (cursor < value.length) {
            if (value[cursor] != '%') {
                cursor += 1
                continue
            }
            val match = PLACEHOLDER.matchAt(value, cursor)
            if (match == null) {
                // Bare percent sign / truncation: let the main gate report it, this only ensures
                // it is never swallowed silently
                signature += "#" to "?"
                cursor += 1
            } else {
                val conversion = match.groupValues[2]
                if (conversion != "%") {
                    signature += match.groupValues[1].ifEmpty { "@" } to conversion
                }
                cursor = match.range.last + 1
            }
        }
        return signature.sortedWith(compareBy({ it.first }, { it.second }))
    }

    private fun readStrings(qualifier: String): List<Pair<String, String>> {
        val file = File(resRoot(), "$qualifier/strings.xml")
        assertTrue("resource file not found: ${file.path}", file.isFile)

        val factory = DocumentBuilderFactory.newInstance()
        val document = factory.newDocumentBuilder().parse(file)
        document.documentElement.normalize()

        val nodes = document.getElementsByTagName("string")
        val entries = mutableListOf<Pair<String, String>>()
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            val name = node.attributes.getNamedItem("name")?.nodeValue.orEmpty()
            entries += name to node.textContent.orEmpty()
        }
        return entries
    }

    /**
     * The working directory of JVM unit tests is the module directory (`app/`), but it differs
     * between runners, so walk upwards level by level until
     * `src/main/res/values/strings.xml` is hit.
     */
    private fun resRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "src/main/res/values/strings.xml").isFile) {
                return File(dir, "src/main/res")
            }
            dir = dir.parentFile
        }
        throw AssertionError("src/main/res not found -- the JVM unit test working directory is not the Android module directory")
    }
}
