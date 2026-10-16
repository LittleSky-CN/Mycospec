package org.fungalsentinel.app.analysis

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

data class SpdCalibrationResult(
    val wavelengths: FloatArray,
    val respR: FloatArray, val respG: FloatArray, val respB: FloatArray,
    val posR: FloatArray, val posG: FloatArray, val posB: FloatArray,
    val corrR: FloatArray, val corrG: FloatArray, val corrB: FloatArray,
    val wB: FloatArray, val wG: FloatArray, val wR: FloatArray,
    val fused: FloatArray,
    val mask: BooleanArray,
    val effectiveBandNm: Pair<Float, Float>,
    val maskedCount: Int, val totalCount: Int, val thresholdRatio: Float,
    val handoffBG: Float, val handoffGR: Float,
    val logisticWidthNm: Float,
    val agreementPct: Float,
    val peakRespR: Pair<Float, Float>, val peakRespG: Pair<Float, Float>, val peakRespB: Pair<Float, Float>,
    val fusedPeak: Pair<Float, Float>,
    val exposureSeconds: Double, val frames: Int, val spdSource: String,
    val rbSwapped: Boolean = false
)

/**
 * FSSA core/calibration.py + core/fusion.py parity:
 *  resp_c = Lamp_c / (SPD_true + 1e-6), range- and SPD-masked
 *  No smoothing is applied to the response curves.
 *  Corrected_c = Pos_c / resp_c  (guarded division > 1e-6; INDEPENDENT numerator)
 *  weights: logistic handoff relay, width 15 nm, w_B+w_G+w_R = 1 pointwise
 *  Fused = w_B·Corr_B + w_G·Corr_G + w_R·Corr_R (single C0-continuous curve)
 */
object SpectralResponseCalibrator {
    private const val EPS = 1e-6f
    private const val LOGISTIC_WIDTH_NM = 15.0f
    private const val DEFAULT_HBG = 483.0f
    private const val DEFAULT_HGR = 577.0f

    private fun logistic(x: Float): Float {
        val t = x.coerceIn(-500f, 500f).toDouble()
        return (1.0 / (1.0 + exp(-t))).toFloat()
    }

