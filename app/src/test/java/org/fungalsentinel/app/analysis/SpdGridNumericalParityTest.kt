package org.fungalsentinel.app.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp

/**
 * The v3 cost of doing business: the response is resampled from the pixel axis
 * (~0.36 nm/px) onto a 1 nm uniform wavelength grid, and that smoothing rides
 * all the way into the Step 3 quantitative result.
 *
 * Everything here runs on a FULL-SENSOR fixture (3072 rows, i.e. a pixel axis
 * that spans roughly -15 .. 1106 nm -- far outside the preset's 380..780 nm
 * grid) with smooth analytic profiles, so the numbers below are the ones real
 * data will see. The reference is computed independently of [SpectralResponseCalibrator]
 * so that it also pins the calibrator's own arithmetic.
 */
class SpdGridNumericalParityTest {

    private data class Gauss(val muNm: Double, val sigmaNm: Double, val amp: Double)

    private companion object {
        const val N = 3072
        const val SLOPE = -2.738911                 // px/nm, the measured device value
        const val INTERCEPT = 3028.7065             // λ(1536) = 545 nm
        const val BAND_LO = 430.0
        const val BAND_HI = 660.0
        const val THRESHOLD = 0.1f
    }

    private fun lambdaOf(i: Int): Double = (i - INTERCEPT) / SLOPE

    private fun physicalFrame(red: List<Gauss>, green: List<Gauss>, blue: List<Gauss>): FrameProfile {
        fun curve(peaks: List<Gauss>): FloatArray {
            val out = FloatArray(N)
            for (i in 0 until N) {
                val l = lambdaOf(i)
                var v = 0.0
                for (p in peaks) {
                    val d = (l - p.muNm) / p.sigmaNm
                    v += p.amp * exp(-0.5 * d * d)
                }
                out[i] = v.toFloat()
            }
            return out
        }
        return FrameProfile(
            width = 4, height = N,
            redProfile = curve(red), greenProfile = curve(green), blueProfile = curve(blue),
            columnEnergy = FloatArray(4) { 1f },
            exposureSeconds = 0.2, sourceKind = "test", xRoiStart = 0, xRoiEnd = 3
        )
    }

    /** Lamp seen through the spectrograph: one broad lobe per channel. */
    private fun lampFrame() = physicalFrame(
        red = listOf(Gauss(640.0, 40.0, 900.0)),
        green = listOf(Gauss(545.0, 45.0, 1000.0)),
        blue = listOf(Gauss(460.0, 35.0, 700.0))
    )

    /** The positioning LEDs Step 2 divides by the lamp with. */
    private fun positioningFrame() = physicalFrame(
        red = listOf(Gauss(622.0, 8.0, 300.0)),
        green = listOf(Gauss(522.0, 8.0, 300.0)),
        blue = listOf(Gauss(462.0, 8.0, 300.0))
    )

    private fun blankFrame() = physicalFrame(
        red = listOf(Gauss(545.0, 400.0, 5.0)),
        green = listOf(Gauss(545.0, 400.0, 5.0)),
        blue = listOf(Gauss(545.0, 400.0, 5.0))
    )

    /** An EGFP-ish emission (520 nm) sitting on the blank. */
    private fun sampleFrame() = physicalFrame(
        red = listOf(Gauss(545.0, 400.0, 5.0)),
        green = listOf(Gauss(545.0, 400.0, 5.0), Gauss(520.0, 15.0, 60.0)),
        blue = listOf(Gauss(545.0, 400.0, 5.0))
    )

    private val lampSpd: List<Pair<Double, Double>> = (400..700).map { wl ->
        val d = (wl.toDouble() - 545.0) / 70.0
        wl.toDouble() to exp(-0.5 * d * d)
    }

