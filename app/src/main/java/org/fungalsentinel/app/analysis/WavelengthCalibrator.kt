package org.fungalsentinel.app.analysis

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import java.util.Locale

data class PeakEstimate(
    val center: Double, val yMax: Int, val baseline: Float, val lo: Int, val hi: Int,
    val height: Float, val estimator: String, val nMaxima: Int, val quality: String, val centroidLegacy: Double
)

data class WavelengthCalibrationResult(
    val quality: String, val slope: Double, val intercept: Double,
    val gResidualPx: Double, val gErrorNm: Double,
    val roiStart: Int, val roiEnd: Int,
    val peakR: Double, val peakG: Double, val peakB: Double?,
    val peakFlags: List<String>, val exposureSeconds: Double,
    val sensorWidth: Int, val sensorHeight: Int,
    val red: List<Float>, val green: List<Float>, val blue: List<Float>,
    val frames: Int,
    val peakWindows: List<Pair<Int, Int>> = emptyList(),
    val useGreenRedFit: Boolean = false,
    val rbSwapped: Boolean = false   // 【v2.2.6】true 表示本次校准判定 R/B 标签互换
)

object WavelengthCalibrator {
    fun calibrate(
        frames: List<FrameProfile>,
        wlR: Double, wlG: Double, wlB: Double,
        useGreenRedFit: Boolean = false
    ): WavelengthCalibrationResult? {
        if (frames.isEmpty()) return null
        val f0 = frames[0]
        val r = mean(frames) { it.redProfile }
        val g = mean(frames) { it.greenProfile }
        val b = mean(frames) { it.blueProfile }
        val rs = f0.xRoiStart
        val re = f0.xRoiEnd

        // 【v2.2.6】反序自检：R 与 B 位于 G 的錯誤两侧 → 直接以交换态起手
        val pR0 = findPeak(r); val pG0 = findPeak(g); val pB0 = findPeak(b)
        val orderBad = !useGreenRedFit && pR0 != null && pG0 != null && pB0 != null &&
                (pR0.center < pG0.center && pB0.center > pG0.center)
        var swapped = orderBad
        var result = if (swapped)
            tryCalibrate(b, g, r, wlR, wlG, wlB, useGreenRedFit, f0, frames.size, rs, re, true)
        else
            tryCalibrate(r, g, b, wlR, wlG, wlB, useGreenRedFit, f0, frames.size, rs, re, false)

        // 兜底：首尝 FAILED 或为空时尝试另一种交换态，取 G 误差更小者
        if (!useGreenRedFit && (result == null || result.quality == "FAILED")) {
            val alt = if (swapped)
                tryCalibrate(r, g, b, wlR, wlG, wlB, useGreenRedFit, f0, frames.size, rs, re, false)
            else
                tryCalibrate(b, g, r, wlR, wlG, wlB, useGreenRedFit, f0, frames.size, rs, re, true)
            if (alt != null && (result == null || abs(alt.gErrorNm) < abs(result.gErrorNm))) {
                result = alt
                swapped = !swapped
            }
        }
        return result
    }

