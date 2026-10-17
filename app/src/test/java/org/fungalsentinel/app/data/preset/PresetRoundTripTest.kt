package org.fungalsentinel.app.data.preset

import org.fungalsentinel.app.analysis.SpectralResponseCalibrator
import org.fungalsentinel.app.analysis.SpdCalibrationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Save -> load -> reuse coverage for the three preset types declared in
 * [PresetTypes] (WAVELENGTH / SPD / CURVE).
 *
 * These tests exercise everything that can be exercised without an Android
 * framework runtime (no Room, no ViewModel).  The Room round-trip itself is
 * covered indirectly: [PresetEntity] is a plain data class whose columns are
 * asserted in [wavelengthPresetCarriesEveryMappingColumn].
 */
class PresetRoundTripTest {

    // ---------------------------------------------------------------- SPD --

    private fun spd(): SpdCalibrationResult {
        val n = 64
        val wavelengths = FloatArray(n) { 430f + it * 3.5f }          // strictly increasing
        fun ramp(offset: Float) = FloatArray(n) { (it + offset) * 1.7f }
        return SpdCalibrationResult(
            wavelengths = wavelengths,
            respR = ramp(1f), respG = ramp(2f), respB = ramp(3f),
            posR = ramp(4f), posG = ramp(5f), posB = ramp(6f),
            corrR = ramp(7f), corrG = ramp(8f), corrB = ramp(9f),
            // v1.4 response-proportional fusion weights (sum == 1 pointwise)
            wB = FloatArray(n) { 0.2f + it * 0.001f },
            wG = FloatArray(n) { 0.3f + it * 0.001f },
            wR = FloatArray(n) { 0.4f - it * 0.002f },
            fused = FloatArray(n) { 12.5f + it },
            mask = BooleanArray(n) { it % 4 != 0 },
            effectiveBandNm = 430f to 650.5f,
            maskedCount = n - n / 4, totalCount = n, thresholdRatio = 0.1f,
            crossingBG = 483.25f, crossingGR = 577.75f, agreementPct = 3.125f,
            peakRespR = 110.5f to 595.5f,
            peakRespG = 210.5f to 522.5f,
            peakRespB = 310.5f to 467.5f,
            fusedPeak = 0.12345f to 512.25f,
            exposureSeconds = 0.2, frames = 3, spdSource = "true_spd.csv (bundled)",
            rbSwapped = true
        )
    }

    @Test
    fun spdCalibrationRoundTripsEveryV14Field() {
        val original = spd()
        val restored = SpectralResponseCalibrator.fromJson(SpectralResponseCalibrator.toJson(original))

        assertNotNull(restored)
        restored!!

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
        // v1.4 response-proportional weights -- the fields the SPD preset payload
        // must carry so a reused calibration reproduces the original fusion.
        assertArray("wB", original.wB, restored.wB)
        assertArray("wG", original.wG, restored.wG)
        assertArray("wR", original.wR, restored.wR)
        assertArray("fused", original.fused, restored.fused)
        assertArray("mask", original.mask, restored.mask)
        assertEquals(original.effectiveBandNm, restored.effectiveBandNm)
        assertEquals(original.maskedCount, restored.maskedCount)
        assertEquals(original.totalCount, restored.totalCount)
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
    }

