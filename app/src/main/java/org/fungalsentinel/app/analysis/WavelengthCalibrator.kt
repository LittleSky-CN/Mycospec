package org.fungalsentinel.app.analysis

import kotlin.math.abs

data class WavelengthCalibrationResult(
    val quality: String,
    val slope: Double,
    val intercept: Double,
    val gResidualPx: Double,
    val gErrorNm: Double,
    val roiStart: Int,
    val roiEnd: Int,
    val peakR: Int,
    val peakG: Int,
    val peakB: Int,
    val exposureSeconds: Double,
    val sensorWidth: Int,
    val sensorHeight: Int,
    val red: List<Float>,
    val green: List<Float>,
    val blue: List<Float>,
    val frames: Int
)

data class SpdResult(
    val status: String,
    val responseR: Double,
    val responseB: Double,
    val frames: Int
)

/**
 * FSSA v1.3.4-aligned wavelength calibration:
 * B + R two-point fit, G third-point validation, auto X-ROI, dynamic peak windows.
 */
object WavelengthCalibrator {

    fun calibrate(
        frames: List<FrameProfile>,
        wlR: Double,
        wlG: Double,
        wlB: Double
    ): WavelengthCalibrationResult? {
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
        val residual = pG - predictedG
        val errorNm = residual / slope

        val a = abs(errorNm)
        val quality = when {
            a <= 2.5 -> "PASS"
            a <= 10.0 -> "WARNING"
            else -> "FAILED"
        }
        val exposure = frames.map { it.exposureSeconds }.filter { it > 0 }
            .average().let { if (it.isNaN()) 0.0 else it }

        return WavelengthCalibrationResult(
            quality = quality,
            slope = slope,
            intercept = intercept,
            gResidualPx = residual,
            gErrorNm = errorNm,
            roiStart = rs,
            roiEnd = re,
            peakR = pR, peakG = pG, peakB = pB,
            exposureSeconds = exposure,
            sensorWidth = f0.width,
            sensorHeight = f0.height,
            red = downsample(r), green = downsample(g), blue = downsample(b),
            frames = frames.size
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

    private fun centroid(p: FloatArray): Int {
        if (p.isEmpty()) return -1
        val sm = smooth(p, 5)
        var max = 0f; var mi = 0
        for (i in sm.indices) if (sm[i] > max) { max = sm[i]; mi = i }
        if (max <= 0f) return -1
        val half = max * 0.5f
        var s = mi; while (s > 0 && sm[s - 1] > half) s--
        var e = mi; while (e < sm.lastIndex && sm[e + 1] > half) e++
        var num = 0.0; var den = 0.0
        for (i in s..e) {
            val w = (sm[i] - half).coerceAtLeast(0f).toDouble()
            num += w * i; den += w
        }
        return if (den > 0) (num / den).toInt() else mi
    }

    private fun smooth(p: FloatArray, k: Int): FloatArray {
        val out = FloatArray(p.size); val half = k / 2
        for (i in p.indices) {
            var s = 0f; var c = 0
            for (j in (i - half)..(i + half)) if (j in p.indices) { s += p[j]; c++ }
            out[i] = if (c > 0) s / c else 0f
        }
        return out
    }

    private fun mean(frames: List<FrameProfile>, sel: (FrameProfile) -> FloatArray): FloatArray {
        val n = frames.maxOf { sel(it).size }
        val out = FloatArray(n)
        for (f in frames) {
            val p = sel(f)
            for (i in p.indices) out[i] += p[i]
        }
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

object SpdCalibrator {
    fun calibrate(frames: List<FrameProfile>): SpdResult? {
        if (frames.isEmpty()) return null
        var ir = 0.0; var ig = 0.0; var ib = 0.0
        for (f in frames) {
            val exp = if (f.exposureSeconds > 0) f.exposureSeconds else 1.0
            ir += f.redProfile.fold(0.0) { a, v -> a + v } / exp
            ig += f.greenProfile.fold(0.0) { a, v -> a + v } / exp
            ib += f.blueProfile.fold(0.0) { a, v -> a + v } / exp
        }
        if (ig <= 0.0) return null
        return SpdResult(
            status = "Calibrated (${frames.size} frame(s))",
            responseR = ir / ig,
            responseB = ib / ig,
            frames = frames.size
        )
    }
}