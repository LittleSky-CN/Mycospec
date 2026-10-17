package org.fungalsentinel.app.analysis

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import org.fungalsentinel.app.data.model.Fluorophore
import org.fungalsentinel.app.data.model.canonicalFluorophore

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
    val blankSpectrum: List<Float>,
    /**
     * Why SPD correction was NOT applied (DEF-A03): `null` when it WAS applied.
     * One of "NO_SPD_PRESET" / "TOO_FEW_POINTS" / "LENGTH_MISMATCH".
     */
    val spdSkipReason: String? = null
)

object SampleAnalyzer {
    private const val VIS_LO = 400.0
    private const val VIS_HI = 700.0

    private fun removeIsolatedSpikes(values: FloatArray): FloatArray {
        val radius = 2
        if (values.size < 2 * radius + 2) return values.copyOf()
        val amplitudeFloor = (values.maxOfOrNull { abs(it) } ?: 0f) * 0.02f
        val result = values.copyOf()
        for (index in radius until values.size - radius) {
            val neighbors = FloatArray(radius * 2) { offset ->
                if (offset < radius) values[index - radius + offset]
                else values[index + 1 + offset - radius]
            }.sorted()
            val median = (neighbors[radius - 1] + neighbors[radius]) / 2f
            val deviations = neighbors.map { abs(it - median) }.sorted()
            val mad = (deviations[radius - 1] + deviations[radius]) / 2f
            val threshold = max(4.5f * 1.4826f * mad, amplitudeFloor)
            if (abs(values[index] - median) > threshold) result[index] = median
        }
        return result
    }

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
        responseThresholdFraction: Float = 0.20f
    ): SampleAnalysisResult? {
        if (blankFrames.isEmpty() || sampleFrames.isEmpty()) return null
        // 走 canonicalFluorophore：原来这里是大小写敏感的精确匹配，"mCherry"
        // 会被判成默认的 "G" 通道（真 bug），现在按 enum 分支不会被写法规避过去
        val channel = when (canonicalFluorophore(fluorophore)) {
            Fluorophore.MCHERRY -> "R"
            Fluorophore.CFP, Fluorophore.MTURQUOISE2 -> "B"
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
        val netReplicates = sampleFrames.map { frame ->
            val profile = sel(frame)
            FloatArray(n) { i ->
                max(0f, (if (i < profile.size) profile[i] else 0f) - meanBlank[i])
            }
        }
        val net = FloatArray(n)
        for (replicate in netReplicates) {
            for (i in 0 until n) net[i] += replicate[i] / sampleFrames.size
        }
        val lam = FloatArray(n) { i -> ((i - intercept) / slope).toFloat() }

        val respSel: FloatArray? = when (channel) { "R" -> respR; "B" -> respB; else -> respG }
        // DEF-A03: make the "silently skipped" path explainable instead of a bare
        // `responseApplied == false`.
        val skipReason: String? = when {
            respSel == null -> "NO_SPD_PRESET"
            wavelengths == null || wavelengths.size < 2 -> "TOO_FEW_POINTS"
            respSel.size != wavelengths.size -> "LENGTH_MISMATCH"
            else -> null
        }
        val applied = skipReason == null

        fun respAt(l: Float): Float {
            val w = wavelengths ?: return 0f
            val r = respSel ?: return 0f
            if (l <= w[0]) return r[0]
            if (l >= w[w.lastIndex]) return r[r.lastIndex]
            var lo = 0; var hi = w.lastIndex
            while (lo < hi - 1) {
                val mid = (lo + hi) / 2
                if (w[mid] <= l) lo = mid else hi = mid
            }
            val span = w[hi] - w[lo]
            val t = if (span == 0f) 0f else (l - w[lo]) / span
            return r[lo] + (r[hi] - r[lo]) * t
        }

        val p0 = (slope * bandStartNm + intercept).toInt()
        val p1 = (slope * bandEndNm + intercept).toInt()
        val lo = minOf(p0, p1).coerceIn(0, n - 1)
        val hi = maxOf(p0, p1).coerceIn(0, n - 1)
        if (lo >= hi) return null

        val floor = if (applied) {
            max(1e-9f, responseThresholdFraction * (respSel!!.maxOrNull() ?: 0f))
        } else 0f

        fun itru(arr: FloatArray, i: Int): Float {
            if (!applied) return arr[i]
            val rv = respAt(lam[i])
            if (!rv.isFinite() || rv <= floor) return 0f
            return arr[i] / rv
        }

        fun trapzRaw(arr: FloatArray): Double {
            var a = 0.0
            for (i in lo until hi) {
                a += (arr[i] + arr[i + 1]) / 2.0 * abs(lam[i + 1] - lam[i])
            }
            return a
        }

        fun trapzCorrected(arr: FloatArray): Double {
            var a = 0.0
            for (i in lo until hi) {
                a += (arr[i] + arr[i + 1]) / 2.0 * abs(lam[i + 1] - lam[i])
            }
            return a
        }

        val rawSample = trapzRaw(meanSample)
        val rawBlank = trapzRaw(meanBlank)
        val cleanedReplicates = netReplicates.map { replicate ->
            removeIsolatedSpikes(FloatArray(n) { i -> itru(replicate, i) })
        }
        val cleanedNet = removeIsolatedSpikes(FloatArray(n) { i -> itru(net, i) })
        val cleanedBlank = removeIsolatedSpikes(FloatArray(n) { i -> itru(meanBlank, i) })
        val frameAreas = cleanedReplicates.map(::trapzCorrected)
        val corrected = frameAreas.average()
        val sd = if (frameAreas.size > 1) {
            sqrt(frameAreas.sumOf { (it - corrected) * (it - corrected) } / (frameAreas.size - 1))
        } else 0.0

        // Microplate features
        val windowSize = hi - lo + 1
        val wls = DoubleArray(windowSize)
        val ints = DoubleArray(windowSize)
        for (i in 0 until windowSize) {
            wls[i] = lam[lo + i].toDouble()
            ints[i] = cleanedNet[lo + i].toDouble()
        }
        if (windowSize > 1 && wls[0] > wls[windowSize - 1]) {
            wls.reverse()
            ints.reverse()
        }

        var peakI = 0.0; var peakNm = 0.0; var peakIdx = 0
        for (i in ints.indices) {
            if (ints[i] > peakI) { peakI = ints[i]; peakNm = wls[i]; peakIdx = i }
        }

        var sumI = 0.0; var sumIL = 0.0
        for (i in ints.indices) { sumI += ints[i]; sumIL += ints[i] * wls[i] }
        val centroidNm = if (sumI > 0.0) sumIL / sumI else peakNm

        var fwhmNm = 0.0
        if (peakI > 0.0) {
            val half = peakI / 2.0
            var leftNm: Double? = null; var rightNm: Double? = null
            for (i in 0 until windowSize - 1) {
                val v1 = ints[i]; val v2 = ints[i + 1]
                if (v1 < half && v2 >= half) {
                    val t = (half - v1) / (v2 - v1 + 1e-12)
                    leftNm = wls[i] + t * (wls[i + 1] - wls[i]); break
                }
            }
            if (leftNm != null) {
                for (i in peakIdx until windowSize - 1) {
                    val v1 = ints[i]; val v2 = ints[i + 1]
                    if (v1 >= half && v2 < half) {
                        val t = (half - v1) / (v2 - v1 + 1e-12)
                        rightNm = wls[i] + t * (wls[i + 1] - wls[i]); break
                    }
                }
            }
            fwhmNm = if (leftNm != null && rightNm != null) rightNm - leftNm else abs(wls[windowSize - 1] - wls[0])
        } else {
            fwhmNm = if (windowSize > 1) abs(wls[windowSize - 1] - wls[0]) else 0.0
        }

        // 【修复】强制 visIdx 按波长递增排序，防止画图时 X 轴反转导致斜坡
        val visIdx = (0 until n).filter { lam[it] in VIS_LO.toFloat()..VIS_HI.toFloat() }
            .sortedBy { lam[it] }

        fun crop(arr: FloatArray, transform: (FloatArray, Int) -> Float): List<Float> {
            if (visIdx.isEmpty()) return emptyList()
            val k = maxOf(1, visIdx.size / 180)
            return visIdx.filterIndexed { i, _ -> i % k == 0 }.map { transform(arr, it) }
        }
        val corrSpec = crop(cleanedNet) { a, i -> a[i] }
        val rawBlankSpec = crop(cleanedBlank) { a, i -> a[i] }
        val blankSpec = smoothList(rawBlankSpec, 7)

        return SampleAnalysisResult(
            channel = channel, responseApplied = applied,
            blankMeanArea = rawBlank, sampleMeanArea = rawSample, correctedArea = corrected,
            sampleSD = sd, replicates = sampleFrames.size,
            windowStartPx = lo, windowEndPx = hi,
            peakNm = peakNm, peakIntensity = peakI, fwhmNm = fwhmNm, centroidNm = centroidNm,
            correctedSpectrum = corrSpec, blankSpectrum = blankSpec,
            spdSkipReason = skipReason
        )
    }

    private fun smoothList(list: List<Float>, window: Int): List<Float> {
        if (list.size <= window) return list
        val out = FloatArray(list.size)
        val half = window / 2
        for (i in list.indices) {
            var sum = 0f; var count = 0
            for (j in (i - half)..(i + half)) {
                if (j in list.indices) {
                    sum += list[j]
                    count++
                }
            }
            out[i] = if (count > 0) sum / count else list[i]
        }
        return out.toList()
    }
}