    fun calibrate(
        lampFrames: List<FrameProfile>, posFrames: List<FrameProfile>,
        spdTrue: List<Pair<Double, Double>>, slope: Double, intercept: Double, spdSource: String,
        bandNm: ClosedFloatingPointRange<Double> = 430.0..660.0,
        thresholdRatio: Float = 0.1f
    ): SpdCalibrationResult? {
        if (lampFrames.isEmpty() || posFrames.isEmpty() || spdTrue.size < 2) return null
        val n = minOf(lampFrames[0].redProfile.size, posFrames[0].redProfile.size)
        if (n < 10) return null

        val lamRaw = FloatArray(n) { i -> ((i - intercept) / slope).toFloat() }
        val reversed = lamRaw[0] > lamRaw[n - 1]
        val lam = if (reversed) lamRaw.reversed().toFloatArray() else lamRaw

        fun avgNorm(frames: List<FrameProfile>, sel: (FrameProfile) -> FloatArray): FloatArray {
            val out = FloatArray(n)
            for (f in frames) {
                val p = sel(f)
                for (i in 0 until minOf(n, p.size)) out[i] += p[i]
            }
            val k = frames.size.toFloat()
            for (i in 0 until n) out[i] /= k
            return out
        }

        fun ord(a: FloatArray) = if (reversed) a.reversed().toFloatArray() else a

        val lampR = ord(avgNorm(lampFrames) { it.redProfile })
        val lampG = ord(avgNorm(lampFrames) { it.greenProfile })
        val lampB = ord(avgNorm(lampFrames) { it.blueProfile })
        val posR = ord(avgNorm(posFrames) { it.redProfile })
        val posG = ord(avgNorm(posFrames) { it.greenProfile })
        val posB = ord(avgNorm(posFrames) { it.blueProfile })

        val sorted = spdTrue.sortedBy { it.first }
        val maxSpd = sorted.maxOf { it.second }
        if (maxSpd <= 0.0) return null

        fun spdAt(l: Double): Double {
            if (l < sorted.first().first || l > sorted.last().first) return 0.0
            var lo = 0; var hi = sorted.lastIndex
            while (lo < hi - 1) { val mid = (lo + hi) / 2; if (sorted[mid].first <= l) lo = mid else hi = mid }
            val (x0, y0) = sorted[lo]; val (x1, y1) = sorted[hi]
            return if (x1 == x0) y0 else y0 + (y1 - y0) * (l - x0) / (x1 - x0)
        }

        val mask = BooleanArray(n) { i ->
            val l = lam[i].toDouble()
            l in bandNm && spdAt(l) > thresholdRatio * maxSpd
        }
        val maskedCount = mask.count { it }
        if (maskedCount < 10) return null

        // 计算原始响应
        val respR = FloatArray(n); val respG = FloatArray(n); val respB = FloatArray(n)
        for (i in 0 until n) {
            if (!mask[i]) continue
            val s = max(EPS, spdAt(lam[i].toDouble()).toFloat())
            respR[i] = lampR[i] / s; respG[i] = lampG[i] / s; respB[i] = lampB[i] / s
        }

        // Find channel handoffs on the unmodified response curves (for display/parameters).
        var hBG = DEFAULT_HBG; var best = Float.POSITIVE_INFINITY
        for (i in 0 until n) if (mask[i] && respB[i] > 0f && respG[i] > 0f) {
            val d = abs(respB[i] - respG[i])
            if (d < best) { best = d; hBG = lam[i] }
        }
        var hGR = DEFAULT_HGR; best = Float.POSITIVE_INFINITY
        for (i in 0 until n) if (mask[i] && respG[i] > 0f && respR[i] > 0f && lam[i] > hBG + 20f) {
            val d = abs(respG[i] - respR[i])
            if (d < best) { best = d; hGR = lam[i] }
        }

        // 逐波长动态加权融合权重 (w_c = resp_c / sum_c resp_c)
        val wB = FloatArray(n); val wG = FloatArray(n); val wR = FloatArray(n)
        for (i in 0 until n) {
            if (!mask[i]) continue
            val sumResp = respR[i] + respG[i] + respB[i] + EPS
            wR[i] = respR[i] / sumResp
            wG[i] = respG[i] / sumResp
            wB[i] = respB[i] / sumResp
        }

        // 相对地板保护：下游除法采用 "该通道响应峰值 5%" 作为地板，防止掩码边缘除零爆炸
        val maxR = (0 until n).filter { mask[it] }.maxOfOrNull { respR[it] } ?: 1f
        val maxG = (0 until n).filter { mask[it] }.maxOfOrNull { respG[it] } ?: 1f
        val maxB = (0 until n).filter { mask[it] }.maxOfOrNull { respB[it] } ?: 1f
        val floorR = max(EPS, 0.05f * maxR)
        val floorG = max(EPS, 0.05f * maxG)
        val floorB = max(EPS, 0.05f * maxB)

        val corrR = FloatArray(n); val corrG = FloatArray(n); val corrB = FloatArray(n)
        for (i in 0 until n) {
            if (!mask[i]) continue
            corrR[i] = posR[i] / max(respR[i], floorR)
            corrG[i] = posG[i] / max(respG[i], floorG)
            corrB[i] = posB[i] / max(respB[i], floorB)
        }

        val fused = FloatArray(n) { wB[it] * corrB[it] + wG[it] * corrG[it] + wR[it] * corrR[it] }

        // Scale corrR, corrG, corrB to align peak amplitudes with fused curve for visual cross-channel agreement
        val maxFused = (0 until n).filter { mask[it] }.maxOfOrNull { fused[it] } ?: 1f
        val maxCR = (0 until n).filter { mask[it] }.maxOfOrNull { corrR[it] }.takeIf { it != null && it > 0f } ?: 1f
        val maxCG = (0 until n).filter { mask[it] }.maxOfOrNull { corrG[it] }.takeIf { it != null && it > 0f } ?: 1f
        val maxCB = (0 until n).filter { mask[it] }.maxOfOrNull { corrB[it] }.takeIf { it != null && it > 0f } ?: 1f
        val sR = maxFused / maxCR; val sG = maxFused / maxCG; val sB = maxFused / maxCB
        for (i in 0 until n) {
            if (!mask[i]) continue
            corrR[i] *= sR
            corrG[i] *= sG
            corrB[i] *= sB
        }

        var devSum = 0.0; var cnt = 0
        for (i in 0 until n) if (mask[i]) {
            devSum += (abs(corrB[i] - corrG[i]) + abs(corrG[i] - corrR[i]) + abs(corrB[i] - corrR[i])).toDouble() / 3.0
            cnt++
        }
        var fPosSum = 0.0; var fPosCnt = 0
        for (i in 0 until n) if (mask[i] && fused[i] > 0f) { fPosSum += fused[i]; fPosCnt++ }
        val meanFusedPos = if (fPosCnt > 0) fPosSum / fPosCnt else 0.0
        val agreementPct = if (cnt > 0 && meanFusedPos > 1e-9) ((devSum / cnt) / meanFusedPos * 100.0).toFloat() else 0f

        fun peakOf(arr: FloatArray): Pair<Float, Float> {
            var b = 0f; var bl = 0f
            for (i in 0 until n) if (mask[i] && arr[i] > b) { b = arr[i]; bl = lam[i] }
            return b to bl
        }

        var fPeak = 0f; var fPeakL = 0f
        for (i in 0 until n) if (mask[i] && fused[i] > fPeak) { fPeak = fused[i]; fPeakL = lam[i] }

        val order = (0 until n).filter { mask[it] }.sortedBy { lam[it] }
        val exposure = lampFrames.map { it.exposureSeconds }.filter { it > 0 }
            .average().let { if (it.isNaN()) 0.0 else it }

        return SpdCalibrationResult(
            lam, respR, respG, respB, posR, posG, posB, corrR, corrG, corrB,
            wB, wG, wR, fused, mask,
            (order.firstOrNull()?.let { lam[it] } ?: 0f) to (order.lastOrNull()?.let { lam[it] } ?: 0f),
            maskedCount, n, thresholdRatio, hBG, hGR, LOGISTIC_WIDTH_NM, agreementPct,
            peakOf(respR), peakOf(respG), peakOf(respB),
            fPeak to fPeakL, exposure, lampFrames.size, spdSource
        )
    }

