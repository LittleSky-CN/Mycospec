package org.fungalsentinel.app.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Coverage gate (Gate A2): switching to any language must not "leak English".
 *
 * Criterion: every translatable key in `values/strings.xml` (except those marked
 * `translatable="false"` -- entries such as `app_name` and `language_*` that are themselves
 * written in another language) must appear in every `values-*` directory. Miss one and Android
 * silently falls back to the English original in `values/`, which is silent in unit tests,
 * lint and CI alike.
 */
class LocaleCoverageTest {

    private companion object {
        const val BASE_QUALIFIER = "values"
        val TRANSLATION_QUALIFIERS = listOf("values-zh", "values-fr", "values-es", "values-ny")
        const val MIN_BASE_ENTRIES = 300
        const val MIN_TRANSLATED_ENTRIES = 50
    }

    @Test
    fun everyTranslatableKeyExistsInEveryLocale() {
        val base = readEntries(BASE_QUALIFIER)
        assertTrue(
            "$BASE_QUALIFIER only resolved ${base.size} <string> entries, suspected resource path / parsing regression",
            base.size >= MIN_BASE_ENTRIES
        )

        val expected = base.filter { it.translatable }.map { it.name }
        assertTrue("$BASE_QUALIFIER contains no translatable keys at all", expected.isNotEmpty())

        val report = mutableListOf<String>()
        TRANSLATION_QUALIFIERS.forEach { qualifier ->
            val translated = readEntries(qualifier)
            assertTrue(
                "$qualifier only resolved ${translated.size} <string> entries, suspected resource path / parsing regression",
                translated.size >= MIN_TRANSLATED_ENTRIES
            )
            val present = translated.map { it.name }.toSet()
            val missing = expected.filter { it !in present }
            if (missing.isNotEmpty()) {
                report += "$qualifier: missing ${missing.size} / ${expected.size}\n    " +
                    missing.joinToString(", ")
            }
        }

        if (report.isNotEmpty()) {
            fail(
                "The following locales are missing keys and will silently fall back to English at runtime:\n" + report.joinToString("\n")
            )
        }
    }

    /** The reverse check: a translation directory must not keep zombie keys that `values/` has deleted. */
    @Test
    fun noLocaleKeepsKeysThatBaseNoLongerDefines() {
        val known = readEntries(BASE_QUALIFIER).map { it.name }.toSet()
        val unknown = mutableListOf<String>()

        TRANSLATION_QUALIFIERS.forEach { qualifier ->
            val stale = readEntries(qualifier).map { it.name }.filter { it !in known }
            if (stale.isNotEmpty()) {
                unknown += "$qualifier: ${stale.joinToString(", ")}"
            }
        }

        if (unknown.isNotEmpty()) {
            fail(
                "The following keys no longer exist in $BASE_QUALIFIER (leftover translations keep holding a resource id):\n" +
                    unknown.joinToString("\n")
            )
        }
    }

    private data class Entry(val name: String, val translatable: Boolean)

    private fun readEntries(qualifier: String): List<Entry> {
        val file = File(resRoot(), "$qualifier/strings.xml")
        assertTrue("resource file not found: ${file.path}", file.isFile)

        val factory = DocumentBuilderFactory.newInstance()
        val document = factory.newDocumentBuilder().parse(file)
        document.documentElement.normalize()

        val nodes = document.getElementsByTagName("string")
        val entries = mutableListOf<Entry>()
        for (i in 0 until nodes.length) {
            val attributes = nodes.item(i).attributes
            val name = attributes.getNamedItem("name")?.nodeValue.orEmpty()
            val translatable = attributes.getNamedItem("translatable")?.nodeValue != "false"
            entries += Entry(name, translatable)
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
