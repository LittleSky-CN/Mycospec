package org.fungalsentinel.app.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Reuse" half of the preset contract: after an SPD / CURVE preset has been
 * loaded, do Step 3 and Step 4 reproduce the numbers of the original
 * calibration?
 *
 * Everything here runs on synthetic profiles so the tests stay hermetic and
 * fast; they pin the *behaviour* the presets depend on.
 */
class PresetReuseParityTest {

    private fun frame(n: Int, value: Float): FrameProfile = FrameProfile(
        width = 4, height = n,
        redProfile = FloatArray(n) { value },
        greenProfile = FloatArray(n) { value },
        blueProfile = FloatArray(n) { value },
        columnEnergy = FloatArray(4) { value },
        exposureSeconds = 0.2,
        sourceKind = "test",
        xRoiStart = 0, xRoiEnd = 3
    )

    /** A bell-shaped green profile so the integration window has structure. */
    private fun peakyFrame(n: Int): FrameProfile {
        fun bell(centre: Float) = FloatArray(n) { i ->
            val d = (i - centre) / (n / 8f)
            (100f * kotlin.math.exp(-d * d.toDouble())).toFloat()
        }
        return FrameProfile(
            width = 4, height = n,
            redProfile = bell(n * 0.25f),
            greenProfile = bell(n * 0.5f),
            blueProfile = bell(n * 0.75f),
            columnEnergy = FloatArray(4) { 1f },
            exposureSeconds = 0.2,
            sourceKind = "test",
            xRoiStart = 0, xRoiEnd = 3
        )
    }

    private fun spdOf(
        wavelengths: FloatArray,
        resp: FloatArray
    ): SpdCalibrationResult {
        val n = wavelengths.size
        return SpdCalibrationResult(
            wavelengths = wavelengths,
            respR = resp, respG = resp, respB = resp,
            posR = FloatArray(n), posG = FloatArray(n), posB = FloatArray(n),
            corrR = FloatArray(n), corrG = FloatArray(n), corrB = FloatArray(n),
            wB = FloatArray(n), wG = FloatArray(n), wR = FloatArray(n),
            fused = FloatArray(n),
            mask = BooleanArray(n) { true },
            effectiveBandNm = wavelengths.first() to wavelengths.last(),
            maskedCount = n, gridPoints = n, thresholdRatio = 0.1f,
            crossingBG = 483f, crossingGR = 577f, agreementPct = 0f,
            peakRespR = 1f to 500f, peakRespG = 1f to 500f, peakRespB = 1f to 500f,
            fusedPeak = 1f to 500f,
            exposureSeconds = 0.2, frames = 1, spdSource = "test",
            rbSwapped = false,
            slope = -2.738911, intercept = 3028.7065, sensorHeight = n
        )
    }

    private fun write(r: SpdCalibrationResult): String = SpectralResponseCalibrator.toJson(
        r, deviceModel = "Pixel 8", appVersion = "2.3.0", createdAt = "2026-10-18T12:00:00Z"
    )

    // ----------------------------------------------------------------------

    /**
     * A Gaussian reference lamp spectrum, so the SPD threshold mask has real
     * structure (on a peak wall `SPD(λ-δ)/SPD(λ)` is the dominant error term).
     */
    private fun gaussianSpd(mu: Double = 545.0, sigma: Double = 70.0): List<Pair<Double, Double>> =
        (400..700).map { wl ->
            val d = (wl.toDouble() - mu) / sigma
            wl.toDouble() to kotlin.math.exp(-0.5 * d * d)
        }

    /**
     * A full-sensor fixture: 3072 rows at -2.7389 px/nm, so the pixel axis runs
     * from roughly -15 nm to 1106 nm -- i.e. FAR outside the preset's 380..780 nm
     * grid. That is the normal case on a phone and it is why neither the response
     * nor the coverage check may be expressed in pixels.
     */
    private companion object {
        const val SENSOR_N = 3072
        const val SENSOR_SLOPE = -2.738911              // px/nm
        const val SENSOR_INTERCEPT = 3028.7065          // λ(1536) = 545 nm
        /** Two to three times the L3 "warn" threshold: a real-world drift. */
        const val GRID_SHIFT_NM = 4.0f
    }

