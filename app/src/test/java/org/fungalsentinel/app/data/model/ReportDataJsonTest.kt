package org.fungalsentinel.app.data.model

import org.fungalsentinel.app.viewmodel.ReportData
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * 【v2.2.8】[ReportData] 落库快照的编解码契约。
 *
 * 这个文件的存在意义不是"验证 org.json 好用"，而是守住一条不变量：
 * **从 History 重开一个已完成的项目时，报告与 Step 1/2/4 的预设溯源必须原样回来。**
 * 三个 `load*Preset` 只改内存里的 reportData、不写 `captures` 表，所以这份快照是
 * 溯源信息的唯一载体 —— 序列化器漏掉任何一个字段都是永久性的数据丢失。
 *
 * 覆盖：全字段往返（102 个字段逐个断言）、默认值往返、畸形输入降级、
 * schema 版本不符降级、三种特殊容器，以及**防遗漏护栏**。
 */
class ReportDataJsonTest {

    // ─────────────────────────────────────────────────────────────────────
    // 1. 全字段往返
    // ─────────────────────────────────────────────────────────────────────

    /** 每个字段都填成**非默认值**，这样漏序列化任何一个都会被断言抓住。 */
    private fun fullyPopulated() = ReportData(
        // ─── Step 1 波长标定 ───
        wavelengthQuality = "wavelengthQuality#1",
        wavelengthGError = "wavelengthGError#2",
        mappingFormula = "mappingFormula#3",
        exposureTime = "exposureTime#4",
        xRoi = "xRoi#5",
        trainingPoints = "trainingPoints#6",
        validationPoint = "validationPoint#7",
        gResidual = "gResidual#8",
        slope = 9.125,
        intercept = 10.125,
        gErrorNm = 11.125,
        peakR = 12.125,
        peakG = 13.125,
        peakB = 14.125,
        peakWindows = listOf(151 to 152, 153 to 154),
        peakDiagnostics = "peakDiagnostics#16",
        roiStart = 2017,
        roiEnd = 2018,
        sensorWidth = 2019,
        sensorHeight = 2020,
        redProfile = listOf(64.5f, 65.25f, 66.125f),
        greenProfile = listOf(67.5f, 68.25f, 69.125f),
        blueProfile = listOf(70.5f, 71.25f, 72.125f),
        previewUri = "previewUri#24",
        wlR = 25.25,
        wlG = 26.25,
        wlB = 27.25,

        // ─── Step 2 SPD ───
        spdStatus = "spdStatus#28",
        spdSource = "spdSource#29",
        spdCoeffR = "spdCoeffR#30",
        spdCoeffB = "spdCoeffB#31",
        spdResponse = "spdResponse#32",
        spdFrames = 1033,
        spdExposure = "spdExposure#34",
        spdEffectiveBand = "spdEffectiveBand#35",
        spdMasked = "spdMasked#36",
        spdHandoffs = "spdHandoffs#37",
        spdAgreement = "spdAgreement#38",
        spdFusedPeak = "spdFusedPeak#39",
        spdPeaks = "spdPeaks#40",
        spdRawPeaks = "spdRawPeaks#41",
        spdCorrPeaks = "spdCorrPeaks#42",
        spdMethodNote = "spdMethodNote#43",
        spdCrossingBG = 54.5f,
        spdCrossingGR = 55.5f,
        spdLambda = listOf(139.5f, 140.25f, 141.125f),
        spdPosR = listOf(142.5f, 143.25f, 144.125f),
        spdPosG = listOf(145.5f, 146.25f, 147.125f),
        spdPosB = listOf(148.5f, 149.25f, 150.125f),
        spdRespR = listOf(151.5f, 152.25f, 153.125f),
        spdRespG = listOf(154.5f, 155.25f, 156.125f),
        spdRespB = listOf(157.5f, 158.25f, 159.125f),
        spdCorrR = listOf(160.5f, 161.25f, 162.125f),
        spdCorrG = listOf(163.5f, 164.25f, 165.125f),
        spdCorrB = listOf(166.5f, 167.25f, 168.125f),
        spdWB = listOf(169.5f, 170.25f, 171.125f),
        spdWG = listOf(172.5f, 173.25f, 174.125f),
        spdWR = listOf(175.5f, 176.25f, 177.125f),
        spdFused = listOf(178.5f, 179.25f, 180.125f),

        // ─── Step 3 样品 ───
        sampleFluorophore = "sampleFluorophore#60",
        sampleBand = "sampleBand#61",
        blankMeanArea = "blankMeanArea#62",
        sampleMeanArea = "sampleMeanArea#63",
        sampleIntegratedArea = "sampleIntegratedArea#64",
        sampleSD = "sampleSD#65",
        replicates = 1066,
        integrationWindow = "integrationWindow#67",
        sampleSpectrum = listOf(205.5f, 206.25f, 207.125f),
        blankSpectrum = listOf(208.5f, 209.25f, 210.125f),
        correctedAreaValue = 70.125,
        samplePeakNm = "samplePeakNm#71",
        samplePeakIntensity = "samplePeakIntensity#72",
        sampleFwhm = "sampleFwhm#73",
        sampleCentroid = "sampleCentroid#74",

        // ─── Step 4 标准曲线 ───
        standardRows = listOf("standardRows.a", "standardRows.b"),
        regressionFormula = "regressionFormula#76",
        regressionR2 = "regressionR2#77",
        predictedConcentration = "predictedConcentration#78",
        curveSlope = 79.125,
        curveIntercept = 80.125,
        curvePoints = listOf(81.5 to 82.25, 83.125 to 84.75),
        curveWarnings = listOf("curveWarnings.a", "curveWarnings.b"),
        spdWarnings = listOf("spdWarnings.a", "spdWarnings.b"),
        isLogFit = true,

        // ─── 步骤状态 ───
        step1Status = "step1Status#85",
        step2Status = "step2Status#86",
        step3Status = "step3Status#87",
        step4Status = "step4Status#88",

        // ─── 溯源 ───
        step1Source = "step1Source#89",
        step1PresetName = "step1PresetName#90",
        step1PresetFile = "step1PresetFile#91",
        step1ImportedAt = "step1ImportedAt#92",
        step1Params = linkedMapOf("step1Params.k1" to "step1Params.v1", "step1Params.k2" to "step1Params.v2"),
        spdPresetName = "spdPresetName#94",
        spdPresetFile = "spdPresetFile#95",
        spdImportedAt = "spdImportedAt#96",
        spdParams = linkedMapOf("spdParams.k1" to "spdParams.v1", "spdParams.k2" to "spdParams.v2"),
        step4Source = "step4Source#98",
        step4PresetName = "step4PresetName#99",
        step4PresetFile = "step4PresetFile#100",
        step4ImportedAt = "step4ImportedAt#101",
        step4Params = linkedMapOf("step4Params.k1" to "step4Params.v1", "step4Params.k2" to "step4Params.v2"),
    )

