package org.fungalsentinel.app.data.preset

import org.fungalsentinel.app.analysis.SpectralResponseCalibrator
import org.fungalsentinel.app.analysis.SpdCalibrationResult
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Save -> load -> reuse coverage for the three preset types declared in
 * [PresetTypes] (WAVELENGTH / SPD / CURVE).
 *
 * Since schema v3 an SPD payload is indexed by WAVELENGTH on the canonical
 * uniform grid (380..780 nm step 1 nm, 401 points) and nothing in it depends on
 * the pixel<->nm mapping any more. Every SPD fixture below therefore rides on
 * [SpectralResponseCalibrator.canonicalGrid].
 *
 * These tests exercise everything that can be exercised without an Android
 * framework runtime (no Room, no ViewModel).  The Room round-trip itself is
 * covered indirectly: [PresetEntity] is a plain data class whose columns are
 * asserted in [wavelengthPresetCarriesEveryMappingColumn].
 */
class PresetRoundTripTest {

    // ---------------------------------------------------------------- SPD --

    /** The toJson file-level metadata cannot default to Build.* in a JVM unit test. */
    private fun write(r: SpdCalibrationResult): String = SpectralResponseCalibrator.toJson(
        r, deviceModel = "Pixel 8", appVersion = "2.3.0", createdAt = "2026-10-18T12:00:00Z"
    )

    private fun grid(): FloatArray = SpectralResponseCalibrator.canonicalGrid()

    private fun spd(): SpdCalibrationResult {
        val w = grid()
        val n = w.size                                            // 401
        fun ramp(offset: Float) = FloatArray(n) { (it + offset) * 1.7f }
        // The Step 2 mask: valid band AND lamp SPD above the threshold.
        val mask = BooleanArray(n) { w[it] >= 430f && w[it] <= 660f }
        return SpdCalibrationResult(
            wavelengths = w,
            respR = ramp(1f), respG = ramp(2f), respB = ramp(3f),
            posR = ramp(4f), posG = ramp(5f), posB = ramp(6f),
            corrR = ramp(7f), corrG = ramp(8f), corrB = ramp(9f),
            // v1.4 response-proportional fusion weights (sum == 1 pointwise)
            wB = FloatArray(n) { 0.2f + it * 0.001f },
            wG = FloatArray(n) { 0.3f + it * 0.001f },
            wR = FloatArray(n) { 0.4f - it * 0.001f },
            fused = FloatArray(n) { 12.5f + it },
            mask = mask,
            effectiveBandNm = 430f to 660f,
            validRangeNm = 430f to 660f,
            maskedCount = mask.count { it }, gridPoints = n, thresholdRatio = 0.1f,
            crossingBG = 483.25f, crossingGR = 577.75f, agreementPct = 3.125f,
            peakRespR = 110.5f to 595.5f,
            peakRespG = 210.5f to 522.5f,
            peakRespB = 310.5f to 467.5f,
            fusedPeak = 0.12345f to 512.25f,
            exposureSeconds = 0.2, frames = 3, spdSource = "true_spd.csv (bundled)",
            rbSwapped = true,
            slope = -2.738911, intercept = 3198.4744, gErrorNm = 5.512,
            xRoiStart = 1204, xRoiEnd = 2871, sensorHeight = 3072
        )
    }

