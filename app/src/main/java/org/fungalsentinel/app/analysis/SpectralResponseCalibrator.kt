package org.fungalsentinel.app.analysis

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * L3 drift classification for a reloaded SPD preset
 * (preset_parity_design.md §1.2 / FSSA core/calibration.py `drift_level`).
 *
 * `UNKNOWN` means the preset carries no mapping to compare against.
 * A drift NEVER rejects a preset -- it only drives a WARN / SEVERE hint.
 */
enum class DriftLevel { OK, WARN, SEVERE, UNKNOWN }

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
    // Diagnostic only: wavelengths where two adjacent channels have EQUAL
    // responsivity. These do NOT drive the fusion weights.
    val crossingBG: Float, val crossingGR: Float,
    val agreementPct: Float,
    val peakRespR: Pair<Float, Float>, val peakRespG: Pair<Float, Float>, val peakRespB: Pair<Float, Float>,
    val fusedPeak: Pair<Float, Float>,
    val exposureSeconds: Double, val frames: Int, val spdSource: String,
    val rbSwapped: Boolean = false,

    // ===== v2.2.6 traceability (FSSA 1.4.6 parity, preset_parity_design.md §3.4) =====
    // All defaulted so existing construction sites keep compiling.
    /** Mapping the response was baked with: px/nm. NaN when unknown (legacy payload). */
    val slope: Double = Double.NaN,
    /** Mapping the response was baked with: px. NaN when unknown (legacy payload). */
    val intercept: Double = Double.NaN,
    /** Step 1 G-point validation residual (nm) at calibration time. */
    val gErrorNm: Double? = null,
    /** X-ROI (px, closed interval) used while decoding the lamp frames. */
    val xRoiStart: Int? = null, val xRoiEnd: Int? = null,
    /** Mode B: un-contaminated lamp profiles, already oriented like `wavelengths`. */
    val lampR: FloatArray? = null, val lampG: FloatArray? = null, val lampB: FloatArray? = null,
    /** Mode B: the reference lamp SPD grid actually used (raw CSV grid, mapping independent). */
    val spdRefWl: FloatArray? = null, val spdRefVal: FloatArray? = null
)

/**
 * Python FSSA core/calibration.py + core/fusion.py parity:
 *  resp_c = Lamp_c / (SPD_true + 1e-6), range- and SPD-masked
 *  No smoothing is applied to the response curves.
 *  Corrected_c = Pos_c / resp_c  (guarded division > 1e-6; INDEPENDENT numerator)
 *  weights: RESPONSE-PROPORTIONAL normalisation
 *           w_c(λ) = resp_c(λ) / (resp_R + resp_G + resp_B + eps), Σ w_c = 1 pointwise
 *  Fused = w_B·Corr_B + w_G·Corr_G + w_R·Corr_R (single continuous curve)
 *
 * The Python build implements byte-for-byte the same rule in core/fusion.py,
 * so Step 2 results are interchangeable between desktop and phone.
 */
object SpectralResponseCalibrator {
    private const val EPS = 1e-6f
    private const val DEFAULT_CROSSING_BG = 483.0f
    private const val DEFAULT_CROSSING_GR = 577.0f

    /** Drift at or below this many nanometres is silent (preset_parity_design.md §1.2). */
    const val DRIFT_WARN_NM = 2.0f
    /** Above this many nanometres the drift is reported as SEVERE -- still never fatal. */
    const val DRIFT_SEVERE_NM = 10.0f
    /** Quintile sampling of the pixel axis used by [SpdCalibrationResult.mappingDriftNm]. */
    private val DRIFT_SAMPLE_FRACTIONS = doubleArrayOf(0.10, 0.30, 0.50, 0.70, 0.90)
    /** L1 structural floor: a payload with fewer non-zero response points is unusable. */
    private const val MIN_NON_EMPTY_POINTS = 20
    /** Valid spectral band (nm). Shared by [calibrate] and [SpdCalibrationResult.recomputeWith]. */
    const val BAND_LO_NM = 430.0
    const val BAND_HI_NM = 660.0