    @Test
    fun fullRoundTrip_preservesEveryField() {
        val src = fullyPopulated()
        val out = reportDataFromJson(src.toJsonString())

        assertNotNull("反序列化不应返回 null", out)
        out!!

        // 整体相等：data class 的 equals 覆盖全部 102 个字段，是最后一道兜底。
        assertEquals("整份 ReportData 往返后应完全相等", src, out)

        // 逐字段断言：整体断言失败时只报一次"不相等"，逐字段能立刻定位到具体字段。
        // ─── Step 1 波长标定 ───
        assertEquals("wavelengthQuality", src.wavelengthQuality, out.wavelengthQuality)
        assertEquals("wavelengthGError", src.wavelengthGError, out.wavelengthGError)
        assertEquals("mappingFormula", src.mappingFormula, out.mappingFormula)
        assertEquals("exposureTime", src.exposureTime, out.exposureTime)
        assertEquals("xRoi", src.xRoi, out.xRoi)
        assertEquals("trainingPoints", src.trainingPoints, out.trainingPoints)
        assertEquals("validationPoint", src.validationPoint, out.validationPoint)
        assertEquals("gResidual", src.gResidual, out.gResidual)
        assertEquals("slope", src.slope, out.slope)
        assertEquals("intercept", src.intercept, out.intercept)
        assertEquals("gErrorNm", src.gErrorNm, out.gErrorNm)
        assertEquals("peakR", src.peakR, out.peakR)
        assertEquals("peakG", src.peakG, out.peakG)
        assertEquals("peakB", src.peakB, out.peakB)
        assertEquals("peakWindows", src.peakWindows, out.peakWindows)
        assertEquals("peakDiagnostics", src.peakDiagnostics, out.peakDiagnostics)
        assertEquals("roiStart", src.roiStart, out.roiStart)
        assertEquals("roiEnd", src.roiEnd, out.roiEnd)
        assertEquals("sensorWidth", src.sensorWidth, out.sensorWidth)
        assertEquals("sensorHeight", src.sensorHeight, out.sensorHeight)
        assertEquals("redProfile", src.redProfile, out.redProfile)
        assertEquals("greenProfile", src.greenProfile, out.greenProfile)
        assertEquals("blueProfile", src.blueProfile, out.blueProfile)
        assertEquals("previewUri", src.previewUri, out.previewUri)
        assertEquals("wlR", src.wlR, out.wlR, 0.0)
        assertEquals("wlG", src.wlG, out.wlG, 0.0)
        assertEquals("wlB", src.wlB, out.wlB, 0.0)

        // ─── Step 2 SPD ───
        assertEquals("spdStatus", src.spdStatus, out.spdStatus)
        assertEquals("spdSource", src.spdSource, out.spdSource)
        assertEquals("spdCoeffR", src.spdCoeffR, out.spdCoeffR)
        assertEquals("spdCoeffB", src.spdCoeffB, out.spdCoeffB)
        assertEquals("spdResponse", src.spdResponse, out.spdResponse)
        assertEquals("spdFrames", src.spdFrames, out.spdFrames)
        assertEquals("spdExposure", src.spdExposure, out.spdExposure)
        assertEquals("spdEffectiveBand", src.spdEffectiveBand, out.spdEffectiveBand)
        assertEquals("spdMasked", src.spdMasked, out.spdMasked)
        assertEquals("spdHandoffs", src.spdHandoffs, out.spdHandoffs)
        assertEquals("spdAgreement", src.spdAgreement, out.spdAgreement)
        assertEquals("spdFusedPeak", src.spdFusedPeak, out.spdFusedPeak)
        assertEquals("spdPeaks", src.spdPeaks, out.spdPeaks)
        assertEquals("spdRawPeaks", src.spdRawPeaks, out.spdRawPeaks)
        assertEquals("spdCorrPeaks", src.spdCorrPeaks, out.spdCorrPeaks)
        assertEquals("spdMethodNote", src.spdMethodNote, out.spdMethodNote)
        assertEquals("spdCrossingBG", src.spdCrossingBG.toDouble(), out.spdCrossingBG.toDouble(), 0.0)
        assertEquals("spdCrossingGR", src.spdCrossingGR.toDouble(), out.spdCrossingGR.toDouble(), 0.0)
        assertEquals("spdLambda", src.spdLambda, out.spdLambda)
        assertEquals("spdPosR", src.spdPosR, out.spdPosR)
        assertEquals("spdPosG", src.spdPosG, out.spdPosG)
        assertEquals("spdPosB", src.spdPosB, out.spdPosB)
        assertEquals("spdRespR", src.spdRespR, out.spdRespR)
        assertEquals("spdRespG", src.spdRespG, out.spdRespG)
        assertEquals("spdRespB", src.spdRespB, out.spdRespB)
        assertEquals("spdCorrR", src.spdCorrR, out.spdCorrR)
        assertEquals("spdCorrG", src.spdCorrG, out.spdCorrG)
        assertEquals("spdCorrB", src.spdCorrB, out.spdCorrB)
        assertEquals("spdWB", src.spdWB, out.spdWB)
        assertEquals("spdWG", src.spdWG, out.spdWG)
        assertEquals("spdWR", src.spdWR, out.spdWR)
        assertEquals("spdFused", src.spdFused, out.spdFused)

        // ─── Step 3 样品 ───
        assertEquals("sampleFluorophore", src.sampleFluorophore, out.sampleFluorophore)
        assertEquals("sampleBand", src.sampleBand, out.sampleBand)
        assertEquals("blankMeanArea", src.blankMeanArea, out.blankMeanArea)
        assertEquals("sampleMeanArea", src.sampleMeanArea, out.sampleMeanArea)
        assertEquals("sampleIntegratedArea", src.sampleIntegratedArea, out.sampleIntegratedArea)
        assertEquals("sampleSD", src.sampleSD, out.sampleSD)
        assertEquals("replicates", src.replicates, out.replicates)
        assertEquals("integrationWindow", src.integrationWindow, out.integrationWindow)
        assertEquals("sampleSpectrum", src.sampleSpectrum, out.sampleSpectrum)
        assertEquals("blankSpectrum", src.blankSpectrum, out.blankSpectrum)
        assertEquals("correctedAreaValue", src.correctedAreaValue, out.correctedAreaValue)
        assertEquals("samplePeakNm", src.samplePeakNm, out.samplePeakNm)
        assertEquals("samplePeakIntensity", src.samplePeakIntensity, out.samplePeakIntensity)
        assertEquals("sampleFwhm", src.sampleFwhm, out.sampleFwhm)
        assertEquals("sampleCentroid", src.sampleCentroid, out.sampleCentroid)

        // ─── Step 4 标准曲线 ───
        assertEquals("standardRows", src.standardRows, out.standardRows)
        assertEquals("regressionFormula", src.regressionFormula, out.regressionFormula)
        assertEquals("regressionR2", src.regressionR2, out.regressionR2)
        assertEquals("predictedConcentration", src.predictedConcentration, out.predictedConcentration)
        assertEquals("curveSlope", src.curveSlope, out.curveSlope)
        assertEquals("curveIntercept", src.curveIntercept, out.curveIntercept)
        assertEquals("curvePoints", src.curvePoints, out.curvePoints)
        assertEquals("curveWarnings", src.curveWarnings, out.curveWarnings)
        assertEquals("spdWarnings", src.spdWarnings, out.spdWarnings)
        assertEquals("isLogFit", src.isLogFit, out.isLogFit)

        // ─── 步骤状态 ───
        assertEquals("step1Status", src.step1Status, out.step1Status)
        assertEquals("step2Status", src.step2Status, out.step2Status)
        assertEquals("step3Status", src.step3Status, out.step3Status)
        assertEquals("step4Status", src.step4Status, out.step4Status)

        // ─── 溯源 ───
        assertEquals("step1Source", src.step1Source, out.step1Source)
        assertEquals("step1PresetName", src.step1PresetName, out.step1PresetName)
        assertEquals("step1PresetFile", src.step1PresetFile, out.step1PresetFile)
        assertEquals("step1ImportedAt", src.step1ImportedAt, out.step1ImportedAt)
        assertEquals("step1Params", src.step1Params, out.step1Params)
        assertEquals("spdPresetName", src.spdPresetName, out.spdPresetName)
        assertEquals("spdPresetFile", src.spdPresetFile, out.spdPresetFile)
        assertEquals("spdImportedAt", src.spdImportedAt, out.spdImportedAt)
        assertEquals("spdParams", src.spdParams, out.spdParams)
        assertEquals("step4Source", src.step4Source, out.step4Source)
        assertEquals("step4PresetName", src.step4PresetName, out.step4PresetName)
        assertEquals("step4PresetFile", src.step4PresetFile, out.step4PresetFile)
        assertEquals("step4ImportedAt", src.step4ImportedAt, out.step4ImportedAt)
        assertEquals("step4Params", src.step4Params, out.step4Params)
    }