    @Test
    fun spdCalibrationRoundTripsEveryV3Field() {
        val original = spd()
        val json = JSONObject(write(original))

        // ---- file-level metadata: written by Android AND by the desktop ----
        assertEquals(3, json.getInt("schema_version"))
        assertEquals("SPD", json.getString("preset_type"))
        assertEquals("2.3.0", json.getString("app_version"))
        assertEquals("2026-10-18T12:00:00Z", json.getString("created_at"))
        assertEquals("Pixel 8", json.getString("device_model"))
        assertEquals("uniform_wavelength", json.getJSONObject("axis").getString("kind"))
        assertEquals(380.0, json.getJSONObject("axis").getDouble("start_nm"), 1e-12)
        assertEquals(780.0, json.getJSONObject("axis").getDouble("stop_nm"), 1e-12)
        assertEquals(1.0, json.getJSONObject("axis").getDouble("step_nm"), 1e-12)

        val restored = SpectralResponseCalibrator.fromJson(json.toString())

        assertNotNull(restored)
        restored!!

        assertEquals(SpectralResponseCalibrator.canonicalGrid().size, restored.gridPoints)
        assertArray("wavelengths", original.wavelengths, restored.wavelengths)
        assertArray("respR", original.respR, restored.respR)
        assertArray("respG", original.respG, restored.respG)
        assertArray("respB", original.respB, restored.respB)
        assertArray("posR", original.posR, restored.posR)
        assertArray("posG", original.posG, restored.posG)
        assertArray("posB", original.posB, restored.posB)
        assertArray("corrR", original.corrR, restored.corrR)
        assertArray("corrG", original.corrG, restored.corrG)
        assertArray("corrB", original.corrB, restored.corrB)
        // The derived curves that report Figure 4a/4b/4c/4d draw from. With Mode B
        // gone there is no raw lamp spectrum to rebuild them from, so they have to
        // survive the payload.
        assertArray("wB", original.wB, restored.wB)
        assertArray("wG", original.wG, restored.wG)
        assertArray("wR", original.wR, restored.wR)
        assertArray("fused", original.fused, restored.fused)
        assertArray("mask", original.mask, restored.mask)
        assertEquals(original.effectiveBandNm, restored.effectiveBandNm)
        assertEquals(original.validRangeNm, restored.validRangeNm)
        assertEquals(original.maskedCount, restored.maskedCount)
        assertEquals(original.gridPoints, restored.gridPoints)
        assertEquals(original.thresholdRatio, restored.thresholdRatio, 0f)
        assertEquals(original.crossingBG, restored.crossingBG, 0f)
        assertEquals(original.crossingGR, restored.crossingGR, 0f)
        assertEquals(original.agreementPct, restored.agreementPct, 0f)
        assertEquals(original.peakRespR, restored.peakRespR)
        assertEquals(original.peakRespG, restored.peakRespG)
        assertEquals(original.peakRespB, restored.peakRespB)
        assertEquals(original.fusedPeak, restored.fusedPeak)
        assertEquals(original.exposureSeconds, restored.exposureSeconds, 0.0)
        assertEquals(original.frames, restored.frames)
        assertEquals(original.spdSource, restored.spdSource)
        assertEquals(original.rbSwapped, restored.rbSwapped)
        assertEquals(original.sensorHeight, restored.sensorHeight)
    }

    @Test
    fun spdCalibrationRoundTripsTheTraceabilityFieldsAndCarriesNoRawLampData() {
        // The mapping (slope/intercept), the Step 1 residual, the X-ROI and the
        // sensor geometry travel with the file for DIAGNOSTICS ONLY -- the
        // drift hint. Nothing rebuilds a curve from them: after v3 the response
        // owns its own wavelength axis, so the Mode B inputs (raw lamp profiles
        // and the reference lamp grid) are gone for good.
        val original = spd()
        val json = JSONObject(write(original))

        val forbidden = listOf(
            // Mode B inputs deleted with Mode B
            "lampR", "lampG", "lampB", "spdRefWl", "spdRefVal",
            // pre-v3 keys that MUST NOT come back
            "version", "respR", "respG", "respB", "totalCount", "exposure",
            "bandLo", "bandHi", "xRoiStart", "xRoiEnd", "gErrorNm",
            "thresholdRatio", "crossingBG", "crossingGR", "peakR", "peakG", "peakB"
        )
        forbidden.forEach { assertFalse("'$it' must not be written any more", json.has(it)) }

        val restored = SpectralResponseCalibrator.fromJsonStrict(json.toString())
        assertEquals(-2.738911, restored.slope, 1e-12)
        assertEquals(3198.4744, restored.intercept, 1e-9)
        assertEquals(5.512, restored.gErrorNm!!, 1e-9)
        assertEquals(1204, restored.xRoiStart)
        assertEquals(2871, restored.xRoiEnd)
        assertEquals(3072, restored.sensorHeight)
    }

