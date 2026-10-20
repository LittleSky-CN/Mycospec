package org.fungalsentinel.app.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.math.sqrt

class AlgorithmParityTest {
    @Test
    fun sampleReplicatesAreClippedBeforeAveraging() {
        val blank = profile(FloatArray(10) { 2f })
        val lowSample = profile(FloatArray(10) { 1f })
        val highSample = profile(FloatArray(10) { 5f })

        val result = SampleAnalyzer.analyze(
            blankFrames = listOf(blank),
            sampleFrames = listOf(lowSample, highSample),
            bandStartNm = 0.0,
            bandEndNm = 9.0,
            slope = 1.0,
            intercept = 0.0,
            fluorophore = "EGFP"
        )

        assertNotNull(result)
        assertEquals(13.5, result!!.correctedArea, 1e-5)
        assertEquals(sqrt(364.5), result.sampleSD, 1e-5)
    }

    @Test
    fun sampleResponseExcludesValuesBelowTheGlobalThreshold() {
        val blank = profile(FloatArray(10) { 0f })
        val sample = profile(FloatArray(10) { 1f })
        val wavelengths = FloatArray(10) { it + 2f }
        val response = FloatArray(10) { if (it == 9) 100f else 2f }

        val result = SampleAnalyzer.analyze(
            blankFrames = listOf(blank),
            sampleFrames = listOf(sample),
            bandStartNm = 0.0,
            bandEndNm = 9.0,
            slope = 1.0,
            intercept = 0.0,
            fluorophore = "EGFP",
            wavelengths = wavelengths,
            respG = response
        )

        assertNotNull(result)
        assertTrue(result!!.responseApplied)
        assertEquals(0.0, result.correctedArea, 1e-5)
    }

    @Test
    fun sampleResponseDoesNotAmplifySignalBelowResponseFloor() {
        val blank = profile(FloatArray(30) { 20f })
        val sample = profile(FloatArray(30) { if (it < 5) 120f else 30f })
        val wavelengths = FloatArray(30) { 400f + it }
        val response = FloatArray(30) { if (it < 5) 0.01f else 1f }

        val result = SampleAnalyzer.analyze(
            blankFrames = listOf(blank),
            sampleFrames = listOf(sample),
            bandStartNm = 410.0,
            bandEndNm = 420.0,
            slope = 1.0,
            intercept = -400.0,
            fluorophore = "EGFP",
            wavelengths = wavelengths,
            respG = response
        )

        assertNotNull(result)
        assertEquals(100.0, result!!.correctedArea, 1e-5)
        assertEquals(0f, result.correctedSpectrum.first(), 1e-6f)
        assertEquals(10f, result.correctedSpectrum[5], 1e-6f)
        assertEquals(0f, result.blankSpectrum.first(), 1e-6f)
    }

    @Test
    fun sampleAnalysisRemovesIsolatedSpikeAndPreservesBroadPeak() {
        val blankValues = FloatArray(60)
        val sampleValues = FloatArray(60) { i ->
            exp(-0.5 * ((i - 30.0) / 5.0) * ((i - 30.0) / 5.0)).toFloat()
        }
        sampleValues[5] = 1f
        val blank = profile(blankValues)
        val sample = profile(sampleValues)

        val result = SampleAnalyzer.analyze(
            blankFrames = listOf(blank),
            sampleFrames = listOf(sample),
            bandStartNm = 400.0,
            bandEndNm = 459.0,
            slope = 1.0,
            intercept = -400.0,
            fluorophore = "EGFP"
        )

        assertNotNull(result)
        assertTrue(result!!.correctedSpectrum[5] < 0.01f)
        assertTrue(result.correctedSpectrum[30] > result.correctedSpectrum[5])
        assertTrue(result.correctedSpectrum[28] < result.correctedSpectrum[30])
        assertTrue(result.correctedSpectrum[32] < result.correctedSpectrum[30])
    }

    @Test
    fun concentrationFitRemainsLinearWhenLogWouldFitBetter() {
        val points = listOf(1.0 to 1.0, 10.0 to 2.0, 100.0 to 3.0)

        val result = ConcentrationRegressor.fit(points)

        assertNotNull(result)
        assertFalse(result!!.isLogarithmic)
        assertEquals(3.0, ConcentrationRegressor.predict(result, 3.0 * result.slope + result.intercept), 1e-10)
    }

