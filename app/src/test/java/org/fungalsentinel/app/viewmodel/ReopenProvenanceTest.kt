package org.fungalsentinel.app.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 【v2.2.8】从 History 重开项目时，Step 1/2/4 溯源的还原边界。
 *
 * `openProject()` 混了两种用途，二者的正确答案相反：
 *
 * | 项目状态        | 用途               | 溯源该不该被 live 覆盖 |
 * |-----------------|--------------------|------------------------|
 * | `IN_PROGRESS`   | 继续做没做完的项目 | **应该** —— 数据确实来自现场重算 |
 * | `FINISHED`      | 查看历史报告       | **不应该** —— 用户是来看上次那份报告的 |
 *
 * 同时还有一条边界：**只还原溯源（12 个字段），绝不还原数值**。
 * 数值必须跟当前真实状态一致，溯源是"这个项目当初是怎么来的"的历史事实。
 *
 * 与 `ReportProvenanceTest` 一样：ViewModel 依赖 Application + 两个 Room 库，
 * JVM 单测无法实例化，因此这里直接断言**生产代码里的纯函数** —— 即
 * `shouldRestoreProvenance()`（闸门）与 `ReportData.restoreProvenance()`（搬运），
 * 正是 `openProject()` 调用的那两个。
 */
class ReopenProvenanceTest {

    private val at = "2026-10-17 09:30:00"

    /** 快照里的溯源：Step 1/2/4 当初都是从预设导入的。 */
    private fun snapshotProvenance() = ReportData(
        step1Source = "Preset · Lab WL 2026",
        step1PresetName = "Lab WL 2026",
        step1PresetFile = "preset://7",
        step1ImportedAt = at,
        step1Params = linkedMapOf("slope_k" to "-2.738911", "cfa" to "BGGR (R/B swapped)"),

        spdSource = "Preset · Lab SPD",
        spdPresetName = "Lab SPD",
        spdPresetFile = "preset://3",
        spdImportedAt = at,
        spdParams = linkedMapOf("drift_nm" to "1.250", "drift_level" to "L1"),

        step4Source = "Preset · Lab Curve",
        step4PresetName = "Lab Curve",
        step4PresetFile = "preset://11",
        step4ImportedAt = at,
        step4Params = linkedMapOf("r2" to "0.9876", "regression_method" to "linear")
    )

    /**
     * 重开后被 `analyzePositioning()` / `analyzeSpd()` 覆盖过的溯源 ——
     * 即 `withLiveWavelength` / `withLiveSpd` 写入的现场标定值。
     */
    private fun liveProvenance() = ReportData(
        step1Source = "Live calibration",
        step1PresetName = null,
        step1PresetFile = null,
        step1ImportedAt = at,
        step1Params = linkedMapOf("slope_k" to "-2.710000", "intercept_b" to "1800.5000", "cfa" to "RGGB"),

        spdSource = "Live calibration · true_spd.csv (bundled)",
        spdPresetName = null,
        spdPresetFile = null,
        spdImportedAt = at,
        spdParams = linkedMapOf("spd_csv" to "true_spd.csv (bundled)"),

        step4Source = "Live standard curve",
        step4PresetName = null,
        step4PresetFile = null,
        step4ImportedAt = at,
        step4Params = linkedMapOf("r2" to "0.9910", "n_standards" to "5")
    )

    // ─────────────────────────────────────────────────────────────────────
    // 1. FINISHED + 曾导入预设 → 溯源保持 "Preset · …"
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun finishedProject_restoresPresetProvenance() {
        val snapshot = snapshotProvenance()
        val recomputed = liveProvenance()

        assertTrue("FINISHED 项目必须还原溯源", shouldRestoreProvenance("FINISHED", snapshot))

        val out = recomputed.restoreProvenance(snapshot)

        // Step 1
        assertEquals("Preset · Lab WL 2026", out.step1Source)
        assertEquals("Lab WL 2026", out.step1PresetName)
        assertEquals("preset://7", out.step1PresetFile)
        assertEquals(at, out.step1ImportedAt)

        // Step 2
        assertEquals("Preset · Lab SPD", out.spdSource)
        assertEquals("Lab SPD", out.spdPresetName)
        assertEquals("preset://3", out.spdPresetFile)
        assertEquals(at, out.spdImportedAt)

        // Step 4
        assertEquals("Preset · Lab Curve", out.step4Source)
        assertEquals("Lab Curve", out.step4PresetName)
        assertEquals("preset://11", out.step4PresetFile)
        assertEquals(at, out.step4ImportedAt)

        // 三个 *Params 是"带数值的溯源"，**刻意不在**还原清单里（12 个而非 15 个），
        // 必须保持重算写入的值。见 ProjectViewModel 里 restoreProvenance 的 KDoc。
        assertEquals(recomputed.step1Params, out.step1Params)
        assertEquals(recomputed.spdParams, out.spdParams)
        assertEquals(recomputed.step4Params, out.step4Params)
        assertNotEquals(snapshot.step1Params, out.step1Params)
        assertNotEquals(snapshot.spdParams, out.spdParams)
        assertNotEquals(snapshot.step4Params, out.step4Params)
    }