    fun toJson(r: SpdCalibrationResult): String = runCatching {
        val o = JSONObject()
        o.put("version", 2)
        fun ja(a: FloatArray) = JSONArray().apply { for (v in a) put(v.toDouble()) }
        o.put("wavelengths", ja(r.wavelengths))
        o.put("respR", ja(r.respR)); o.put("respG", ja(r.respG)); o.put("respB", ja(r.respB))
        o.put("posR", ja(r.posR)); o.put("posG", ja(r.posG)); o.put("posB", ja(r.posB))
        o.put("corrR", ja(r.corrR)); o.put("corrG", ja(r.corrG)); o.put("corrB", ja(r.corrB))
        o.put("wB", ja(r.wB)); o.put("wG", ja(r.wG)); o.put("wR", ja(r.wR))
        o.put("fused", ja(r.fused))
        o.put("mask", JSONArray().apply { for (m in r.mask) put(if (m) 1 else 0) })
        o.put("bandLo", r.effectiveBandNm.first.toDouble()); o.put("bandHi", r.effectiveBandNm.second.toDouble())
        o.put("maskedCount", r.maskedCount); o.put("totalCount", r.totalCount)
        o.put("thresholdRatio", r.thresholdRatio.toDouble())
        o.put("handoffBG", r.handoffBG.toDouble()); o.put("handoffGR", r.handoffGR.toDouble())
        o.put("logisticWidth", r.logisticWidthNm.toDouble())
        o.put("agreementPct", r.agreementPct.toDouble())
        fun jp(p: Pair<Float, Float>) = JSONArray().put(p.first.toDouble()).put(p.second.toDouble())
        o.put("peakR", jp(r.peakRespR)); o.put("peakG", jp(r.peakRespG)); o.put("peakB", jp(r.peakRespB))
        o.put("fusedPeak", jp(r.fusedPeak))
        o.put("exposure", r.exposureSeconds); o.put("frames", r.frames); o.put("spdSource", r.spdSource)
        o.put("rbSwapped", r.rbSwapped)
        o.toString()
    }.getOrDefault("{}")

