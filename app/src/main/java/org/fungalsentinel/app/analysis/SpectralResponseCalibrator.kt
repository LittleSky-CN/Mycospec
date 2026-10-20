package org.fungalsentinel.app.analysis

import android.os.Build
import org.fungalsentinel.app.BuildConfig
import org.fungalsentinel.app.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * L3 drift classification for a reloaded SPD preset
 * (preset_parity_design.md §1.2 / FSSA core/calibration.py `drift_level`).
 *
 * `UNKNOWN` means the preset carries no mapping to compare against.
 * A drift NEVER rejects a preset -- it only drives a WARN / SEVERE hint.
 */
enum class DriftLevel { OK, WARN, SEVERE, UNKNOWN }

/**
 * A finished Step 2 calibration.
 *
 * Since schema v3 every curve is indexed by WAVELENGTH on the canonical
 * uniform grid ([SpectralResponseCalibrator.canonicalGrid]), never by pixels:
 * `wavelengths.size == gridPoints == 401`. Nothing here depends on the
 * pixel<->nm mapping any more, which is what makes a preset portable between
 * devices and between the Android and the desktop (FSSA) build.
 *
 * `slope` / `intercept` / `sensorHeight` survive ONLY as diagnostics for the
 * L3 drift hint. They must never be used to rebuild or re-index a curve.
 */