    /** Derived curves that follow mechanically from (lam, mask, resp, pos). */
    internal data class DerivedCurves(
        val wB: FloatArray, val wG: FloatArray, val wR: FloatArray,
        val corrR: FloatArray, val corrG: FloatArray, val corrB: FloatArray,
        val fused: FloatArray,
        val crossingBG: Float, val crossingGR: Float,
        val agreementPct: Float,
        val peakRespR: Pair<Float, Float>,
        val peakRespG: Pair<Float, Float>,
        val peakRespB: Pair<Float, Float>,
        val fusedPeak: Pair<Float, Float>
    )

    /**
     * Steps 5..10 of the Step 2 pipeline: crossings, response-proportional
     * fusion weights, floor-guarded correction, fused curve, channel scaling,
     * cross-channel agreement and the reported peaks.
     *
     * Shared by [calibrate] and [SpdCalibrationResult.recomputeWith] so that a
     * Mode B re-computation is bit-identical to a fresh Step 2 run.
     */
    internal fun derive(
        lam: FloatArray,
        mask: BooleanArray,
        respR: FloatArray,
        respG: FloatArray,
        respB: FloatArray,
        posR: FloatArray,
        posG: FloatArray,
        posB: FloatArray
    ): DerivedCurves {
        val n = lam.size

        // Diagnostic only: responsivity crossings on the unmodified response
        // curves (reported as figures of merit; they do NOT build the weights).
        var xBG = DEFAULT_CROSSING_BG; var best = Float.POSITIVE_INFINITY
        for (i in 0 until n) if (mask[i] && respB[i] > 0f && respG[i] > 0f) {
            val d = abs(respB[i] - respG[i])
            if (d < best) { best = d; xBG = lam[i] }
        }
        var xGR = DEFAULT_CROSSING_GR; best = Float.POSITIVE_INFINITY
        for (i in 0 until n) if (mask[i] && respG[i] > 0f && respR[i] > 0f && lam[i] > xBG + 20f) {
            val d = abs(respG[i] - respR[i])
            if (d < best) { best = d; xGR = lam[i] }
        }

        // RESPONSE-PROPORTIONAL FUSION WEIGHTS: w_c = resp_c / Σresp_c  (Σ w_c = 1)
        // Each wavelength is carried by whichever channel is genuinely most
        // sensitive there. Identical to Python FSSA core/fusion.py.
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

        return DerivedCurves(
            wB = wB, wG = wG, wR = wR,
            corrR = corrR, corrG = corrG, corrB = corrB,
            fused = fused,
            crossingBG = xBG, crossingGR = xGR,
            agreementPct = agreementPct,
            peakRespR = peakOf(respR), peakRespG = peakOf(respG), peakRespB = peakOf(respB),
            fusedPeak = fPeak to fPeakL
        )
    }

    fun calibrate(
        lampFrames: List<FrameProfile>, posFrames: List<FrameProfile>,
        spdTrue: List<Pair<Double, Double>>, slope: Double, intercept: Double, spdSource: String,
        bandNm: ClosedFloatingPointRange<Double> = BAND_LO_NM..BAND_HI_NM,
        thresholdRatio: Float = 0.1f,
        gErrorNm: Double? = null
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

        val derived = derive(lam, mask, respR, respG, respB, posR, posG, posB)

        val order = (0 until n).filter { mask[it] }.sortedBy { lam[it] }
        val exposure = lampFrames.map { it.exposureSeconds }.filter { it > 0 }
            .average().let { if (it.isNaN()) 0.0 else it }

        return SpdCalibrationResult(
            lam, respR, respG, respB, posR, posG, posB,
            derived.corrR, derived.corrG, derived.corrB,
            derived.wB, derived.wG, derived.wR, derived.fused, mask,
            (order.firstOrNull()?.let { lam[it] } ?: 0f) to (order.lastOrNull()?.let { lam[it] } ?: 0f),
            maskedCount, n, thresholdRatio, derived.crossingBG, derived.crossingGR, derived.agreementPct,
            derived.peakRespR, derived.peakRespG, derived.peakRespB,
            derived.fusedPeak, exposure, lampFrames.size, spdSource,
            rbSwapped = false,
            // ---- v2.2.6 traceability + Mode B inputs ----
            slope = slope, intercept = intercept, gErrorNm = gErrorNm,
            xRoiStart = lampFrames[0].xRoiStart, xRoiEnd = lampFrames[0].xRoiEnd,
            lampR = lampR, lampG = lampG, lampB = lampB,
            spdRefWl = FloatArray(sorted.size) { sorted[it].first.toFloat() },
            spdRefVal = FloatArray(sorted.size) { sorted[it].second.toFloat() }
        )
    }