    @Test
    fun responseCalibrationUsesResponseProportionalWeights() {
        // calibrate() requires n >= 10 and >= 10 masked wavelengths.
        val n = 31
        // Distinct per-channel lamp responses, so the proportions are non-trivial
        // (a shared profile would degenerate to w = 1/3 everywhere).
        val lamp = profile3(
            red = FloatArray(n) { 1f + it * 0.5f },
            green = FloatArray(n) { 8f },
            blue = FloatArray(n) { 16f - it * 0.4f }
        )
        val positioning = profile(FloatArray(n) { 10f })
        val spd = (0 until n).map { (430.0 + it) to 1.0 }

        val result = SpectralResponseCalibrator.calibrate(
            lampFrames = listOf(lamp),
            posFrames = listOf(positioning),
            spdTrue = spd,
            slope = 1.0,
            intercept = -430.0,
            spdSource = "test"
        )

        assertNotNull(result)
        // Since schema v3 the result is indexed by WAVELENGTH on the canonical
        // 380..780 nm grid, so its arrays are 401 long no matter how many pixels
        // the fixture had. The 31 nm-wide fixture axis therefore lives at grid
        // indices 50..80 only.
        val res = result!!
        assertEquals(401, res.gridPoints)
        assertTrue("expected masked grid points, got ${res.maskedCount}", res.maskedCount >= 10)
        // Weights must equal resp_c / Σ resp_c and form a partition of unity.
        // The derived curves are RESAMPLED onto the grid together with the
        // responses, so the identity holds to interpolation accuracy rather than
        // bit-exactly (see the note in SpdCalibrationResult).
        val eps = 1e-6f
        var checked = 0
        for (i in res.wavelengths.indices) {
            if (!res.mask[i]) continue
            val total = res.respR[i] + res.respG[i] + res.respB[i] + eps
            assertEquals(res.respR[i] / total, res.wR[i], 1e-3f)
            assertEquals(res.respG[i] / total, res.wG[i], 1e-3f)
            assertEquals(res.respB[i] / total, res.wB[i], 1e-3f)
            assertEquals(1f, res.wR[i] + res.wG[i] + res.wB[i], 1e-4f)
            checked++
        }
        assertTrue("expected at least 10 masked wavelengths, got $checked", checked >= 10)
    }

    @Test
    fun fusionWeightsMatchPythonReferenceVector() {
        // Mirrors FSSA tests/test_fusion_reference.py reference vector.
        val eps = 1e-6f
        val (wB, wG, wR) = referenceWeights(1f, 2f, 3f)
        val total = 1f + 2f + 3f + eps
        assertEquals(1f / total, wB, 1e-6f)
        assertEquals(2f / total, wG, 1e-6f)
        assertEquals(3f / total, wR, 1e-6f)
    }

    private fun referenceWeights(respB: Float, respG: Float, respR: Float): Triple<Float, Float, Float> {
        val total = respB + respG + respR + 1e-6f
        return Triple(respB / total, respG / total, respR / total)
    }

    @Test
    fun responseCalibrationDoesNotSmoothRawResponse() {
        val n = 31
        val lamp = profile(FloatArray(n) { if (it == 15) 100f else 10f })
        val positioning = profile(FloatArray(n) { 10f })
        val spd = (0 until n).map { (430.0 + it) to 1.0 }

        val result = SpectralResponseCalibrator.calibrate(
            lampFrames = listOf(lamp),
            posFrames = listOf(positioning),
            spdTrue = spd,
            slope = 1.0,
            intercept = -430.0,
            spdSource = "test"
        )

        assertNotNull(result)
        // No smoothing IS still the rule for the response itself -- but it is now
        // carried on the 1 nm wavelength grid, so the spike is addressed by
        // WAVELENGTH, not by pixel index. This fixture uses slope = 1 px/nm with
        // λ(0) = 430 nm, i.e. its pixels sit exactly on the grid nodes, so the
        // spike must land on node 445 nm unchanged.
        val res = result!!
        fun indexOfNm(nm: Float): Int = res.wavelengths.indexOfFirst { it >= nm }
        val peak = indexOfNm(445f)
        assertEquals(445f, res.wavelengths[peak], 0f)
        assertTrue(res.respG[peak] > res.respG[peak - 1] * 5f)
        assertTrue(res.respG[peak] > res.respG[peak + 1] * 5f)
    }

    @Test
    fun wavelengthValidationErrorUsesAbsoluteSlopeForPositiveMapping() {
        val width = 3000
        fun gaussian(center: Double): FloatArray =
            FloatArray(width) { i -> exp(-0.5 * ((i - center) / 10.0) * ((i - center) / 10.0)).toFloat() }

        val frame = FrameProfile(
            width = width,
            height = 10,
            redProfile = gaussian(2500.0),
            greenProfile = gaussian(2275.0),
            blueProfile = gaussian(2100.0),
            columnEnergy = FloatArray(width),
            exposureSeconds = 0.3,
            sourceKind = "test",
            xRoiStart = 100,
            xRoiEnd = 200
        )

        val result = WavelengthCalibrator.calibrate(listOf(frame), 622.5, 522.5, 462.5)

        assertNotNull(result)
        assertEquals(10.0, result!!.gErrorNm, 0.2)
    }

