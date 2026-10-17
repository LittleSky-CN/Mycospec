package org.fungalsentinel.app.data.model

import org.fungalsentinel.app.viewmodel.ReportData
import org.json.JSONArray
import org.json.JSONObject

/**
 * 【v2.2.8】[ReportData] ↔ JSON 的文本编解码。
 *
 * ### 为什么需要它
 * `ProjectViewModel` 把整份报告存在内存里的 `ProjectUiState.reportData`。
 * 三个 `load*Preset` 入口只改这一份内存状态，**不往 `captures` 表写任何记录**，
 * 于是「这个项目 Step 1 用了某某预设」除了 reportData 本身没有第二个载体。
 * 只要报告不落库，从 History 重开一个已完成的项目，溯源信息就永久消失。
 * 本文件提供落库/恢复所需的编解码。
 *
 * ### 为什么用 org.json
 * 平台自带、零新增主依赖；`app/build.gradle.kts` 里已有
 * `testImplementation("org.json:json:20250517")`，JVM 单测也能跑真实现
 * （mockable android.jar 会把 org.json 的每个方法都 stub 掉）。
 * 因此不引入 Gson / kotlinx.serialization / Moshi。
 *
 * ### 契约
 * - **键名 = [ReportData] 的属性名**。`ReportDataJsonTest` 的防遗漏护栏依赖这个
 *   不变量：新增属性但忘了改这里，护栏用例会失败。
 * - 可空标量写 `JSONObject.NULL`（**不是** `null`——`JSONObject.put(key, null)`
 *   在新版 org.json 里是「删除键」，会让护栏用例误判字段被漏掉）。
 * - 反序列化**全程 try/catch**：畸形 JSON、类型不符、schema 不认识一律返回 null，
 *   由调用方安全降级，绝不让旧数据把 App 搞崩。
 * - 数值一律走 `Locale.US`（`JSONObject` 内部用 `Double.toString`，
 *   已是 Locale 无关；此处只约束我们自己不引入 `String.format` 默认 Locale）。
 */
private const val KEY_SCHEMA = "schema"

/** 当前 schema 版本。改变了 [ReportData] 的语义或形状就 +1。 */
const val REPORT_DATA_SCHEMA: Int = 1

fun ReportData.toJsonString(): String = JSONObject().apply {
    put(KEY_SCHEMA, REPORT_DATA_SCHEMA)

    // ─── Step 1 波长标定 ───
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

    // ─── Step 3 样品 ───
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

    // ─── Step 4 标准曲线 ───
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

    // ─── 步骤状态 ───
    put("step1Status", step1Status)
    put("step2Status", step2Status)
    put("step3Status", step3Status)
    put("step4Status", step4Status)

    // ─── 溯源 ───
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
 * 反序列化。**任何**失败都返回 null：调用方按「恢复失败 → 退回 `ReportData()`」
 * 安全降级，不要让旧数据或残缺数据把 App 搞崩。
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

            // 步骤状态
            step1Status = o.requireString("step1Status"),
            step2Status = o.requireString("step2Status"),
            step3Status = o.requireString("step3Status"),
            step4Status = o.requireString("step4Status"),

            // 溯源
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
// 写入侧
// ─────────────────────────────────────────────────────────────────────────────

/**
 * NaN / Infinity 在 JSON 里没有合法表示，而 org.json 的 `testValidity` 对它们
 * 是**直接抛** `JSONException`（不是默默写成 null）。生产代码里 `spdCrossing*`、
 * 光谱数组之类的量理论上可能算出非有限值，所以写入侧一律先把它们压回有限值：
 * 不可空标量退回 [fallback]（取 [ReportData] 的默认值），数组元素退回 0。
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
// 读取侧
//
// `JSONObject.isNull(key)` 对「键不存在」和「值是 JSONObject.NULL」都返回 true，
// 因此所有 `optXxxOrNull` 天然把两种情形都折叠成 null —— 这正是我们要的宽松语义。
// 不可空标量则用 `optXxx(key, default)`，缺失与显式 NULL 都退回默认值。
// ─────────────────────────────────────────────────────────────────────────────

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key)

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (isNull(key)) null else optInt(key)

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeIf { it.isFinite() }

/**
 * 不可空但**缺字段就当成数据坏了**：不静默补默认值，让整个反序列化返回 null，
 * 由调用方退回全新的 `ReportData()`。否则一份残缺快照会伪装成「用户真的没跑过
 * Step 1」，比直接恢复失败更难排查。
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