    @Test
    fun spdCalibrationRoundTripsTheTraceabilityAndModeBFields() {
        // DEF-A04 (fixed): the payload now carries the mapping it was baked with
        // plus the un-contaminated Mode B inputs (raw lamp profiles + reference
        // SPD grid), so a reloaded preset can be re-derived for a drifted
        // mapping instead of being silently interpolated.
        val original = spd().copy(
            slope = -2.738911, intercept = 3198.4744, gErrorNm = 5.512,
            xRoiStart = 1204, xRoiEnd = 2871,
            lampR = FloatArray(64) { 1f + it },
            lampG = FloatArray(64) { 2f + it },
            lampB = FloatArray(64) { 3f + it },
            spdRefWl = FloatArray(31) { 430f + it * 10f },
            spdRefVal = FloatArray(31) { 0.5f + it * 0.01f }
        )
        val restored = SpectralResponseCalibrator.fromJsonStrict(
            SpectralResponseCalibrator.toJson(original))

        assertEquals(-2.738911, restored.slope, 1e-12)
        assertEquals(3198.4744, restored.intercept, 1e-9)
        assertEquals(5.512, restored.gErrorNm!!, 1e-9)
        assertEquals(1204, restored.xRoiStart)
        assertEquals(2871, restored.xRoiEnd)
        // Mode B arrays are serialised with 6 significant digits (§7.2), so they
        // round-trip to ~1e-6 relative, not bit-exact.
        assertArrayApprox("lampR", original.lampR!!, restored.lampR!!)
        assertArrayApprox("lampG", original.lampG!!, restored.lampG!!)
        assertArrayApprox("lampB", original.lampB!!, restored.lampB!!)
        assertArrayApprox("spdRefWl", original.spdRefWl!!, restored.spdRefWl!!)
        assertArrayApprox("spdRefVal", original.spdRefVal!!, restored.spdRefVal!!)
        // A payload without lamp* (legacy) must still load -- Mode A fallback.
        val legacy = SpectralResponseCalibrator.fromJsonStrict(
            SpectralResponseCalibrator.toJson(spd()))
        assertNull(legacy.lampR)
        assertNull(legacy.spdRefWl)
        assertTrue(legacy.slope.isNaN())
    }

    @Test
    fun spdPayloadForAFullSensorStaysInsideTheCursorWindow() {
        // Mode B makes the payload considerably bigger.  `payloadJson` is a
        // SQLite TEXT column read through a CursorWindow (default 2 MB), so
        // N=3072 -- a full-height sensor -- has to stay comfortably below it.
        val n = 3072
        fun ramp(offset: Float) = FloatArray(n) { (it + offset) * 1.7f }
        val big = SpdCalibrationResult(
            wavelengths = FloatArray(n) { 430f + it * 0.09f },
            respR = ramp(1f), respG = ramp(2f), respB = ramp(3f),
            posR = ramp(4f), posG = ramp(5f), posB = ramp(6f),
            corrR = ramp(7f), corrG = ramp(8f), corrB = ramp(9f),
            wB = FloatArray(n) { 0.2f }, wG = FloatArray(n) { 0.3f }, wR = FloatArray(n) { 0.5f },
            fused = FloatArray(n) { 12.5f + it * 0.01f },
            mask = BooleanArray(n) { it % 4 != 0 },
            effectiveBandNm = 430f to 706.5f,
            maskedCount = n - n / 4, totalCount = n, thresholdRatio = 0.1f,
            crossingBG = 483.25f, crossingGR = 577.75f, agreementPct = 3.125f,
            peakRespR = 110.5f to 595.5f,
            peakRespG = 210.5f to 522.5f,
            peakRespB = 310.5f to 467.5f,
            fusedPeak = 0.12345f to 512.25f,
            exposureSeconds = 0.2, frames = 3, spdSource = "true_spd.csv (bundled)",
            rbSwapped = false,
            slope = -2.738911, intercept = 3198.4744,
            lampR = ramp(10f), lampG = ramp(11f), lampB = ramp(12f),
            spdRefWl = FloatArray(301) { 400f + it }, spdRefVal = FloatArray(301) { 0.5f }
        )
        val json = SpectralResponseCalibrator.toJson(big)
        assertTrue(
            "N=3072 payload is ${json.length} chars, expected < 1_000_000 (CursorWindow guard)",
            json.length < 1_000_000
        )
        val restored = SpectralResponseCalibrator.fromJsonStrict(json)
        assertEquals(n, restored.wavelengths.size)
        assertEquals(n, restored.lampG!!.size)
        assertEquals(301, restored.spdRefWl!!.size)
    }