    /** Piecewise-linear SPD lookup -- the same convention as the production code. */
    private fun spdAt(l: Double): Double {
        val sorted = lampSpd
        if (l < sorted.first().first || l > sorted.last().first) return 0.0
        var lo = 0; var hi = sorted.lastIndex
        while (lo < hi - 1) { val mid = (lo + hi) / 2; if (sorted[mid].first <= l) lo = mid else hi = mid }
        val (x0, y0) = sorted[lo]; val (x1, y1) = sorted[hi]
        return if (x1 == x0) y0 else y0 + (y1 - y0) * (l - x0) / (x1 - x0)
    }

    /**
     * The PRE-v3 truth: the response computed and kept PER PIXEL, which is what
     * a Step 3 run used to be corrected with. Ascending in λ.
     */
    private fun pixelReference(): Triple<DoubleArray, FloatArray, BooleanArray> {
        val lamp = lampFrame()
        val maxSpd = lampSpd.maxOf { it.second }
        val idx = (0 until N).toList()
        val pixels = idx
            .map { i -> lambdaOf(i) }
            .mapIndexed { i, l ->
                val masked = l in BAND_LO..BAND_HI && spdAt(l) > THRESHOLD * maxSpd
                // Outside the mask the production code keeps the response at 0,
                // and it must: past the reference grid there is nothing to divide
                // by, so `lamp / 1e-6` there would out-scale the whole curve.
                val resp = if (masked) lamp.greenProfile[i] / spdAt(l).toFloat() else 0f
                Triple(l, resp, masked)
            }
        val order = pixels.indices.sortedBy { pixels[it].first }
        return Triple(
            DoubleArray(order.size) { pixels[order[it]].first },
            FloatArray(order.size) { pixels[order[it]].second },
            BooleanArray(order.size) { pixels[order[it]].third }
        )
    }

    /** Linear interpolation with endpoint clamping -- mirrors SampleAnalyzer. */
    private fun interpAt(wl: DoubleArray, v: FloatArray, x: Double): Float {
        if (x <= wl[0]) return v[0]
        if (x >= wl[wl.lastIndex]) return v[wl.lastIndex]
        var lo = 0; var hi = wl.lastIndex
        while (lo < hi - 1) { val mid = (lo + hi) / 2; if (wl[mid] <= x) lo = mid else hi = mid }
        val span = wl[hi] - wl[lo]
        val t = if (span == 0.0) 0.0 else (x - wl[lo]) / span
        return v[lo] + ((v[hi] - v[lo]).toDouble() * t).toFloat()
    }

    /** Nearest sample index -- used to look up a mask flag at an arbitrary λ. */
    private fun nearestIndex(wl: DoubleArray, x: Double): Int {
        var lo = 0; var hi = wl.lastIndex
        while (lo < hi - 1) { val mid = (lo + hi) / 2; if (wl[mid] <= x) lo = mid else hi = mid }
        return if (Math.abs(x - wl[lo]) <= Math.abs(wl[hi] - x)) lo else hi
    }

