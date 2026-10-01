package org.fungalsentinel.app.analysis

import kotlin.math.sqrt

data class SampleAnalysisResult(
    val blankMeanArea: Double,
    val sampleMeanArea: Double,
    val correctedArea: Double,
    val sampleSD: Double,
    val replicates: Int,
    val windowStartPx: Int,
    val windowEndPx: Int,
    val correctedSpectrum: List<Float>,
    val blankSpectrum: List<Float>
)

data class RegressionResult(
    val slope: Double,
    val intercept: Double,
    val r2: Double,
    val warnings: List<String>
)

/**
 * Step 3: Blank-subtracted, band-limited fluorescence integration.
 * The integration window (in pixels) is derived from the Step 1
 * wavelength mapping, so Step 3 is scientifically chained to Step 1.
 */
object SampleAnalyzer {

    fun analyze(
        blankFrames: List<FrameProfile>,
        sampleFrames: List<FrameProfile>,
        bandStartNm: Double,
        bandEndNm: Double,
        slope: Double,
        intercept: Double
    ): SampleAnalysisResult? {
        if (blankFrames.isEmpty() || sampleFrames.isEmpty()) return null

        // wavelength -> pixel window via Step 1 mapping
        val p0 = (slope * bandStartNm + intercept).toInt()
        val p1 = (slope * bandEndNm + intercept).toInt()
        val size = sampleFrames[0].total().size
        val lo = minOf(p0, p1).coerceIn(0, (size - 1).coerceAtLeast(0))
        val hi = maxOf(p0, p1).coerceIn(0, (size - 1).coerceAtLeast(0))
        if (lo > hi) return null

        fun areas(frames: List<FrameProfile>): List<Double> = frames.map { f ->
            val t = f.total()
            val exp = if (f.exposureSeconds > 0) f.exposureSeconds else 1.0
            var sum = 0.0
            for (px in lo..hi) sum += t[px]
            sum / exp
        }

        val blankAreas = areas(blankFrames)
        val sampleAreas = areas(sampleFrames)
        val blankMean = blankAreas.average()
        val sampleMean = sampleAreas.average()
        val corrected = sampleMean - blankMean

        val sd = if (sampleAreas.size > 1) {
            val m = sampleMean
            sqrt(sampleAreas.sumOf { (it - m) * (it - m) } / (sampleAreas.size - 1))
        } else 0.0

        val meanSample = meanTotal(sampleFrames)
        val meanBlank = meanTotal(blankFrames)
        val n = minOf(meanSample.size, meanBlank.size)
        val diff = FloatArray(n) { i -> meanSample[i] - meanBlank[i] }

        return SampleAnalysisResult(
            blankMeanArea = blankMean,
            sampleMeanArea = sampleMean,
            correctedArea = corrected,
            sampleSD = sd,
            replicates = sampleFrames.size,
            windowStartPx = lo,
            windowEndPx = hi,
            correctedSpectrum = downsample(diff),
            blankSpectrum = downsample(meanBlank)
        )
    }

    private fun FrameProfile.total(): FloatArray {
        val n = minOf(redProfile.size, minOf(greenProfile.size, blueProfile.size))
        return FloatArray(n) { redProfile[it] + greenProfile[it] + blueProfile[it] }
    }

    private fun meanTotal(frames: List<FrameProfile>): FloatArray {
        val n = frames.minOf { it.total().size }
        val out = FloatArray(n)
        for (f in frames) { val t = f.total(); for (i in 0 until n) out[i] += t[i] }
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

/**
 * Step 4: ordinary least-squares concentration regression with
 * iGEM-grade quality warnings.
 */
object ConcentrationRegressor {

    fun fit(points: List<Pair<Double, Double>>): RegressionResult? {
        if (points.size < 2) return null
        val n = points.size
        val sx = points.sumOf { it.first }
        val sy = points.sumOf { it.second }
        val sxx = points.sumOf { it.first * it.first }
        val sxy = points.sumOf { it.first * it.second }
        val denom = n * sxx - sx * sx
        if (denom == 0.0) return null
        val slope = (n * sxy - sx * sy) / denom
        val intercept = (sy - slope * sx) / n
        val meanY = sy / n
        val ssTot = points.sumOf { (it.second - meanY) * (it.second - meanY) }
        val ssRes = points.sumOf { val p = slope * it.first + intercept; (it.second - p) * (it.second - p) }
        val r2 = if (ssTot > 0) 1.0 - ssRes / ssTot else 1.0
        val warnings = buildList {
            if (slope <= 0.0) add("Negative or zero slope — check standard preparation")
            if (r2 < 0.95) add("Low R² (<0.95) — curve may not be reliable")
            if (points.size < 3) add("Only 2 standards — linearity cannot be validated")
        }
        return RegressionResult(slope, intercept, r2, warnings)
    }

    fun predict(r: RegressionResult, area: Double): Double? =
        if (r.slope != 0.0) (area - r.intercept) / r.slope else null
}