    /** 溯源字段必须**真的**来自快照，而不是碰巧与重算值相同。 */
    @Test
    fun finishedProject_restoreActuallyMovesTheValues() {
        val snapshot = snapshotProvenance()
        val recomputed = liveProvenance()

        for (pair in listOf(
            "step1Source" to (recomputed.step1Source to snapshot.step1Source),
            "spdSource" to (recomputed.spdSource to snapshot.spdSource),
            "step4Source" to (recomputed.step4Source to snapshot.step4Source),
            "step1PresetName" to (recomputed.step1PresetName to snapshot.step1PresetName),
            "spdPresetName" to (recomputed.spdPresetName to snapshot.spdPresetName),
            "step4PresetName" to (recomputed.step4PresetName to snapshot.step4PresetName)
        )) {
            assertNotEquals("前置条件：${pair.first} 的重算值与快照值必须先不同", pair.second.first, pair.second.second)
        }

        val out = recomputed.restoreProvenance(snapshot)
        assertEquals(snapshot.step1Source, out.step1Source)
        assertEquals(snapshot.spdSource, out.spdSource)
        assertEquals(snapshot.step4Source, out.step4Source)
    }

    // ─────────────────────────────────────────────────────────────────────
    // 2. IN_PROGRESS + 曾导入预设 → 溯源保持 live，不还原
    // （这条专门防止把闸门条件写反）
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun inProgressProject_doesNotRestore() {
        val snapshot = snapshotProvenance()

        assertFalse(
            "IN_PROGRESS 是继续做项目，溯源必须被现场重算覆盖",
            shouldRestoreProvenance("IN_PROGRESS", snapshot)
        )
        // 其余非 FINISHED 状态一并不还原，避免只判了 "IN_PROGRESS" 这一个字符串。
        assertFalse(shouldRestoreProvenance(null, snapshot))
        assertFalse(shouldRestoreProvenance("ARCHIVED", snapshot))
        assertFalse(shouldRestoreProvenance("FINISHED ", snapshot)) // 尾部空格不算
    }

    // ─────────────────────────────────────────────────────────────────────
    // 3. restored == null → 不还原，且还原函数本身不崩、不凭空造值
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun nullSnapshot_doesNotRestore() {
        assertFalse(shouldRestoreProvenance("FINISHED", null))
        assertFalse(shouldRestoreProvenance("IN_PROGRESS", null))
    }

    @Test
    fun restoreFromBlankProvenance_isInertAndNonNull() {
        // 即使快照里的溯源全为默认值，还原也应当如实照搬，不崩、不编造。
        val receiver = snapshotProvenance()
        val out = receiver.restoreProvenance(ReportData())

        assertEquals(STEP_SOURCE_NOT_RUN, out.step1Source)
        assertEquals(STEP_SOURCE_NOT_RUN, out.step4Source)
        assertNull(out.step1PresetName)
        assertNull(out.step1PresetFile)
        assertNull(out.step1ImportedAt)
        assertNull(out.spdPresetName)
        assertNull(out.step4PresetName)

        // 三个 *Params 不在还原清单内，应保持接收方原值，而不是被清成 emptyMap。
        assertEquals(receiver.step1Params, out.step1Params)
        assertEquals(receiver.spdParams, out.spdParams)
        assertEquals(receiver.step4Params, out.step4Params)
    }