    @Test
    fun resamplingOntoThe1nmGridKeepsTheResponseAndStep3WithinOnePercent() {
        val calibrated = SpectralResponseCalibrator.calibrate(
            lampFrames = listOf(lampFrame()), posFrames = listOf(positioningFrame()),
            spdTrue = lampSpd, slope = SLOPE, intercept = INTERCEPT, spdSource = "numeric-parity",
            gErrorNm = 5.512
        )
        assertNotNull(calibrated)
        val restored = SpectralResponseCalibrator.fromJsonStrict(
            SpectralResponseCalibrator.toJson(
                calibrated!!, deviceModel = "Pixel 8", appVersion = "2.3.0",
                createdAt = "2026-10-18T12:00:00Z"
            )
        )

        val (refWl, refResp, refMask) = pixelReference()
        val gridWl = DoubleArray(restored.wavelengths.size) { restored.wavelengths[it].toDouble() }
        val refPeak = refResp.filterIndexed { i, _ -> refMask[i] }.maxOrNull() ?: 0f
        val gridPeak = restored.respG.filterIndexed { i, _ -> restored.mask[i] }.maxOrNull() ?: 0f

        // Both sides are compared only where BOTH consider the point usable and
        // BOTH are above Step 3's own response floor (20% of the in-band peak) --
        // SampleAnalyzer zeroes everything below that anyway, so anything lower
        // cannot influence a quantitative result.
        val refFloor = 0.2f * refPeak
        val gridFloor = 0.2f * gridPeak
        var worstAll = 0.0
        var worstCore = 0.0
        var samples = 0
        var coreSamples = 0
        var l = BAND_LO
        while (l <= BAND_HI) {
            val r = interpAt(refWl, refResp, l)
            val g = interpAt(gridWl, restored.respG, l)
            val usable = refMask[nearestIndex(refWl, l)] && restored.mask[nearestIndex(gridWl, l)]
            if (usable && r > 0f) {
                val rel = Math.abs(g.toDouble() - r.toDouble()) / r.toDouble()
                if (rel > worstAll) worstAll = rel
                samples++
                if (r >= refFloor && g >= gridFloor) {
                    if (rel > worstCore) worstCore = rel
                    coreSamples++
                }
            }
            l += 0.05
        }
        assertTrue("the fixture produced no usable in-band samples", samples > 1000)
        assertTrue("the fixture produced no above-floor samples", coreSamples > 1000)

        // ---- Step 3 end to end ------------------------------------------
        fun area(wl: FloatArray?, resp: FloatArray?): Double = SampleAnalyzer.analyze(
            blankFrames = listOf(blankFrame()), sampleFrames = listOf(sampleFrame()),
            bandStartNm = 500.0, bandEndNm = 600.0,
            slope = SLOPE, intercept = INTERCEPT,
            fluorophore = "EGFP",
            wavelengths = wl, respG = resp
        )!!.correctedArea
        val refArea = area(refWl.map { it.toFloat() }.toFloatArray(), refResp)
        val gridArea = area(restored.wavelengths, restored.respG)
        val areaRel = Math.abs(gridArea - refArea) / Math.abs(refArea)

        println("[METRIC] response max rel dev over the whole usable band = ${worstAll * 100}%")
        println("[METRIC] response max rel dev above Step 3's own 20% floor = ${worstCore * 100}%")
        println("[METRIC] Step 3 corrected area: pixel=${refArea} grid=${gridArea} rel dev = ${areaRel * 100}%")

        // Thresholds are deliberately tight: switching the resampler from linear
        // interpolation to nearest-neighbour measures 0.47% here, so 0.05% still
        // catches it with a comfortable margin (linear measures < 0.01%).
        assertTrue(
            "the gridded response moved by ${worstCore * 100}% above Step 3's floor (> 0.05%)",
            worstCore < 5e-4
        )
        assertTrue(
            "the Step 3 result moved by ${areaRel * 100}% (> 0.005%)",
            areaRel < 5e-5
        )
    }

    @Test
    fun theGridIsTheCanonical401PointAxisRegardlessOfTheSensorHeight() {
        val cal = SpectralResponseCalibrator.calibrate(
            lampFrames = listOf(lampFrame()), posFrames = listOf(positioningFrame()),
            spdTrue = lampSpd, slope = SLOPE, intercept = INTERCEPT, spdSource = "numeric-parity"
        )!!
        assertEquals(401, cal.gridPoints)
        assertEquals(401, cal.wavelengths.size)
        assertEquals(N, cal.sensorHeight)
        // The valid band must be fully inside the grid, else Step 3 can never apply it.
        assertTrue(
            "the grid must contain the valid band $BAND_LO..$BAND_HI nm",
            cal.wavelengths.first() <= BAND_LO.toFloat() && cal.wavelengths.last() >= BAND_HI.toFloat()
        )
        assertEquals(580f, cal.wavelengths[200], 0f)
        assertNull(
            "a full-sensor pixel axis that runs past the grid must still be accepted",
            SpectralResponseCalibrator.coverageError(cal, SLOPE, INTERCEPT, N)
        )
    }
}
