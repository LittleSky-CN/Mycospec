package org.fungalsentinel.app.data.model

import org.fungalsentinel.app.viewmodel.ReportData
import org.json.JSONArray
import org.json.JSONObject

/**
 * [v2.2.8] text codec for [ReportData] <-> JSON.
 *
 * ### Why it is needed
 * `ProjectViewModel` keeps the whole report in memory as `ProjectUiState.reportData`.
 * The three `load*Preset` entry points only touch that in-memory state and **write no row to the `captures` table**,
 * so "this project used such-and-such preset for Step 1" has no second carrier beyond reportData itself.
 * As long as the report is not persisted, reopening a finished project from History loses the provenance forever.
 * This file provides the codec needed to persist and restore it.
 *
 * ### Why org.json
 * It ships with the platform and adds no new main dependency; `app/build.gradle.kts` already declares
 * `testImplementation("org.json:json:20250517")`, so JVM unit tests can run the real implementation
 * (mockable android.jar would stub out every org.json method).
 * Gson / kotlinx.serialization / Moshi are therefore not introduced.
 *
 * ### Contract
 * - **key name = property name of [ReportData]**. The anti-omission guard in `ReportDataJsonTest` relies on this
 *   invariant: add a property but forget to update this file and the guard case fails.
 * - nullable scalars are written as `JSONObject.NULL` (**not** `null` -- `JSONObject.put(key, null)`
 *   deletes the key in recent org.json versions, which would make the guard case think a field was dropped).
 * - deserialization is **wrapped in try/catch throughout**: malformed JSON, wrong types and unknown schemas all return null,
 *   letting the caller degrade safely and never letting old data crash the app.
 * - numbers always go through `Locale.US` (JSONObject uses `Double.toString` internally,
 *   which is already locale-independent; this only stops us from introducing a default-locale `String.format`).
 */
private const val KEY_SCHEMA = "schema"

/** Current schema version. Bump it by 1 whenever the meaning or shape of [ReportData] changes. */
const val REPORT_DATA_SCHEMA: Int = 1

