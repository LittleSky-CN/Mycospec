package org.fungalsentinel.app.data.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Contract tests for `canonicalFluorophore()`.
 *
 * Background: Android and Python FSSA import / export each other's curve presets, and the two sides spell fluorophores differently.
 * The canonical ID is defined as the Android enum name (`YPET` / `MCHERRY` / ...), so the reader must be able to
 * resolve the Python spellings, otherwise the dropdown silently falls back to "please select a fluorophore".
 */
class FluorophoreCanonicalTest {

    // ── 1. canonical itself ────────────────────────────────────────────────
    @Test
    fun resolvesCanonicalEnumNamesInAnyCase() {
        Fluorophore.values().forEach { expected ->
            assertEquals(expected, canonicalFluorophore(expected.name))
            assertEquals(expected, canonicalFluorophore(expected.name.lowercase()))
            assertEquals(expected, canonicalFluorophore(expected.name.uppercase()))
            assertEquals(expected, canonicalFluorophore("  ${expected.name}  "))
        }
    }

    @Test
    fun resolvesOwnDisplayNameForEveryCategory() {
        // all 6 classes must be covered -- missing one means some preset cannot be read
        assertEquals(Fluorophore.YPET, canonicalFluorophore("Ypet"))
        assertEquals(Fluorophore.EGFP, canonicalFluorophore("EGFP"))
        assertEquals(Fluorophore.MCHERRY, canonicalFluorophore("mCherry"))
        assertEquals(Fluorophore.CFP, canonicalFluorophore("CFP"))
        assertEquals(Fluorophore.MTURQUOISE2, canonicalFluorophore("mTurquoise2"))
        assertEquals(Fluorophore.FLUORESCEIN, canonicalFluorophore("Fluorescein"))
    }

    // ── 2. cross-platform: keys / display names of Python FSSA ────────────────────────────
    @Test
    fun resolvesPythonFssaKeysWhenItStillShipsOldNames() {
        assertEquals(Fluorophore.EGFP, canonicalFluorophore("EGFP"))
        assertEquals(Fluorophore.CFP, canonicalFluorophore("CFP"))
        assertEquals(Fluorophore.MCHERRY, canonicalFluorophore("mCherry"))
        assertEquals(Fluorophore.MTURQUOISE2, canonicalFluorophore("mTurquoise2"))
    }

    @Test
    fun resolvesPythonFssaDisplayNames() {
        assertEquals(Fluorophore.YPET, canonicalFluorophore("Ypet (Venus variant)"))
        assertEquals(Fluorophore.FLUORESCEIN, canonicalFluorophore("Fluorescein (sodium salt)"))
        // dirty spellings with odd casing and spaces inside parentheses must resolve too
        assertEquals(Fluorophore.YPET, canonicalFluorophore("YPET (VENUS VARIANT)"))
        assertEquals(Fluorophore.FLUORESCEIN, canonicalFluorophore("fluorescein(sodium salt)"))
    }

    @Test
    fun resolvesCommonIdiomVariants() {
        assertEquals(Fluorophore.FLUORESCEIN, canonicalFluorophore("fluorescein"))
        assertEquals(Fluorophore.MTURQUOISE2, canonicalFluorophore("m-turquoise2"))
        assertEquals(Fluorophore.MTURQUOISE2, canonicalFluorophore("mturquoise2"))
        assertEquals(Fluorophore.MTURQUOISE2, canonicalFluorophore("M_Turquoise2"))
        assertEquals(Fluorophore.MCHERRY, canonicalFluorophore("mcherry"))
        assertEquals(Fluorophore.EGFP, canonicalFluorophore("egfp"))
    }

    // ── 3. unrecognisable input must return null so the caller can fail explicitly ────────────────────
    @Test
    fun unrecognisedInputReturnsNullInsteadOfAFallback() {
        assertNull(canonicalFluorophore(null))
        assertNull(canonicalFluorophore(""))
        assertNull(canonicalFluorophore("   "))
        assertNull(canonicalFluorophore("---"))
        assertNull(canonicalFluorophore("DsRed"))
        assertNull(canonicalFluorophore("mCherri"))   // one letter off; fuzzy matching is not allowed
        assertNull(canonicalFluorophore("mCherry2"))
        assertNull(canonicalFluorophore("sampleFluorophore#60"))
    }

    // ── 4. the fluorophore field of a curve preset goes through the same resolution path ────────────────────
    @Test
    fun pythonExportedPresetFluorophoreFieldIsResolvable() {
        val json = """
            {
              "version": 1,
              "fluorophore": "mCherry",
              "slope": 2.0, "intercept": 1.0, "rSquared": 0.99,
              "standardPoints": [
                {"concentration": 0.0, "signal": 1.0, "unit": "a.u."},
                {"concentration": 1.0, "signal": 3.0, "unit": "a.u."}
              ],
              "wavelengthsNm": [500.0, 510.0, 520.0],
              "referenceEmission": [0.0, 10.0, 0.0],
              "referenceAuc": 100.0,
              "windowMinNm": 500.0, "windowMaxNm": 520.0,
              "rbSwapped": false
            }
        """.trimIndent()
        val payload = org.fungalsentinel.app.data.preset.CurvePresetPayload.fromJson(json)
        assertEquals("mCherry", payload.fluorophore)          // the original text is kept when persisted
        assertEquals(Fluorophore.MCHERRY, canonicalFluorophore(payload.fluorophore))
        assertEquals("MCHERRY", canonicalFluorophore(payload.fluorophore)!!.name)
        assertEquals("590–630 nm", canonicalFluorophore(payload.fluorophore)!!.integrationRangeNm)
    }

    // ── 5. the two enums have been merged into one ───────────────────────────────────────
    @Test
    fun onlyOneFluorophoreEnumIsDeclaredInTheApp() {
        val declarations = sourceRoot().walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> line.contains("enum class Fluorophore") }
                    .map { (index, _) -> "${file.path.replace('\\', '/')}:${index + 1}" }
            }
            .toList()
        assertEquals(
            "Fluorophore must be declared exactly once (single source of truth); found ${declarations.size}: $declarations",
            1, declarations.size
        )
        assertEquals(
            "the single declaration must live in the data layer, Entities.kt",
            "org/fungalsentinel/app/data/model/Entities.kt",
            declarations.single().substringAfter("main/java/").substringBefore(":")
        )
    }

    @Test
    fun step3ScreenImportsTheDataLayerEnum() {
        val screen = File(sourceRoot(), "org/fungalsentinel/app/ui/steps/Step3SampleScreen.kt")
        val text = screen.readText()
        assertEquals(true, text.contains("import org.fungalsentinel.app.data.model.Fluorophore"))
        assertEquals(true, text.contains("import org.fungalsentinel.app.data.model.canonicalFluorophore"))
        assertEquals(false, text.contains("enum class Fluorophore"))
        assertEquals(false, text.contains("bandNm"))
    }

    private fun sourceRoot(): File {
        val start = System.getProperty("user.dir") ?: "."
        var dir = File(start)
        while (!File(dir, "src/main/java").isDirectory) {
            dir = dir.parentFile ?: error("Cannot locate src/main/java from $start")
        }
        return File(dir, "src/main/java")
    }
}
