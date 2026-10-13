package org.fungalsentinel.app.analysis

import kotlin.math.abs
import kotlin.math.sqrt

data class SampleAnalysisResult(
    val channel: String,
    val responseApplied: Boolean,
    val blankMeanArea: Double,     // RAW blank AUC (trapezoid) in window
    val sampleMeanArea: Double,    // RAW sample AUC (trapezoid) in window
    val correctedArea: Double,     // AUC of (sample-blank)/resp in window (quantification metric)
    val sampleSD: Double,          // SD of per-frame corrected areas
    val replicates: Int,
    val windowStartPx: Int,
    val windowEndPx: Int,
    val peakNm: Double,
    val peakIntensity: Double,
    val fwhmNm: Double,
    val centroidNm: Double,
    val correctedSpectrum: List<Float>,   // I_true, cropped to 400-700 nm
    val blankSpectrum: List<Float>        // raw blank, cropped to 400-700 nm
)

/**
 * Step 3 — Python parity (quantification.py / spectral_features.py):
 *  exposure-normalized profiles -> S_net = Avg(Sample) - Avg(Blank)
 *  -> I_true = S_net / R_camera(λ) with 5% relative floor
 *  -> trapezoid AUC + microplate features (Peak, FWHM, Centroid).
 *
 * 【FIX】Features are calculated on an ascending-wavelength array (reversed if
 * slope is negative) using crossing detection + linear interpolation, exactly
 * matching core/spectral_features.py. This eliminates negative FWHM and
 * correctly handles negative net spectra (Peak stays 0.0).
 */
object SampleAnalyzer {
    private const val VIS_LO = 400.0
    private const val VIS_HI = 700.0