fun ReportData.toJsonString(): String = JSONObject().apply {
    put(KEY_SCHEMA, REPORT_DATA_SCHEMA)

    // ─── Step 1 wavelength calibration ───
    put("wavelengthQuality", wavelengthQuality)
    put("wavelengthGError", wavelengthGError)
    put("mappingFormula", mappingFormula)
    put("exposureTime", exposureTime)
    put("xRoi", xRoi)
    put("trainingPoints", trainingPoints)
    put("validationPoint", validationPoint)
    put("gResidual", gResidual)
    putDoubleOrNull("slope", slope)
    putDoubleOrNull("intercept", intercept)
    putDoubleOrNull("gErrorNm", gErrorNm)
    putDoubleOrNull("peakR", peakR)
    putDoubleOrNull("peakG", peakG)
    putDoubleOrNull("peakB", peakB)
    put("peakWindows", peakWindows.toJsonIntPairs())
    put("peakDiagnostics", peakDiagnostics)
    putIntOrNull("roiStart", roiStart)
    putIntOrNull("roiEnd", roiEnd)
    putIntOrNull("sensorWidth", sensorWidth)
    putIntOrNull("sensorHeight", sensorHeight)
    put("redProfile", redProfile.toJsonFloats())
    put("greenProfile", greenProfile.toJsonFloats())
    put("blueProfile", blueProfile.toJsonFloats())
    putStringOrNull("previewUri", previewUri)
    put("wlR", wlR.finiteOr(622.5))
    put("wlG", wlG.finiteOr(522.5))
    put("wlB", wlB.finiteOr(462.5))

    // ─── Step 2 SPD ───
    put("spdStatus", spdStatus)
    put("spdSource", spdSource)
    put("spdCoeffR", spdCoeffR)
    put("spdCoeffB", spdCoeffB)
    put("spdResponse", spdResponse)
    put("spdFrames", spdFrames)
    put("spdExposure", spdExposure)
    put("spdEffectiveBand", spdEffectiveBand)
    put("spdMasked", spdMasked)
    put("spdHandoffs", spdHandoffs)
    put("spdAgreement", spdAgreement)
    put("spdFusedPeak", spdFusedPeak)
    put("spdPeaks", spdPeaks)
    put("spdRawPeaks", spdRawPeaks)
    put("spdCorrPeaks", spdCorrPeaks)
    put("spdMethodNote", spdMethodNote)
    put("spdCrossingBG", spdCrossingBG.finiteOr(483f))
    put("spdCrossingGR", spdCrossingGR.finiteOr(577f))
    put("spdLambda", spdLambda.toJsonFloats())
    put("spdPosR", spdPosR.toJsonFloats())
    put("spdPosG", spdPosG.toJsonFloats())
    put("spdPosB", spdPosB.toJsonFloats())
    put("spdRespR", spdRespR.toJsonFloats())
    put("spdRespG", spdRespG.toJsonFloats())
    put("spdRespB", spdRespB.toJsonFloats())
    put("spdCorrR", spdCorrR.toJsonFloats())
    put("spdCorrG", spdCorrG.toJsonFloats())
    put("spdCorrB", spdCorrB.toJsonFloats())
    put("spdWB", spdWB.toJsonFloats())
    put("spdWG", spdWG.toJsonFloats())
    put("spdWR", spdWR.toJsonFloats())
    put("spdFused", spdFused.toJsonFloats())

    // ─── Step 3 sample ───
    put("sampleFluorophore", sampleFluorophore)
    put("sampleBand", sampleBand)
    put("blankMeanArea", blankMeanArea)
    put("sampleMeanArea", sampleMeanArea)
    put("sampleIntegratedArea", sampleIntegratedArea)
    put("sampleSD", sampleSD)
    put("replicates", replicates)
    put("integrationWindow", integrationWindow)
    put("sampleSpectrum", sampleSpectrum.toJsonFloats())
    put("blankSpectrum", blankSpectrum.toJsonFloats())
    putDoubleOrNull("correctedAreaValue", correctedAreaValue)
    put("samplePeakNm", samplePeakNm)
    put("samplePeakIntensity", samplePeakIntensity)
    put("sampleFwhm", sampleFwhm)
    put("sampleCentroid", sampleCentroid)

    // ─── Step 4 standard curve ───
    put("standardRows", standardRows.toJsonStrings())
    put("regressionFormula", regressionFormula)
    put("regressionR2", regressionR2)
    put("predictedConcentration", predictedConcentration)
    putDoubleOrNull("curveSlope", curveSlope)
    putDoubleOrNull("curveIntercept", curveIntercept)
    put("curvePoints", curvePoints.toJsonDoublePairs())
    put("curveWarnings", curveWarnings.toJsonStrings())
    put("spdWarnings", spdWarnings.toJsonStrings())
    put("isLogFit", isLogFit)

    // ─── step status ───
    put("step1Status", step1Status)
    put("step2Status", step2Status)
    put("step3Status", step3Status)
    put("step4Status", step4Status)

    // ─── provenance ───
    put("step1Source", step1Source)
    putStringOrNull("step1PresetName", step1PresetName)
    putStringOrNull("step1PresetFile", step1PresetFile)
    putStringOrNull("step1ImportedAt", step1ImportedAt)
    put("step1Params", step1Params.toJsonStringMap())

    putStringOrNull("spdPresetName", spdPresetName)
    putStringOrNull("spdPresetFile", spdPresetFile)
    putStringOrNull("spdImportedAt", spdImportedAt)
    put("spdParams", spdParams.toJsonStringMap())

    put("step4Source", step4Source)
    putStringOrNull("step4PresetName", step4PresetName)
    putStringOrNull("step4PresetFile", step4PresetFile)
    putStringOrNull("step4ImportedAt", step4ImportedAt)
    put("step4Params", step4Params.toJsonStringMap())
}.toString()

/**
 * Deserialization. **Any** failure returns null: the caller degrades safely with "restoration failed -> fall back to `ReportData()`"
 * and old or partial data must never crash the app.
 */