    // ─────────────────────────────────────────────────────────────────────
    // 4. 最关键的一条：只还原溯源，绝不还原数值
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun restoreProvenance_leavesNumericFieldsAlone() {
        // current = 重开后由 analyzePositioning / analyzeSpd / analyzeSample 算出来的
        val recomputed = ReportData(
            slope = -2.71,
            intercept = 1800.5,
            gErrorNm = 0.123,
            wavelengthQuality = "Excellent",
            mappingFormula = "Pixel(p) = -2.710000 * Wavelength(nm) + 1800.5000",
            redProfile = listOf(1f, 2f, 3f),
            spdLambda = listOf(400f, 401f),
            spdFused = listOf(9f),
            spdStatus = "Calibrated",
            samplePeakNm = "509.0",
            correctedAreaValue = 0.5,
            curveSlope = 12.5,
            curveIntercept = 3.25,
            regressionFormula = "Unmixed area = 12.5000 * Conc + 3.25",
            predictedConcentration = "1.234",
            replicates = 3,
            // 三个 *Params 同样参与：重算值 vs 快照值
            step1Params = linkedMapOf("slope_k" to "-2.710000", "cfa" to "RGGB"),
            spdParams = linkedMapOf("spd_csv" to "true_spd.csv (bundled)"),
            step4Params = linkedMapOf("r2" to "0.9910")
        )
        // from = 快照，数值全部与重算结果不同
        val snapshot = ReportData(
            slope = -2.739,
            intercept = 1807.68,
            gErrorNm = 0.999,
            wavelengthQuality = "PRESET",
            mappingFormula = "Pixel(p) = -2.738911 * Wavelength(nm) + 1807.6813",
            redProfile = listOf(7f, 8f, 9f),
            spdLambda = listOf(700f),
            spdFused = listOf(1f),
            spdStatus = "Pending",
            samplePeakNm = "700.0",
            correctedAreaValue = 42.0,
            curveSlope = 99.0,
            curveIntercept = -5.0,
            regressionFormula = "来自快照的公式",
            predictedConcentration = "999.000",
            replicates = 9,
            // 溯源部分：来自预设
            step1Source = "Preset · Lab WL 2026",
            step1PresetName = "Lab WL 2026",
            step1PresetFile = "preset://7",
            step1ImportedAt = at,
            step1Params = linkedMapOf("slope_k" to "-2.738911", "cfa" to "BGGR (R/B swapped)"),
            spdSource = "Preset · Lab SPD",
            spdPresetName = "Lab SPD",
            spdPresetFile = "preset://3",
            spdImportedAt = at,
            spdParams = linkedMapOf("drift_nm" to "1.250"),
            step4Source = "Preset · Lab Curve",
            step4PresetName = "Lab Curve",
            step4PresetFile = "preset://11",
            step4ImportedAt = at,
            step4Params = linkedMapOf("r2" to "0.9876")
        )

        val out = recomputed.restoreProvenance(snapshot)

        // 逐条钉死：数值必须是**重算**的值，不是快照的值。
        assertEquals("slope 必须是重算值", recomputed.slope, out.slope)
        assertEquals("intercept 必须是重算值", recomputed.intercept, out.intercept)
        assertEquals("gErrorNm 必须是重算值", recomputed.gErrorNm, out.gErrorNm)
        assertEquals("wavelengthQuality 必须是重算值", recomputed.wavelengthQuality, out.wavelengthQuality)
        assertEquals("mappingFormula 必须是重算值", recomputed.mappingFormula, out.mappingFormula)
        assertEquals("redProfile 必须是重算值", recomputed.redProfile, out.redProfile)
        assertEquals("spdLambda 必须是重算值", recomputed.spdLambda, out.spdLambda)
        assertEquals("spdFused 必须是重算值", recomputed.spdFused, out.spdFused)
        assertEquals("spdStatus 必须是重算值", recomputed.spdStatus, out.spdStatus)
        assertEquals("samplePeakNm 必须是重算值", recomputed.samplePeakNm, out.samplePeakNm)
        assertEquals("correctedAreaValue 必须是重算值", recomputed.correctedAreaValue, out.correctedAreaValue)
        assertEquals("curveSlope 必须是重算值", recomputed.curveSlope, out.curveSlope)
        assertEquals("curveIntercept 必须是重算值", recomputed.curveIntercept, out.curveIntercept)
        assertEquals("regressionFormula 必须是重算值", recomputed.regressionFormula, out.regressionFormula)
        assertEquals("predictedConcentration 必须是重算值", recomputed.predictedConcentration, out.predictedConcentration)
        assertEquals("replicates 必须是重算值", recomputed.replicates, out.replicates)

        // 三个 *Params 同样是"带数值的溯源"，必须保持重算值。
        assertEquals("step1Params 必须是重算值", recomputed.step1Params, out.step1Params)
        assertEquals("spdParams 必须是重算值", recomputed.spdParams, out.spdParams)
        assertEquals("step4Params 必须是重算值", recomputed.step4Params, out.step4Params)

        // 最强断言：整份 reportData 等于「重算结果」只把那 12 个溯源字段换成快照的。
        // 这一条覆盖了剩下的几十个字段，任何越界搬运都会让它失败。
        assertEquals(
            "还原后必须恰好等于「重算结果 + 12 个快照溯源字段」，不多不少",
            recomputed.copy(
                step1Source = snapshot.step1Source,
                step1PresetName = snapshot.step1PresetName,
                step1PresetFile = snapshot.step1PresetFile,
                step1ImportedAt = snapshot.step1ImportedAt,
                spdSource = snapshot.spdSource,
                spdPresetName = snapshot.spdPresetName,
                spdPresetFile = snapshot.spdPresetFile,
                spdImportedAt = snapshot.spdImportedAt,
                step4Source = snapshot.step4Source,
                step4PresetName = snapshot.step4PresetName,
                step4PresetFile = snapshot.step4PresetFile,
                step4ImportedAt = snapshot.step4ImportedAt
            ),
            out
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // 5. Case B：只导预设、没拍定位图
    //
    // 这条钉死上面那张表右下一格 —— 剔除 *Params 之后，这类项目的报告里
    // **仍然看得到预设参数**。因为 `analyzePositioning()` 会在 :1228 直接
    // return，基线里的预设 params 根本没人覆盖。
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun caseB_presetOnlyNoFrames_stillShowsPresetParams() {
        val snapshot = snapshotProvenance()

        // ① 基线：openProject 以快照为底，只把 fluorophore / band 换成项目实体的值
        val baseline = reopenBaseline(snapshot, "EGFP", 500 to 540)

        // ② 定位图为空 → analyzePositioning() 在 :1228 直接 return，不碰任何字段
        //    （这一步无法在 JVM 上直接跑 ViewModel，此处以「未对 baseline 施加任何
        //     重算写入」来等价表达。）
        assertTrue("FINISHED 项目走还原路径", shouldRestoreProvenance("FINISHED", snapshot))

        // ③ 还原：只搬 12 个溯源字段，params 不搬 —— 于是基线里的预设值原样留下
        val out = baseline.restoreProvenance(snapshot)

        assertEquals("Case B 下 params 仍是预设值", snapshot.step1Params, out.step1Params)
        assertEquals("-2.738911", out.step1Params["slope_k"])
        assertEquals("BGGR (R/B swapped)", out.step1Params["cfa"])
        assertEquals(snapshot.spdParams, out.spdParams)
        assertEquals(snapshot.step4Params, out.step4Params)

        // 溯源行同样是预设
        assertEquals("Preset · Lab WL 2026", out.step1Source)
        assertEquals("Lab WL 2026", out.step1PresetName)
        assertEquals("preset://7", out.step1PresetFile)
        assertEquals(at, out.step1ImportedAt)

        // fluorophore / band 来自项目实体而非快照
        assertEquals("EGFP", out.sampleFluorophore)
        assertEquals("500–540 nm", out.sampleBand)
    }

    /** Case B 的另一半：快照为 null 时基线退回空白报告，不崩、不残留。 */
    @Test
    fun caseB_nullSnapshot_fallsBackToBlankReport() {
        val out = reopenBaseline(null, "MCHERRY", 590 to 630)
        assertEquals(ReportData().copy(sampleFluorophore = "MCHERRY", sampleBand = "590–630 nm"), out)
        assertEquals(STEP_SOURCE_NOT_RUN, out.step1Source)
        assertEquals(emptyMap<String, String>(), out.step1Params)
    }

    /** 基线必须原样保留快照内容，不能只挑一部分字段。 */
    @Test
    fun reopenBaseline_preservesTheWholeSnapshot() {
        val snapshot = snapshotProvenance().copy(
            slope = -2.739,
            redProfile = listOf(7f, 8f, 9f),
            regressionFormula = "来自快照的公式"
        )
        val out = reopenBaseline(snapshot, "YPET", 500 to 530)

        assertEquals(snapshot.copy(sampleFluorophore = "YPET", sampleBand = "500–530 nm"), out)
        assertEquals(-2.739, out.slope!!, 0.0)
        assertEquals(listOf(7f, 8f, 9f), out.redProfile)
        assertEquals("来自快照的公式", out.regressionFormula)
    }
}