    @Test
    fun unmixingRecoversPythonMetricCoefficients() {
        val wavelengths = DoubleArray(8) { 500.0 + it }
        val emission = doubleArrayOf(0.0, 1.0, 0.5, 2.0, 1.5, 0.3, 0.1, 0.0)
        val blank = doubleArrayOf(1.0, 0.0, 2.0, 0.5, 3.0, 1.5, 0.2, 2.5)
        val sample = DoubleArray(wavelengths.size) {
            1.7 * emission[it] + 0.4 * blank[it] + 3.0
        }

        val result = SpectralUnmixer.unmixSpectrum(
            sample,
            emission,
            blank,
            BooleanArray(wavelengths.size) { true }
        )

        assertEquals(1.7, result.alpha, 1e-10)
        assertEquals(0.4, result.scatterScale, 1e-10)
        assertEquals(3.0, result.offset, 1e-10)
        assertEquals(1.0, result.r2, 1e-10)
    }

    @Test
    fun referenceEmissionUsesLargestPositiveInBandAuc() {
        val wavelengths = doubleArrayOf(500.0, 510.0, 520.0)
        val result = SpectralUnmixer.selectReferenceEmission(
            wavelengths,
            listOf(
                SpectralCandidate(doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(0.0, 0.0, 0.0)),
                SpectralCandidate(doubleArrayOf(0.0, 2.0, 0.0), doubleArrayOf(0.0, 0.0, 0.0))
            ),
            booleanArrayOf(true, true, true)
        )

        assertEquals(1, result.candidateIndex)
        assertEquals(20.0, result.auc, 1e-10)
    }

    @Test
    fun unmixingKeepsEmissionScaleWhenBlankAndOffsetAreCollinear() {
        val emission = doubleArrayOf(0.0, 1.0, 0.0, 2.0, 1.0, 0.0, 3.0)
        val blank = DoubleArray(emission.size) { 2.0 }
        val sample = DoubleArray(emission.size) { 2.0 * emission[it] + 3.0 * blank[it] + 4.0 }

        val result = SpectralUnmixer.unmixSpectrum(
            sample,
            emission,
            blank,
            BooleanArray(emission.size) { true }
        )

        assertEquals(2.0, result.alpha, 1e-10)
        assertEquals(1.0, result.r2, 1e-10)
    }

    @Test
    fun descendingWavelengthAxisKeepsSpectraAlignedForReferenceSelection() {
        val wavelengths = doubleArrayOf(530.0, 520.0, 510.0, 505.0, 500.0, 495.0, 490.0)
        val sample = profile(floatArrayOf(0f, 0f, 0f, 0f, 10f, 0f, 0f))
        val blank = profile(FloatArray(7))
        val sampleSpectrum = SpectralUnmixer.extractChannelSpectra(
            listOf(sample), wavelengths, "EGFP", null
        ).single()
        val blankSpectrum = SpectralUnmixer.extractChannelSpectra(
            listOf(blank), wavelengths, "EGFP", null
        ).single()
        val mask = SpectralUnmixer.integrationMask(wavelengths, 502.0, 10.0)

        val reference = SpectralUnmixer.selectReferenceEmission(
            wavelengths,
            listOf(SpectralCandidate(sampleSpectrum, blankSpectrum)),
            mask
        )

        assertEquals(25.0, reference.auc, 1e-10)
    }

    private fun profile3(red: FloatArray, green: FloatArray, blue: FloatArray): FrameProfile {
        return FrameProfile(
            width = red.size,
            height = 1,
            redProfile = red,
            greenProfile = green,
            blueProfile = blue,
            columnEnergy = FloatArray(red.size),
            exposureSeconds = 1.0,
            sourceKind = "test",
            xRoiStart = 0,
            xRoiEnd = red.lastIndex
        )
    }

    private fun profile(green: FloatArray): FrameProfile {
        val empty = FloatArray(green.size)
        return FrameProfile(
            width = green.size,
            height = 1,
            redProfile = green.copyOf(),
            greenProfile = green,
            blueProfile = green.copyOf(),
            columnEnergy = empty,
            exposureSeconds = 1.0,
            sourceKind = "test",
            xRoiStart = 0,
            xRoiEnd = green.lastIndex
        )
    }
}
