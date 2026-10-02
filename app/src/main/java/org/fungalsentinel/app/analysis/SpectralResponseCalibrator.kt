package org.fungalsentinel.app.analysis

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader
import kotlin.math.abs

data class SpdCalibrationResult(
    val wavelengths: FloatArray,
    val respR: FloatArray, val respG: FloatArray, val respB: FloatArray,
    val corrR: FloatArray, val corrG: FloatArray, val corrB: FloatArray,
    val wR: FloatArray, val wG: FloatArray, val wB: FloatArray,
    val fused: FloatArray, val mask: BooleanArray,
    val effectiveBandNm: Pair<Float, Float>,
    val maskedCount: Int, val totalCount: Int, val thresholdRatio: Float,
    val handoffBG: Float?, val handoffGR: Float?,
    val weightsAt450: Triple<Float, Float, Float>,
    val weightsAt520: Triple<Float, Float, Float>,
    val weightsAt600: Triple<Float, Float, Float>,
    val peakRespR: Pair<Float, Float>, val peakRespG: Pair<Float, Float>, val peakRespB: Pair<Float, Float>,
    val robustR: Pair<Float, Int>, val robustG: Pair<Float, Int>, val robustB: Pair<Float, Int>,
    val fusedPeak: Pair<Float, Float>,
    val exposureSeconds: Double, val frames: Int, val spdSource: String
)

/**
 * FSSA v1.4: resp_c = Lamp_c/SPD_true (dual mask, no smoothing, 5% floor),
 * C_c = Pos_c/resp_c on INDEPENDENT positioning data, w_c = resp_c/Σresp,
 * F = Σ w_c·C_c (single continuous curve). JSON-serializable for presets.
 */