    /** 6 significant digits -- matches the Python serialisation口径 (§7.2). */
    private fun sig6(v: Float): Double {
        val d = v.toDouble()
        if (!d.isFinite() || d == 0.0) return d
        val exponent = floor(log10(abs(d)))
        val factor = 10.0.pow(5.0 - exponent)
        return Math.round(d * factor) / factor
    }

    fun toJson(r: SpdCalibrationResult): String = runCatching {
        val o = JSONObject()
        o.put("version", 3)
        fun ja(a: FloatArray) = JSONArray().apply { for (v in a) put(v.toDouble()) }
        /** Mode B arrays: 6 significant digits keeps the CursorWindow footprint small. */
        fun ja6(a: FloatArray?) = JSONArray().apply { if (a != null) for (v in a) put(sig6(v)) }
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
        o.put("crossingBG", r.crossingBG.toDouble()); o.put("crossingGR", r.crossingGR.toDouble())
        o.put("agreementPct", r.agreementPct.toDouble())
        fun jp(p: Pair<Float, Float>) = JSONArray().put(p.first.toDouble()).put(p.second.toDouble())
        o.put("peakR", jp(r.peakRespR)); o.put("peakG", jp(r.peakRespG)); o.put("peakB", jp(r.peakRespB))
        o.put("fusedPeak", jp(r.fusedPeak))
        o.put("exposure", r.exposureSeconds); o.put("frames", r.frames); o.put("spdSource", r.spdSource)
        o.put("rbSwapped", r.rbSwapped)
        // ---- v2.2.6 traceability ----
        if (!r.slope.isNaN()) o.put("slope", r.slope)
        if (!r.intercept.isNaN()) o.put("intercept", r.intercept)
        if (r.gErrorNm != null) o.put("gErrorNm", r.gErrorNm)
        if (r.xRoiStart != null) o.put("xRoiStart", r.xRoiStart)
        if (r.xRoiEnd != null) o.put("xRoiEnd", r.xRoiEnd)
        if (r.lampR != null && r.lampR.size == r.wavelengths.size) o.put("lampR", ja6(r.lampR))
        if (r.lampG != null && r.lampG.size == r.wavelengths.size) o.put("lampG", ja6(r.lampG))
        if (r.lampB != null && r.lampB.size == r.wavelengths.size) o.put("lampB", ja6(r.lampB))
        if (r.spdRefWl != null && r.spdRefVal != null && r.spdRefWl.size == r.spdRefVal.size) {
            o.put("spdRefWl", ja6(r.spdRefWl))
            o.put("spdRefVal", ja6(r.spdRefVal))
        }
        o.toString()
    }.getOrDefault("{}")