    @Test
    fun defaultRoundTrip_preservesDefaults() {
        val out = reportDataFromJson(ReportData().toJsonString())
        assertNotNull(out)
        assertEquals("默认 ReportData 往返后应完全相等", ReportData(), out)
    }

    // ─────────────────────────────────────────────────────────────────────
    // 2. 降级路径：任何异常都必须变成 null，绝不抛给调用方
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun malformedInput_returnsNull() {
        assertNull("null 输入", reportDataFromJson(null))
        assertNull("空串", reportDataFromJson(""))
        assertNull("纯空白", reportDataFromJson("   \n\t "))
        assertNull("不是 JSON", reportDataFromJson("this is not json at all"))
        assertNull("截断的 JSON", reportDataFromJson("{\"schema\":1,\"wavelengthQuality\":"))
        assertNull("JSON 数组而非对象", reportDataFromJson("[1,2,3]"))
        assertNull("只有 schema、其余全缺", reportDataFromJson("{\"schema\":1}"))
        assertNull(
            "字段应是数组却给了字符串",
            reportDataFromJson("{\"schema\":1,\"redProfile\":\"not-an-array\"}")
        )
        assertNull(
            "字段应是数字却给了字符串",
            reportDataFromJson("{\"schema\":1,\"spdFrames\":\"not-a-number\"}")
        )
    }