fun reportDataFromJson(raw: String?): ReportData? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val o = JSONObject(raw)
        val schema = o.optInt(KEY_SCHEMA, Int.MIN_VALUE)
        if (schema != REPORT_DATA_SCHEMA) return null
        ReportData(
            // Step 1
            wavelengthQuality = o.requireString("wavelengthQuality"),
            wavelengthGError = o.requireString("wavelengthGError"),
            mappingFormula = o.requireString("mappingFormula"),
            exposureTime = o.requireString("exposureTime"),
            xRoi = o.requireString("xRoi"),
            trainingPoints = o.requireString("trainingPoints"),
            validationPoint = o.requireString("validationPoint"),
            gResidual = o.requireString("gResidual"),
            slope = o.optDoubleOrNull("slope"),
            intercept = o.optDoubleOrNull("intercept"),
            gErrorNm = o.optDoubleOrNull("gErrorNm"),
            peakR = o.optDoubleOrNull("peakR"),
            peakG = o.optDoubleOrNull("peakG"),
            peakB = o.optDoubleOrNull("peakB"),
            peakWindows = o.optIntPairs("peakWindows"),
            peakDiagnostics = o.requireString("peakDiagnostics"),
            roiStart = o.optIntOrNull("roiStart"),
            roiEnd = o.optIntOrNull("roiEnd"),
            sensorWidth = o.optIntOrNull("sensorWidth"),
            sensorHeight = o.optIntOrNull("sensorHeight"),
            redProfile = o.optFloats("redProfile"),
            greenProfile = o.optFloats("greenProfile"),
            blueProfile = o.optFloats("blueProfile"),
            previewUri = o.optStringOrNull("previewUri"),
            wlR = o.requireDouble("wlR"),
            wlG = o.requireDouble("wlG"),
            wlB = o.requireDouble("wlB"),

            // Step 2
            spdStatus = o.requireString("spdStatus"),
            spdSource = o.requireString("spdSource"),
            spdCoeffR = o.requireString("spdCoeffR"),
            spdCoeffB = o.requireString("spdCoeffB"),
            spdResponse = o.requireString("spdResponse"),
            spdFrames = o.requireInt("spdFrames"),
            spdExposure = o.requireString("spdExposure"),
            spdEffectiveBand = o.requireString("spdEffectiveBand"),
            spdMasked = o.requireString("spdMasked"),
            spdHandoffs = o.requireString("spdHandoffs"),
            spdAgreement = o.requireString("spdAgreement"),
            spdFusedPeak = o.requireString("spdFusedPeak"),
            spdPeaks = o.requireString("spdPeaks"),
            spdRawPeaks = o.requireString("spdRawPeaks"),
            spdCorrPeaks = o.requireString("spdCorrPeaks"),
            spdMethodNote = o.requireString("spdMethodNote"),
            spdCrossingBG = o.requireDouble("spdCrossingBG").toFloat(),
            spdCrossingGR = o.requireDouble("spdCrossingGR").toFloat(),
            spdLambda = o.optFloats("spdLambda"),
            spdPosR = o.optFloats("spdPosR"),
            spdPosG = o.optFloats("spdPosG"),
            spdPosB = o.optFloats("spdPosB"),
            spdRespR = o.optFloats("spdRespR"),
            spdRespG = o.optFloats("spdRespG"),
            spdRespB = o.optFloats("spdRespB"),
            spdCorrR = o.optFloats("spdCorrR"),
            spdCorrG = o.optFloats("spdCorrG"),
            spdCorrB = o.optFloats("spdCorrB"),
            spdWB = o.optFloats("spdWB"),
            spdWG = o.optFloats("spdWG"),
            spdWR = o.optFloats("spdWR"),
            spdFused = o.optFloats("spdFused"),

            // Step 3
            sampleFluorophore = o.requireString("sampleFluorophore"),
            sampleBand = o.requireString("sampleBand"),
            blankMeanArea = o.requireString("blankMeanArea"),
            sampleMeanArea = o.requireString("sampleMeanArea"),
            sampleIntegratedArea = o.requireString("sampleIntegratedArea"),
            sampleSD = o.requireString("sampleSD"),
            replicates = o.requireInt("replicates"),
            integrationWindow = o.requireString("integrationWindow"),
            sampleSpectrum = o.optFloats("sampleSpectrum"),
            blankSpectrum = o.optFloats("blankSpectrum"),
            correctedAreaValue = o.optDoubleOrNull("correctedAreaValue"),
            samplePeakNm = o.requireString("samplePeakNm"),
            samplePeakIntensity = o.requireString("samplePeakIntensity"),
            sampleFwhm = o.requireString("sampleFwhm"),
            sampleCentroid = o.requireString("sampleCentroid"),

            // Step 4
            standardRows = o.optStrings("standardRows"),
            regressionFormula = o.requireString("regressionFormula"),
            regressionR2 = o.requireString("regressionR2"),
            predictedConcentration = o.requireString("predictedConcentration"),
            curveSlope = o.optDoubleOrNull("curveSlope"),
            curveIntercept = o.optDoubleOrNull("curveIntercept"),
            curvePoints = o.optDoublePairs("curvePoints"),
            curveWarnings = o.optStrings("curveWarnings"),
            spdWarnings = o.optStrings("spdWarnings"),
            isLogFit = o.requireBoolean("isLogFit"),

            // step status
            step1Status = o.requireString("step1Status"),
            step2Status = o.requireString("step2Status"),
            step3Status = o.requireString("step3Status"),
            step4Status = o.requireString("step4Status"),

            // provenance
            step1Source = o.requireString("step1Source"),
            step1PresetName = o.optStringOrNull("step1PresetName"),
            step1PresetFile = o.optStringOrNull("step1PresetFile"),
            step1ImportedAt = o.optStringOrNull("step1ImportedAt"),
            step1Params = o.optStringMap("step1Params"),

            spdPresetName = o.optStringOrNull("spdPresetName"),
            spdPresetFile = o.optStringOrNull("spdPresetFile"),
            spdImportedAt = o.optStringOrNull("spdImportedAt"),
            spdParams = o.optStringMap("spdParams"),

            step4Source = o.requireString("step4Source"),
            step4PresetName = o.optStringOrNull("step4PresetName"),
            step4PresetFile = o.optStringOrNull("step4PresetFile"),
            step4ImportedAt = o.optStringOrNull("step4ImportedAt"),
            step4Params = o.optStringMap("step4Params")
        )
    }.getOrNull()
}