    fun analyze(
        blankFrames: List<FrameProfile>,
        sampleFrames: List<FrameProfile>,
        bandStartNm: Double,
        bandEndNm: Double,
        slope: Double,
        intercept: Double,
        fluorophore: String,
        wavelengths: FloatArray? = null,
        respR: FloatArray? = null,
        respG: FloatArray? = null,
        respB: FloatArray? = null,
        floorFraction: Float = 0.05f
    ): SampleAnalysisResult? {
        if (blankFrames.isEmpty() || sampleFrames.isEmpty()) return null

        val channel = when (fluorophore) {
            "MCHERRY" -> "R"
            "CFP", "MTURQUOISE2" -> "B"
            else -> "G"
        }
        val sel: (FrameProfile) -> FloatArray = when (channel) {
            "R" -> { f -> f.redProfile }
            "B" -> { f -> f.blueProfile }
            else -> { f -> f.greenProfile }
        }

        val n = minOf(sampleFrames[0].redProfile.size, blankFrames[0].redProfile.size)
        if (n < 10) return null

        fun meanOf(frames: List<FrameProfile>): FloatArray {
            val out = FloatArray(n)
            for (f in frames) {
                val p = sel(f)
                for (i in 0 until minOf(n, p.size)) out[i] += p[i]
            }
            val k = frames.size.toFloat()
            for (i in 0 until n) out[i] /= k
            return out
        }

        val meanSample = meanOf(sampleFrames)
        val meanBlank = meanOf(blankFrames)
        val net = FloatArray(n) { meanSample[it] - meanBlank[it] }

        // TRUE wavelength per pixel index (pixel order, negative-slope safe)
        val lam = FloatArray(n) { i -> ((i - intercept) / slope).toFloat() }

        // Response lookup by wavelength (order-agnostic)
        val respSel: FloatArray? = when (channel) { "R" -> respR; "B" -> respB; else -> respG }
        val applied = respSel != null && wavelengths != null && wavelengths.size >= 2 &&
                respSel.size == wavelengths.size

        fun respAt(l: Float): Float {
            val w = wavelengths ?: return 0f
            val r = respSel ?: return 0f
            if (l < w[0] || l > w[w.lastIndex]) return 0f
            var lo = 0; var hi = w.lastIndex
            while (lo < hi - 1) {
                val mid = (lo + hi) / 2
                if (w[mid] <= l) lo = mid else hi = mid
            }
            val span = w[hi] - w[lo]
            val t = if (span == 0f) 0f else (l - w[lo]) / span
            return r[lo] + (r[hi] - r[lo]) * t
        }

        // Integration window in pixels from fluorophore band
        val p0 = (slope * bandStartNm + intercept).toInt()
        val p1 = (slope * bandEndNm + intercept).toInt()
        val lo = minOf(p0, p1).coerceIn(0, n - 1)
        val hi = maxOf(p0, p1).coerceIn(0, n - 1)
        if (lo >= hi) return null

        // 5% relative floor of resp inside window
        var floor = 0f
        if (applied) {
            var pk = 0f
            for (i in lo..hi) { val v = respAt(lam[i]); if (v > pk) pk = v }
            floor = floorFraction * pk
        }

        fun itru(arr: FloatArray, i: Int): Float {
            if (!applied) return arr[i]
            val rv = respAt(lam[i])
            if (rv <= 1e-6f) return 0f
            return arr[i] / maxOf(rv, floor)
        }

        fun trapzRaw(arr: FloatArray): Double {
            var a = 0.0
            for (i in lo until hi) {
                a += (arr[i] + arr[i + 1]) / 2.0 * abs(lam[i + 1] - lam[i])
            }
            return a
        }

        fun trapzItru(arr: FloatArray): Double {
            var a = 0.0
            for (i in lo until hi) {
                a += (itru(arr, i) + itru(arr, i + 1)) / 2.0 * abs(lam[i + 1] - lam[i])
            }
            return a
        }

        val rawSample = trapzRaw(meanSample)
        val rawBlank = trapzRaw(meanBlank)
        val corrected = trapzItru(net)

        val frameAreas = sampleFrames.map { f ->
            val pf = sel(f)
            val m = minOf(n, pf.size)
            val netF = FloatArray(n) { i -> (if (i < m) pf[i] else 0f) - meanBlank[i] }
            trapzItru(netF)
        }
        val sd = if (frameAreas.size > 1) {
            sqrt(frameAreas.sumOf { (it - corrected) * (it - corrected) } / (frameAreas.size - 1))
        } else 0.0

        // ═══════════ Microplate features (Python parity: spectral_features.py) ═══════════

        // 1. Extract window data and ensure wavelength is ascending
        val windowSize = hi - lo + 1
        val wls = DoubleArray(windowSize)
        val ints = DoubleArray(windowSize)
        for (i in 0 until windowSize) {
            wls[i] = lam[lo + i].toDouble()
            ints[i] = itru(net, lo + i).toDouble()
        }

        // Ensure ascending wavelength (handles negative slope mapping)
        if (windowSize > 1 && wls[0] > wls[windowSize - 1]) {
            wls.reverse()
            ints.reverse()
        }

        // 2. Peak (init 0.0: stays 0 if all negative, aligned with Python)
        var peakI = 0.0
        var peakNm = 0.0
        var peakIdx = 0
        for (i in ints.indices) {
            if (ints[i] > peakI) {
                peakI = ints[i]
                peakNm = wls[i]
                peakIdx = i
            }
        }

        // 3. Centroid
        var sumI = 0.0
        var sumIL = 0.0
        for (i in ints.indices) {
            sumI += ints[i]
            sumIL += ints[i] * wls[i]
        }
        val centroidNm = if (sumI > 0.0) sumIL / sumI else peakNm

        // 4. FWHM (crossing detection + linear interpolation, right bound searches AFTER peak)
        var fwhmNm = 0.0
        if (peakI > 0.0) {
            val half = peakI / 2.0
            var leftNm: Double? = null
            var rightNm: Double? = null

            // Find left crossing (rising edge)
            for (i in 0 until windowSize - 1) {
                val v1 = ints[i]
                val v2 = ints[i + 1]
                if (v1 < half && v2 >= half) {
                    val t = (half - v1) / (v2 - v1 + 1e-12)
                    leftNm = wls[i] + t * (wls[i + 1] - wls[i])
                    break
                }
            }

            // Find right crossing (falling edge) - AFTER the peak
            if (leftNm != null) {
                for (i in peakIdx until windowSize - 1) {
                    val v1 = ints[i]
                    val v2 = ints[i + 1]
                    if (v1 >= half && v2 < half) {
                        val t = (half - v1) / (v2 - v1 + 1e-12)
                        rightNm = wls[i] + t * (wls[i + 1] - wls[i])
                        break
                    }
                }
            }

            fwhmNm = if (leftNm != null && rightNm != null) {
                rightNm - leftNm
            } else {
                abs(wls[windowSize - 1] - wls[0])
            }
        } else {
            fwhmNm = if (windowSize > 1) abs(wls[windowSize - 1] - wls[0]) else 0.0
        }

        // Spectra cropped to visible band (Python: VISIBLE_NM = 400-700)
        val visIdx = (0 until n).filter { lam[it] in VIS_LO.toFloat()..VIS_HI.toFloat() }
        fun crop(arr: FloatArray, transform: (FloatArray, Int) -> Float): List<Float> {
            if (visIdx.isEmpty()) return emptyList()
            val k = maxOf(1, visIdx.size / 180)
            return visIdx.filterIndexed { i, _ -> i % k == 0 }.map { transform(arr, it) }
        }
        val corrSpec = crop(net) { a, i -> itru(a, i) }
        val blankSpec = crop(meanBlank) { a, i -> a[i] }

        return SampleAnalysisResult(
            channel = channel,
            responseApplied = applied,
            blankMeanArea = rawBlank,
            sampleMeanArea = rawSample,
            correctedArea = corrected,
            sampleSD = sd,
            replicates = sampleFrames.size,
            windowStartPx = lo,
            windowEndPx = hi,
            peakNm = peakNm,
            peakIntensity = peakI,
            fwhmNm = fwhmNm,
            centroidNm = centroidNm,
            correctedSpectrum = corrSpec,
            blankSpectrum = blankSpec
        )
    }
}