    @Test
    fun spdPayloadForAFullSensorStaysInsideTheCursorWindow() {
        // `payloadJson` is a SQLite TEXT column read through a CursorWindow
        // (default 2 MB). A v3 payload always carries 13 GRID-indexed arrays --
        // the same count whether the sensor had 640 or 3072 rows -- so the size
        // no longer scales with the sensor height at all.
        val w = grid()
        val n = w.size
        fun ramp(offset: Float) = FloatArray(n) { (it + offset) * 1.7f }
        val mask = BooleanArray(n) { w[it] >= 430f && w[it] <= 660f }
        val big = SpdCalibrationResult(
            wavelengths = w,
            respR = ramp(1f), respG = ramp(2f), respB = ramp(3f),
            posR = ramp(4f), posG = ramp(5f), posB = ramp(6f),
            corrR = ramp(7f), corrG = ramp(8f), corrB = ramp(9f),
            wB = FloatArray(n) { 0.2f }, wG = FloatArray(n) { 0.3f }, wR = FloatArray(n) { 0.5f },
            fused = FloatArray(n) { 12.5f + it * 0.01f },
            mask = mask,
            effectiveBandNm = 430f to 660f,
            validRangeNm = 430f to 660f,
            maskedCount = mask.count { it }, gridPoints = n, thresholdRatio = 0.1f,
            crossingBG = 483.25f, crossingGR = 577.75f, agreementPct = 3.125f,
            peakRespR = 110.5f to 595.5f,
            peakRespG = 210.5f to 522.5f,
            peakRespB = 310.5f to 467.5f,
            fusedPeak = 0.12345f to 512.25f,
            exposureSeconds = 0.2, frames = 3, spdSource = "true_spd.csv (bundled)",
            rbSwapped = false,
            slope = -2.738911, intercept = 3198.4744, gErrorNm = 5.512,
            xRoiStart = 1204, xRoiEnd = 2871, sensorHeight = 3072
        )
        val payload = write(big)
        println("[METRIC] v3 full-sensor SPD payload = ${payload.length} chars (N=3072 sensor)")
        assertTrue(
            "payload is ${payload.length} chars, expected < 1_000_000 (CursorWindow guard)",
            payload.length < 1_000_000
        )
        val restored = SpectralResponseCalibrator.fromJsonStrict(payload)
        assertEquals(n, restored.wavelengths.size)
        assertEquals(401, restored.gridPoints)
        assertEquals(3072, restored.sensorHeight)
    }

    @Test
    fun spdCalibrationRoundTripKeepsTheRbSwapFlag() {
        // DEF-A02 mitigation check: the R/B auto-correction flag survives the
        // SPD payload (it does NOT survive the WAVELENGTH preset -- see below).
        val restored = SpectralResponseCalibrator.fromJson(write(spd()))
        assertEquals(true, restored!!.rbSwapped)
        val notSwapped = SpectralResponseCalibrator.fromJson(write(spd().copy(rbSwapped = false)))
        assertEquals(false, notSwapped!!.rbSwapped)
    }

    @Test
    fun spdCalibrationFromJsonReturnsNullForTruncatedPayload() {
        // DEF-A03: a broken payload degrades to null and
        // ProjectViewModel.loadSpdPreset() then calls failSpdPreset() with the
        // reason, so the user is told instead of silently running uncorrected.
        assertNull(SpectralResponseCalibrator.fromJson("{}"))
        assertNull(SpectralResponseCalibrator.fromJson("not json at all"))
        assertNull(SpectralResponseCalibrator.fromJson("{\"schema_version\":2}"))
        assertNull(SpectralResponseCalibrator.fromJson("{\"version\":3}"))
    }

    @Test
    fun spdCalibrationFromJsonRejectsMissingArrays() {
        // DEF-A03 (fixed): a missing key must be REJECTED, not silently
        // zero-filled. `fromJson` keeps its nullable signature and reports the
        // failure as null; `fromJsonStrict` surfaces the reason.
        val payload = write(spd())
        val stripped = JSONObject(payload).apply { remove("response_g") }.toString()

        assertNull("a payload missing response_g must not restore", SpectralResponseCalibrator.fromJson(stripped))
        assertThrows<IllegalArgumentException> { SpectralResponseCalibrator.fromJsonStrict(stripped) }

        // A length mismatch is rejected the same way.
        val short = JSONObject(payload)
            .apply { put("response_b", org.json.JSONArray((0 until 7).map { 1.0 })) }
            .toString()
        assertNull(SpectralResponseCalibrator.fromJson(short))
        assertThrows<IllegalArgumentException> { SpectralResponseCalibrator.fromJsonStrict(short) }

        // The intact payload still round-trips.
        val restored = SpectralResponseCalibrator.fromJson(payload)
        assertNotNull(restored)
        assertEquals(401, restored!!.respG.size)
    }

    // -------------------------------------------------------------- CURVE --

