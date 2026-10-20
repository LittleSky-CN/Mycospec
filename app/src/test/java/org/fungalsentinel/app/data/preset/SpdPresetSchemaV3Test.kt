package org.fungalsentinel.app.data.preset

import org.fungalsentinel.app.analysis.SpectralResponseCalibrator
import org.fungalsentinel.app.analysis.DriftLevel
import org.fungalsentinel.app.analysis.SpdCalibrationResult
import org.fungalsentinel.app.analysis.mappingDriftNm
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The schema-v3 contract itself: what a file MUST look like to be accepted, and
 * -- just as important -- what must NOT happen.
 *
 *  L0  schema_version != 3            -> rejected (no silent migration)
 *  L1  structure (increasing axis, every array == grid_points, enough signal)
 *  L2  coverage of the ANALYSIS band  -> the only remaining hard failure
 *  L3  drift                          -> soft hint, never a rejection
 */
class SpdPresetSchemaV3Test {

    private companion object {
        const val N_PIXELS = 3072
        const val SLOPE = -2.738911
        const val INTERCEPT = 3028.7065          // λ(1536) = 545 nm
        /** Every array key a v3 file may carry, required or optional. */
        val REQUIRED_ARRAYS = listOf("wavelengths", "response_r", "response_g", "response_b", "mask")
        val OPTIONAL_ARRAYS = listOf(
            "pos_r", "pos_g", "pos_b", "corr_r", "corr_g", "corr_b",
            "weight_b", "weight_g", "weight_r", "fused"
        )
    }

    private fun write(r: SpdCalibrationResult): String = SpectralResponseCalibrator.toJson(
        r, deviceModel = "Pixel 8", appVersion = "2.3.0", createdAt = "2026-10-18T12:00:00Z"
    )

    /** A well-formed grid-indexed result: every array is 401 long. */
    private fun result(): SpdCalibrationResult {
        val w = SpectralResponseCalibrator.canonicalGrid()
        val n = w.size
        fun ramp(offset: Float) = FloatArray(n) { (it + offset) * 1.7f }
        val mask = BooleanArray(n) { w[it] >= 430f && w[it] <= 660f }
        return SpdCalibrationResult(
            wavelengths = w,
            respR = ramp(1f), respG = ramp(2f), respB = ramp(3f),
            posR = ramp(4f), posG = ramp(5f), posB = ramp(6f),
            corrR = ramp(7f), corrG = ramp(8f), corrB = ramp(9f),
            wB = FloatArray(n) { 0.2f }, wG = FloatArray(n) { 0.3f }, wR = FloatArray(n) { 0.5f },
            fused = FloatArray(n) { 12.5f + it },
            mask = mask,
            effectiveBandNm = 430f to 660f, validRangeNm = 430f to 660f,
            maskedCount = mask.count { it }, gridPoints = n, thresholdRatio = 0.1f,
            crossingBG = 483.25f, crossingGR = 577.75f, agreementPct = 3.125f,
            peakRespR = 1f to 595f, peakRespG = 1f to 522f, peakRespB = 1f to 467f,
            fusedPeak = 1f to 512f,
            exposureSeconds = 0.2, frames = 3, spdSource = "true_spd.csv (bundled)",
            rbSwapped = false,
            slope = SLOPE, intercept = INTERCEPT, gErrorNm = 5.512,
            xRoiStart = 1204, xRoiEnd = 2871, sensorHeight = N_PIXELS
        )
    }