    /** Maximum relative difference over a masked sub-range of the grid. */
    private fun maxRelDiff(a: FloatArray, b: FloatArray, mask: BooleanArray): Double {
        var worst = 0.0
        for (i in a.indices) {
            if (!mask[i]) continue
            val ref = Math.abs(a[i].toDouble())
            if (ref <= 0.0) continue
            val d = Math.abs(a[i].toDouble() - b[i].toDouble()) / ref
            if (d > worst) worst = d
        }
        return worst
    }

    @Test
    fun spdPresetCarriesItsMappingAndReportsDrift() {
        // The payload still carries the mapping it was baked with, but ONLY so
        // that a drift can be *reported*: since v3 the response is indexed by
        // wavelength, so a different mapping no longer deforms it. That is the
        // whole point of the pixel decoupling -- the old Mode A/B asymmetry is
        // gone, there is exactly one way to load a preset and it is always
        // faithful.
        val slope = SENSOR_SLOPE
        val intercept = SENSOR_INTERCEPT
        val spdTrue = gaussianSpd()
        val lampFrames = listOf(peakyFrame(SENSOR_N))
        val posFrames = listOf(frame(SENSOR_N, 1f))

        val calibrated = SpectralResponseCalibrator.calibrate(
            lampFrames = lampFrames, posFrames = posFrames,
            spdTrue = spdTrue, slope = slope, intercept = intercept, spdSource = "test",
            gErrorNm = 5.512
        )
        assertNotNull(calibrated)
        val cal = calibrated!!

        // ---- the grid replaces the pixel axis -----------------------------
        val grid = SpectralResponseCalibrator.canonicalGrid()
        assertEquals(401, cal.gridPoints)
        assertEquals(grid.size, cal.wavelengths.size)
        assertEquals(380f, cal.wavelengths.first(), 0f)
        assertEquals(780f, cal.wavelengths.last(), 0f)
        assertEquals(1f, cal.wavelengths[1] - cal.wavelengths[0], 0f)
        assertEquals(SENSOR_N, cal.sensorHeight)

        // ---- the payload carries the mapping (diagnostic only) ------------
        assertEquals(slope, cal.slope, 1e-12)
        assertEquals(intercept, cal.intercept, 1e-9)
        assertEquals(5.512, cal.gErrorNm!!, 1e-9)

        val restored = SpectralResponseCalibrator.fromJsonStrict(write(cal))

        // ---- L3 drift: 0 drift -> ~0; 1 px of drift -> 0.365 nm -----------
        val zeroDrift = restored.mappingDriftNm(slope, intercept)
        assertNotNull(zeroDrift)
        assertEquals(0f, zeroDrift!!, 1e-5f)

        val driftedIntercept = intercept + 1.0      // 1 px, i.e. 1/|slope| nm
        val onePixel = restored.mappingDriftNm(slope, driftedIntercept)
        assertNotNull(onePixel)
        assertEquals(0.36511f, onePixel!!, 1e-4f)

        // ---- drift is classified, never rejected --------------------------
        assertEquals(DriftLevel.OK, SpectralResponseCalibrator.driftLevel(1.9f))
        assertEquals(DriftLevel.WARN, SpectralResponseCalibrator.driftLevel(2.1f))
        assertEquals(DriftLevel.WARN, SpectralResponseCalibrator.driftLevel(9.9f))
        assertEquals(DriftLevel.SEVERE, SpectralResponseCalibrator.driftLevel(10.1f))
        assertEquals(DriftLevel.UNKNOWN, SpectralResponseCalibrator.driftLevel(null))

        // ---- the SAME file loaded against a mapping drifted by ~4 nm ------
        // What "decoupled from pixels" means: nothing about the LOADED curves
        // depends on the active mapping, and they reproduce Step 2 exactly.
        // (A fresh Step 2 run under the drifted mapping really would measure a
        // slightly different curve -- the drift re-assigns the same pixels to
        // other wavelengths -- but that is physics, not a payload defect, and it
        // used to be papered over by Mode B.)
        val interceptB = intercept - GRID_SHIFT_NM * slope      // λ -> λ + 4 nm
        val fourNmDrift = restored.mappingDriftNm(slope, interceptB)
        assertNotNull(fourNmDrift)
        assertEquals(GRID_SHIFT_NM, fourNmDrift!!, 1e-4f)

        val freshA = SpectralResponseCalibrator.calibrate(
            lampFrames = lampFrames, posFrames = posFrames,
            spdTrue = spdTrue, slope = slope, intercept = intercept, spdSource = "test"
        )!!
        val freshB = SpectralResponseCalibrator.calibrate(
            lampFrames = lampFrames, posFrames = posFrames,
            spdTrue = spdTrue, slope = slope, intercept = interceptB, spdSource = "test"
        )!!
        // Compared above 20% of the in-band peak on both sides -- that is the
        // floor SampleAnalyzer itself applies, so points below it can never reach
        // a quantitative result (the fringe matters, the extreme tail does not).
        fun core(a: SpdCalibrationResult, b: SpdCalibrationResult): BooleanArray {
            val pa = a.respG.filterIndexed { i, _ -> a.mask[i] }.maxOrNull() ?: 0f
            val pb = b.respG.filterIndexed { i, _ -> b.mask[i] }.maxOrNull() ?: 0f
            return BooleanArray(grid.size) {
                a.mask[it] && b.mask[it] && a.respG[it] >= 0.2f * pa && b.respG[it] >= 0.2f * pb
            }
        }
        val gridBefore = maxRelDiff(restored.respG, freshA.respG, core(restored, freshA))
        println("[METRIC] preset vs fresh Step 2 under the same mapping = $gridBefore")
        assertTrue(
            "loading the file must reproduce a fresh Step 2, but it moved by $gridBefore",
            gridBefore < 1e-6
        )
        val physics = maxRelDiff(freshA.respG, freshB.respG, core(freshA, freshB))
        println("[METRIC] how much Step 2 itself would move after ${GRID_SHIFT_NM} nm of drift = $physics")

        // ---- and Step 3 reproduces a fresh Step 2 under EITHER mapping -----
        val blank = listOf(frame(SENSOR_N, 1f))
        val sample = listOf(peakyFrame(SENSOR_N))
        fun area(result: SpdCalibrationResult, b: Double): Double = SampleAnalyzer.analyze(
            blankFrames = blank, sampleFrames = sample,
            bandStartNm = 500.0, bandEndNm = 600.0,
            slope = slope, intercept = b,
            fluorophore = "EGFP",
            wavelengths = result.wavelengths, respG = result.respG
        )!!.correctedArea
        for (b in listOf(intercept, interceptB)) {
            val truth = area(freshA, b)
            val loaded = area(restored, b)
            val rel = Math.abs(truth - loaded) / Math.abs(truth)
            println("[METRIC] Step 3 at intercept=$b: fresh=$truth loaded=$loaded rel=$rel")
            assertEquals(
                "loading the same file must reproduce a fresh Step 2",
                truth, loaded, Math.abs(truth) * 1e-3 + 1e-9
            )
        }
    }


