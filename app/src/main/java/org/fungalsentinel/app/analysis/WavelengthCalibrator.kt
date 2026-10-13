package org.fungalsentinel.app.analysis

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import java.util.Locale

/**
 * v1.4.5 peak estimator (parity with Python find_peak_auto):
 *  - valley-bounded window (structure, not fixed ±45 px)
 *  - primary estimate = Gaussian fit on the half-height core (log-parabola vertex);
 *    falls back to half-height edge midpoint when the fit fails ("edges")
 *  - unimodality + gauss-vs-edge consistency checks -> quality = "suspect"
 *  - legacy intensity centroid retained ONLY as a diagnostic
 */
data class PeakEstimate(
    val center: Double,
    val yMax: Int,
    val baseline: Float,
    val lo: Int,
    val hi: Int,
    val height: Float,
    val estimator: String,      // "gauss" | "edges"
    val nMaxima: Int,
    val quality: String,        // "good" | "suspect"
    val centroidLegacy: Double
)

data class WavelengthCalibrationResult(
    val quality: String,
    val slope: Double,
    val intercept: Double,
    val gResidualPx: Double,
    val gErrorNm: Double,
    val roiStart: Int,
    val roiEnd: Int,
    val peakR: Double,
    val peakG: Double,
    val peakB: Double,
    val peakFlags: List<String>,
    val exposureSeconds: Double,
    val sensorWidth: Int,
    val sensorHeight: Int,
    val red: List<Float>,
    val green: List<Float>,
    val blue: List<Float>,
    val frames: Int,
    val peakWindows: List<Pair<Int, Int>> = emptyList(),   // [R, G, B] valley-bounded windows
)

object WavelengthCalibrator {

    fun calibrate(
        frames: List<FrameProfile>,
        wlR: Double, wlG: Double, wlB: Double
    ): WavelengthCalibrationResult? {
        if (frames.isEmpty()) return null
        val f0 = frames[0]
        val r = mean(frames) { it.redProfile }
        val g = mean(frames) { it.greenProfile }
        val b = mean(frames) { it.blueProfile }
        val col = mean(frames) { it.columnEnergy }
        val (rs, re) = autoRoi(col)

        val pR = findPeak(r) ?: return null
        val pG = findPeak(g) ?: return null
        val pB = findPeak(b) ?: return null

        val slope = (pR.center - pB.center) / (wlR - wlB)
        if (slope == 0.0 || slope.isNaN() || slope.isInfinite()) return null
        val intercept = pR.center - slope * wlR
        val residualPx = pG.center - (slope * wlG + intercept)
        val errorNm = -residualPx / slope           // Python/PDF sign convention
        val a = abs(errorNm)
        val quality = when {
            a <= 2.5 -> "PASS"
            a <= 10.0 -> "WARNING"
            else -> "FAILED"
        }
        val flags = buildList {
            listOf("R" to pR, "G" to pG, "B" to pB).forEach { (name, p) ->
                if (p.quality == "suspect") {
                    add(
                        "$name peak suspect: estimator=${p.estimator}, n_maxima=${p.nMaxima}, " +
                                "centroid_legacy-pick=" +
                                String.format(Locale.US, "%+.1f", p.centroidLegacy - p.center) + "px"
                    )
                }
            }
        }
        val exposure = frames.map { it.exposureSeconds }.filter { it > 0 }
            .average().let { if (it.isNaN()) 0.0 else it }
        return WavelengthCalibrationResult(
            quality, slope, intercept, residualPx, errorNm, rs, re,
            pR.center, pG.center, pB.center, flags,
            exposure, f0.width, f0.height,
            downsample(r), downsample(g), downsample(b), frames.size,
            peakWindows = listOf(pR.lo to pR.hi, pG.lo to pG.hi, pB.lo to pB.hi),
        )
    }