    private fun curvePayload(spdJson: String?) = CurvePresetPayload(
        fluorophore = "EGFP",
        slope = 0.00031401929253439126,
        intercept = 0.25574303425597766,
        rSquared = 0.987654321,
        standardPoints = listOf(
            CurveStandardPoint(100.0, 0.27778, "mg/L"),
            CurveStandardPoint(60.0, 0.293314, "mg/L"),
            CurveStandardPoint(20.0, 0.252658, "mg/L")
        ),
        wavelengthsNm = listOf(497.0, 520.0, 560.0, 600.0, 617.0),
        referenceEmission = listOf(0.0, 0.293314, 0.12, 0.03, 0.0),
        referenceAuc = 0.293314,
        windowMinNm = 497.0,
        windowMaxNm = 617.0,
        rbSwapped = true,
        spdCalibrationJson = spdJson
    )

    @Test
    fun curvePresetRoundTripsRegressionAndReferenceSpectrum() {
        val original = curvePayload(spdJson = null)
        val restored = CurvePresetPayload.fromJson(original.toJson())

        assertEquals(original.fluorophore, restored.fluorophore)
        assertEquals(original.slope, restored.slope, 0.0)
        assertEquals(original.intercept, restored.intercept, 0.0)
        assertEquals(original.rSquared, restored.rSquared, 0.0)
        assertEquals(original.standardPoints, restored.standardPoints)
        assertEquals(original.wavelengthsNm, restored.wavelengthsNm)
        assertEquals(original.referenceEmission, restored.referenceEmission)
        assertEquals(original.referenceAuc, restored.referenceAuc, 0.0)
        assertEquals(original.windowMinNm, restored.windowMinNm, 0.0)
        assertEquals(original.windowMaxNm, restored.windowMaxNm, 0.0)
        assertEquals(original.rbSwapped, restored.rbSwapped)
        assertNull(restored.spdCalibrationJson)
    }

    @Test
    fun curvePresetRoundTripsItsEmbeddedSpdCalibration() {
        // A CURVE preset embeds the SPD calibration so Step 4 can be reused
        // without redoing Step 2.  The embedded payload must round-trip too.
        val original = curvePayload(spdJson = write(spd()))
        val restored = CurvePresetPayload.fromJson(original.toJson())

        assertNotNull(restored.spdCalibrationJson)
        val originalSpd = SpectralResponseCalibrator.fromJson(original.spdCalibrationJson!!)
        val restoredSpd = SpectralResponseCalibrator.fromJson(restored.spdCalibrationJson!!)
        assertNotNull(originalSpd)
        assertNotNull(restoredSpd)
        assertArray("embedded wavelengths", originalSpd!!.wavelengths, restoredSpd!!.wavelengths)
        assertArray("embedded response_g", originalSpd.respG, restoredSpd.respG)
        assertArray("embedded weight_g", originalSpd.wG, restoredSpd.wG)
        assertArray("embedded fused", originalSpd.fused, restoredSpd.fused)
        assertEquals(originalSpd.rbSwapped, restoredSpd.rbSwapped)
    }

    @Test
    fun curvePresetRejectsBrokenEmbeddedSpdCalibration() {
        val broken = curvePayload(spdJson = "{\"schema_version\":2}")
        assertThrows<IllegalArgumentException> { broken.validate() }
        assertThrows<IllegalArgumentException> { CurvePresetPayload.fromJson(broken.toJson()) }
    }

    // ---------------------------------------------------------- WAVELENGTH --

    @Test
    fun wavelengthPresetCarriesEveryMappingColumn() {
        // A WAVELENGTH preset is only reusable if it can rebuild the analysis
        // state that ProjectViewModel keeps in memory: the pixel<->nm mapping
        // (slope/intercept), the ROI used while decoding, the validation error
        // and the sensor geometry.
        val preset = PresetEntity(
            name = "bench-1",
            type = PresetTypes.WAVELENGTH,
            deviceModel = "Pixel",
            slope = -2.738911,
            intercept = 3198.4744,
            gErrorNm = 5.512,
            quality = "WARNING",
            roiStart = 100, roiEnd = 900,
            sensorWidth = 4032, sensorHeight = 3024,
            peakR = 1493, peakG = 1782, peakB = 1931,
            wlR = 622.5, wlG = 522.5, wlB = 462.5,
            exposureSeconds = 0.05
        )
        val reloaded = preset.copy()          // Room would rebuild the same instance

        assertEquals(PresetTypes.WAVELENGTH, reloaded.type)
        assertEquals(-2.738911, reloaded.slope!!, 1e-12)
        assertEquals(3198.4744, reloaded.intercept!!, 1e-9)
        assertEquals(5.512, reloaded.gErrorNm!!, 1e-9)
        assertEquals(100, reloaded.roiStart)
        assertEquals(900, reloaded.roiEnd)
        assertEquals(4032, reloaded.sensorWidth)
        assertEquals(3024, reloaded.sensorHeight)
        assertEquals(1493, reloaded.peakR)
        assertEquals(1782, reloaded.peakG)
        assertEquals(1931, reloaded.peakB)
        assertEquals(622.5, reloaded.wlR!!, 1e-9)
        assertEquals(522.5, reloaded.wlG!!, 1e-9)
        assertEquals(462.5, reloaded.wlB!!, 1e-9)
        assertEquals(0.05, reloaded.exposureSeconds!!, 1e-12)
    }