data class SpdCalibrationResult(
    val wavelengths: FloatArray,
    val respR: FloatArray, val respG: FloatArray, val respB: FloatArray,
    val posR: FloatArray, val posG: FloatArray, val posB: FloatArray,
    val corrR: FloatArray, val corrG: FloatArray, val corrB: FloatArray,
    val wB: FloatArray, val wG: FloatArray, val wR: FloatArray,
    val fused: FloatArray,
    val mask: BooleanArray,
    /** Actually usable sub-range of the grid (min/max masked wavelength). */
    val effectiveBandNm: Pair<Float, Float>,
    /** The analysis band requested when the calibration was made (`valid_range`). */
    val validRangeNm: Pair<Float, Float> = SpectralResponseCalibrator.BAND_LO_NM.toFloat() to
            SpectralResponseCalibrator.BAND_HI_NM.toFloat(),
    /** Number of masked grid points == the number of usable points. */
    val maskedCount: Int,
    /** Length of every grid-indexed array (401 for the canonical grid). NOT a pixel count. */
    val gridPoints: Int,
    val thresholdRatio: Float,
    // Diagnostic only: wavelengths where two adjacent channels have EQUAL
    // responsivity. These do NOT drive the fusion weights.
    val crossingBG: Float, val crossingGR: Float,
    val agreementPct: Float,
    val peakRespR: Pair<Float, Float>, val peakRespG: Pair<Float, Float>, val peakRespB: Pair<Float, Float>,
    val fusedPeak: Pair<Float, Float>,
    val exposureSeconds: Double, val frames: Int, val spdSource: String,
    val rbSwapped: Boolean = false,

    // ===== v3 traceability (diagnostic only) =====
    // All defaulted so existing construction sites keep compiling.
    /** Mapping the response was baked with: px/nm. NaN when unknown. */
    val slope: Double = Double.NaN,
    /** Mapping the response was baked with: px. NaN when unknown. */
    val intercept: Double = Double.NaN,
    /** Step 1 G-point validation residual (nm) at calibration time. */
    val gErrorNm: Double? = null,
    /** X-ROI (px, closed interval) used while decoding the lamp frames. */
    val xRoiStart: Int? = null, val xRoiEnd: Int? = null,
    /** Pixel rows on the sensor at calibration time. Diagnostic only. */
    val sensorHeight: Int? = null
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
    /** L1 structural floor: a payload with fewer non-zero response points is unusable. */
    private const val MIN_NON_EMPTY_POINTS = 20
    /** Valid spectral band (nm). Only this band has to be covered by a preset. */
    const val BAND_LO_NM = 430.0
    const val BAND_HI_NM = 660.0

    // ===== schema v3: the canonical wavelength grid =======================
    /** Only payloads carrying this schema version are loadable; anything else is REJECTED. */
    const val SPD_SCHEMA_VERSION = 3
    /** Payload discriminator; must be [SPD_PRESET_TYPE]. */
    const val SPD_PRESET_TYPE = "SPD"
    const val SPD_AXIS_KIND = "uniform_wavelength"
    const val SPD_GRID_START_NM = 380.0
    const val SPD_GRID_STOP_NM = 780.0
    const val SPD_GRID_STEP_NM = 1.0
    /** Tolerance when checking a payload's `wavelengths` against its declared `axis` (nm). */
    private const val GRID_TOL_NM = 1e-3

    /** The one axis every v3 preset is indexed by: 380..780 nm step 1 nm (401 points). */
    fun canonicalGrid(): FloatArray {
        val points = Math.round((SPD_GRID_STOP_NM - SPD_GRID_START_NM) / SPD_GRID_STEP_NM).toInt() + 1
        return FloatArray(points) { i -> (SPD_GRID_START_NM + i * SPD_GRID_STEP_NM).toFloat() }
    }

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

        // Relative floor protection: downstream division uses "5% of that channel's
        // response peak" as a floor, so division cannot blow up at the mask edges
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

    /**
     * Linear resampling of a pixel-indexed curve onto the canonical wavelength
     * grid. Outside the source range a point is 0 -- the same convention as
     * scipy `interp1d(bounds_error=False, fill_value=0.0)` on the Python side.
     *
     * `srcWl` must be strictly increasing (which [calibrate] guarantees).
     */
    internal fun resampleToGrid(srcWl: FloatArray, src: FloatArray, grid: FloatArray): FloatArray {
        val out = FloatArray(grid.size)
        val n = srcWl.size
        if (n == 0) return out
        for (g in grid.indices) {
            val x = grid[g]
            if (x < srcWl[0] || x > srcWl[n - 1]) continue          // outside -> 0
            var lo = 0; var hi = n - 1
            while (lo < hi - 1) {
                val mid = (lo + hi) / 2
                if (srcWl[mid] <= x) lo = mid else hi = mid
            }
            val span = srcWl[hi] - srcWl[lo]
            val t = if (span == 0f) 0f else (x - srcWl[lo]) / span
            out[g] = src[lo] + (src[hi] - src[lo]) * t
        }
        return out
    }

    /**
     * Full Step 2: everything is computed per pixel first (because the frames
     * are pixel-indexed) and then resampled onto [canonicalGrid], so the stored
     * preset owns its own wavelength axis and no longer depends on the mapping.
     */
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
        if (mask.count { it } < 10) return null

        // Compute the raw response
        val respR = FloatArray(n); val respG = FloatArray(n); val respB = FloatArray(n)
        for (i in 0 until n) {
            if (!mask[i]) continue
            val s = max(EPS, spdAt(lam[i].toDouble()).toFloat())
            respR[i] = lampR[i] / s; respG[i] = lampG[i] / s; respB[i] = lampB[i] / s
        }

        val derived = derive(lam, mask, respR, respG, respB, posR, posG, posB)

        // ===== v3: leave the pixel domain; index everything by wavelength ====
        val grid = canonicalGrid()
        val gRespR = resampleToGrid(lam, respR, grid)
        val gRespG = resampleToGrid(lam, respG, grid)
        val gRespB = resampleToGrid(lam, respB, grid)
        val gPosR = resampleToGrid(lam, posR, grid)
        val gPosG = resampleToGrid(lam, posG, grid)
        val gPosB = resampleToGrid(lam, posB, grid)
        val gCorrR = resampleToGrid(lam, derived.corrR, grid)
        val gCorrG = resampleToGrid(lam, derived.corrG, grid)
        val gCorrB = resampleToGrid(lam, derived.corrB, grid)
        val gWB = resampleToGrid(lam, derived.wB, grid)
        val gWG = resampleToGrid(lam, derived.wG, grid)
        val gWR = resampleToGrid(lam, derived.wR, grid)
        val gFused = resampleToGrid(lam, derived.fused, grid)
        val gMaskSrc = FloatArray(n) { i -> if (mask[i]) 1f else 0f }
        val gMaskDense = resampleToGrid(lam, gMaskSrc, grid)
        val gMask = BooleanArray(grid.size) { i -> gMaskDense[i] > 0.5f }

        val maskedIdx = (0 until grid.size).filter { gMask[it] }
        if (maskedIdx.size < MIN_NON_EMPTY_POINTS) return null
        val gMaskedCount = maskedIdx.size
        val gBand = (maskedIdx.firstOrNull()?.let { grid[it] } ?: 0f) to
                (maskedIdx.lastOrNull()?.let { grid[it] } ?: 0f)

        val exposure = lampFrames.map { it.exposureSeconds }.filter { it > 0 }
            .average().let { if (it.isNaN()) 0.0 else it }

        return SpdCalibrationResult(
            wavelengths = grid,
            respR = gRespR, respG = gRespG, respB = gRespB,
            posR = gPosR, posG = gPosG, posB = gPosB,
            corrR = gCorrR, corrG = gCorrG, corrB = gCorrB,
            wB = gWB, wG = gWG, wR = gWR, fused = gFused,
            mask = gMask,
            effectiveBandNm = gBand,
            validRangeNm = bandNm.start.toFloat() to bandNm.endInclusive.toFloat(),
            maskedCount = gMaskedCount, gridPoints = grid.size, thresholdRatio = thresholdRatio,
            crossingBG = derived.crossingBG, crossingGR = derived.crossingGR,
            agreementPct = derived.agreementPct,
            peakRespR = derived.peakRespR, peakRespG = derived.peakRespG, peakRespB = derived.peakRespB,
            fusedPeak = derived.fusedPeak,
            exposureSeconds = exposure, frames = lampFrames.size, spdSource = spdSource,
            rbSwapped = false,
            // ---- v3 traceability: diagnostic ONLY ----
            slope = slope, intercept = intercept, gErrorNm = gErrorNm,
            xRoiStart = lampFrames[0].xRoiStart, xRoiEnd = lampFrames[0].xRoiEnd,
            sensorHeight = lampFrames[0].height
        )
    }

    private fun JSONObject.reqString(k: String): String {
        if (!has(k) || isNull(k)) throw IllegalArgumentException("SPD payload is missing '$k'")
        return optString(k) ?: ""
    }

    private fun JSONObject.reqDouble(k: String): Double {
        if (!has(k) || isNull(k)) throw IllegalArgumentException("SPD payload is missing '$k'")
        val v = get(k)
        if (v !is Number) throw IllegalArgumentException("'$k' must be a number, got ${v.javaClass.simpleName}")
        val d = v.toDouble()
        if (!d.isFinite()) throw IllegalArgumentException("'$k' is not finite")
        return d
    }

    private fun JSONObject.reqInt(k: String): Int {
        val d = reqDouble(k)
        if (d != Math.floor(d)) throw IllegalArgumentException("'$k' must be an integer, got $d")
        return d.toInt()
    }

    private fun JSONObject.reqBool(k: String): Boolean {
        if (!has(k) || isNull(k)) throw IllegalArgumentException("SPD payload is missing '$k'")
        val v = get(k)
        return when (v) {
            is Boolean -> v
            is Number -> v.toDouble() != 0.0
            is String -> v.equals("true", ignoreCase = true)
            else -> throw IllegalArgumentException("'$k' must be a boolean, got ${v.javaClass.simpleName}")
        }
    }

    /** Optional scalar: absent / null / non-numeric -> [default]. Never fatal. */
    private fun JSONObject.optScalar(k: String, default: Double): Double {
        if (!has(k) || isNull(k)) return default
        val v = get(k)
        val d = (v as? Number)?.toDouble() ?: return default
        return if (d.isFinite()) d else default
    }

    private fun JSONObject.reqPairNm(k: String): Pair<Float, Float> {
        val a = optJSONArray(k)
            ?: throw IllegalArgumentException("SPD payload is missing '$k'")
        require(a.length() == 2) { "'$k' must hold exactly 2 entries ([lo, hi] in nm), got ${a.length()}" }
        return a.getDouble(0).toFloat() to a.getDouble(1).toFloat()
    }

    private fun JSONObject.reqPairInt(k: String): Pair<Int, Int> {
        val a = optJSONArray(k)
            ?: throw IllegalArgumentException(
                if (has(k)) "'$k' must hold [start, end] in px, got null" else "SPD payload is missing '$k'"
            )
        require(a.length() == 2) { "'$k' must hold exactly 2 entries ([start, end] in px), got ${a.length()}" }
        return a.getInt(0) to a.getInt(1)
    }

    /**
     * Purely diagnostic scalars: `null` (or absent) is accepted and reported back
     * as null. These carry no quantitative meaning, so a side whose pipeline does
     * not track them must still be able to exchange presets. Everything that can
     * influence a result stays strict.
     */
    private fun JSONObject.optPairIntOrNull(k: String): Pair<Int, Int>? =
        if (has(k) && !isNull(k)) reqPairInt(k) else null

    private fun JSONObject.optIntOrNull(k: String): Int? =
        if (has(k) && !isNull(k)) reqInt(k) else null

    private fun JSONObject.optPairNm(k: String): Pair<Float, Float>? =
        if (has(k) && !isNull(k)) reqPairNm(k) else null

    private fun JSONObject.reqFloatArray(k: String, n: Int): FloatArray {
        val a = optJSONArray(k)
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

    /** Optional array: absent -> null; present but the wrong length -> rejected. */
    private fun JSONObject.optFloatArray(k: String, n: Int): FloatArray? =
        if (has(k) && !isNull(k)) reqFloatArray(k, n) else null

    /** ISO-8601 UTC timestamp for the `created_at` field. */
    private fun isoUtcNow(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date())

    /**
     * Serialise [r] as a schema-v3 SPD preset.
     *
     * The file-level metadata (`device_model` / `app_version` / `created_at`)
     * describes WHO wrote the file, not the calibration itself, so it is passed
     * in by the caller. Unit tests must pass them explicitly: `Build.MODEL`
     * cannot be evaluated outside a real Android runtime.
     */
    @JvmOverloads
    fun toJson(
        r: SpdCalibrationResult,
        deviceModel: String = Build.MODEL,
        appVersion: String = BuildConfig.VERSION_NAME,
        createdAt: String = isoUtcNow()
    ): String = runCatching {
        val o = JSONObject()
        o.put("schema_version", SPD_SCHEMA_VERSION)
        o.put("app_version", appVersion)
        o.put("preset_type", SPD_PRESET_TYPE)
        o.put("created_at", createdAt)
        o.put("device_model", deviceModel)
        o.put(
            "axis", JSONObject()
                .put("kind", SPD_AXIS_KIND)
                .put("start_nm", SPD_GRID_START_NM)
                .put("stop_nm", SPD_GRID_STOP_NM)
                .put("step_nm", SPD_GRID_STEP_NM)
        )
        fun ja(a: FloatArray) = JSONArray().apply { for (v in a) put(v.toDouble()) }
        o.put("wavelengths", ja(r.wavelengths))
        o.put("response_r", ja(r.respR))
        o.put("response_g", ja(r.respG))
        o.put("response_b", ja(r.respB))
        // Optional keys, but Android always writes them: after Mode B was removed
        // there is no raw lamp spectrum to rebuild them from, and report
        // Figure 4a/4b/4c/4d are drawn straight from these arrays.
        o.put("pos_r", ja(r.posR)); o.put("pos_g", ja(r.posG)); o.put("pos_b", ja(r.posB))
        o.put("corr_r", ja(r.corrR)); o.put("corr_g", ja(r.corrG)); o.put("corr_b", ja(r.corrB))
        o.put("weight_b", ja(r.wB)); o.put("weight_g", ja(r.wG)); o.put("weight_r", ja(r.wR))
        o.put("fused", ja(r.fused))
        o.put("mask", JSONArray().apply { for (m in r.mask) put(if (m) 1 else 0) })
        fun jp(p: Pair<Float, Float>) =
            JSONArray().put(p.first.toDouble()).put(p.second.toDouble())
        o.put("valid_range", jp(r.validRangeNm))
        o.put("effective_band", jp(r.effectiveBandNm))
        o.put("spd_threshold_ratio", r.thresholdRatio.toDouble())
        o.put("masked_count", r.maskedCount)
        o.put("grid_points", r.gridPoints)
        o.put("crossing_bg", r.crossingBG.toDouble())
        o.put("crossing_gr", r.crossingGR.toDouble())
        o.put("agreement_pct", r.agreementPct.toDouble())
        o.put("peak_r", jp(r.peakRespR)); o.put("peak_g", jp(r.peakRespG)); o.put("peak_b", jp(r.peakRespB))
        o.put("fused_peak", jp(r.fusedPeak))
        // Diagnostic-only mapping: required by the v3 schema, so it is only
        // omitted when the calibration genuinely has no mapping to report.
        if (r.slope.isFinite()) o.put("slope", r.slope)
        if (r.intercept.isFinite()) o.put("intercept", r.intercept)
        o.put("g_error_nm", if (r.gErrorNm != null && r.gErrorNm.isFinite()) r.gErrorNm else JSONObject.NULL)
        o.put("x_roi", if (r.xRoiStart != null && r.xRoiEnd != null)
            JSONArray().put(r.xRoiStart).put(r.xRoiEnd) else JSONObject.NULL)
        o.put("sensor_height", if (r.sensorHeight != null) r.sensorHeight else JSONObject.NULL)
        o.put("rb_swapped", r.rbSwapped)
        o.put("exposure_seconds", r.exposureSeconds)
        o.put("frames", r.frames)
        o.put("spd_source", r.spdSource)
        o.toString()
    }.getOrDefault("{}")

    /**
     * Upgrade a pre-v3 (pixel-indexed, camelCase) payload onto [canonicalGrid].
     *
     * Presets written by builds before schema v3 index every curve by PIXEL and
     * name their keys `respR` / `posR` / `wB` / `corrR` / `fused` / `bandLo` /
     * `totalCount`. They still carry a usable calibration, so instead of being
     * rejected they are linearly resampled onto the wavelength grid -- the same
     * operation [calibrate] applies to live data. The stored curves are carried
     * over point by point; only the scalars that cannot survive resampling
     * (mask, effective band, peaks, masked count) are recomputed from the
     * resampled arrays, so an upgraded preset is self-consistent rather than a
     * mixture of old pixel counts and new grid values.
     *
     * Returns null when the payload is not a recognisable legacy preset, which
     * lets the caller keep its strict "re-run Step 2" error.
     */
    private fun upgradeLegacyPayload(o: JSONObject): SpdCalibrationResult? {
        val wlArray = o.optJSONArray("wavelengths") ?: return null
        val n = wlArray.length()
        if (n < MIN_NON_EMPTY_POINTS) return null

        val rawLam = FloatArray(n) { i -> wlArray.getDouble(i).toFloat() }
        if (rawLam.any { !it.isFinite() }) return null
        val descending = rawLam[0] > rawLam[n - 1]
        val lam = if (descending) rawLam.reversedArray() else rawLam
        for (i in 1 until n) if (lam[i] <= lam[i - 1]) return null   // unusable axis

        /** A legacy array re-ordered to match [lam]; first present key wins. */
        fun arr(vararg keys: String): FloatArray? {
            for (k in keys) {
                val a = o.optJSONArray(k) ?: continue
                if (a.length() != n) return null
                val v = FloatArray(n) { i -> a.getDouble(i).toFloat() }
                if (v.any { !it.isFinite() }) return null
                return if (descending) v.reversedArray() else v
            }
            return null
        }

        val respR = arr("respR", "resp_r") ?: return null
        val respG = arr("respG", "resp_g") ?: return null
        val respB = arr("respB", "resp_b") ?: return null

        val grid = canonicalGrid()
        fun onGrid(a: FloatArray?) = if (a == null) FloatArray(grid.size) else resampleToGrid(lam, a, grid)

        /** Scalar readers: the legacy camelCase key first, then the v3 spelling. */
        fun num(vararg keys: String): Double? {
            for (k in keys) {
                if (!o.has(k) || o.isNull(k)) continue
                val d = o.optDouble(k, Double.NaN)
                if (d.isFinite()) return d
            }
            return null
        }
        fun int(vararg keys: String): Int? {
            for (k in keys) if (o.has(k) && !o.isNull(k)) return o.optInt(k)
            return null
        }
        fun text(vararg keys: String): String? {
            for (k in keys) {
                val s = o.optString(k, "")
                if (s.isNotBlank()) return s
            }
            return null
        }
        fun flag(vararg keys: String): Boolean {
            for (k in keys) if (o.has(k) && !o.isNull(k)) return o.optBoolean(k, false)
            return false
        }

        val maskSrc = arr("mask") ?: FloatArray(n) { 1f }
        val maskDense = resampleToGrid(lam, maskSrc, grid)
        val mask = BooleanArray(grid.size) { maskDense[it] > 0.5f }
        val maskedIdx = (0 until grid.size).filter { mask[it] }
        if (maskedIdx.size < MIN_NON_EMPTY_POINTS) return null

        fun peaksOf(a: FloatArray): Pair<Float, Float> {
            var best = -1
            var value = Float.NEGATIVE_INFINITY
            for (i in a.indices) if (mask[i] && a[i] > value) { value = a[i]; best = i }
            return if (best < 0) 0f to 0f else grid[best] to value
        }

        val gRespR = onGrid(respR); val gRespG = onGrid(respG); val gRespB = onGrid(respB)
        val gFused = onGrid(arr("fused"))
        val xRoi = o.optJSONArray("x_roi")

        val bandLo = num("bandLo")
        val bandHi = num("bandHi")
        val validRange = if (bandLo != null && bandHi != null) {
            bandLo.toFloat() to bandHi.toFloat()
        } else {
            BAND_LO_NM.toFloat() to BAND_HI_NM.toFloat()
        }

        return SpdCalibrationResult(
            wavelengths = grid,
            respR = gRespR, respG = gRespG, respB = gRespB,
            posR = onGrid(arr("posR")), posG = onGrid(arr("posG")), posB = onGrid(arr("posB")),
            corrR = onGrid(arr("corrR")), corrG = onGrid(arr("corrG")), corrB = onGrid(arr("corrB")),
            wB = onGrid(arr("wB")), wG = onGrid(arr("wG")), wR = onGrid(arr("wR")),
            fused = gFused,
            mask = mask,
            effectiveBandNm = grid[maskedIdx.first()] to grid[maskedIdx.last()],
            validRangeNm = validRange,
            maskedCount = maskedIdx.size,
            gridPoints = grid.size,
            thresholdRatio = (num("thresholdRatio") ?: 0.1).toFloat(),
            crossingBG = (num("crossingBG") ?: DEFAULT_CROSSING_BG.toDouble()).toFloat(),
            crossingGR = (num("crossingGR") ?: DEFAULT_CROSSING_GR.toDouble()).toFloat(),
            agreementPct = (num("agreementPct") ?: 0.0).toFloat(),
            peakRespR = peaksOf(gRespR), peakRespG = peaksOf(gRespG), peakRespB = peaksOf(gRespB),
            fusedPeak = peaksOf(gFused),
            exposureSeconds = num("exposure", "exposure_seconds") ?: 0.0,
            frames = int("frames") ?: 0,
            spdSource = text("spdSource", "spd_source") ?: "legacy preset",
            rbSwapped = flag("rbSwapped", "rb_swapped"),
            slope = num("slope") ?: Double.NaN,
            intercept = num("intercept") ?: Double.NaN,
            gErrorNm = num("gErrorNm"),
            xRoiStart = int("xRoiStart") ?: (if (xRoi != null && xRoi.length() == 2) xRoi.optInt(0) else null),
            xRoiEnd = int("xRoiEnd") ?: (if (xRoi != null && xRoi.length() == 2) xRoi.optInt(1) else null),
            sensorHeight = int("sensor_height", "sensorHeight")
        )
    }

    /**
     * Strict parse of a schema-v3 SPD payload. Any structural problem -- wrong
     * schema version, missing key, wrong length, non-finite value, degenerate
     * or non-increasing axis -- throws [IllegalArgumentException] instead of
     * silently degrading to zero-filled arrays (DEF-A03).
     */
    fun fromJsonStrict(s: String): SpdCalibrationResult {
        val o = JSONObject(s)

        // ---- L0 -- schema gate ----
        // A payload without `schema_version` is a pre-v3 preset: pixel-indexed
        // camelCase keys (`respR`, `posR`, `bandLo`, `totalCount`, ...). Those
        // presets are still valid calibrations, so they are upgraded onto the
        // canonical grid instead of being rejected. Only a payload that is not
        // recognisable as a legacy preset falls through to the hard error.
        if (!o.has("schema_version") || o.isNull("schema_version")) {
            upgradeLegacyPayload(o)?.let { return it }
            throw IllegalArgumentException(
                "SPD payload carries no 'schema_version': this is an old-format preset. " +
                    "Re-run Step 2 or import a v3 SPD file."
            )
        }
        val schemaRaw = o.get("schema_version")
        require(schemaRaw is Number && schemaRaw.toDouble() == SPD_SCHEMA_VERSION.toDouble()) {
            "Unsupported SPD preset schema_version '$schemaRaw' (expected $SPD_SCHEMA_VERSION): " +
                "this is an old-format preset. Re-run Step 2 or import a v3 SPD file."
        }

        // ---- L0 -- file-level metadata (required by the v3 contract) ------
        val presetType = o.reqString("preset_type")
        require(presetType == SPD_PRESET_TYPE) {
            "SPD payload declares preset_type '$presetType', expected '$SPD_PRESET_TYPE'"
        }
        o.reqString("app_version")
        o.reqString("created_at")
        o.reqString("device_model")

        // ---- L1 -- the axis the payload is indexed by ---------------------
        val axis = o.optJSONObject("axis")
            ?: throw IllegalArgumentException("SPD payload is missing 'axis'")
        val kind = axis.reqString("kind")
        require(kind == SPD_AXIS_KIND) {
            "Unsupported SPD axis kind '$kind'; v3 presets must be '$SPD_AXIS_KIND'"
        }
        val startNm = axis.reqDouble("start_nm")
        val stopNm = axis.reqDouble("stop_nm")
        val stepNm = axis.reqDouble("step_nm")
        require(stepNm > 0.0) { "SPD axis 'step_nm' must be > 0, got $stepNm" }
        require(stopNm > startNm) { "SPD axis 'stop_nm' ($stopNm) must be greater than 'start_nm' ($startNm)" }

        val wlArray = o.optJSONArray("wavelengths")
            ?: throw IllegalArgumentException("SPD payload is missing 'wavelengths'")
        val n = wlArray.length()
        require(n >= 2) { "SPD payload has $n wavelength(s); at least 2 are required" }
        val wavelengths = FloatArray(n) { i ->
            val v = wlArray.getDouble(i).toFloat()
            if (!v.isFinite()) throw IllegalArgumentException("'wavelengths'[$i] is not finite")
            v
        }
        // L1: strictly INCREASING -- a descending payload is an old-format file.
        for (i in 1 until n) {
            require(wavelengths[i] > wavelengths[i - 1]) {
                "SPD wavelengths must be strictly increasing (index $i: ${wavelengths[i - 1]} -> ${wavelengths[i]} nm)"
            }
        }
        // L1: `wavelengths` must be the grid `axis` claims.
        val expectedN = Math.round((stopNm - startNm) / stepNm).toInt() + 1
        require(n == expectedN) {
            "'axis' declares $expectedN grid points (${startNm}..${stopNm} step $stepNm nm) but 'wavelengths' has $n"
        }
        for (i in 0 until n) {
            val want = startNm + i * stepNm
            require(abs(wavelengths[i].toDouble() - want) <= GRID_TOL_NM) {
                "'wavelengths'[$i] = ${wavelengths[i]} nm does not sit on the declared axis (expected $want nm)"
            }
        }

        val gridPoints = o.reqInt("grid_points")
        require(gridPoints == n) {
            "'grid_points' is $gridPoints but 'wavelengths' has $n entries"
        }

        /** Required float array: present, right length, all finite. */
        fun rfa(k: String) = o.reqFloatArray(k, n)
        /** Optional float array: absent -> zeros; present -> must be right. */
        fun ofa(k: String) = o.optFloatArray(k, n) ?: FloatArray(n)

        val respR = rfa("response_r"); val respG = rfa("response_g"); val respB = rfa("response_b")
        // L1 -- structural: a payload with no usable response is rejected instead
        // of being applied as a no-op.
        var nonEmpty = 0
        for (i in 0 until n) if (respR[i] != 0f || respG[i] != 0f || respB[i] != 0f) nonEmpty++
        require(nonEmpty >= MIN_NON_EMPTY_POINTS) {
            "SPD payload has only $nonEmpty non-zero response point(s); at least $MIN_NON_EMPTY_POINTS are required"
        }

        val posR = ofa("pos_r"); val posG = ofa("pos_g"); val posB = ofa("pos_b")
        val corrR = ofa("corr_r"); val corrG = ofa("corr_g"); val corrB = ofa("corr_b")
        val wB = ofa("weight_b"); val wG = ofa("weight_g"); val wR = ofa("weight_r")
        val fused = ofa("fused")

        val maskArray = o.optJSONArray("mask")
            ?: throw IllegalArgumentException("SPD payload is missing 'mask'")
        require(maskArray.length() == n) {
            "'mask' has ${maskArray.length()} entries, expected $n"
        }
        // 0/1 and true/false both accepted: numpy writes the former, Python's
        // json.dumps writes the latter.
        val mask = BooleanArray(n) { i ->
            when (val v = maskArray.get(i)) {
                is Boolean -> v
                is Number -> v.toDouble() != 0.0
                is String -> v.toDoubleOrNull()?.let { it != 0.0 }
                    ?: throw IllegalArgumentException("'mask'[$i] must be 0/1 or true/false, got \"$v\"")
                else -> throw IllegalArgumentException("'mask'[$i] must be 0/1 or true/false, got ${v.javaClass.simpleName}")
            }
        }

        val validRange = o.reqPairNm("valid_range")
        val effectiveBand = o.reqPairNm("effective_band")
        val thresholdRatio = o.reqDouble("spd_threshold_ratio").toFloat()
        val maskedCount = o.reqInt("masked_count")

        val slope = o.reqDouble("slope")
        val intercept = o.reqDouble("intercept")
        val gErrorNm = if (o.has("g_error_nm") && !o.isNull("g_error_nm")) o.reqDouble("g_error_nm") else null
        val xRoi = o.optPairIntOrNull("x_roi")
        val sensorHeight = o.optIntOrNull("sensor_height")
        val rbSwapped = o.reqBool("rb_swapped")

        val crossingBG = o.optScalar(k = "crossing_bg", default = DEFAULT_CROSSING_BG.toDouble()).toFloat()
        val crossingGR = o.optScalar(k = "crossing_gr", default = DEFAULT_CROSSING_GR.toDouble()).toFloat()
        val agreementPct = o.optScalar("agreement_pct", 0.0).toFloat()
        val peakRespR = o.optPairNm("peak_r") ?: (0f to 0f)
        val peakRespG = o.optPairNm("peak_g") ?: (0f to 0f)
        val peakRespB = o.optPairNm("peak_b") ?: (0f to 0f)
        val fusedPeak = o.optPairNm("fused_peak") ?: (0f to 0f)

        return SpdCalibrationResult(
            wavelengths = wavelengths,
            respR = respR, respG = respG, respB = respB,
            posR = posR, posG = posG, posB = posB,
            corrR = corrR, corrG = corrG, corrB = corrB,
            wB = wB, wG = wG, wR = wR, fused = fused,
            mask = mask,
            effectiveBandNm = effectiveBand,
            validRangeNm = validRange,
            maskedCount = maskedCount, gridPoints = gridPoints, thresholdRatio = thresholdRatio,
            crossingBG = crossingBG, crossingGR = crossingGR, agreementPct = agreementPct,
            peakRespR = peakRespR, peakRespG = peakRespG, peakRespB = peakRespB,
            fusedPeak = fusedPeak,
            exposureSeconds = o.reqDouble("exposure_seconds"),
            frames = o.reqInt("frames"),
            spdSource = o.reqString("spd_source"),
            rbSwapped = rbSwapped,
            slope = slope, intercept = intercept, gErrorNm = gErrorNm,
            xRoiStart = xRoi?.first, xRoiEnd = xRoi?.second,
            sensorHeight = sensorHeight
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
     * L2 -- coverage (hard): is every wavelength the ANALYSIS actually needs
     * covered by the preset? `null` means "covered".
     *
     * Since v3 a preset is indexed by wavelength on a canonical grid that is
     * deliberately narrower than a raw sensor axis (which routinely spans far
     * beyond 380..780 nm). Checking the whole current pixel axis would then
     * reject every preset for a reason the analysis does not care about, so the
     * requirement is intersected with the valid spectral band [BAND_LO_NM,
     * BAND_HI_NM] -- the only range Step 3 / Step 4 can ever integrate over.
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
        val needLo = max(lo, BAND_LO_NM)
        val needHi = min(hi, BAND_HI_NM)
        // The current axis touches none of the valid band -> there is nothing
        // the response has to cover (Step 3 will fail on its own terms).
        if (needLo >= needHi) return null
        val pLo = preset.wavelengths.minOrNull()?.toDouble() ?: return null
        val pHi = preset.wavelengths.maxOrNull()?.toDouble() ?: return null
        return if (needLo < pLo || needHi > pHi) {
            "current analysis band [%.1f, %.1f] nm is not covered by the preset [%.1f, %.1f] nm"
                .format(needLo, needHi, pLo, pHi)
        } else null
    }
}

/**
 * L3 -- drift between the mapping stored inside a preset and the mapping that is
 * active right now, in nm (max |λ_current(px) − λ_preset(px)| over five
 * quintiles of the pixel axis).
 *
 * Purely diagnostic: it decides whether the user gets a WARN hint. It is NEVER
 * used to rebuild, shift or re-index a curve -- a v3 preset is already indexed
 * by wavelength.
 *
 * Returns `null` when either side has no usable mapping.
 */
fun SpdCalibrationResult.mappingDriftNm(slope: Double, intercept: Double): Float? {
    if (!this.slope.isFinite() || !this.intercept.isFinite()) return null
    if (!slope.isFinite() || !intercept.isFinite()) return null
    if (this.slope == 0.0 || slope == 0.0) return null
    // The pixel axis length of the calibration is `sensor_height`, NOT
    // `gridPoints` (that is a wavelength count since v3).
    val n = (sensorHeight ?: gridPoints).coerceAtLeast(2)
    var worst = 0.0
    for (f in doubleArrayOf(0.10, 0.30, 0.50, 0.70, 0.90)) {
        val px = f * (n - 1)
        val current = (px - intercept) / slope
        val preset = (px - this.intercept) / this.slope
        worst = max(worst, abs(current - preset))
    }
    return worst.toFloat()
}

object SpdLoader {
    const val CUSTOM_NAME = "custom_spd.csv"
    fun customFile(context: android.content.Context) = File(context.filesDir, "spd/$CUSTOM_NAME")
    fun installCustom(context: android.content.Context, uri: android.net.Uri): String? = runCatching {
        val parsed = context.contentResolver.openInputStream(uri)?.use { parseReader(InputStreamReader(it)) }
            ?: return context.getString(R.string.spd_err_cannot_open)
        if (parsed.size < 10) return context.getString(R.string.spd_err_too_short)
        val dir = File(context.filesDir, "spd").apply { mkdirs() }
        context.contentResolver.openInputStream(uri)?.use { inp -> File(dir, CUSTOM_NAME).outputStream().use { out -> inp.copyTo(out) } }
        null
    }.getOrElse { it.message ?: context.getString(R.string.spd_err_parse_failed) }
    fun load(context: android.content.Context): Pair<String, List<Pair<Double, Double>>>? {
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