    private fun tryCalibrate(
        r: FloatArray, g: FloatArray, b: FloatArray,
        wlR: Double, wlG: Double, wlB: Double, useGreenRedFit: Boolean,
        f0: FrameProfile, frameCount: Int, rs: Int, re: Int, swapped: Boolean
    ): WavelengthCalibrationResult? {
        val pR = findPeak(r) ?: return null
        val pG = findPeak(g) ?: return null
        val pB = findPeak(b)

        val slope: Double; val intercept: Double; val residualPx: Double; val errorNm: Double
        val quality: String; val flags: List<String>; val peakWindows: List<Pair<Int, Int>>

        if (useGreenRedFit) {
            slope = (pR.center - pG.center) / (wlR - wlG)
            if (slope == 0.0 || slope.isNaN() || slope.isInfinite()) return null
            intercept = pR.center - slope * wlR
            residualPx = 0.0; errorNm = 0.0
            quality = if (pR.quality == "good" && pG.quality == "good") "PASS" else "WARNING"
            peakWindows = listOf(pR.lo to pR.hi, pG.lo to pG.hi)
            flags = buildList {
                if (pR.quality == "suspect") add("R peak suspect: estimator=${pR.estimator}, n_maxima=${pR.nMaxima}")
                if (pG.quality == "suspect") add("G peak suspect: estimator=${pG.estimator}, n_maxima=${pG.nMaxima}")
                if (pB != null && pB.quality == "suspect") add("B peak detected but ignored (filter mode)")
                if (pB == null) add("B peak absent (expected with blue filter)")
            }
        } else {
            if (pB == null) return null
            slope = (pR.center - pB.center) / (wlR - wlB)
            if (slope == 0.0 || slope.isNaN() || slope.isInfinite()) return null
            intercept = pR.center - slope * wlR
            residualPx = pG.center - (slope * wlG + intercept)
            errorNm = residualPx / abs(slope)
            val a = abs(errorNm)
            quality = when { a <= 2.5 -> "PASS"; a <= 10.0 -> "WARNING"; else -> "FAILED" }
            peakWindows = listOf(pR.lo to pR.hi, pG.lo to pG.hi, pB.lo to pB.hi)
            flags = buildList {
                listOf("R" to pR, "G" to pG, "B" to pB).forEach { (name, p) ->
                    if (p.quality == "suspect") {
                        add(
                            "$name peak suspect: estimator=${p.estimator}, n_maxima=${p.nMaxima}, centroid_legacy-pick=" +
                                    String.format(Locale.US, "%+.1f", p.centroidLegacy - p.center) + "px"
                        )
                    }
                }
            }
        }

        val exposure = if (f0.exposureSeconds > 0) f0.exposureSeconds else 0.0
        return WavelengthCalibrationResult(
            quality, slope, intercept, residualPx, errorNm, rs, re,
            pR.center, pG.center, pB?.center, flags, exposure, f0.width, f0.height,
            downsample(r), downsample(g), downsample(b), frameCount,
            peakWindows, useGreenRedFit, swapped
        )
    }

    private fun findPeak(p: FloatArray): PeakEstimate? {
        val n = p.size
        val smooth = movingAverage(p, 11)
        val validLo = 50; val validHi = n - 50
        if (validLo >= validHi) return null
        val baseline = percentile(smooth, validLo, validHi, 0.10)
        val corrected = FloatArray(n) { i -> max(0f, smooth[i] - baseline) }
        var yMax = validLo; var maxY = 0f
        for (i in validLo until validHi) if (corrected[i] > maxY) { maxY = corrected[i]; yMax = i }
        if (maxY <= 0f) return null
        val half = 0.5f * maxY
        var left = yMax; var right = yMax
        while (left > validLo && corrected[left] > half) left--
        while (right < validHi - 1 && corrected[right] > half) right++
        val fwhmLo = left; val fwhmHi = right
        val fw = max(1, fwhmHi - fwhmLo)
        fun valleyBound(start: Int, step: Int): Int {
            var i = start
            val limit = start + step * (1.5 * fw + 10).toInt()
            while (i > validLo && i < validHi - 1 && i != limit) {
                if (corrected[i + step] >= corrected[i]) break
                i += step
            }
            return i
        }
        val lo = max(validLo, valleyBound(fwhmLo, -1))
        val hi = min(validHi, valleyBound(fwhmHi, +1) + 1)
        val thr = 0.2f * maxY
        var nMax = 0
        for (i in (lo + 1) until (hi - 1)) {
            val v = corrected[i]
            if (v >= thr && v >= corrected[i - 1] && v > corrected[i + 1]) nMax++
        }
        val edgeC = 0.5 * (fwhmLo + fwhmHi)
        var center = edgeC; var estimator = "edges"
        val coreN = fwhmHi - fwhmLo + 1
        if (coreN >= 5) {
            val xs = DoubleArray(coreN) { (fwhmLo + it).toDouble() }
            val ys = DoubleArray(coreN) { ln(max(1e-9f, corrected[fwhmLo + it]).toDouble()) }
            val vertex = fitParabolaVertex(xs, ys)
            if (vertex != null && vertex.first < -1e-12 && vertex.second in fwhmLo.toDouble()..fwhmHi.toDouble()) {
                center = vertex.second; estimator = "gauss"
            }
        }
        var sumW = 0.0; var sumWI = 0.0
        for (i in lo until hi) { val w = max(0f, p[i] - baseline).toDouble(); sumW += w; sumWI += w * i }
        val centroidLegacy = if (sumW > 0) sumWI / sumW else yMax.toDouble()
        val suspect = (nMax > 1) || (abs(center - edgeC) > 0.25 * fw)
        return PeakEstimate(center, yMax, baseline, lo, hi, maxY, estimator, nMax, if (suspect) "suspect" else "good", centroidLegacy)
    }