object SpectralResponseCalibrator {
    fun calibrate(
        lampFrames: List<FrameProfile>, posFrames: List<FrameProfile>,
        spdTrue: List<Pair<Double, Double>>, slope: Double, intercept: Double, spdSource: String,
        bandNm: ClosedFloatingPointRange<Double> = 430.0..660.0,
        thresholdRatio: Float = 0.1f, floorFraction: Float = 0.05f
    ): SpdCalibrationResult? {
        if (lampFrames.isEmpty() || posFrames.isEmpty() || spdTrue.size < 2) return null
        val n = minOf(lampFrames[0].redProfile.size, posFrames[0].redProfile.size)
        if (n < 10) return null
        val lam = FloatArray(n) { i -> ((i - intercept) / slope).toFloat() }
        fun avgNorm(frames: List<FrameProfile>, sel: (FrameProfile) -> FloatArray): FloatArray {
            val out = FloatArray(n)
            for (f in frames) {
                val p = sel(f); val e = if (f.exposureSeconds > 0) f.exposureSeconds.toFloat() else 1f
                val m = minOf(n, p.size)
                for (i in 0 until m) out[i] += p[i] / e
            }
            val k = frames.size.toFloat(); for (i in 0 until n) out[i] /= k
            return out
        }
        val lampR = avgNorm(lampFrames) { it.redProfile }
        val lampG = avgNorm(lampFrames) { it.greenProfile }
        val lampB = avgNorm(lampFrames) { it.blueProfile }
        val posR = avgNorm(posFrames) { it.redProfile }
        val posG = avgNorm(posFrames) { it.greenProfile }
        val posB = avgNorm(posFrames) { it.blueProfile }
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
        val mask = BooleanArray(n) { i -> val l = lam[i].toDouble(); l in bandNm && spdAt(l) > thresholdRatio * maxSpd }
        val maskedCount = mask.count { it }
        if (maskedCount < 10) return null
        val respR = FloatArray(n); val respG = FloatArray(n); val respB = FloatArray(n)
        for (i in 0 until n) {
            if (!mask[i]) continue
            val s = spdAt(lam[i].toDouble())
            if (s <= 0.0) continue
            respR[i] = lampR[i] / s.toFloat(); respG[i] = lampG[i] / s.toFloat(); respB[i] = lampB[i] / s.toFloat()
        }
        fun peakOf(arr: FloatArray): Pair<Float, Float> {
            var best = 0f; var bl = 0f
            for (i in 0 until n) if (mask[i] && arr[i] > best) { best = arr[i]; bl = lam[i] }
            return best to bl
        }
        val pkR = peakOf(respR); val pkG = peakOf(respG); val pkB = peakOf(respB)
        val floorR = floorFraction * pkR.first; val floorG = floorFraction * pkG.first; val floorB = floorFraction * pkB.first
        val corrR = FloatArray(n); val corrG = FloatArray(n); val corrB = FloatArray(n)
        for (i in 0 until n) {
            if (!mask[i]) continue
            corrR[i] = posR[i] / maxOf(respR[i], floorR)
            corrG[i] = posG[i] / maxOf(respG[i], floorG)
            corrB[i] = posB[i] / maxOf(respB[i], floorB)
        }
        val wR = FloatArray(n); val wG = FloatArray(n); val wB = FloatArray(n)
        for (i in 0 until n) {
            if (!mask[i]) continue
            val sum = respR[i] + respG[i] + respB[i]
            if (sum <= 0f) continue
            wR[i] = respR[i] / sum; wG[i] = respG[i] / sum; wB[i] = respB[i] / sum
        }
        val fused = FloatArray(n) { wR[it] * corrR[it] + wG[it] * corrG[it] + wB[it] * corrB[it] }
        val order = (0 until n).filter { mask[it] }.sortedBy { lam[it] }
        var hBG: Float? = null; var hGR: Float? = null
        for (i in order) {
            if (hBG == null && wG[i] > wB[i]) hBG = lam[i]
            if (hGR == null && wR[i] > wG[i]) hGR = lam[i]
        }
        fun weightsAt(t: Float): Triple<Float, Float, Float> {
            val i = order.minByOrNull { abs(lam[it] - t) } ?: return Triple(0f, 0f, 0f)
            return Triple(wR[i], wG[i], wB[i])
        }
        var maxF = 0f
        for (i in 0 until n) if (mask[i] && fused[i] > maxF) maxF = fused[i]
        fun robust(w: FloatArray, c: FloatArray): Pair<Float, Int> {
            var sum = 0.0; var cnt = 0
            for (i in 0 until n) if (mask[i] && w[i] > 0.5f) { sum += abs(c[i] - fused[i]); cnt++ }
            return (if (cnt > 0 && maxF > 0f) (100.0 * sum / cnt / maxF).toFloat() else 0f) to cnt
        }
        var fPeak = 0f; var fPeakL = 0f
        for (i in 0 until n) if (mask[i] && fused[i] > fPeak) { fPeak = fused[i]; fPeakL = lam[i] }
        val exposure = lampFrames.map { it.exposureSeconds }.filter { it > 0 }.average().let { if (it.isNaN()) 0.0 else it }
        return SpdCalibrationResult(
            lam, respR, respG, respB, corrR, corrG, corrB, wR, wG, wB, fused, mask,
            (order.firstOrNull()?.let { lam[it] } ?: 0f) to (order.lastOrNull()?.let { lam[it] } ?: 0f),
            maskedCount, n, thresholdRatio, hBG, hGR,
            weightsAt(450f), weightsAt(520f), weightsAt(600f),
            pkR, pkG, pkB, robust(wR, corrR), robust(wG, corrG), robust(wB, corrB),
            fPeak to fPeakL, exposure, lampFrames.size, spdSource
        )
    }

    fun toJson(r: SpdCalibrationResult): String = runCatching {
        val o = JSONObject()
        o.put("version", 1)
        fun ja(a: FloatArray) = JSONArray().apply { for (v in a) put(v.toDouble()) }
        o.put("wavelengths", ja(r.wavelengths))
        o.put("respR", ja(r.respR)); o.put("respG", ja(r.respG)); o.put("respB", ja(r.respB))
        o.put("corrR", ja(r.corrR)); o.put("corrG", ja(r.corrG)); o.put("corrB", ja(r.corrB))
        o.put("wR", ja(r.wR)); o.put("wG", ja(r.wG)); o.put("wB", ja(r.wB))
        o.put("fused", ja(r.fused))
        o.put("mask", JSONArray().apply { for (m in r.mask) put(if (m) 1 else 0) })
        o.put("bandLo", r.effectiveBandNm.first.toDouble()); o.put("bandHi", r.effectiveBandNm.second.toDouble())
        o.put("maskedCount", r.maskedCount); o.put("totalCount", r.totalCount)
        o.put("thresholdRatio", r.thresholdRatio.toDouble())
        o.put("handoffBG", r.handoffBG?.toDouble() ?: JSONObject.NULL)
        o.put("handoffGR", r.handoffGR?.toDouble() ?: JSONObject.NULL)
        fun jt(t: Triple<Float, Float, Float>) = JSONArray().put(t.first.toDouble()).put(t.second.toDouble()).put(t.third.toDouble())
        o.put("w450", jt(r.weightsAt450)); o.put("w520", jt(r.weightsAt520)); o.put("w600", jt(r.weightsAt600))
        fun jp(p: Pair<Float, Float>) = JSONArray().put(p.first.toDouble()).put(p.second.toDouble())
        o.put("peakR", jp(r.peakRespR)); o.put("peakG", jp(r.peakRespG)); o.put("peakB", jp(r.peakRespB))
        o.put("robR", jp(r.robustR.first to r.robustR.second.toFloat()))
        o.put("robG", jp(r.robustG.first to r.robustG.second.toFloat()))
        o.put("robB", jp(r.robustB.first to r.robustB.second.toFloat()))
        o.put("fusedPeak", jp(r.fusedPeak))
        o.put("exposure", r.exposureSeconds); o.put("frames", r.frames); o.put("spdSource", r.spdSource)
        o.toString()
    }.getOrDefault("{}")