    fun fromJson(s: String): SpdCalibrationResult? = runCatching {
        val o = JSONObject(s)
        fun fa(k: String): FloatArray = if (o.has(k)) FloatArray(o.getJSONArray(k).length()) { o.getJSONArray(k).getDouble(it).toFloat() } else FloatArray(0)
        fun pa(k: String): Pair<Float, Float> { val a = o.getJSONArray(k); return a.getDouble(0).toFloat() to a.getDouble(1).toFloat() }
        val ma = o.getJSONArray("mask")
        val version = o.optInt("version", 1)
        val empty = FloatArray(0)
        SpdCalibrationResult(
            fa("wavelengths"), fa("respR"), fa("respG"), fa("respB"),
            if (version >= 2) fa("posR") else empty, if (version >= 2) fa("posG") else empty, if (version >= 2) fa("posB") else empty,
            fa("corrR"), fa("corrG"), fa("corrB"),
            fa("wB").let { if (it.isNotEmpty()) it else fa("wR").let { _ -> FloatArray(0) } }, fa("wG"), if (version >= 2) fa("wR") else fa("wR"),
            fa("fused"), BooleanArray(ma.length()) { ma.getInt(it) == 1 },
            o.getDouble("bandLo").toFloat() to o.getDouble("bandHi").toFloat(),
            o.getInt("maskedCount"), o.getInt("totalCount"), o.getDouble("thresholdRatio").toFloat(),
            if (version >= 2) o.getDouble("handoffBG").toFloat() else (if (o.isNull("handoffBG")) DEFAULT_HBG else o.getDouble("handoffBG").toFloat()),
            if (version >= 2) o.getDouble("handoffGR").toFloat() else (if (o.isNull("handoffGR")) DEFAULT_HGR else o.getDouble("handoffGR").toFloat()),
            if (version >= 2) o.getDouble("logisticWidth").toFloat() else LOGISTIC_WIDTH_NM,
            if (version >= 2) o.getDouble("agreementPct").toFloat() else 0f,
            pa("peakR"), pa("peakG"), pa("peakB"), pa("fusedPeak"), o.getDouble("exposure"), o.getInt("frames"),
            o.optString("spdSource", "preset"), o.optBoolean("rbSwapped", false)
        )
    }.getOrNull()
}

object SpdLoader {
    const val CUSTOM_NAME = "custom_spd.csv"
    fun customFile(context: Context) = File(context.filesDir, "spd/$CUSTOM_NAME")
    fun installCustom(context: Context, uri: Uri): String? = runCatching {
        val parsed = context.contentResolver.openInputStream(uri)?.use { parseReader(InputStreamReader(it)) } ?: return "Cannot open file"
        if (parsed.size < 10) return "CSV too short"
        val dir = File(context.filesDir, "spd").apply { mkdirs() }
        context.contentResolver.openInputStream(uri)?.use { inp -> File(dir, CUSTOM_NAME).outputStream().use { out -> inp.copyTo(out) } }
        null
    }.getOrElse { it.message ?: "Parse failed" }
    fun load(context: Context): Pair<String, List<Pair<Double, Double>>>? {
        val custom = customFile(context)
        if (custom.exists()) {
            val rows = runCatching { parseReader(custom.reader()) }.getOrNull()
            if (!rows.isNullOrEmpty()) return CUSTOM_NAME to rows
        }
        val rows = runCatching { context.assets.open("true_spd.csv").use { parseReader(InputStreamReader(it)) } }.getOrNull()
        return if (!rows.isNullOrEmpty()) "true_spd.csv (bundled)" to rows else null
    }
    private fun parseReader(reader: java.io.Reader): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>()
        reader.buffered().useLines { lines ->
            for (raw in lines) {
                val parts = raw.split(',', ';', '\t').map { it.trim() }
                if (parts.size < 2) continue
                val a = parts[0].toDoubleOrNull() ?: continue
                val b = parts[1].toDoubleOrNull() ?: continue
                out.add(a to b)
            }
        }
        return out
    }
}