    @Test
    fun sampleAnalyzerSilentlySkipsSpdCorrectionWhenThePresetRestoredEmptyArrays() {
        // DEF-A03: SpectralResponseCalibrator.fromJson() returns FloatArray(0)
        // for any missing key.  SampleAnalyzer then sees
        // `wavelengths.size >= 2 == false` and quietly reports
        // responseApplied == false -- the user gets raw, un-corrected spectra
        // while Step 2 still reads "loaded from preset".
        val n = 64
        val result = SampleAnalyzer.analyze(
            blankFrames = listOf(frame(n, 1f)),
            sampleFrames = listOf(peakyFrame(n)),
            bandStartNm = 5.0, bandEndNm = 50.0,
            slope = 1.0, intercept = 0.0,
            fluorophore = "EGFP",
            wavelengths = FloatArray(0), respR = FloatArray(0),
            respG = FloatArray(0), respB = FloatArray(0)
        )
        assertNotNull(result)
        assertEquals(false, result!!.responseApplied)
        // DEF-A03 (fixed): "silently" is now "explainable" -- the UI can say why.
        assertNotNull("an unapplied SPD correction must state a reason", result.spdSkipReason)
        assertEquals("TOO_FEW_POINTS", result.spdSkipReason)
        assertNull(result.copy(spdSkipReason = null).spdSkipReason)
    }