    /**
     * Strict parse. Any structural problem (missing key, wrong length,
     * non-finite value, degenerate axis) throws [IllegalArgumentException]
     * instead of silently degrading to zero-filled arrays (DEF-A03).
     */
    fun fromJsonStrict(s: String): SpdCalibrationResult {
        val o = JSONObject(s)
        val version = o.optInt("version", 1)
        val legacy = version < 2

        val wlArray = o.optJSONArray("wavelengths")
            ?: throw IllegalArgumentException("SPD payload is missing 'wavelengths'")
        val n = wlArray.length()
        require(n >= 2) { "SPD payload has $n wavelength(s); at least 2 are required" }

        /** Required float array: present, right length, all finite. */
        fun rfa(k: String): FloatArray {
            val a = o.optJSONArray(k)
                ?: throw IllegalArgumentException("SPD payload is missing '$k'")
            if (a.length() != n) {
                throw IllegalArgumentException("'$k' has ${a.length()} entries, expected $n")
            }
            return FloatArray(n) { i ->
                val v = a.getDouble(i).toFloat()
                if (!v.isFinite()) throw IllegalArgumentException("'$k'[$i] is not finite")
                v
            }
        }

        /** Optional float array (v1 payloads predate it): absent -> zeros. */
        fun ofa(k: String): FloatArray = if (o.has(k)) rfa(k) else FloatArray(n)

        /** Optional float array with a free length (the reference SPD grid). */
        fun freeFa(k: String): FloatArray? {
            val a = o.optJSONArray(k) ?: return null
            return FloatArray(a.length()) { i ->
                val v = a.getDouble(i).toFloat()
                if (!v.isFinite()) throw IllegalArgumentException("'$k'[$i] is not finite")
                v
            }
        }

        val wavelengths = rfa("wavelengths")
        // L1 -- structural: strictly monotonic (either direction) and finite.
        val increasing = wavelengths[0] < wavelengths[n - 1]
        for (i in 1 until n) {
            val ok = if (increasing) wavelengths[i] > wavelengths[i - 1] else wavelengths[i] < wavelengths[i - 1]
            require(ok) { "SPD wavelengths must be strictly monotonic (index $i)" }
        }

        val respR = rfa("respR"); val respG = rfa("respG"); val respB = rfa("respB")
        // L1 -- structural: a payload with no usable response is rejected instead
        // of being applied as a no-op.
        var nonEmpty = 0
        for (i in 0 until n) if (respR[i] != 0f || respG[i] != 0f || respB[i] != 0f) nonEmpty++
        require(nonEmpty >= MIN_NON_EMPTY_POINTS) {
            "SPD payload has only $nonEmpty non-zero response point(s); at least $MIN_NON_EMPTY_POINTS are required"
        }

        val posR = if (legacy) ofa("posR") else rfa("posR")
        val posG = if (legacy) ofa("posG") else rfa("posG")
        val posB = if (legacy) ofa("posB") else rfa("posB")
        val corrR = rfa("corrR"); val corrG = rfa("corrG"); val corrB = rfa("corrB")
        val wB = if (legacy) ofa("wB") else rfa("wB")
        val wG = if (legacy) ofa("wG") else rfa("wG")
        val wR = if (legacy) ofa("wR") else rfa("wR")
        val fused = rfa("fused")

        val maskArray = o.optJSONArray("mask")
            ?: throw IllegalArgumentException("SPD payload is missing 'mask'")
        require(maskArray.length() == n) {
            "'mask' has ${maskArray.length()} entries, expected $n"
        }
        val mask = BooleanArray(n) { maskArray.getInt(it) == 1 }

        fun pa(k: String): Pair<Float, Float> {
            val a = o.optJSONArray(k)
                ?: throw IllegalArgumentException("SPD payload is missing '$k'")
            return a.getDouble(0).toFloat() to a.getDouble(1).toFloat()
        }
        // v2 renamed handoffBG/GR -> crossingBG/GR; keep reading the old keys.
        val readBG = o.optDouble("crossingBG", o.optDouble("handoffBG", DEFAULT_CROSSING_BG.toDouble())).toFloat()
        val readGR = o.optDouble("crossingGR", o.optDouble("handoffGR", DEFAULT_CROSSING_GR.toDouble())).toFloat()

        val spdRefWl = freeFa("spdRefWl")
        val spdRefVal = freeFa("spdRefVal")
        if (spdRefWl != null && spdRefVal != null) {
            require(spdRefWl.size >= 2 && spdRefWl.size == spdRefVal.size) {
                "SPD reference grid is inconsistent (${spdRefWl.size} vs ${spdRefVal.size} entries)"
            }
        }

        fun lampOf(k: String): FloatArray? = freeFa(k)?.also {
            require(it.size == n) { "'$k' has ${it.size} entries, expected $n" }
        }

        return SpdCalibrationResult(
            wavelengths, respR, respG, respB, posR, posG, posB,
            corrR, corrG, corrB, wB, wG, wR, fused, mask,
            o.getDouble("bandLo").toFloat() to o.getDouble("bandHi").toFloat(),
            o.getInt("maskedCount"), o.getInt("totalCount"), o.getDouble("thresholdRatio").toFloat(),
            readBG, readGR,
            o.optDouble("agreementPct", 0.0).toFloat(),
            pa("peakR"), pa("peakG"), pa("peakB"), pa("fusedPeak"), o.getDouble("exposure"), o.getInt("frames"),
            o.optString("spdSource", "preset"), o.optBoolean("rbSwapped", false),
            slope = o.optDouble("slope", Double.NaN),
            intercept = o.optDouble("intercept", Double.NaN),
            gErrorNm = if (o.has("gErrorNm")) o.optDouble("gErrorNm") else null,
            xRoiStart = if (o.has("xRoiStart")) o.optInt("xRoiStart") else null,
            xRoiEnd = if (o.has("xRoiEnd")) o.optInt("xRoiEnd") else null,
            lampR = if (version >= 3) lampOf("lampR") else null,
            lampG = if (version >= 3) lampOf("lampG") else null,
            lampB = if (version >= 3) lampOf("lampB") else null,
            spdRefWl = if (version >= 3) spdRefWl else null,
            spdRefVal = if (version >= 3) spdRefVal else null
        )
    }