    @Test
    fun unknownSchema_returnsNull() {
        val raw = ReportData().toJsonString()

        // 先确认基线：当前 schema 的正常快照是能恢复的，否则下面几条断言毫无意义。
        assertNotNull("当前 schema 的正常快照必须能恢复", reportDataFromJson(raw))

        assertNull(
            "未来的 schema 版本必须拒绝",
            reportDataFromJson(JSONObject(raw).put("schema", REPORT_DATA_SCHEMA + 1).toString())
        )
        assertNull(
            "过旧的 schema 版本必须拒绝",
            reportDataFromJson(JSONObject(raw).put("schema", REPORT_DATA_SCHEMA - 1).toString())
        )
        assertNull(
            "没有 schema 字段必须拒绝",
            reportDataFromJson(JSONObject(raw).apply { remove("schema") }.toString())
        )
        assertNull(
            "schema 不是整数必须拒绝",
            reportDataFromJson(JSONObject(raw).put("schema", "one").toString())
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // 3. 三种特殊容器
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun stringMaps_roundTrip() {
        val src = ReportData(
            step1Params = linkedMapOf("slope_k" to "-2.738911", "cfa" to "BGGR (R/B swapped)"),
            spdParams = linkedMapOf("drift_nm" to "1.250", "drift_level" to "L1"),
            step4Params = linkedMapOf("r2" to "0.9876", "regression_method" to "log10")
        )
        val out = reportDataFromJson(src.toJsonString())!!
        assertEquals(src.step1Params, out.step1Params)
        assertEquals(src.spdParams, out.spdParams)
        assertEquals(src.step4Params, out.step4Params)
    }

    @Test
    fun stringMaps_emptyRoundTrip() {
        val out = reportDataFromJson(ReportData().toJsonString())!!
        assertEquals(emptyMap<String, String>(), out.step1Params)
        assertEquals(emptyMap<String, String>(), out.spdParams)
        assertEquals(emptyMap<String, String>(), out.step4Params)
    }

    @Test
    fun doublePairs_roundTrip() {
        val src = ReportData(curvePoints = listOf(0.5 to 12.25, 1.0 to 24.5, 2.0 to 48.125))
        val out = reportDataFromJson(src.toJsonString())!!
        assertEquals(src.curvePoints, out.curvePoints)
    }

    @Test
    fun intPairs_roundTrip() {
        val src = ReportData(peakWindows = listOf(11 to 22, 33 to 44, 55 to 66))
        val out = reportDataFromJson(src.toJsonString())!!
        assertEquals(src.peakWindows, out.peakWindows)
    }

    @Test
    fun floatLists_roundTrip() {
        val src = ReportData(
            spdLambda = listOf(400f, 400.5f, 401f),
            redProfile = listOf(-1.5f, 0.25f, 1023.125f),
            spdFused = listOf(0.125f, 0f, 12345.5f)
        )
        val out = reportDataFromJson(src.toJsonString())!!
        assertEquals(src.spdLambda, out.spdLambda)
        assertEquals(src.redProfile, out.redProfile)
        assertEquals(src.spdFused, out.spdFused)
    }

    // ─────────────────────────────────────────────────────────────────────
    // 4. 防遗漏护栏
    //
    // 序列化器最容易的腐烂方式：有人给 ReportData 加了字段、也更新了 ViewModel，
    // 但忘了改 ReportDataJson.kt —— 于是这个字段的取值在重开项目后永久丢失，
    // 且不产生任何编译错误或测试失败。下面三个用例专门堵这个洞。
    // ─────────────────────────────────────────────────────────────────────

    /**
     * [ReportData] 的全部属性名，从**类本身**反射出来，不是手写的清单。
     *
     * 用 `declaredFields` 而不是 `declaredMethods`：data class 的幕后字段名与属性名
     * 逐字一致，不需要从 `getXxx` / `isXxx` 反推 —— 后者对 `isLogFit` 这种本身以
     * `is` 开头的 Boolean 属性会推错。这样也就不必引入 `kotlin-reflect`：
     * 它的版本必须与 kotlin-stdlib 严格对齐，为一个断言多挂一个依赖不划算。
     */
    private fun reportDataPropertyNames(): Set<String> =
        ReportData::class.java.declaredFields
            .filter { f ->
                !f.isSynthetic &&
                    !f.name.contains('$') &&
                    !Modifier.isStatic(f.modifiers)
            }
            .map { it.name }
            .toSortedSet()

    private fun jsonKeys(raw: String): Set<String> =
        JSONObject(raw).keys().asSequence().toSortedSet()

    @Test
    fun guardrail_everyPropertyIsSerialized() {
        val properties = reportDataPropertyNames()
        val keys = jsonKeys(ReportData().toJsonString())

        // 契约：序列化出的键集合必须恰好是「全部属性名 + schema」，一个不多一个不少。
        assertEquals(
            "ReportData 的属性集合与序列化键集合不一致 —— 多半是加了字段却忘了改 " +
                "ReportDataJson.kt。缺少的键：${properties - keys}；" +
                "多余的键：${keys - properties - "schema"}",
            properties + "schema",
            keys
        )
        // 反射若失效（两边都变空集）上面那条会假通过，这里把规模钉在一个下界上。
        assertTrue(
            "反射只取到 ${properties.size} 个字段，护栏可能已经失效",
            properties.size >= 100
        )
    }

    @Test
    fun guardrail_detectsADroppedKey() {
        // 反向验证护栏有牙：人为删掉一个键，护栏必须能精确到**是哪一个字段**。
        val properties = reportDataPropertyNames()
        val pruned = jsonKeys(ReportData().toJsonString()) - "step1PresetName"
        assertEquals(
            "人为删掉 step1PresetName 后，护栏必须精确报出这个字段",
            setOf("step1PresetName"),
            properties - pruned
        )
    }

    @Test
    fun guardrail_reflectionActuallySeesTheFields() {
        // 再兜一层：钉死几个分布在不同区块的已知字段确实被反射看到了，
        // 防止过滤器写错导致集合为空、上面两条假通过。
        val properties = reportDataPropertyNames()
        assertTrue(properties.contains("wavelengthQuality"))
        assertTrue(properties.contains("step1PresetName"))
        assertTrue(properties.contains("spdFused"))
        assertTrue(properties.contains("isLogFit"))
        assertTrue(properties.contains("curvePoints"))
        assertTrue(properties.contains("step4Params"))
    }
}