    /** v1.4.5 robust peak localization. */
    private fun findPeak(p: FloatArray): PeakEstimate? {
        val n = p.size
        val smooth = movingAverage(p, 11)
        val validLo = 50
        val validHi = n - 50
        if (validLo >= validHi) return null
        val baseline = percentile(smooth, validLo, validHi, 0.10)
        val corrected = FloatArray(n) { i -> max(0f, smooth[i] - baseline) }

        var yMax = validLo; var maxY = 0f
        for (i in validLo until validHi) if (corrected[i] > maxY) { maxY = corrected[i]; yMax = i }
        if (maxY <= 0f) return null
        val half = 0.5f * maxY

        // half-height core bounds
        var left = yMax; var right = yMax
        while (left > validLo && corrected[left] > half) left--
        while (right < validHi - 1 && corrected[right] > half) right++
        val fwhmLo = left; val fwhmHi = right
        val fw = max(1, fwhmHi - fwhmLo)

        // valley-bounded window: walk outward until profile rises again; cap 1.5*FWHM+10
        fun valleyBound(start: Int, step: Int): Int {
            var i = start
            val limit = start + step * (1.5 * fw + 10).toInt()
            while (i > validLo && i < validHi - 1 && i != limit) {
                if (corrected[i + step] >= corrected[i]) break   // i is a valley
                i += step
            }
            return i
        }
        val lo = max(validLo, valleyBound(fwhmLo, -1))
        val hi = min(validHi, valleyBound(fwhmHi, +1) + 1)

        // unimodality: local maxima above 20% of peak height inside window
        val thr = 0.2f * maxY
        var nMax = 0
        for (i in (lo + 1) until (hi - 1)) {
            val v = corrected[i]
            if (v >= thr && v >= corrected[i - 1] && v > corrected[i + 1]) nMax++
        }

        // primary estimator: log-parabola (Gaussian) vertex on the half-height core
        val edgeC = 0.5 * (fwhmLo + fwhmHi)
        var center = edgeC
        var estimator = "edges"
        val coreN = fwhmHi - fwhmLo + 1
        if (coreN >= 5) {
            val xs = DoubleArray(coreN) { (fwhmLo + it).toDouble() }
            val ys = DoubleArray(coreN) { ln(max(1e-9f, corrected[fwhmLo + it]).toDouble()) }
            val vertex = fitParabolaVertex(xs, ys)
            if (vertex != null && vertex.first < -1e-12 &&
                vertex.second in fwhmLo.toDouble()..fwhmHi.toDouble()
            ) {
                center = vertex.second
                estimator = "gauss"
            }
        }

        // legacy centroid — diagnostic only
        var sumW = 0.0; var sumWI = 0.0
        for (i in lo until hi) {
            val w = max(0f, p[i] - baseline).toDouble()
            sumW += w; sumWI += w * i
        }
        val centroidLegacy = if (sumW > 0) sumWI / sumW else yMax.toDouble()

        val suspect = (nMax > 1) || (abs(center - edgeC) > 0.25 * fw)
        return PeakEstimate(
            center, yMax, baseline, lo, hi, maxY, estimator, nMax,
            if (suspect) "suspect" else "good", centroidLegacy
        )
    }

    private fun percentile(a: FloatArray, from: Int, to: Int, q: Double): Float {
        val slice = a.sliceArray(from until to).sorted()
        if (slice.isEmpty()) return 0f
        return slice[(slice.size * q).toInt().coerceIn(0, slice.lastIndex)]
    }

    /** Least-squares parabola y = a·u² + b·u + c (u = x − mean); returns (a, vertexX). */
    private fun fitParabolaVertex(xs: DoubleArray, ys: DoubleArray): Pair<Double, Double>? {
        val n = xs.size
        if (n < 3) return null
        var mx = 0.0
        for (x in xs) mx += x
        mx /= n
        var s1 = 0.0; var s2 = 0.0; var s3 = 0.0; var s4 = 0.0
        var t0 = 0.0; var t1 = 0.0; var t2 = 0.0
        for (i in 0 until n) {
            val u = xs[i] - mx
            val u2 = u * u
            s1 += u; s2 += u2; s3 += u2 * u; s4 += u2 * u2
            t0 += ys[i]; t1 += ys[i] * u; t2 += ys[i] * u2
        }
        val m = arrayOf(
            doubleArrayOf(n.toDouble(), s1, s2),
            doubleArrayOf(s1, s2, s3),
            doubleArrayOf(s2, s3, s4)
        )
        val v = doubleArrayOf(t0, t1, t2)
        val sol = solve3(m, v) ?: return null
        val c = sol[0]; val b = sol[1]; val a = sol[2]
        if (a >= -1e-12) return null
        return a to (mx - b / (2 * a))
    }

    private fun solve3(m: Array<DoubleArray>, v: DoubleArray): DoubleArray? {
        val a = arrayOf(m[0].copyOf(), m[1].copyOf(), m[2].copyOf())
        val b = v.copyOf()
        for (col in 0..2) {
            var piv = col
            for (r in col + 1..2) if (abs(a[r][col]) > abs(a[piv][col])) piv = r
            if (abs(a[piv][col]) < 1e-12) return null
            if (piv != col) {
                val t = a[piv]; a[piv] = a[col]; a[col] = t
                val tb = b[piv]; b[piv] = b[col]; b[col] = tb
            }
            for (r in 0..2) {
                if (r == col) continue
                val f = a[r][col] / a[col][col]
                for (c in col..2) a[r][c] -= f * a[col][c]
                b[r] -= f * b[col]
            }
        }
        return doubleArrayOf(b[0] / a[0][0], b[1] / a[1][1], b[2] / a[2][2])
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