    private fun base(): JSONObject = JSONObject(write(result()))

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        var thrown: Throwable? = null
        try {
            block()
        } catch (t: Throwable) {
            thrown = t
        }
        assertTrue("Expected ${T::class.java.simpleName} but got $thrown", thrown is T)
    }

    private fun assertRejected(label: String, json: JSONObject) {
        assertNull("$label must not restore", SpectralResponseCalibrator.fromJson(json.toString()))
        assertThrows<IllegalArgumentException> { SpectralResponseCalibrator.fromJsonStrict(json.toString()) }
    }

    // ------------------------------------------------------------ L0 gate --

    @Test
    fun schemaVersionMustBeExactlyThreeOrTheFileIsRefused() {
        val cases = mapOf(
            "missing" to base().apply { remove("schema_version") },
            "the old Int key 'version' instead" to base().apply {
                remove("schema_version"); put("version", 3)
            },
            "the string \"3\"" to base().apply { put("schema_version", "3") },
            "a v2 file" to base().apply { put("schema_version", 2) },
            "a v4 file" to base().apply { put("schema_version", 4) },
            "null" to base().apply { put("schema_version", JSONObject.NULL) }
        )
        for ((label, json) in cases) {
            val error = runCatching { SpectralResponseCalibrator.fromJsonStrict(json.toString()) }
                .exceptionOrNull()
            assertNotNull("'$label' must be rejected", error)
            val msg = error!!.message ?: ""
            assertTrue(
                "the user has to be told what to do about '$label' ($msg)",
                msg.contains("old-format preset") && msg.contains("Re-run Step 2")
            )
            assertNull(SpectralResponseCalibrator.fromJson(json.toString()))
        }

        // Exactly 3 -- written as an Int or as 3.0 -- is the only accepted thing.
        for (v in listOf(3, 3.0)) {
            val ok = SpectralResponseCalibrator.fromJson(
                base().apply { put("schema_version", v) }.toString()
            )
            assertNotNull("schema_version=$v must load", ok)
        }
    }

    // ------------------------------------------------------------- L1 ----

    @Test
    fun everyGridArrayMustHaveGridPointsEntries() {
        for (k in REQUIRED_ARRAYS + OPTIONAL_ARRAYS) {
            val short = base().apply { put(k, JSONArray((0 until 7).map { 1.0 })) }
            assertRejected("'$k' shortened", short)
            val long = base().apply { put(k, JSONArray((0 until 402).map { 1.0 })) }
            assertRejected("'$k' lengthened", long)
        }
        // `grid_points` itself has to agree with the axis it describes.
        assertRejected("grid_points = 400", base().apply { put("grid_points", 400) })
    }

    @Test
    fun theAxisAndTheWavelengthArrayMustAgree() {
        assertRejected("step 5 nm", base().apply {
            put("axis", JSONObject().put("kind", "uniform_wavelength")
                .put("start_nm", 380.0).put("stop_nm", 780.0).put("step_nm", 5.0))
        })
        assertRejected("missing axis", base().apply { remove("axis") })
        assertRejected("step 0", base().apply {
            put("axis", JSONObject().put("kind", "uniform_wavelength")
                .put("start_nm", 380.0).put("stop_nm", 780.0).put("step_nm", 0.0))
        })
        // A wavelengths array that does not sit on the declared grid: shift it by
        // half a step -- still strictly increasing, still 401 points, still wrong.
        val off = JSONArray((0 until 401).map { 380.0 + it + 0.5 })
        assertRejected("off-grid wavelengths", base().apply { put("wavelengths", off) })
    }

    @Test
    fun descendingWavelengthsAreRejected() {
        // Pre-v3 files could legally be stored either way round; v3 has exactly
        // one orientation, so a descending file is an OLD file.
        val descending = base().apply {
            val wl = getJSONArray("wavelengths")
            val reversed = JSONArray()
            for (i in (wl.length() - 1) downTo 0) reversed.put(wl.getDouble(i))
            put("wavelengths", reversed)
        }
        assertRejected("descending axis", descending)

        // ... whereas the canonical file, which IS increasing, loads.
        assertNotNull(SpectralResponseCalibrator.fromJson(base().toString()))

        // A FLAT / non-strictly-increasing axis is rejected too.
        val duplicated = JSONArray((0 until 401).map { 380.0 + (it / 2) })
        assertRejected("duplicated wavelengths", base().apply { put("wavelengths", duplicated) })
    }

    @Test
    fun aPresetWithoutEnoughSignalIsRefused() {
        val empty = base().apply {
            put("response_r", JSONArray((0 until 401).map { 0.0 }))
            put("response_g", JSONArray((0 until 401).map { 0.0 }))
            put("response_b", JSONArray((0 until 401).map { 0.0 }))
        }
        assertRejected("all-zero response", empty)
    }

    // --------------------------------------------- mapping independence --

    @Test
    fun oneFileLoadsIdenticallyUnderTwoVeryDifferentMappings() {
        // The core promise of the pixel decoupling: nothing about the loaded
        // curves depends on the active mapping. Under v2 this same file used to
        // be either interpolated wrong (Mode A) or recomputed (Mode B); now there
        // is one path and it is faithful.
        val file = write(result())
        val loadedA = SpectralResponseCalibrator.fromJsonStrict(file)
        val interceptB = INTERCEPT - 4.0 * SLOPE          // the whole axis slides 4 nm
        val loadedB = SpectralResponseCalibrator.fromJsonStrict(file)

        loadedA.wavelengths.indices.forEach { i ->
            assertEquals(
                "the curves must not move with the mapping (wavelengths[$i])",
                loadedA.wavelengths[i], loadedB.wavelengths[i], 0f
            )
            assertEquals(
                "the curves must not move with the mapping (response_g[$i])",
                loadedA.respG[i], loadedB.respG[i], 0f
            )
        }

        // The drift hint DOES change -- that is all it is there for.
        assertEquals(0f, loadedA.mappingDriftNm(SLOPE, INTERCEPT)!!, 1e-4f)
        assertEquals(4f, loadedB.mappingDriftNm(SLOPE, interceptB)!!, 1e-4f)
    }

    @Test
    fun coverageIsCheckedOnTheAnalysisBandNotOnTheWholeSensorAxis() {
        // A phone sensor's pixel axis routinely runs far past the 380..780 nm
        // grid (3072 rows at 0.36 nm/px spans roughly -15 .. 1106 nm). Requiring
        // the WHOLE axis to be covered would reject 100% of presets.
        val preset = SpectralResponseCalibrator.fromJsonStrict(write(result()))
        assertNull(
            SpectralResponseCalibrator.coverageError(preset, SLOPE, INTERCEPT, N_PIXELS)
        )
        assertNull(
            SpectralResponseCalibrator.coverageError(preset, SLOPE, INTERCEPT - 4.0 * SLOPE, N_PIXELS)
        )

        // A preset that genuinely does not cover the analysis band still fails:
        val narrow = result().copy(wavelengths = FloatArray(101) { 500f + it })
        assertNotNull(
            SpectralResponseCalibrator.coverageError(narrow, SLOPE, INTERCEPT, N_PIXELS)
        )
    }

    @Test
    fun driftIsReportedButNeverRejects() {
        val preset = SpectralResponseCalibrator.fromJsonStrict(write(result()))
        val severe = INTERCEPT - 40.0 * SLOPE      // 40 nm of drift
        val drift = preset.mappingDriftNm(SLOPE, severe)
        assertEquals(40f, drift!!, 1e-3f)
        assertEquals(
            org.fungalsentinel.app.analysis.DriftLevel.SEVERE,
            SpectralResponseCalibrator.driftLevel(drift)
        )
        // ... and yet the file still loads and is still usable.
        assertNotNull(SpectralResponseCalibrator.fromJson(write(result())))
        assertNull(SpectralResponseCalibrator.coverageError(preset, SLOPE, severe, N_PIXELS))
    }

    // --------------------------------------------------- export / import --

    @Test
    fun anExportedFileImportsBackToTheSameCalibration() {
        // Round-trip at the parser boundary, i.e. what ProjectViewModel does
        // around SAF: openOutputStream(write) / openInputStream(fromJsonStrict).
        val original = result()
        val file = write(original)
        val imported = SpectralResponseCalibrator.fromJsonStrict(file)

        assertEquals(3, JSONObject(file).getInt("schema_version"))
        assertEquals(original.gridPoints, imported.gridPoints)
        assertEquals(original.maskedCount, imported.maskedCount)
        assertEquals(original.validRangeNm, imported.validRangeNm)
        assertEquals(original.effectiveBandNm, imported.effectiveBandNm)
        assertEquals(original.sensorHeight, imported.sensorHeight)
        assertEquals(original.slope, imported.slope, 1e-12)
        assertEquals(original.intercept, imported.intercept, 1e-9)
        assertEquals(original.gErrorNm!!, imported.gErrorNm!!, 1e-9)
        assertEquals(original.xRoiStart, imported.xRoiStart)
        assertEquals(original.xRoiEnd, imported.xRoiEnd)
        assertEquals(original.rbSwapped, imported.rbSwapped)
        listOf(
            original.wavelengths to imported.wavelengths,
            original.respR to imported.respR,
            original.respG to imported.respG,
            original.respB to imported.respB,
            original.posG to imported.posG,
            original.corrG to imported.corrG,
            original.wG to imported.wG,
            original.fused to imported.fused
        ).forEach { (a, b) ->
            assertEquals(a.size, b.size)
            a.indices.forEach { i -> assertEquals(a[i], b[i], 0f) }
        }
        original.mask.indices.forEach { i -> assertEquals(original.mask[i], imported.mask[i]) }

        // Re-exporting the imported copy must be byte-identical apart from the
        // file metadata, so a desktop round trip cannot drift.
        assertEquals(file, write(imported))
    }

    @Test
    fun purelyDiagnosticScalarsMayBeNullEverythingElseMayNot() {
        // `x_roi` / `sensor_height` (and `g_error_nm`) describe HOW the file was
        // made, never WHAT was measured. A pipeline that does not track them must
        // still be able to exchange presets, so they are allowed to be null --
        // while a missing quantitative key still kills the file.
        val json = base().apply {
            put("x_roi", JSONObject.NULL)
            put("sensor_height", JSONObject.NULL)
        }
        val loaded = SpectralResponseCalibrator.fromJsonStrict(json.toString())
        assertNull(loaded.xRoiStart)
        assertNull(loaded.xRoiEnd)
        assertNull(loaded.sensorHeight)
        // The drift hint degrades gracefully to the grid length instead of failing.
        assertEquals(DriftLevel.WARN, SpectralResponseCalibrator.driftLevel(loaded.mappingDriftNm(SLOPE, INTERCEPT - 4.0 * SLOPE)))

        listOf("masked_count", "grid_points", "slope", "intercept", "rb_swapped", "frames").forEach { k ->
            assertRejected("missing '$k'", base().apply { remove(k) })
        }
    }

    @Test
    fun aFileWrittenByAnotherAppVersionStillLoads() {
        // This is what a desktop (FSSA) export looks like: same schema, different
        // `app_version` / `device_model` / `created_at`. Those three describe who
        // wrote the file, never the calibration, so they must not gate anything.
        val desktop = base().apply {
            put("app_version", "FSSA 1.4.7")
            put("device_model", "desktop")
            put("created_at", "2026-10-19T08:15:00Z")
        }
        val loaded = SpectralResponseCalibrator.fromJsonStrict(desktop.toString())
        assertEquals(401, loaded.gridPoints)
        assertEquals(N_PIXELS, loaded.sensorHeight)
    }
}