    private fun percentile(a: FloatArray, from: Int, to: Int, q: Double): Float {
        val slice = a.sliceArray(from until to).sorted()
        if (slice.isEmpty()) return 0f
        return slice[(slice.size * q).toInt().coerceIn(0, slice.lastIndex)]
    }

    private fun fitParabolaVertex(xs: DoubleArray, ys: DoubleArray): Pair<Double, Double>? {
        val n = xs.size; if (n < 3) return null
        var mx = 0.0; for (x in xs) mx += x; mx /= n
        var s1 = 0.0; var s2 = 0.0; var s3 = 0.0; var s4 = 0.0
        var t0 = 0.0; var t1 = 0.0; var t2 = 0.0
        for (i in 0 until n) {
            val u = xs[i] - mx; val u2 = u * u
            s1 += u; s2 += u2; s3 += u2 * u; s4 += u2 * u2
            t0 += ys[i]; t1 += ys[i] * u; t2 += ys[i] * u2
        }
        val m = arrayOf(doubleArrayOf(n.toDouble(), s1, s2), doubleArrayOf(s1, s2, s3), doubleArrayOf(s2, s3, s4))
        val v = doubleArrayOf(t0, t1, t2)
        val sol = solve3(m, v) ?: return null
        val a = sol[2]; val b = sol[1]
        if (a >= -1e-12) return null
        return a to (mx - b / (2 * a))
    }

    private fun solve3(m: Array<DoubleArray>, v: DoubleArray): DoubleArray? {
        val a = arrayOf(m[0].copyOf(), m[1].copyOf(), m[2].copyOf()); val b = v.copyOf()
        for (col in 0..2) {
            var piv = col; for (r in col + 1..2) if (abs(a[r][col]) > abs(a[piv][col])) piv = r
            if (abs(a[piv][col]) < 1e-12) return null
            if (piv != col) { val t = a[piv]; a[piv] = a[col]; a[col] = t; val tb = b[piv]; b[piv] = b[col]; b[col] = tb }
            for (r in 0..2) {
                if (r == col) continue
                val f = a[r][col] / a[col][col]
                for (c in col..2) a[r][c] -= f * a[col][c]; b[r] -= f * b[col]
            }
        }
        return doubleArrayOf(b[0] / a[0][0], b[1] / a[1][1], b[2] / a[2][2])
    }

    private fun movingAverage(p: FloatArray, window: Int): FloatArray {
        if (window <= 1) return p.copyOf()
        val w = if (window % 2 == 0) window + 1 else window; val half = w / 2
        val out = FloatArray(p.size)
        for (i in p.indices) {
            var sum = 0f; var cnt = 0
            for (j in (i - half)..(i + half)) { val idx = j.coerceIn(0, p.lastIndex); sum += p[idx]; cnt++ }
            out[i] = sum / cnt
        }
        return out
    }

    private fun mean(frames: List<FrameProfile>, sel: (FrameProfile) -> FloatArray): FloatArray {
        val n = frames.maxOf { sel(it).size }; val out = FloatArray(n)
        for (f in frames) { val p = sel(f); for (i in p.indices) out[i] += p[i] }
        val k = frames.size.toFloat(); for (i in 0 until n) out[i] /= k
        return out
    }

    private fun downsample(p: FloatArray, target: Int = 180): List<Float> {
        if (p.isEmpty()) return emptyList(); if (p.size <= target) return p.toList()
        val out = ArrayList<Float>(target)
        for (i in 0 until target) {
            val a = (i.toLong() * p.size / target).toInt(); val b = (((i + 1).toLong() * p.size) / target).toInt().coerceAtMost(p.size)
            var s = 0f; var c = 0; for (j in a until b) { s += p[j]; c++ }; out.add(if (c > 0) s / c else 0f)
        }
        return out
    }
}