    /** Lenient entry point kept for existing callers: a broken payload -> null. */
    fun fromJson(s: String): SpdCalibrationResult? = runCatching { fromJsonStrict(s) }.getOrNull()

    /** L3: classify a drift in nm. Never fatal. */
    fun driftLevel(driftNm: Float?): DriftLevel = when {
        driftNm == null || driftNm.isNaN() -> DriftLevel.UNKNOWN
        driftNm <= DRIFT_WARN_NM -> DriftLevel.OK
        driftNm <= DRIFT_SEVERE_NM -> DriftLevel.WARN
        else -> DriftLevel.SEVERE
    }

    /**
     * L2 -- coverage (hard): is the CURRENT pixel axis fully inside the range the
     * preset covers? `null` means "covered".
     *
     * Only meaningful after a Mode B re-computation has been attempted, because a
     * re-computed preset owns the current wavelength axis by construction.
     */
    fun coverageError(
        preset: SpdCalibrationResult,
        slope: Double,
        intercept: Double,
        currentN: Int
    ): String? {
        if (preset.wavelengths.size < 2 || currentN < 2) return null
        if (!slope.isFinite() || slope == 0.0 || !intercept.isFinite()) return null
        var lo = Double.POSITIVE_INFINITY
        var hi = Double.NEGATIVE_INFINITY
        for (i in 0 until currentN) {
            val l = (i - intercept) / slope
            if (l < lo) lo = l
            if (l > hi) hi = l
        }
        val pLo = preset.wavelengths.minOrNull()?.toDouble() ?: return null
        val pHi = preset.wavelengths.maxOrNull()?.toDouble() ?: return null
        return if (lo < pLo || hi > pHi) {
            "current wavelength axis [%.1f, %.1f] nm is not covered by the preset [%.1f, %.1f] nm"
                .format(lo, hi, pLo, pHi)
        } else null
    }
}

/**
 * L3 -- drift between the mapping stored inside a preset and the mapping that is
 * active right now, in nm (max |λ_current(px) − λ_preset(px)| over five
 * quintiles of the pixel axis).
 *
 * Returns `null` when either side has no usable mapping.
 */
fun SpdCalibrationResult.mappingDriftNm(slope: Double, intercept: Double): Float? {
    if (!this.slope.isFinite() || !this.intercept.isFinite()) return null
    if (!slope.isFinite() || !intercept.isFinite()) return null
    if (this.slope == 0.0 || slope == 0.0) return null
    val n = if (totalCount >= 2) totalCount else wavelengths.size
    if (n < 2) return null
    var worst = 0.0
    for (f in doubleArrayOf(0.10, 0.30, 0.50, 0.70, 0.90)) {
        val px = f * (n - 1)
        val current = (px - intercept) / slope
        val preset = (px - this.intercept) / this.slope
        worst = max(worst, abs(current - preset))
    }
    return worst.toFloat()
}

/**
 * Mode B: rebuild the response from the UN-CONTAMINATED inputs carried by the
 * preset (raw lamp profiles + the reference SPD grid) using the CURRENT mapping.
 *
 * The result is identical to re-running Step 2 with the current mapping, so the
 * `SPD(λ−δ)/SPD(λ)` multiplicative error baked in at save time disappears.
 *
 * Returns `null` when the preset does not carry the raw inputs (legacy payload)
 * or when the re-computation turns out degenerate -- the caller then falls back
 * to Mode A.
 */