    @Test
    fun wavelengthPresetPersistsTheRbSwapFlag() {
        // DEF-A01 (fixed): WavelengthCalibrator exposes `rbSwapped` and
        // ProjectViewModel writes it into the global decoder flag
        // `FrameProfileExtractor.forceRbSwap`.  PresetEntity now has a column
        // for it (Room DB version 3 + MIGRATION_2_3), so a reloaded WAVELENGTH
        // preset no longer silently falls back to forceRbSwap == false -- which
        // for MCHERRY / CFP / mTurquoise2 would read the wrong channel.
        val names = PresetEntity::class.java.declaredFields
            .map { it.name }
            .filter { it != "Companion" && !it.startsWith("\$") }
            .toSet()
        assertTrue(
            "Expected PresetEntity to expose the mapping columns",
            names.containsAll(listOf("slope", "intercept", "gErrorNm", "roiStart", "roiEnd"))
        )
        assertTrue(
            "rbSwapped column is required (DEF-A01)",
            "rbSwapped" in names
        )

        // The flag itself must survive a Room round-trip (data class rebuild).
        val preset = PresetEntity(
            name = "bench-1", type = PresetTypes.WAVELENGTH, deviceModel = "Pixel",
            slope = -2.738911, intercept = 3198.4744, rbSwapped = true
        )
        assertEquals(true, preset.copy().rbSwapped)
        assertEquals(
            false,
            preset.copy(rbSwapped = false).rbSwapped
        )
        // Legacy rows (column absent until the migration runs) read back as null.
        assertEquals(null, preset.copy(rbSwapped = null).rbSwapped)
    }

    @Test
    fun spdPresetSummaryColumnsAreUnparsableFromTheReportStrings() {
        // DEF-A05 (reproduction): ProjectViewModel.saveSpdPreset() reads
        // `r.spdCoeffR` / `r.spdCoeffB`, which applySpdResult() formats as
        // "%.0f a.u./SPD @ %.1f nm".  String.toDoubleOrNull() requires the whole
        // string to be a number, so coeffR/coeffB are always stored as null and
        // the legacy fallback in loadSpdPreset() can only ever show "—".
        val formatted = String.format(java.util.Locale.US, "%.0f a.u./SPD @ %.1f nm", 34571.6f, 595.7f)
        assertNull(formatted.toDoubleOrNull())
        // The neighbouring exposure column is parsed from "%.4f s" and works:
        assertEquals(0.2, String.format(java.util.Locale.US, "%.4f s", 0.2).removeSuffix(" s")
            .toDoubleOrNull()!!, 1e-12)
    }

    @Test
    fun presetTypesCoverTheThreeReusableCalibrations() {
        assertEquals("WAVELENGTH", PresetTypes.WAVELENGTH)
        assertEquals("SPD", PresetTypes.SPD)
        assertEquals("CURVE", PresetTypes.CURVE)
        // The three reusable calibrations are Step 1 / Step 2 / Step 4.
        assertEquals(
            setOf("WAVELENGTH", "SPD", "CURVE"),
            setOf(PresetTypes.WAVELENGTH, PresetTypes.SPD, PresetTypes.CURVE)
        )
    }

    // ------------------------------------------------------------- helpers --

    private fun assertArray(label: String, expected: FloatArray, actual: FloatArray) {
        assertEquals("$label size", expected.size, actual.size)
        expected.indices.forEach { i ->
            assertEquals("$label[$i]", expected[i], actual[i], 0f)
        }
    }

    private fun assertArray(label: String, expected: BooleanArray, actual: BooleanArray) {
        assertEquals("$label size", expected.size, actual.size)
        expected.indices.forEach { i ->
            assertEquals("$label[$i]", expected[i], actual[i])
        }
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        var thrown: Throwable? = null
        try {
            block()
        } catch (t: Throwable) {
            thrown = t
        }
        assertTrue("Expected ${T::class.java.simpleName} but got $thrown", thrown is T)
    }
}