    @Test
    fun sampleAnalyzerSkipsSpdCorrectionWhenArraysDoNotMatch() {
        val n = 64
        val result = SampleAnalyzer.analyze(
            blankFrames = listOf(frame(n, 1f)),
            sampleFrames = listOf(peakyFrame(n)),
            bandStartNm = 5.0, bandEndNm = 50.0,
            slope = 1.0, intercept = 0.0,
            fluorophore = "EGFP",
            wavelengths = FloatArray(n) { 430f + it }, respG = FloatArray(7) { 2f }
        )
        assertNotNull(result)
        assertEquals(
            "a size mismatch must not be applied silently",
            false, result!!.responseApplied
        )
        assertEquals("LENGTH_MISMATCH", result.spdSkipReason)
    }

    @Test
    fun unmixingRejectsAMismatchedSpdPayloadWhileStep3DoesNot() {
        // Asymmetry worth knowing: the Step 4 unmixing path fails loudly ...
        val n = 32
        val axis = SpectralUnmixer.wavelengthAxis(n, 3.5, 100.0)
        val broken = spdOf(FloatArray(n) { 430f + it }, FloatArray(7) { 2f })
        assertThrows(IllegalArgumentException::class.java) {
            SpectralUnmixer.extractChannelSpectra(
                listOf(peakyFrame(n)), axis, "EGFP", broken
            )
        }
        // ... whereas the very same payload is silently ignored in Step 3
        // (see sampleAnalyzerSilentlySkipsSpdCorrectionWhenThePresetRestoredEmptyArrays).
    }

    @Test
    fun unmixingProducesIdenticalSpectraForAnIdenticalRestoredSpdPayload() {
        // The happy path: an SPD preset whose payload round-trips exactly must
        // give byte-identical spectra before and after save/load.
        val n = 32
        val frames = listOf(peakyFrame(n))
        val axis = SpectralUnmixer.wavelengthAxis(n, 3.5, 100.0)
        val w = SpectralResponseCalibrator.canonicalGrid()
        val saved = spdOf(w, FloatArray(w.size) { 2f + it * 0.1f })
        val restored = SpectralResponseCalibrator.fromJson(write(saved))!!

        val before = SpectralUnmixer.extractChannelSpectra(frames, axis, "EGFP", saved)
        val after = SpectralUnmixer.extractChannelSpectra(frames, axis, "EGFP", restored)

        assertEquals(before.size, after.size)
        before.indices.forEach { i ->
            before[i].indices.forEach { j ->
                assertEquals(before[i][j], after[i][j], 0.0)
            }
        }
    }

    @Test
    fun curvePresetReferenceIsInterpolatedOntoTheCurrentAxis() {
        // Step 4 preset reuse interpolates the saved reference emission onto the
        // CURRENT wavelength axis (CurvePresetPayload.emissionAt) and rebuilds
        // the unmixing window from the saved nm bounds.  It only stays faithful
        // while the Step 1 mapping is unchanged -- there is no stored
        // slope/intercept in the preset to verify that.
        val wavelengthsNm = listOf(500.0, 510.0, 520.0, 530.0, 540.0)
        val emission = listOf(0.0, 10.0, 20.0, 10.0, 0.0)
        val payload = org.fungalsentinel.app.data.preset.CurvePresetPayload(
            fluorophore = "EGFP", slope = 2.0, intercept = 1.0, rSquared = 0.99,
            standardPoints = listOf(
                org.fungalsentinel.app.data.preset.CurveStandardPoint(0.0, 1.0, "a.u."),
                org.fungalsentinel.app.data.preset.CurveStandardPoint(1.0, 3.0, "a.u.")
            ),
            wavelengthsNm = wavelengthsNm,
            referenceEmission = emission,
            referenceAuc = 100.0,
            windowMinNm = 505.0, windowMaxNm = 535.0,
            rbSwapped = false, spdCalibrationJson = null
        )
        val axis = doubleArrayOf(505.0, 515.0, 525.0, 535.0)
        val sampled = payload.emissionAt(axis)
        assertEquals(5.0, sampled[0], 1e-9)
        assertEquals(15.0, sampled[1], 1e-9)
        assertEquals(15.0, sampled[2], 1e-9)
        assertEquals(5.0, sampled[3], 1e-9)
        // Outside the saved grid the emission is zero-filled -- a preset whose
        // window is not covered by the current calibration is rejected in
        // ProjectViewModel.applyCurvePreset(), but only AFTER the spectra were
        // already built with the zero-filled emission.
        assertEquals(0.0, payload.emissionAt(doubleArrayOf(450.0))[0], 1e-9)
    }
}
