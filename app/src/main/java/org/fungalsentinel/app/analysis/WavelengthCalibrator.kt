package org.fungalsentinel.app.analysis

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class WavelengthCalibrationResult(
    val quality: String, val slope: Double, val intercept: Double,
    val gResidualPx: Double, val gErrorNm: Double,
    val roiStart: Int, val roiEnd: Int,
    val peakR: Int, val peakG: Int, val peakB: Int,
    val exposureSeconds: Double, val sensorWidth: Int, val sensorHeight: Int,
    val red: List<Float>, val green: List<Float>, val blue: List<Float>,
    val frames: Int
)

/**
 * Step 1: auto X-ROI, dynamic peak windows, B+R two-point fit, G validation.
 * Aligned with Python find_peak_auto + TwoPointValidation.
 */
object WavelengthCalibrator {
    fun calibrate(frames: List<FrameProfile>, wlR: Double, wlG: Double, wlB: Double): WavelengthCalibrationResult? {
        if (frames.isEmpty()) return null
        val f0 = frames[0]
        val r = mean(frames) { it.redProfile }
        val g = mean(frames) { it.greenProfile }
        val b = mean(frames) { it.blueProfile }
        val col = mean(frames) { it.columnEnergy }
        val (rs, re) = autoRoi(col)
        val pR = centroid(r); val pG = centroid(g); val pB = centroid(b)
        if (pR < 0 || pG < 0 || pB < 0) return null
        val slope = (pR - pB) / (wlR - wlB)
        if (slope == 0.0 || slope.isNaN() || slope.isInfinite()) return null
        val intercept = pR - slope * wlR
        val predictedG = slope * wlG + intercept
        val residualPx = pG - predictedG  // measured - predicted
        // Python convention: wavelength_error = -residual_px / slope
        val errorNm = -residualPx / slope
        val a = abs(errorNm)
        val quality = when {
            a <= 2.5 -> "PASS"
            a <= 10.0 -> "WARNING"
            else -> "FAILED"
        }
        val exposure = frames.map { it.exposureSeconds }.filter { it > 0 }
            .average().let { if (it.isNaN()) 0.0 else it }
        return WavelengthCalibrationResult(
            quality, slope, intercept, residualPx, errorNm, rs, re, pR, pG, pB,
            exposure, f0.width, f0.height,
            downsample(r), downsample(g), downsample(b), frames.size
        )
    }

    private fun autoRoi(col: FloatArray): Pair<Int, Int> {
        if (col.isEmpty()) return 0 to 0
        var max = 0f; var mi = 0
        for (i in col.indices) if (col[i] > max) { max = col[i]; mi = i }
        if (max <= 0f) return 0 to col.lastIndex
        val thr = max * 0.35f
        var s = mi; while (s > 0 && col[s - 1] > thr) s--
        var e = mi; while (e < col.lastIndex && col[e + 1] > thr) e++
        return s to e
    }

    /** Python find_peak_auto: moving_average(11), baseline(10th percentile), FWHM ±45px, baseline-corrected centroid */
    private fun centroid(p: FloatArray): Int {
        if (p.isEmpty()) return -1
        val n = p.size
        val smooth = movingAverage(p, 11)
        val validLo = 50; val validHi = n - 50
        if (validLo >= validHi) return -1
        val validSlice = smooth.sliceArray(validLo until validHi)
        val sorted = validSlice.sorted()
        val baseline = sorted[(sorted.size * 0.10).toInt().coerceAtMost(sorted.lastIndex)]
        val corrected = FloatArray(n) { i -> max(0f, smooth[i] - baseline) }
        var yMax = validLo
        var maxY = 0f
        for (i in validLo until validHi) {
            if (corrected[i] > maxY) { maxY = corrected[i]; yMax = i }
        }
        if (maxY <= 0f) return -1
        val half = 0.5f * maxY
        var left = yMax; var right = yMax
        while (left > validLo && corrected[left] > half) left--
        while (right < validHi - 1 && corrected[right] > half) right++
        val lo = max(validLo, left - 45)
        val hi = min(validHi, right + 46)
        // Baseline-corrected centroid
        var sumW = 0.0; var sumWI = 0.0
        for (i in lo..hi) {
            val w = max(0f, p[i] - baseline).toDouble()
            sumW += w; sumWI += w * i
        }
        return if (sumW > 0) (sumWI / sumW).toInt() else yMax
    }

    private fun movingAverage(p: FloatArray, window: Int): FloatArray {
        if (window <= 1) return p.copyOf()
        val w = if (window % 2 == 0) window + 1 else window
        val half = w / 2
        val out = FloatArray(p.size)
        for (i in p.indices) {
            var sum = 0f; var cnt = 0
            for (j in (i - half)..(i + half)) {
                val idx = j.coerceIn(0, p.lastIndex)
                sum += p[idx]; cnt++
            }
            out[i] = sum / cnt
        }
        return out
    }

    private fun mean(frames: List<FrameProfile>, sel: (FrameProfile) -> FloatArray): FloatArray {
        val n = frames.maxOf { sel(it).size }
        val out = FloatArray(n)
        for (f in frames) { val p = sel(f); for (i in p.indices) out[i] += p[i] }
        val k = frames.size.toFloat()
        for (i in 0 until n) out[i] /= k
        return out
    }

    private fun downsample(p: FloatArray, target: Int = 180): List<Float> {
        if (p.isEmpty()) return emptyList()
        if (p.size <= target) return p.toList()
        val out = ArrayList<Float>(target)
        for (i in 0 until target) {
            val a = (i.toLong() * p.size / target).toInt()
            val b = (((i + 1).toLong() * p.size) / target).toInt().coerceAtMost(p.size)
            var s = 0f; var c = 0
            for (j in a until b) { s += p[j]; c++ }
            out.add(if (c > 0) s / c else 0f)
        }
        return out
    }
}