    fun fromJson(s: String): SpdCalibrationResult? = runCatching {
        val o = JSONObject(s)
        if (o.optInt("version", 0) != 1) return null
        fun fa(k: String) = FloatArray(o.getJSONArray(k).length()) { o.getJSONArray(k).getDouble(it).toFloat() }
        fun pa(k: String): Pair<Float, Float> { val a = o.getJSONArray(k); return a.getDouble(0).toFloat() to a.getDouble(1).toFloat() }
        fun tri(k: String): Triple<Float, Float, Float> { val a = o.getJSONArray(k); return Triple(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getDouble(2).toFloat()) }
        val ma = o.getJSONArray("mask")
        SpdCalibrationResult(
            fa("wavelengths"), fa("respR"), fa("respG"), fa("respB"),
            fa("corrR"), fa("corrG"), fa("corrB"), fa("wR"), fa("wG"), fa("wB"), fa("fused"),
            BooleanArray(ma.length()) { ma.getInt(it) == 1 },
            o.getDouble("bandLo").toFloat() to o.getDouble("bandHi").toFloat(),
            o.getInt("maskedCount"), o.getInt("totalCount"), o.getDouble("thresholdRatio").toFloat(),
            if (o.isNull("handoffBG")) null else o.getDouble("handoffBG").toFloat(),
            if (o.isNull("handoffGR")) null else o.getDouble("handoffGR").toFloat(),
            tri("w450"), tri("w520"), tri("w600"),
            pa("peakR"), pa("peakG"), pa("peakB"),
            pa("robR").let { it.first to it.second.toInt() },
            pa("robG").let { it.first to it.second.toInt() },
            pa("robB").let { it.first to it.second.toInt() },
            pa("fusedPeak"), o.getDouble("exposure"), o.getInt("frames"), o.optString("spdSource", "preset")
        )
    }.getOrNull()
}

/** SPD source: custom imported CSV first, then bundled assets/true_spd.csv. */
object SpdLoader {
    const val CUSTOM_NAME = "custom_spd.csv"
    fun customFile(context: Context) = File(context.filesDir, "spd/$CUSTOM_NAME")

    fun installCustom(context: Context, uri: Uri): String? = runCatching {
        val parsed = context.contentResolver.openInputStream(uri)?.use { parseReader(InputStreamReader(it)) }
            ?: return "Cannot open file"
        if (parsed.size < 10) return "CSV too short (need wavelength_nm,intensity rows)"
        val dir = File(context.filesDir, "spd").apply { mkdirs() }
        context.contentResolver.openInputStream(uri)?.use { inp ->
            File(dir, CUSTOM_NAME).outputStream().use { out -> inp.copyTo(out) }
        }
        null
    }.getOrElse { it.message ?: "Parse failed" }

    fun load(context: Context): Pair<String, List<Pair<Double, Double>>>? {
        val custom = customFile(context)
        if (custom.exists()) {
            val rows = runCatching { parseReader(custom.reader()) }.getOrNull()
            if (!rows.isNullOrEmpty()) return CUSTOM_NAME to rows
        }
        val rows = runCatching {
            context.assets.open("true_spd.csv").use { parseReader(InputStreamReader(it)) }
        }.getOrNull()
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