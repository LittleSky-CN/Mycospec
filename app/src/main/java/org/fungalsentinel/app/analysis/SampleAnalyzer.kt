package org.fungalsentinel.app.analysis

import kotlin.math.abs
import kotlin.math.sqrt

data class SampleAnalysisResult(
    val channel: String,
    val responseApplied: Boolean,
    val blankMeanArea: Double,
    val sampleMeanArea: Double,
    val correctedArea: Double,
    val sampleSD: Double,
    val replicates: Int,
    val windowStartPx: Int,
    val windowEndPx: Int,
    val peakNm: Double,
    val peakIntensity: Double,
    val fwhmNm: Double,
    val centroidNm: Double,
    val correctedSpectrum: List<Float>,
    val blankSpectrum: List<Float>
)

/**
 * Step 3 — Python-aligned (quantification flow v1.4.3):
 * exposure-normalized profiles -> S_net = Avg(Sample) - Avg(Blank) ->
 * I_true = S_net / R_camera(lambda) with 5% floor -> trapezoid AUC and
 * microplate-style features (Peak, FWHM, Centroid) on the fluorophore's own channel.
 */
object SampleAnalyzer {

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

        // Channel selection per config.py FLUOROPHORES
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

        // Lambda axis (SPD axis if available, else pixel->nm mapping)
        val lam = FloatArray(n) { i ->
            if (wavelengths != null && i < wavelengths.size) wavelengths[i]
            else ((i - intercept) / slope).toFloat()
        }

        // Band window in pixels (negative-slope safe)
        val p0 = (slope * bandStartNm + intercept).toInt()
        val p1 = (slope * bandEndNm + intercept).toInt()
        val lo = minOf(p0, p1).coerceIn(0, n - 1)
        val hi = maxOf(p0, p1).coerceIn(0, n - 1)
        if (lo >= hi) return null

        // Response division ("then divide") with 5% relative floor
        val respArr: FloatArray? = when (channel) {
            "R" -> respR; "B" -> respB; else -> respG
        }
        val applied = respArr != null && wavelengths != null
        var floor = 0f
        if (applied && respArr != null) {
            var pk = 0f
            for (i in lo..hi) if (respArr[i] > pk) pk = respArr[i]
            floor = floorFraction * pk
        }
        fun itru(net: FloatArray, i: Int): Float {
            val r = respArr
            return if (applied && r != null) net[i] / maxOf(r[i], floor) else net[i]
        }

        // S_net = Avg(Sample) - Avg(Blank)
        val net = FloatArray(n) { meanSample[it] - meanBlank[it] }

        // Trapezoid AUC over band (Python: np.trapz)
        fun trapz(arr: FloatArray): Double {
            var a = 0.0
            for (i in lo until hi) {
                val y1 = itru(arr, i).toDouble()
                val y2 = itru(arr, i + 1).toDouble()
                a += (y1 + y2) / 2.0 * abs(lam[i + 1] - lam[i])
            }
            return a
        }
        val corrected = trapz(net)

        // Per-frame areas for replicate SD (same physics per frame)
        val frameAreas = sampleFrames.map { f ->
            val pf = sel(f)
            val m = minOf(n, pf.size)
            val netF = FloatArray(n) { i -> (if (i < m) pf[i] else 0f) - meanBlank[i] }
            trapz(netF)
        }
        val sampleMeanArea = frameAreas.average()
        val blankArea = trapz(meanBlank)
        val sd = if (frameAreas.size > 1) {
            sqrt(frameAreas.sumOf { (it - sampleMeanArea) * (it - sampleMeanArea) } / (frameAreas.size - 1))
        } else 0.0

        // Microplate-style features on I_true within band
        var peakI = Double.NEGATIVE_INFINITY
        var peakNm = 0.0
        var sumI = 0.0
        var sumIL = 0.0
        for (i in lo..hi) {
            val v = itru(net, i).toDouble()
            if (v > peakI) { peakI = v; peakNm = lam[i].toDouble() }
            sumI += v
            sumIL += v * lam[i]
        }
        val centroidNm = if (sumI > 0.0) sumIL / sumI else peakNm
        val half = peakI / 2.0
        var leftNm: Double? = null
        var rightNm: Double? = null
        for (i in lo..hi) {
            val v = itru(net, i).toDouble()
            if (leftNm == null && v >= half) leftNm = lam[i].toDouble()
            if (leftNm != null && v >= half) rightNm = lam[i].toDouble()
        }
        val fwhmNm = if (leftNm != null && rightNm != null) rightNm - leftNm else 0.0

        return SampleAnalysisResult(
            channel = channel,
            responseApplied = applied,
            blankMeanArea = blankArea,
            sampleMeanArea = sampleMeanArea,
            correctedArea = corrected,
            sampleSD = sd,
            replicates = sampleFrames.size,
            windowStartPx = lo,
            windowEndPx = hi,
            peakNm = peakNm,
            peakIntensity = peakI,
            fwhmNm = fwhmNm,
            centroidNm = centroidNm,
            correctedSpectrum = downsample(FloatArray(n) { itru(net, it) }),
            blankSpectrum = downsample(meanBlank)
        )
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