    @Test
    fun spdCalibrationRoundTripKeepsTheRbSwapFlag() {
        // DEF-A02 mitigation check: the R/B auto-correction flag survives the
        // SPD payload (it does NOT survive the WAVELENGTH preset -- see below).
        val restored = SpectralResponseCalibrator.fromJson(SpectralResponseCalibrator.toJson(spd()))
        assertEquals(true, restored!!.rbSwapped)
        val notSwapped = SpectralResponseCalibrator.fromJson(
            SpectralResponseCalibrator.toJson(spd().copy(rbSwapped = false)))
        assertEquals(false, notSwapped!!.rbSwapped)
    }

    @Test
    fun spdCalibrationFromJsonReturnsNullForTruncatedPayload() {
        // Characterisation: a broken payload degrades to null -- and
        // ProjectViewModel.loadSpdPreset() then silently falls back to the
        // "legacy preset" branch, which leaves SPD correction DISABLED with no
        // error shown to the user.  See DEF-A03.
        assertNull(SpectralResponseCalibrator.fromJson("{}"))
        assertNull(SpectralResponseCalibrator.fromJson("not json at all"))
        assertNull(SpectralResponseCalibrator.fromJson("{\"version\":2}"))
    }

    @Test
    fun spdCalibrationFromJsonRejectsMissingArrays() {
        // DEF-A03 (fixed): a missing key must be REJECTED, not silently
        // zero-filled. `fromJson` keeps its nullable signature and reports the
        // failure as null; `fromJsonStrict` surfaces the reason.
        val payload = SpectralResponseCalibrator.toJson(spd())
        val stripped = org.json.JSONObject(payload).apply { remove("respG") }.toString()

        assertNull("a payload missing respG must not restore", SpectralResponseCalibrator.fromJson(stripped))
        assertThrows<IllegalArgumentException> { SpectralResponseCalibrator.fromJsonStrict(stripped) }

        // A length mismatch is rejected the same way.
        val short = org.json.JSONObject(payload)
            .apply { put("respB", org.json.JSONArray((0 until 7).map { 1.0 })) }
            .toString()
        assertNull(SpectralResponseCalibrator.fromJson(short))
        assertThrows<IllegalArgumentException> { SpectralResponseCalibrator.fromJsonStrict(short) }

        // The intact payload still round-trips.
        val restored = SpectralResponseCalibrator.fromJson(payload)
        assertNotNull(restored)
        assertEquals(64, restored!!.respG.size)
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
        val original = curvePayload(spdJson = SpectralResponseCalibrator.toJson(spd()))
        val restored = CurvePresetPayload.fromJson(original.toJson())

        assertNotNull(restored.spdCalibrationJson)
        val originalSpd = SpectralResponseCalibrator.fromJson(original.spdCalibrationJson!!)
        val restoredSpd = SpectralResponseCalibrator.fromJson(restored.spdCalibrationJson!!)
        assertNotNull(originalSpd)
        assertNotNull(restoredSpd)
        assertArray("embedded wavelengths", originalSpd!!.wavelengths, restoredSpd!!.wavelengths)
        assertArray("embedded respG", originalSpd.respG, restoredSpd.respG)
        assertArray("embedded wG", originalSpd.wG, restoredSpd.wG)
        assertArray("embedded fused", originalSpd.fused, restoredSpd.fused)
        assertEquals(originalSpd.rbSwapped, restoredSpd.rbSwapped)
    }

    @Test
    fun curvePresetRejectsBrokenEmbeddedSpdCalibration() {
        val broken = curvePayload(spdJson = "{\"version\":2}")
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

    /** 6-significant-digit arrays: compare to 1e-5 relative. */
    private fun assertArrayApprox(label: String, expected: FloatArray, actual: FloatArray) {
        assertEquals("$label size", expected.size, actual.size)
        expected.indices.forEach { i ->
            assertEquals("$label[$i]", expected[i], actual[i], Math.abs(expected[i]) * 1e-5f + 1e-7f)
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