fun SpdCalibrationResult.recomputeWith(slope: Double, intercept: Double): SpdCalibrationResult? {
    val rawR = lampR ?: return null
    val rawG = lampG ?: return null
    val rawB = lampB ?: return null
    val refWl = spdRefWl ?: return null
    val refVal = spdRefVal ?: return null
    val n = wavelengths.size
    if (n < 2 || rawR.size != n || rawG.size != n || rawB.size != n) return null
    if (refWl.size < 2 || refVal.size != refWl.size) return null
    if (!slope.isFinite() || slope == 0.0 || !intercept.isFinite()) return null
    if (posR.size != n || posG.size != n || posB.size != n) return null

    val lamRaw = FloatArray(n) { i -> ((i - intercept) / slope).toFloat() }
    val reversed = lamRaw[0] > lamRaw[n - 1]
    // `lam` is always increasing, by construction.
    val lam = if (reversed) lamRaw.reversed().toFloatArray() else lamRaw
    // The stored lamp profiles are already oriented like the stored
    // `wavelengths`; only flip them if the stored axis ran the other way.
    val storedIncreasing = wavelengths.size < 2 || wavelengths[0] < wavelengths[n - 1]
    val flip = !storedIncreasing
    fun ord(a: FloatArray) = if (flip) a.reversed().toFloatArray() else a
    val lR = ord(rawR); val lG = ord(rawG); val lB = ord(rawB)
    // The stored pos profiles share the orientation of the stored `wavelengths`.
    val pR = ord(posR); val pG = ord(posG); val pB = ord(posB)

    val sorted = refWl.indices
        .map { refWl[it].toDouble() to refVal[it].toDouble() }
        .sortedBy { it.first }
    val maxSpd = sorted.maxOf { it.second }
    if (maxSpd <= 0.0) return null

    fun spdAt(l: Double): Double {
        if (l < sorted.first().first || l > sorted.last().first) return 0.0
        var lo = 0; var hi = sorted.lastIndex
        while (lo < hi - 1) { val mid = (lo + hi) / 2; if (sorted[mid].first <= l) lo = mid else hi = mid }
        val (x0, y0) = sorted[lo]; val (x1, y1) = sorted[hi]
        return if (x1 == x0) y0 else y0 + (y1 - y0) * (l - x0) / (x1 - x0)
    }

    val band = SpectralResponseCalibrator.BAND_LO_NM..SpectralResponseCalibrator.BAND_HI_NM
    val mask = BooleanArray(n) { i ->
        val l = lam[i].toDouble()
        l in band && spdAt(l) > thresholdRatio * maxSpd
    }
    val maskedCount = mask.count { it }
    if (maskedCount < 10) return null

    val respR = FloatArray(n); val respG = FloatArray(n); val respB = FloatArray(n)
    for (i in 0 until n) {
        if (!mask[i]) continue
        val s = maxOf(1e-6f, spdAt(lam[i].toDouble()).toFloat())
        respR[i] = lR[i] / s; respG[i] = lG[i] / s; respB[i] = lB[i] / s
    }

    val derived = SpectralResponseCalibrator.derive(lam, mask, respR, respG, respB, pR, pG, pB)
    val order = (0 until n).filter { mask[it] }.sortedBy { lam[it] }

    return copy(
        wavelengths = lam,
        respR = respR, respG = respG, respB = respB,
        corrR = derived.corrR, corrG = derived.corrG, corrB = derived.corrB,
        wB = derived.wB, wG = derived.wG, wR = derived.wR,
        fused = derived.fused,
        mask = mask,
        effectiveBandNm = (order.firstOrNull()?.let { lam[it] } ?: 0f) to
                (order.lastOrNull()?.let { lam[it] } ?: 0f),
        maskedCount = maskedCount,
        crossingBG = derived.crossingBG, crossingGR = derived.crossingGR,
        agreementPct = derived.agreementPct,
        peakRespR = derived.peakRespR, peakRespG = derived.peakRespG, peakRespB = derived.peakRespB,
        fusedPeak = derived.fusedPeak,
        slope = slope, intercept = intercept
    )
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