// ─────────────────────────────────────────────────────────────────────────────
// write side
// ─────────────────────────────────────────────────────────────────────────────

/**
 * NaN / Infinity have no legal JSON representation, and org.json's `testValidity` **throws** `JSONException`
 * for them outright (rather than quietly writing null). In production code quantities such as `spdCrossing*` or
 * the spectral arrays can in principle evaluate to non-finite values, so the write side always clamps them back to finite ones:
 * non-null scalars fall back to [fallback] (the default of [ReportData]), array elements fall back to 0.
 */
private fun Double.finiteOr(fallback: Double): Double = if (isFinite()) this else fallback
private fun Float.finiteOr(fallback: Float): Double = if (isFinite()) toDouble() else fallback.toDouble()

private fun JSONObject.putDoubleOrNull(key: String, value: Double?) {
    if (value != null && value.isFinite()) put(key, value) else put(key, JSONObject.NULL)
}

private fun JSONObject.putIntOrNull(key: String, value: Int?) {
    if (value != null) put(key, value) else put(key, JSONObject.NULL)
}

private fun JSONObject.putStringOrNull(key: String, value: String?) {
    if (value != null) put(key, value) else put(key, JSONObject.NULL)
}

private fun List<String>.toJsonStrings(): JSONArray = JSONArray().also { arr ->
    forEach { arr.put(it) }
}

private fun List<Float>.toJsonFloats(): JSONArray = JSONArray().also { arr ->
    forEach { arr.put(it.finiteOr(0f)) }
}

private fun List<Pair<Int, Int>>.toJsonIntPairs(): JSONArray = JSONArray().also { arr ->
    forEach { (a, b) -> arr.put(JSONArray().put(a).put(b)) }
}

private fun List<Pair<Double, Double>>.toJsonDoublePairs(): JSONArray = JSONArray().also { arr ->
    forEach { (a, b) -> arr.put(JSONArray().put(a.finiteOr(0.0)).put(b.finiteOr(0.0))) }
}

private fun Map<String, String>.toJsonStringMap(): JSONObject = JSONObject().also { o ->
    forEach { (k, v) -> o.put(k, v) }
}

// ─────────────────────────────────────────────────────────────────────────────
// read side
//
// `JSONObject.isNull(key)` returns true both for "key absent" and for "value is JSONObject.NULL",
// so every `optXxxOrNull` naturally folds both cases into null -- exactly the lenient semantics we want.
// Non-null scalars use `optXxx(key, default)`, so both a missing key and an explicit NULL fall back to the default value.
// ─────────────────────────────────────────────────────────────────────────────

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key)

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (isNull(key)) null else optInt(key)

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeIf { it.isFinite() }

/**
 * Non-null but **a missing field means the data is broken**: do not silently fill in a default; let the whole deserialization
 * return null so the caller falls back to a fresh `ReportData()`. Otherwise a partial snapshot would masquerade as
 * "the user really never ran Step 1", which is far harder to diagnose than an outright restoration failure.
 */
private fun JSONObject.requireString(key: String): String =
    getString(key)

private fun JSONObject.requireInt(key: String): Int = getInt(key)

private fun JSONObject.requireDouble(key: String): Double = getDouble(key)

private fun JSONObject.requireBoolean(key: String): Boolean = getBoolean(key)

private fun JSONObject.optStrings(key: String): List<String> {
    val arr = optJSONArray(key) ?: return emptyList()
    return List(arr.length()) { arr.getString(it) }
}

private fun JSONObject.optFloats(key: String): List<Float> {
    val arr = optJSONArray(key) ?: return emptyList()
    return List(arr.length()) { arr.getDouble(it).toFloat() }
}

private fun JSONObject.optIntPairs(key: String): List<Pair<Int, Int>> {
    val arr = optJSONArray(key) ?: return emptyList()
    return List(arr.length()) { i ->
        val p = arr.getJSONArray(i)
        p.getInt(0) to p.getInt(1)
    }
}

private fun JSONObject.optDoublePairs(key: String): List<Pair<Double, Double>> {
    val arr = optJSONArray(key) ?: return emptyList()
    return List(arr.length()) { i ->
        val p = arr.getJSONArray(i)
        p.getDouble(0) to p.getDouble(1)
    }
}

private fun JSONObject.optStringMap(key: String): Map<String, String> {
    val o = optJSONObject(key) ?: return emptyMap()
    return o.keys().asSequence().associateWith { k -> o.getString(k) }
}
