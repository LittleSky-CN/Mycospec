package org.fungalsentinel.app.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
            maskedCount = n, totalCount = n, thresholdRatio = 0.1f,
            crossingBG = 483f, crossingGR = 577f, agreementPct = 0f,
            peakRespR = 1f to 500f, peakRespG = 1f to 500f, peakRespB = 1f to 500f,
            fusedPeak = 1f to 500f,
            exposureSeconds = 0.2, frames = 1, spdSource = "test",
            rbSwapped = false
        )
    }

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

    @Test
    fun spdPresetCarriesItsMappingAndReportsDrift() {
        // DEF-A04 (fixed): SpdCalibrationResult now stores the slope/intercept
        // the response was baked with, exposes [SpdCalibrationResult.mappingDriftNm]
        // and -- via Mode B -- can be re-derived for the CURRENT mapping, which
        // makes "load old preset" equivalent to "re-run Step 2".
        val n = 640
        val slope = -2.738911              // px/nm, the measured device value
        val intercept = 1807.68126         // λ(0) = 660 nm, λ(639) ≈ 426.7 nm
        val spdTrue = gaussianSpd()
        val lampFrames = listOf(peakyFrame(n))
        val posFrames = listOf(frame(n, 1f))

        val calibrated = SpectralResponseCalibrator.calibrate(
            lampFrames = lampFrames, posFrames = posFrames,
            spdTrue = spdTrue, slope = slope, intercept = intercept, spdSource = "test",
            gErrorNm = 5.512
        )
        assertNotNull(calibrated)
        val cal = calibrated!!

        // ---- the payload carries the mapping + the raw Mode B inputs ----
        assertEquals(slope, cal.slope, 1e-12)
        assertEquals(intercept, cal.intercept, 1e-9)
        assertEquals(5.512, cal.gErrorNm!!, 1e-9)
        assertNotNull(cal.lampG)
        assertNotNull(cal.spdRefWl)
        assertEquals(n, cal.lampG!!.size)
        assertEquals(301, cal.spdRefWl!!.size)

        val restored = SpectralResponseCalibrator.fromJsonStrict(
            SpectralResponseCalibrator.toJson(cal))

        // ---- L3 drift: 0 drift -> ~0; 1 px of drift -> 0.365 nm ----
        val zeroDrift = restored.mappingDriftNm(slope, intercept)
        assertNotNull(zeroDrift)
        assertEquals(0f, zeroDrift!!, 1e-5f)

        val driftedIntercept = intercept + 1.0      // 1 px, i.e. 1/|slope| nm
        val onePixel = restored.mappingDriftNm(slope, driftedIntercept)
        assertNotNull(onePixel)
        assertEquals(0.36511f, onePixel!!, 1e-4f)

        // ---- drift is classified, never rejected ----
        assertEquals(DriftLevel.OK, SpectralResponseCalibrator.driftLevel(1.9f))
        assertEquals(DriftLevel.WARN, SpectralResponseCalibrator.driftLevel(2.1f))
        assertEquals(DriftLevel.WARN, SpectralResponseCalibrator.driftLevel(9.9f))
        assertEquals(DriftLevel.SEVERE, SpectralResponseCalibrator.driftLevel(10.1f))
        assertEquals(DriftLevel.UNKNOWN, SpectralResponseCalibrator.driftLevel(null))

        // ---- Mode A (no recompute): the area still drifts away, as before ----
        val blank = listOf(frame(n, 1f))
        val sample = listOf(peakyFrame(n))
        fun area(result: SpdCalibrationResult, b: Double): Double = SampleAnalyzer.analyze(
            blankFrames = blank, sampleFrames = sample,
            bandStartNm = 500.0, bandEndNm = 600.0,
            slope = slope, intercept = b,
            fluorophore = "EGFP",
            wavelengths = result.wavelengths, respG = result.respG
        )!!.correctedArea
        val modeA = area(restored, driftedIntercept)

        // ---- Mode B: recompute with the current mapping == fresh Step 2 ----
        val recomputed = restored.recomputeWith(slope, driftedIntercept)
        assertNotNull("Mode B must be available for a v3 payload", recomputed)
        val fresh = SpectralResponseCalibrator.calibrate(
            lampFrames = lampFrames, posFrames = posFrames,
            spdTrue = spdTrue, slope = slope, intercept = driftedIntercept, spdSource = "test"
        )!!
        // Mode B must reproduce a fresh Step 2 point by point (the lamp profiles
        // travel with 6 significant digits, hence a relative tolerance).
        for (i in 0 until n) {
            assertEquals("mask[$i]", fresh.mask[i], recomputed!!.mask[i])
            assertEquals(
                "respG[$i]", fresh.respG[i], recomputed!!.respG[i],
                Math.abs(fresh.respG[i]) * 1e-4f + 1e-7f
            )
        }
        val modeB = area(recomputed!!, driftedIntercept)
        val truth = area(fresh, driftedIntercept)

        assertEquals("Mode B must reproduce a fresh Step 2", truth, modeB, Math.abs(truth) * 1e-3 + 1e-9)
        assertNotEquals(
            "Mode A should NOT reproduce it -- that is the defect being fixed",
            truth, modeA, Math.abs(truth) * 1e-6 + 1e-12
        )

        // ---- a legacy payload without lamp* falls back to Mode A, not to null ----
        assertNull(restored.copy(lampR = null, lampG = null, lampB = null).recomputeWith(slope, intercept))
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
        val saved = spdOf(FloatArray(n) { 430f + it * 3.5f }, FloatArray(n) { 2f + it * 0.1f })
        val restored = SpectralResponseCalibrator.fromJson(
            SpectralResponseCalibrator.toJson(saved))!!

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
