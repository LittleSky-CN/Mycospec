package org.fungalsentinel.app.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.fungalsentinel.app.FssaApplication
import org.fungalsentinel.app.R
import org.fungalsentinel.app.analysis.ConcentrationRegressor
import org.fungalsentinel.app.analysis.DriftLevel
import org.fungalsentinel.app.analysis.FrameProfile
import org.fungalsentinel.app.analysis.FrameProfileExtractor
import org.fungalsentinel.app.analysis.SampleAnalyzer
import org.fungalsentinel.app.analysis.SpectralCandidate
import org.fungalsentinel.app.analysis.SpectralUnmixer
import org.fungalsentinel.app.analysis.SpdCalibrationResult
import org.fungalsentinel.app.analysis.SpdLoader
import org.fungalsentinel.app.analysis.SpectralResponseCalibrator
import org.fungalsentinel.app.analysis.WavelengthCalibrator
import org.fungalsentinel.app.analysis.mappingDriftNm
import org.fungalsentinel.app.analysis.recomputeWith
import org.fungalsentinel.app.data.AppSettings
import org.fungalsentinel.app.data.AppPreferences
import org.fungalsentinel.app.data.model.CaptureEntity
import org.fungalsentinel.app.data.model.Fluorophore
import org.fungalsentinel.app.data.model.ProjectEntity
import org.fungalsentinel.app.data.model.StandardGroupEntity
import org.fungalsentinel.app.data.model.canonicalFluorophore
import org.fungalsentinel.app.data.model.reportDataFromJson
import org.fungalsentinel.app.data.model.toJsonString
import org.fungalsentinel.app.data.preset.CurvePresetPayload
import org.fungalsentinel.app.data.preset.CurveStandardPoint
import org.fungalsentinel.app.data.preset.PresetEntity
import org.fungalsentinel.app.data.preset.PresetTypes
import java.util.Locale
import kotlin.math.roundToInt

data class StandardGroupInput(
    val id: Int,
    val concentration: Double,
    val unit: String,
    val blankUris: List<Uri>,
    val sampleUris: List<Uri>
)

data class ReportData(
    val wavelengthQuality: String = "—",
    val wavelengthGError: String = "— nm",
    val mappingFormula: String = "—",
    val exposureTime: String = "—",
    val xRoi: String = "—",
    val trainingPoints: String = "—",
    val validationPoint: String = "—",
    val gResidual: String = "—",
    val slope: Double? = null,
    val intercept: Double? = null,
    val gErrorNm: Double? = null,
    val peakR: Double? = null,
    val peakG: Double? = null,
    val peakB: Double? = null,
    val peakWindows: List<Pair<Int, Int>> = emptyList(),
    val peakDiagnostics: String = "—",
    val roiStart: Int? = null,
    val roiEnd: Int? = null,
    val sensorWidth: Int? = null,
    val sensorHeight: Int? = null,
    val redProfile: List<Float> = emptyList(),
    val greenProfile: List<Float> = emptyList(),
    val blueProfile: List<Float> = emptyList(),
    val previewUri: String? = null,
    val wlR: Double = 622.5,
    val wlG: Double = 522.5,
    val wlB: Double = 462.5,
    val spdStatus: String = "Pending",
    val spdSource: String = STEP_SOURCE_NOT_RUN,
    val spdCoeffR: String = "—",
    val spdCoeffB: String = "—",
    val spdResponse: String = "—",
    val spdFrames: Int = 0,
    val spdExposure: String = "—",
    val spdEffectiveBand: String = "—",
    val spdMasked: String = "—",
    val spdHandoffs: String = "—",
    val spdAgreement: String = "—",
    val spdFusedPeak: String = "—",
    val spdPeaks: String = "—",
    val spdRawPeaks: String = "—",
    val spdCorrPeaks: String = "—",
    val spdMethodNote: String = "Numerator = Pos (independent LEDs); pointwise ops only; response-proportional weights (sum = 1)",
    val spdCrossingBG: Float = 483f,
    val spdCrossingGR: Float = 577f,
    val spdLambda: List<Float> = emptyList(),
    val spdPosR: List<Float> = emptyList(),
    val spdPosG: List<Float> = emptyList(),
    val spdPosB: List<Float> = emptyList(),
    val spdRespR: List<Float> = emptyList(),
    val spdRespG: List<Float> = emptyList(),
    val spdRespB: List<Float> = emptyList(),
    val spdCorrR: List<Float> = emptyList(),
    val spdCorrG: List<Float> = emptyList(),
    val spdCorrB: List<Float> = emptyList(),
    val spdWB: List<Float> = emptyList(),
    val spdWG: List<Float> = emptyList(),
    val spdWR: List<Float> = emptyList(),
    val spdFused: List<Float> = emptyList(),
    val sampleFluorophore: String = "—",
    val sampleBand: String = "—",
    val blankMeanArea: String = "—",
    val sampleMeanArea: String = "—",
    val sampleIntegratedArea: String = "—",
    val sampleSD: String = "—",
    val replicates: Int = 0,
    val integrationWindow: String = "—",
    val sampleSpectrum: List<Float> = emptyList(),
    val blankSpectrum: List<Float> = emptyList(),
    val correctedAreaValue: Double? = null,
    val samplePeakNm: String = "—",
    val samplePeakIntensity: String = "—",
    val sampleFwhm: String = "—",
    val sampleCentroid: String = "—",
    val standardRows: List<String> = emptyList(),
    val regressionFormula: String = "—",
    val regressionR2: String = "—",
    val predictedConcentration: String = "—",
    val curveSlope: Double? = null,
    val curveIntercept: Double? = null,
    val curvePoints: List<Pair<Double, Double>> = emptyList(),
    val curveWarnings: List<String> = emptyList(),
    /** 【v2.2.6】SPD preset 加载期的软提示（L2/L3），永不阻断。 */
    val spdWarnings: List<String> = emptyList(),
    val isLogFit: Boolean = false,
    val step1Status: String = "IDLE",
    val step2Status: String = "IDLE",
    val step3Status: String = "IDLE",
    val step4Status: String = "IDLE",

    // ─── Step 1 provenance ───
    val step1Source: String = STEP_SOURCE_NOT_RUN,
    val step1PresetName: String? = null,
    val step1PresetFile: String? = null,
    val step1ImportedAt: String? = null,
    val step1Params: Map<String, String> = emptyMap(),

    // ─── Step 2 provenance (spdSource 已存在，配齐其余) ───
    val spdPresetName: String? = null,
    val spdPresetFile: String? = null,
    val spdImportedAt: String? = null,
    val spdParams: Map<String, String> = emptyMap(),

    // ─── Step 4 provenance ───
    val step4Source: String = STEP_SOURCE_NOT_RUN,
    val step4PresetName: String? = null,
    val step4PresetFile: String? = null,
    val step4ImportedAt: String? = null,
    val step4Params: Map<String, String> = emptyMap()
)

data class ProjectUiState(
    val currentProject: ProjectEntity? = null,
    val positioningCaptures: List<Uri> = emptyList(),
    val spdCaptures: List<Uri> = emptyList(),
    val blankCaptures: List<Uri> = emptyList(),
    val sampleCaptures: List<Uri> = emptyList(),
    val standardGroups: List<StandardGroupInput> = emptyList(),
    val curveReferencePreset: CurvePresetPayload? = null,
    val activeCurvePreset: PresetEntity? = null,
    val reportData: ReportData = ReportData()
)

/**
 * 【溯源 v2.2.7】Step 1 / 2 / 4 的「这部分数据是从哪儿来的」写入器。
 *
 * 之所以抽成顶层纯函数而不是内联在 ViewModel 里：`ProjectViewModel` 依赖
 * `Application` + Room，JVM 单测无法实例化它；而这些写入器只吃 data class，
 * 因此可以在 `ReportProvenanceTest` 里对**生产代码本身**做断言。
 */
private fun nowStamp(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        .format(java.util.Date())

/** Room 里的 preset 没有真实文件路径，用稳定的行标识代替。 */
private fun presetFileRef(id: Long): String = "preset://$id"

/**
 * 步骤从未执行时报告里显示的来源。
 *
 * 不能再用 "Live calibration" / "Live standard curve" / "true_spd.csv (bundled)"
 * 之类的默认值：`ReportScreen` 无条件渲染 Source 行，未跑过的步骤会凭空声称自己是
 * 现场标定，比留空更具误导性。
 */
internal const val STEP_SOURCE_NOT_RUN = "Not run"

private fun step1Params(
    slope: Double?, intercept: Double?, roiStart: Int?, roiEnd: Int?, rbSwapped: Boolean?
): Map<String, String> = linkedMapOf(
    "slope_k" to (slope?.let { String.format(Locale.US, "%.6f", it) } ?: "—"),
    "intercept_b" to (intercept?.let { String.format(Locale.US, "%.4f", it) } ?: "—"),
    "roi" to (if (roiStart != null && roiEnd != null) "$roiStart-$roiEnd" else "—"),
    "rb_swapped" to (rbSwapped?.toString() ?: "unknown"),
    "cfa" to if (rbSwapped == true) "BGGR (R/B swapped)" else "RGGB"
)

internal fun ReportData.withWavelengthPreset(
    p: PresetEntity, at: String = nowStamp()
): ReportData = copy(
    step1Source = "Preset · ${p.name}",
    step1PresetName = p.name,
    step1PresetFile = presetFileRef(p.id),
    step1ImportedAt = at,
    step1Params = step1Params(p.slope, p.intercept, p.roiStart, p.roiEnd, p.rbSwapped)
)

/** 实时标定必须清掉 preset 痕迹，否则旧 preset 名字会残留在报告里。 */
internal fun ReportData.withLiveWavelength(
    slope: Double, intercept: Double, roiStart: Int?, roiEnd: Int?, rbSwapped: Boolean,
    at: String = nowStamp()
): ReportData = copy(
    step1Source = "Live calibration",
    step1PresetName = null,
    step1PresetFile = null,
    step1ImportedAt = at,
    step1Params = step1Params(slope, intercept, roiStart, roiEnd, rbSwapped)
)

internal fun ReportData.withSpdPreset(
    p: PresetEntity, driftNm: Float?, at: String = nowStamp()
): ReportData = copy(
    spdSource = "Preset · ${p.name}",
    spdPresetName = p.name,
    spdPresetFile = presetFileRef(p.id),
    spdImportedAt = at,
    spdParams = linkedMapOf(
        "drift_nm" to (driftNm?.let { String.format(Locale.US, "%.3f", it) } ?: "unknown"),
        "drift_level" to SpectralResponseCalibrator.driftLevel(driftNm).name
    )
)

/**
 * 实时 SPD 标定：来源标记成 "Live calibration"，但保留参考光谱文件名
 * （`true_spd.csv (bundled)`），否则报告会丢掉唯一能说明参考光谱的字段。
 */
internal fun ReportData.withLiveSpd(csvSource: String, at: String = nowStamp()): ReportData = copy(
    spdSource = "Live calibration · $csvSource",
    spdPresetName = null,
    spdPresetFile = null,
    spdImportedAt = at,
    spdParams = linkedMapOf("spd_csv" to csvSource)
)

/**
 * 【D1】还原 Step 2 的 preset 溯源。
 *
 * 场景：Step 1 加载波长 preset 后，`loadWavelengthPreset` 会用新映射级联重跑
 * `analyzeSpd()`，而 `analyzeSpd` 末尾的 `withLiveSpd` 会把 `spdPresetName/File`
 * 置 null、把 `spdSource` 改写成 "Live calibration · …"。这次重跑是**用新波长映射
 * 重算**，不是用户重新拍了灯，所以报告会静默丢掉 "Preset · X" 的来源。
 *
 * 只在重跑前确实存在 preset 溯源时才还原 —— 现场重跑（本来就是 live）不受影响。
 */
internal fun ReportData.restoreSpdPresetProvenance(before: ReportData): ReportData =
    if (before.spdPresetName != null) copy(
        spdSource = before.spdSource,
        spdPresetName = before.spdPresetName,
        spdPresetFile = before.spdPresetFile,
        spdImportedAt = before.spdImportedAt,
        spdParams = before.spdParams
    ) else this

/**
 * 【v2.2.8】重开项目时，是否该用落库快照里的溯源覆盖掉重算结果。
 *
 * `openProject()` 混了两种用途，正确答案相反：
 *
 * | 项目状态      | 用途               | 该不该覆盖                     |
 * |---------------|--------------------|--------------------------------|
 * | `IN_PROGRESS` | 继续做没做完的项目 | **应该** —— 数据来自现场重算   |
 * | `FINISHED`    | 查看历史报告       | **不应该** —— 用户是来看报告的 |
 *
 * 抽成纯函数是为了能在 JVM 上直接对「闸门」本身下断言：`ProjectViewModel` 依赖
 * `Application` + 两个 Room 库，单测里无法实例化，但闸门写反是这个修复最容易犯的错。
 */
internal fun shouldRestoreProvenance(status: String?, restored: ReportData?): Boolean =
    status == "FINISHED" && restored != null

/**
 * 【v2.2.8-D1】项目行（projects 表）的受保护写入口 —— 「快照 / 流程状态」的每一次
 * 「读 → 改 → 写」都必须在同一个临界区里跑完，否则会丢更新（lost-update）。
 *
 * ## 为什么必须互斥
 *
 * `projects` 行同时承载两件事：**报告快照**（Step 4 每出一次结果就存一次，保证用户
 * 跑完直接退 App 也不丢）与**流程状态**（Finish 时写 `FINISHED` / `finishedAt`）。
 * 两条写入路径各跑在 fire-and-forget 协程里，谁都可能读到一个已经被对方改过的行。
 * 各自凭手里的副本 `copy()` 回写时，**后写的那次会把先写的结果整体冲掉**：
 *
 * ```
 * persistReportSnapshot ：读出 ProjectEntity(status=IN_PROGRESS, …)   ← 陈旧
 * finishProject         ：写入 ProjectEntity(status=FINISHED,  …)
 * persistReportSnapshot ：回写 ProjectEntity(status=IN_PROGRESS, …)   ← 把 FINISHED 冲掉
 * ```
 *
 * 后果是**静默**的：不崩不报错，但 `shouldRestoreProvenance()` 要求
 * `status == "FINISHED"`，一旦被打回 `IN_PROGRESS`，从 History 重开时 Step 1/2/4
 * 的溯源就不会还原 —— 正是本次要治的那个症状，只是换了一条触发路径。
 *
 * ## 死锁分析（这里不能在外面再套一层同名的锁）
 *
 * 两个 `…Locked()` 函数是**自己拿锁**，而不是要求调用方持锁再进来：`Mutex` 不可
 * 重入，上层持锁后再调这里会立刻自锁。同理 `finishProjectLocked` 内部**不会**去
 * `join` 那个 fire-and-forget 的 `persistReportSnapshot()` 协程（它也可能正在等这把
 * 锁）。两条路径靠**共享同一把 `mutex`** 互斥，而不是靠「外层排队 + 内层等待」。
 *
 * 持锁期间会 `withContext(Dispatchers.IO)` 序列化快照（几十 KB）：锁随协程迁移，不
 * 会自锁；代价只是别的元数据写在序列化期间排队。这是有意接受的取舍 —— 比起临界区
 * 内读到陈旧行的正确性收益，几十毫秒排队可以忽略。
 *
 * ## 适用范围（别把话说满）
 *
 * 这里管的是「快照 / 流程状态」那两条会互相覆盖的路径 —— `finishProject()` 与
 * `persistReportSnapshot()`。第三个写入点 `setSampleFluorophore()`（改 fluorophore /
 * band 后回写）**没有**套进来：它自己持同一把 `projectMetadataMutex`，同样在锁内重读
 * 最新 uiState 之后再写（见该处注释），语义与本函数一致；只是它既不动快照也不动状态，
 * 硬套一个 `transform` 反而更绕。它若哪天也开始写快照，必须改走这里。
 *
 * @return 是否真的写了。`readProject()` 返回 null（项目已被清空 / 不存在）时为
 *         false，这时一次 `writeProject` 都不会发生。
 */
private suspend fun updateProjectLocked(
    mutex: Mutex,
    readProject: () -> ProjectEntity?,
    transform: suspend (ProjectEntity) -> ProjectEntity,
    writeProject: suspend (ProjectEntity) -> Unit
): Boolean = mutex.withLock {
    val current = readProject() ?: return@withLock false
    writeProject(transform(current))
    true
}

/**
 * 【v2.2.8-D1】把当前报告快照写回项目行（供 [ProjectViewModel.persistReportSnapshot]
 * 使用）。
 *
 * 除 `finishProject()` 之外还要在 Step 4 结果产出时再存一次：用户跑完标准曲线后很
 * 可能直接退出 App，根本不会走到 `finishProject()`。
 */
internal suspend fun persistReportSnapshotLocked(
    mutex: Mutex,
    readProject: () -> ProjectEntity?,
    serialize: suspend () -> String,
    writeProject: suspend (ProjectEntity) -> Unit
): Boolean = updateProjectLocked(
    mutex = mutex,
    readProject = readProject,
    transform = { project -> project.copy(reportDataJson = serialize()) },
    writeProject = writeProject
)

/**
 * 【v2.2.8-D1】原子完成「写快照 + 标记 FINISHED」（供 [ProjectViewModel.finishProject]
 * 使用）。
 *
 * 关键点是**在同一个临界区内**读出项目行并写回 `status` / `finishedAt`：只要这三者
 * 分离，飞行中的 [persistReportSnapshotLocked] 就有机会用陈旧副本把状态冲回去。
 *
 * 调用方负责顺序 —— 本函数返回（快照与状态都已落库）之后才可以清空 uiState。
 */
internal suspend fun finishProjectLocked(
    mutex: Mutex,
    readProject: () -> ProjectEntity?,
    serialize: suspend () -> String,
    writeProject: suspend (ProjectEntity) -> Unit
): Boolean = updateProjectLocked(
    mutex = mutex,
    readProject = readProject,
    transform = { project ->
        project.copy(
            status = "FINISHED",
            finishedAt = System.currentTimeMillis(),
            reportDataJson = serialize()
        )
    },
    writeProject = writeProject
)

/**
 * 【v2.2.8】把 Step 1/2/4 的**溯源**（共 12 个字段）从 [from] 搬过来。
 *
 * ## 刻意只搬这 12 个，不搬任何数值
 *
 * `slope` / `intercept` / `mappingFormula` / `redProfile` / `spdFused` /
 * `curveSlope` / `regressionFormula` / `predictedConcentration` 等一律保持调用方
 * （即 `analyzePositioning()` / `analyzeSpd()` / `analyzeSample()`）重算出来的
 * 结果。数值必须反映当前真实状态；溯源是「这个项目当初是怎么来的」的历史事实。
 * 两者职责不同，绑在一起还原就会让报告出现「数值是重算的、标签却说来自预设」的
 * 错位 —— 那正是 v2.2.7 刚修掉的溯源谎报。
 *
 * ## 为什么 `*Params` 也**不**在清单里（只有 12 个而不是 15 个，不是漏写）
 *
 * 三个 `Map<String, String>` 看着像溯源，其实**带数值**：`withLiveWavelength`
 * 会用重算出来的 slope / intercept 重写 `step1Params`（见 `step1Params()`，
 * `:202`），`withLiveSpd` / `withLiveCurve` 同理。若一并还原，报告里就会出现
 *
 * ```
 * step1Source          = "Preset · Lab WL 2026"   ← 还原的
 * step1Params[slope_k] = "-2.738911"              ← 还原的（预设值）
 * slope                = -2.710000                ← 重算值
 * ```
 *
 * 同一个报告里 params 的 slope 和正文的 slope 对不上。
 *
 * 剔除之后两种场景都自洽（无论是否拍过定位图）：
 *
 * | 场景                        | `step1Source`     | `step1Params[slope_k]`    | `slope`   |
 * |-----------------------------|-------------------|---------------------------|-----------|
 * | 拍过图 + 导过预设（会重算） | Preset · X（还原）| 重算值（未还原）          | 重算值    |
 * | 只导预设、没拍图（不重算）  | Preset · X（基线）| 预设值（基线未被覆盖）    | 预设值    |
 *
 * 注意第二行：没拍图时 `analyzePositioning()` 会在 `:1228` 直接 return，基线里的
 * 预设 params 没人覆盖，最终仍是预设值。**所以剔除 params 不会让"只导预设"的项目
 * 看不到预设参数** —— 看不到预设值的唯一情形是数值本来就被重算了，那时看不到才
 * 是对的。
 *
 * ## 与 `restoreSpdPresetProvenance` 的区别（因此**没有**合并）
 *
 * - 那一个是**有守卫**的（只在 `before.spdPresetName != null` 时还原），用于
 *   `loadWavelengthPreset` 的级联重跑 —— 那里本来就是现场操作，只在确实存在
 *   preset 溯源时才需要保护。
 * - 这一个**无条件**搬运这 12 个字段，因为 `shouldRestoreProvenance()` 已在上游
 *   把住闸门。若合并，D1 那条路径会连带把 Step 1 / Step 4 的溯源一起还原，越界。
 */
internal fun ReportData.restoreProvenance(from: ReportData): ReportData = copy(
    step1Source = from.step1Source,
    step1PresetName = from.step1PresetName,
    step1PresetFile = from.step1PresetFile,
    step1ImportedAt = from.step1ImportedAt,

    spdSource = from.spdSource,
    spdPresetName = from.spdPresetName,
    spdPresetFile = from.spdPresetFile,
    spdImportedAt = from.spdImportedAt,

    step4Source = from.step4Source,
    step4PresetName = from.step4PresetName,
    step4PresetFile = from.step4PresetFile,
    step4ImportedAt = from.step4ImportedAt
)

/**
 * 【v2.2.8】重开项目时的报告基线：以快照为底，再把 fluorophore / band 换成项目
 * 实体里的值（项目实体是这两项的权威来源，不信任快照）。
 *
 * 快照为 null 时退回空白报告 —— v4 之前落库的旧项目就是这条路径。
 *
 * 抽成纯函数是为了让「只导预设、没拍图」这条路径（Case B）能在 JVM 上断言：
 * 那条路径上 `analyzePositioning()` 会在 `:1228` 直接 return，最终报告**就是**
 * 这个基线的原值，基线若丢了 params 就没人补得回来。
 */
internal fun reopenBaseline(
    restored: ReportData?,
    fluorophore: String,
    band: Pair<Int, Int>
): ReportData = (restored ?: ReportData()).copy(
    sampleFluorophore = fluorophore,
    sampleBand = "${band.first}–${band.second} nm"
)

internal fun ReportData.withCurvePreset(
    p: PresetEntity, payload: CurvePresetPayload, at: String = nowStamp()
): ReportData = copy(
    step4Source = "Preset · ${p.name}",
    step4PresetName = p.name,
    step4PresetFile = presetFileRef(p.id),
    step4ImportedAt = at,
    step4Params = linkedMapOf(
        "slope" to String.format(Locale.US, "%.6f", payload.slope),
        "intercept" to String.format(Locale.US, "%.4f", payload.intercept),
        "r2" to String.format(Locale.US, "%.4f", payload.rSquared),
        "regression_method" to "linear",
        "n_standards" to payload.standardPoints.size.toString()
    )
)

internal fun ReportData.withLiveCurve(
    reg: ConcentrationRegressor.RegressionResult, nStandards: Int, at: String = nowStamp()
): ReportData = copy(
    step4Source = "Live standard curve",
    step4PresetName = null,
    step4PresetFile = null,
    step4ImportedAt = at,
    step4Params = linkedMapOf(
        "slope" to String.format(Locale.US, "%.6f", reg.slope),
        "intercept" to String.format(Locale.US, "%.4f", reg.intercept),
        "r2" to String.format(Locale.US, "%.4f", reg.r2),
        "regression_method" to if (reg.isLogarithmic) "log10" else "linear",
        "n_standards" to nStandards.toString()
    )
)

/** 用户主动清掉 curve preset 后，报告里不能再留着 "Preset · X"。 */
internal fun ReportData.withoutCurvePreset(): ReportData = copy(
    // 【D2】清掉 preset 后 Step 4 已无任何曲线数据，不能再声称 "Live standard curve"
    step4Source = STEP_SOURCE_NOT_RUN,
    step4PresetName = null,
    step4PresetFile = null,
    step4ImportedAt = null,
    step4Params = emptyMap()
)

/**
 * 【D4】SPD preset 实际没落地（没有任何可用采样点）时，报告不能留着 "Preset · X"。
 * 没有 preset 溯源可退回，因此回到中性未执行标记。
 */
internal fun ReportData.withoutSpdPreset(): ReportData = copy(
    spdSource = STEP_SOURCE_NOT_RUN,
    spdPresetName = null,
    spdPresetFile = null,
    spdImportedAt = null,
    spdParams = emptyMap()
)

class ProjectViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as FssaApplication).database.projectDao()
    private val presetDao = (application as FssaApplication).presetDatabase.presetDao()

    private val _uiState = MutableStateFlow(ProjectUiState())
    val uiState: StateFlow<ProjectUiState> = _uiState.asStateFlow()
    private val persistenceMutex = Mutex()
    private val projectMetadataMutex = Mutex()
    private val _settings = MutableStateFlow(AppSettings())

    private val _hasBlueFilter = MutableStateFlow(false)
    val hasBlueFilter: StateFlow<Boolean> = _hasBlueFilter.asStateFlow()

    val wlPresets: StateFlow<List<PresetEntity>> =
        presetDao.observeByType(PresetTypes.WAVELENGTH)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val spdPresets: StateFlow<List<PresetEntity>> =
        presetDao.observeByType(PresetTypes.SPD)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val curvePresets: StateFlow<List<PresetEntity>> =
        presetDao.observeByType(PresetTypes.CURVE)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var spdResult: SpdCalibrationResult? = null
    private var spdRbSwapRetried = false
    @Volatile
    private var analysisXRoi: Pair<Int, Int>? = null

    init {
        viewModelScope.launch {
            AppPreferences.getSettings(application).collect { _settings.value = it }
        }
        _uiState.value = _uiState.value.copy(
            reportData = _uiState.value.reportData.copy(
                step1Status = "IDLE|" + str(R.string.st_idle1),
                step2Status = "IDLE|" + str(R.string.st_idle2),
                step3Status = "IDLE|" + str(R.string.st_idle3),
                step4Status = "IDLE|" + str(R.string.st_idle4)
            )
        )
    }

    private fun str(id: Int, vararg a: Any): String = getApplication<Application>().getString(id, *a)
    private fun setStep1(s: String) { _uiState.value = _uiState.value.copy(reportData = _uiState.value.reportData.copy(step1Status = s)) }
    private fun setStep2(s: String) { _uiState.value = _uiState.value.copy(reportData = _uiState.value.reportData.copy(step2Status = s)) }
    private fun setStep3(s: String) { _uiState.value = _uiState.value.copy(reportData = _uiState.value.reportData.copy(step3Status = s)) }
    private fun setStep4(s: String) { _uiState.value = _uiState.value.copy(reportData = _uiState.value.reportData.copy(step4Status = s)) }

    private class DecodeBatch(val frames: List<FrameProfile>, val total: Int, val failed: List<String>)
    private fun note(b: DecodeBatch): String =
        if (b.failed.isEmpty()) str(R.string.st_decoded, b.total, b.total)
        else str(R.string.st_decoded, b.frames.size, b.total) + " (" +
                str(R.string.st_undecodable, b.failed.joinToString(", ")) + ")"

    private fun decode(ctx: Context, uris: List<Uri>): DecodeBatch {
        val ok = mutableListOf<FrameProfile>(); val bad = mutableListOf<String>()
        var roi = analysisXRoi
        var referenceIndex = -1
        var referenceFrame: FrameProfile? = null
        if (roi == null) {
            for (i in uris.indices) {
                val frame = FrameProfileExtractor.extract(ctx, uris[i]) ?: continue
                referenceIndex = i
                referenceFrame = frame
                roi = frame.xRoiStart to frame.xRoiEnd
                analysisXRoi = roi
                break
            }
        }
        uris.forEachIndexed { i, u ->
            val p = if (i == referenceIndex) referenceFrame
                else FrameProfileExtractor.extract(ctx, u, roi)
            if (p != null) ok.add(p) else bad.add("#${i + 1}")
        }
        return DecodeBatch(ok, uris.size, bad)
    }

    private fun swapRbChannels(frames: List<FrameProfile>): List<FrameProfile> =
        frames.map { it.copy(redProfile = it.blueProfile, blueProfile = it.redProfile) }

    fun createProject(name: String, hasBlueFilter: Boolean = false) {
        _hasBlueFilter.value = hasBlueFilter
        FrameProfileExtractor.forceRbSwap = false   // 【v2.2.6】新项目重置通道交换标志
        analysisXRoi = null
        spdRbSwapRetried = false
        viewModelScope.launch {
            val id = dao.insertProject(ProjectEntity(name = name))
            _uiState.value = ProjectUiState(currentProject = ProjectEntity(id = id, name = name))
        }
    }

    fun finishProject() {
        viewModelScope.launch {
            // 【v2.2.8】报告快照必须在清空 uiState **之前**落库 —— 那之后数据就没了。
            // 【v2.2.8-D1】这一步还必须与 Step 4 的快照写入共用同一把
            // projectMetadataMutex：否则飞在半路的 persistReportSnapshot() 会拿它读到的
            // 陈旧行把 status / finishedAt 冲回 IN_PROGRESS / null，
            // shouldRestoreProvenance() 的闸门随之关上，溯源再次丢失。
            // 详见 finishProjectLocked() 的 KDoc（含死锁分析）。
            val written = finishProjectLocked(
                mutex = projectMetadataMutex,
                readProject = { _uiState.value.currentProject },
                serialize = {
                    // 序列化是 60KB 量级（三条通道 profile + 六条 SPD 曲线），切到 IO。
                    withContext(Dispatchers.IO) { _uiState.value.reportData.toJsonString() }
                },
                writeProject = { updated -> dao.updateProject(updated) }
            )
            // 没有进行中的项目时维持原语义：一字不落库、一行状态也不动。
            if (!written) return@launch
            _uiState.value = ProjectUiState()
            _hasBlueFilter.value = false
            FrameProfileExtractor.forceRbSwap = false
            analysisXRoi = null
            spdResult = null
            spdRbSwapRetried = false
        }
    }

    fun openProject(projectId: Long, onReady: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val project = dao.getProjectById(projectId) ?: return@launch
                val pos = dao.getCapturesByStepOnce(projectId, "POSITIONING").map { Uri.parse(it.uri) }
                val spd = dao.getCapturesByStepOnce(projectId, "SPD").map { Uri.parse(it.uri) }
                val blank = dao.getCapturesByStepOnce(projectId, "BLANK").map { Uri.parse(it.uri) }
                val sample = dao.getCapturesByStepOnce(projectId, "SAMPLE").map { Uri.parse(it.uri) }
                val groups = dao.getStandardGroupsOnce(projectId).map { ge ->
                    StandardGroupInput(
                        ge.id.toInt(), ge.concentration, ge.unit,
                        dao.getCapturesByStepAndGroupOnce(projectId, "STANDARD_BLANK", ge.id).map { Uri.parse(it.uri) },
                        dao.getCapturesByStepAndGroupOnce(projectId, "STANDARD_SAMPLE", ge.id).map { Uri.parse(it.uri) }
                    )
                }
                _hasBlueFilter.value = false
                FrameProfileExtractor.forceRbSwap = false   // 【v2.2.6】先重置，再由 Step 1 重新判定
                analysisXRoi = null
                spdRbSwapRetried = false
                val band = fluorophoreBand(project.fluorophore)
                // 【v2.2.8】从落库快照恢复整份报告 —— 这是 Step 1/2/4 预设溯源的
                // 唯一载体（三个 load*Preset 都不写 captures 表）。
                // 恢复失败（v4 之前的旧项目 / 畸形 JSON / schema 不认识）→ restored 为
                // null，退回空白报告，行为与改动前一致，绝不因为旧数据崩 App。
                val restored = reportDataFromJson(project.reportDataJson)
                _uiState.value = ProjectUiState(
                    currentProject = project,
                    positioningCaptures = pos,
                    spdCaptures = spd,
                    blankCaptures = blank,
                    sampleCaptures = sample,
                    standardGroups = groups,
                    reportData = reopenBaseline(restored, project.fluorophore, band)
                )
                if (pos.isNotEmpty()) setStep1("PROCESSING|" + str(R.string.st_processing_frames, pos.size))
                if (spd.isNotEmpty()) setStep2("PROCESSING|" + str(R.string.st_processing_frames, spd.size))
                if (blank.isNotEmpty() || sample.isNotEmpty()) setStep3("PROCESSING|" + str(R.string.st_processing_frames, blank.size + sample.size))
                analyzePositioning()
                if (spd.isNotEmpty()) analyzeSpd()
                analyzeSample()
                // 【v2.2.8】上面三句会用现场重算把溯源覆盖成 "Live calibration"。
                // FINISHED 项目是**查看历史报告**，不是重做，所以把当初的溯源搬回来。
                // 只搬 12 个溯源字段，数值一律保持上面重算出来的结果。
                val snapshot = restored.takeIf { shouldRestoreProvenance(project.status, it) }
                if (snapshot != null) {
                    _uiState.value = _uiState.value.copy(
                        reportData = _uiState.value.reportData.restoreProvenance(snapshot)
                    )
                }
                val step = when {
                    pos.isEmpty() -> 1
                    spd.isEmpty() -> 2
                    sample.isEmpty() -> 3
                    else -> 4
                }
                withContext(Dispatchers.Main) { onReady(step) }
            }
        }
    }

    fun setPositioningCaptures(uris: List<Uri>) {
        analysisXRoi = null
        _uiState.value = _uiState.value.copy(positioningCaptures = uris)
        markLoadedCurveForReapply()
        setStep1("PROCESSING|" + str(R.string.st_processing_frames, uris.size))
        viewModelScope.launch(Dispatchers.IO) {
            persistAll(); analyzePositioning()
            if (_uiState.value.spdCaptures.isNotEmpty()) analyzeSpd()
            analyzeSample()
        }
    }

    fun setSpdCaptures(uris: List<Uri>) {
        _uiState.value = _uiState.value.copy(spdCaptures = uris)
        setStep2("PROCESSING|" + str(R.string.st_processing_frames, uris.size))
        viewModelScope.launch(Dispatchers.IO) { persistAll(); analyzeSpd() }
    }

    fun setBlankCaptures(uris: List<Uri>) {
        _uiState.value = _uiState.value.copy(blankCaptures = uris)
        markLoadedCurveForReapply()
        setStep3("PROCESSING|" + str(R.string.st_processing_frames, uris.size))
        viewModelScope.launch(Dispatchers.IO) { persistAll(); analyzeSample() }
    }

    fun setSampleCaptures(uris: List<Uri>) {
        _uiState.value = _uiState.value.copy(sampleCaptures = uris)
        markLoadedCurveForReapply()
        setStep3("PROCESSING|" + str(R.string.st_processing_frames, uris.size))
        viewModelScope.launch(Dispatchers.IO) { persistAll(); analyzeSample() }
    }

    fun setFluorophore(name: String) {
        // 唯一的写入点在这里卡成 canonical —— 保证 projects.fluorophore 落库的
        // 永远是 Fluorophore.X.name（存量数据本来就是 canonical，无需迁移）
        val canonical = requireKnownFluorophore(name, "the Step 3 selection").name
        val band = fluorophoreBand(canonical)
        val state = _uiState.value
        val changedFluorophore = state.reportData.sampleFluorophore != canonical
        val updatedProject = state.currentProject?.copy(fluorophore = canonical)
        _uiState.value = state.copy(
            currentProject = updatedProject,
            curveReferencePreset = if (changedFluorophore) null else state.curveReferencePreset,
            activeCurvePreset = if (changedFluorophore) null else state.activeCurvePreset,
            reportData = state.reportData.copy(
                sampleFluorophore = canonical,
                sampleBand = "${band.first}–${band.second} nm",
                standardRows = if (changedFluorophore) emptyList() else state.reportData.standardRows,
                regressionFormula = if (changedFluorophore) "—" else state.reportData.regressionFormula,
                regressionR2 = if (changedFluorophore) "—" else state.reportData.regressionR2,
                predictedConcentration = if (changedFluorophore) "—" else state.reportData.predictedConcentration,
                curveSlope = if (changedFluorophore) null else state.reportData.curveSlope,
                curveIntercept = if (changedFluorophore) null else state.reportData.curveIntercept,
                curvePoints = if (changedFluorophore) emptyList() else state.reportData.curvePoints,
                curveWarnings = if (changedFluorophore) emptyList() else state.reportData.curveWarnings,
                isLogFit = if (changedFluorophore) false else state.reportData.isLogFit
            )
        )
        if (changedFluorophore) setStep4("IDLE|" + str(R.string.st_idle4))
        viewModelScope.launch(Dispatchers.IO) {
            updatedProject?.let { expectedProject ->
                projectMetadataMutex.withLock {
                    val latestProject = _uiState.value.currentProject
                    if (latestProject?.id == expectedProject.id) dao.updateProject(latestProject)
                }
            }

            analyzeSample()
        }
    }

    private fun markLoadedCurveForReapply() {
        val current = _uiState.value
        if (current.activeCurvePreset == null) return
        _uiState.value = current.copy(
            reportData = current.reportData.copy(
                standardRows = emptyList(),
                regressionFormula = "—",
                regressionR2 = "—",
                predictedConcentration = "—",
                curveSlope = null,
                curveIntercept = null,
                curvePoints = emptyList(),
                curveWarnings = emptyList(),
                isLogFit = false
            )
        )
        setStep4("PRESET|" + str(R.string.st_curve_preset_reapply))
    }

    fun setStandardGroups(groups: List<StandardGroupInput>) {
        _uiState.value = _uiState.value.copy(standardGroups = groups)
        viewModelScope.launch(Dispatchers.IO) { persistAll() }
    }

    fun buildCurve(pendingGroup: StandardGroupInput? = null) {
        if (pendingGroup != null) {
            _uiState.value = _uiState.value.copy(
                standardGroups = _uiState.value.standardGroups + pendingGroup
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val currentState = _uiState.value
            if (currentState.activeCurvePreset != null) {
                applyCurvePreset(currentState)
                return@launch
            }
            runCatching {
                if (pendingGroup != null) persistAll()
                val st = _uiState.value
                val slope = st.reportData.slope ?: run { setStep4("BLOCKED|" + str(R.string.st_blocked_step1)); return@launch }
                val intercept = st.reportData.intercept ?: run { setStep4("BLOCKED|" + str(R.string.st_blocked_step1)); return@launch }
                if (st.standardGroups.size < 2) { setStep4("BLOCKED|" + str(R.string.st4_blocked_groups)); return@launch }

                val singleBlank = _settings.value.blankMode == "SINGLE"
                if (st.blankCaptures.isEmpty() || st.sampleCaptures.isEmpty()) {
                    setStep4("BLOCKED|" + str(R.string.st4_blocked_blank)); return@launch
                }

                setStep4("PROCESSING|" + str(R.string.st4_processing))
                val ctx = getApplication<Application>()
                val spd = spdResult
                val decodedGroups = st.standardGroups.map { g ->
                    val blankUris = if (singleBlank) st.blankCaptures else g.blankUris
                    val blankBatch = decode(ctx, blankUris)
                    val sampleBatch = decode(ctx, g.sampleUris)
                    Triple(g, blankBatch.frames, sampleBatch.frames)
                }
                val sampleBlankBatch = decode(ctx, st.blankCaptures)
                val unknownSampleBatch = decode(ctx, st.sampleCaptures)
                val sampleBlankFrames = sampleBlankBatch.frames
                val unknownSampleFrames = unknownSampleBatch.frames
                if (sampleBlankFrames.isEmpty() || unknownSampleFrames.isEmpty()) {
                    setStep4(
                        "FAILED|" + str(
                            R.string.st_failed_decode,
                            note(sampleBlankBatch) + "; " + note(unknownSampleBatch)
                        )
                    )
                    return@launch
                }

                data class CurveCandidate(
                    val points: List<Pair<Double, Double>>,
                    val rows: List<String>,
                    val sampleArea: Double,
                    val wavelengthsNm: List<Double>,
                    val referenceEmission: List<Double>,
                    val referenceAuc: Double,
                    val windowMinNm: Double,
                    val windowMaxNm: Double,
                    val rbSwapped: Boolean,
                    val units: List<String>
                )

                val (peakWavelength, peakFwhm) = pythonEmissionBand(st.reportData.sampleFluorophore)
                fun collectPoints(swapRb: Boolean): CurveCandidate {
                    val points = mutableListOf<Pair<Double, Double>>()
                    val rows = mutableListOf<String>()
                    val validGroups = decodedGroups.filter { it.second.isNotEmpty() && it.third.isNotEmpty() }
                    require(validGroups.isNotEmpty()) { "No standard group contains decodable blank and sample frames" }
                    val initialFrames = validGroups.first().third
                    val initialWidth = initialFrames.first().greenProfile.size
                    val wavelengths = SpectralUnmixer.wavelengthAxis(initialWidth, slope, intercept)
                    val candidates = validGroups.map { (group, blankFrames, sampleFrames) ->
                        val analysisBlank = if (swapRb) swapRbChannels(blankFrames) else blankFrames
                        val analysisSample = if (swapRb) swapRbChannels(sampleFrames) else sampleFrames
                        val sampleSpectra = SpectralUnmixer.extractChannelSpectra(
                            analysisSample, wavelengths, st.reportData.sampleFluorophore, spd
                        )
                        val blankSpectra = SpectralUnmixer.extractChannelSpectra(
                            analysisBlank, wavelengths, st.reportData.sampleFluorophore, spd
                        )
                        Triple(group, sampleSpectra, SpectralUnmixer.meanSpectra(blankSpectra))
                    }
                    val emissionMask = SpectralUnmixer.integrationMask(wavelengths, peakWavelength, peakFwhm)
                    val reference = SpectralUnmixer.selectReferenceEmission(
                        wavelengths,
                        candidates.map { (_, samples, blankMean) ->
                            SpectralCandidate(SpectralUnmixer.meanSpectra(samples), blankMean)
                        },
                        emissionMask
                    )
                    val referenceBlank = candidates[reference.candidateIndex].third
                    val unmixingMask = SpectralUnmixer.unmixingMask(
                        wavelengths, referenceBlank, peakWavelength
                    )
                    candidates.forEach { (group, sampleSpectra, blankMean) ->
                        val metrics = sampleSpectra.map { spectrum ->
                            SpectralUnmixer.unmixSpectrum(
                                spectrum, reference.emission, blankMean, unmixingMask
                            ).alpha * reference.auc
                        }
                        val meanMetric = metrics.average()
                        points.add(group.concentration to meanMetric)
                        rows.add(String.format(Locale.US, "%.3f %s → %.1f", group.concentration, group.unit, meanMetric))
                    }

                    val baseBlank = if (swapRb) swapRbChannels(sampleBlankFrames) else sampleBlankFrames
                    val baseSample = if (swapRb) swapRbChannels(unknownSampleFrames) else unknownSampleFrames
                    val unknownBlankSpectra = SpectralUnmixer.extractChannelSpectra(
                        baseBlank, wavelengths, st.reportData.sampleFluorophore, spd
                    )
                    val unknownSampleSpectra = SpectralUnmixer.extractChannelSpectra(
                        baseSample, wavelengths, st.reportData.sampleFluorophore, spd
                    )
                    val unknownBlankMean = SpectralUnmixer.meanSpectra(unknownBlankSpectra)
                    val sampleArea = unknownSampleSpectra.map { spectrum ->
                        SpectralUnmixer.unmixSpectrum(
                            spectrum, reference.emission, unknownBlankMean, unmixingMask
                        ).alpha * reference.auc
                    }.average()
                    val orderedIndices = wavelengths.indices.sortedBy { wavelengths[it] }
                    val windowWavelengths = wavelengths.filterIndexed { index, _ -> unmixingMask[index] }
                    require(windowWavelengths.size >= 3) { "Unmixing window must contain at least three wavelengths" }
                    return CurveCandidate(
                        points = points,
                        rows = rows,
                        sampleArea = sampleArea,
                        wavelengthsNm = orderedIndices.map { wavelengths[it] },
                        referenceEmission = orderedIndices.map { reference.emission[it] },
                        referenceAuc = reference.auc,
                        windowMinNm = windowWavelengths.min(),
                        windowMaxNm = windowWavelengths.max(),
                        rbSwapped = FrameProfileExtractor.forceRbSwap != swapRb,
                        units = validGroups.map { it.first.unit }
                    )
                }

                val initialSwap = spdRbSwapRetried
                var candidate = collectPoints(initialSwap)
                var regression = if (candidate.points.size >= 2) ConcentrationRegressor.fit(candidate.points) else null
                var retried = false
                if (regression == null) {
                    val retryCandidate = collectPoints(!initialSwap)
                    val retryReg = if (retryCandidate.points.size >= 2) {
                        ConcentrationRegressor.fit(retryCandidate.points)
                    } else null
                    if (retryReg != null) {
                        candidate = retryCandidate
                        regression = retryReg
                        retried = true
                    }
                }
                val points = candidate.points
                val rows = candidate.rows
                if (points.size < 2) { setStep4("FAILED|" + str(R.string.st4_failed_groups, points.size)); return@launch }

                val reg = regression ?: run {
                    setStep4("FAILED|" + str(R.string.st4_failed_reg)); return@launch
                }
                val useLogFit = reg.isLogarithmic

                val sampleArea = candidate.sampleArea
                val predictedConc = ConcentrationRegressor.predict(reg, sampleArea)

                val warnings = reg.warnings.toMutableList()
                // 过曝/信号反转检测：浓度升高但信号显著下降
                val sortedPoints = points.sortedBy { it.first }
                var saturationDetected = false
                for (i in 1 until sortedPoints.size) {
                    if (sortedPoints[i].first > sortedPoints[i - 1].first &&
                        sortedPoints[i].second < sortedPoints[i - 1].second * 0.8
                    ) { saturationDetected = true; break }
                }
                if (saturationDetected)
                    warnings.add("Signal drops at high concentration — possible sensor saturation (overexposure). Please reduce exposure time.")
                if (points.any { it.second < 0.0 }) warnings.add("Negative standard area(s) — check blank pairing / SPD correction")
                if (sampleArea < 0.0) warnings.add("Negative sample area — prediction unreliable")
                if (reg.r2 < 0.95) warnings.add("Low R² (<0.95) — curve may not be reliable")
                if (reg.slope < 0 && !useLogFit) warnings.add("Negative slope detected — check for overexposure or incorrect blank subtraction.")
                if (predictedConc < 0) warnings.add("Negative prediction — check blank / calibration")
                val xs = points.map { it.first }
                if (xs.isNotEmpty() && (predictedConc < xs.min() || predictedConc > xs.max())) {
                    warnings.add("Prediction outside calibration range")
                }

                val formulaStr = if (useLogFit) {
                    String.format(Locale.US, "Unmixed area = %.4f * log10(Conc) + %.2f", reg.slope, reg.intercept)
                } else {
                    String.format(Locale.US, "Unmixed area = %.4f * Conc + %.2f", reg.slope, reg.intercept)
                }
                val orderedPresetPoints = candidate.points.indices
                    .sortedBy { candidate.points[it].first }
                    .map { index ->
                        CurveStandardPoint(
                            concentration = candidate.points[index].first,
                            signal = candidate.points[index].second,
                            unit = candidate.units[index]
                        )
                    }
                val spdJson = spd?.let { SpectralResponseCalibrator.toJson(it) }
                require(spdJson == null || spdJson != "{}") { "Could not serialize SPD calibration for the curve preset" }
                val curveReferencePreset = CurvePresetPayload(
                    fluorophore = st.reportData.sampleFluorophore,
                    slope = reg.slope,
                    intercept = reg.intercept,
                    rSquared = reg.r2,
                    standardPoints = orderedPresetPoints,
                    wavelengthsNm = candidate.wavelengthsNm,
                    referenceEmission = candidate.referenceEmission,
                    referenceAuc = candidate.referenceAuc,
                    windowMinNm = candidate.windowMinNm,
                    windowMaxNm = candidate.windowMaxNm,
                    rbSwapped = candidate.rbSwapped,
                    spdCalibrationJson = spdJson
                ).also { it.validate() }

                _uiState.value = _uiState.value.copy(
                    curveReferencePreset = curveReferencePreset,
                    activeCurvePreset = null,
                    reportData = _uiState.value.reportData.copy(
                        standardRows = rows,
                        regressionFormula = formulaStr,
                        regressionR2 = String.format(Locale.US, "%.4f", reg.r2),
                        predictedConcentration = String.format(Locale.US, "%.3f", predictedConc),
                        curveSlope = reg.slope,
                        curveIntercept = reg.intercept,
                        curvePoints = points.sortedBy { it.first },
                        curveWarnings = warnings,
                        isLogFit = useLogFit
                    ).withLiveCurve(reg, points.size)
                )
                persistReportSnapshot()
                setStep4(
                    "COMPUTED|" + str(R.string.st4_computed, String.format(Locale.US, "%.4f", reg.r2), points.size) +
                            (if (useLogFit) " (Log-Fit)" else "") +
                            (if (warnings.isNotEmpty()) str(R.string.st_warn_count, warnings.size) else "") +
                            (if (retried) " [R/B swap retry]" else "")
                )
            }.onFailure { e ->
                setStep4("FAILED|" + (e.message ?: e.javaClass.simpleName))
                _uiState.value = _uiState.value.copy(
                    reportData = _uiState.value.reportData.copy(
                        regressionFormula = "Analysis error: ${e.javaClass.simpleName}"
                    )
                )
            }
        }
    }

    private fun applyCurvePreset(st: ProjectUiState) {
        val preset = st.curveReferencePreset
        val report = st.reportData
        runCatching {
            require(preset != null) { "Loaded curve preset data is missing" }
            val mappingSlope = report.slope ?: error("Complete wavelength calibration before applying the curve")
            val mappingIntercept = report.intercept ?: error("Complete wavelength calibration before applying the curve")
            require(st.blankCaptures.isNotEmpty() && st.sampleCaptures.isNotEmpty()) {
                "Capture a current blank and sample in Step 3 before applying the curve"
            }
            // 跨端 preset 的 fluorophore 可能是 "mCherry" 这类非 canonical 写法，
            // 一律先解析；解析不出来就报错，不要静默走到错误的通道上
            val curveFluorophore = requireKnownFluorophore(preset.fluorophore, "this curve preset")
            val selectedFluorophore = canonicalFluorophore(report.sampleFluorophore)
            require(curveFluorophore == selectedFluorophore) {
                "This curve is for ${curveFluorophore.displayName}; select that fluorophore in Step 3"
            }
            setStep4("PROCESSING|" + str(R.string.st4_processing_preset))
            val context = getApplication<Application>()
            val blankBatch = decode(context, st.blankCaptures)
            val sampleBatch = decode(context, st.sampleCaptures)
            require(blankBatch.frames.isNotEmpty() && sampleBatch.frames.isNotEmpty()) {
                str(R.string.st_failed_decode, note(blankBatch) + "; " + note(sampleBatch))
            }
            val framesForAxis = sampleBatch.frames.first()
            val wavelengths = SpectralUnmixer.wavelengthAxis(
                framesForAxis.greenProfile.size, mappingSlope, mappingIntercept)
            val additionalSwap = preset.rbSwapped != FrameProfileExtractor.forceRbSwap
            val analysisBlank = if (additionalSwap) swapRbChannels(blankBatch.frames) else blankBatch.frames
            val analysisSample = if (additionalSwap) swapRbChannels(sampleBatch.frames) else sampleBatch.frames
            val spd = preset.spdCalibrationJson?.let {
                SpectralResponseCalibrator.fromJson(it)
                    ?: error("Saved SPD calibration in this curve preset is invalid")
            }
            val blankSpectra = SpectralUnmixer.extractChannelSpectra(
                analysisBlank, wavelengths, curveFluorophore.name, spd)
            val sampleSpectra = SpectralUnmixer.extractChannelSpectra(
                analysisSample, wavelengths, curveFluorophore.name, spd)
            val blankMean = SpectralUnmixer.meanSpectra(blankSpectra)
            val emission = preset.emissionAt(wavelengths)
            val unmixingMask = BooleanArray(wavelengths.size) { index ->
                wavelengths[index] in preset.windowMinNm..preset.windowMaxNm
            }
            require(wavelengths.min() <= preset.windowMinNm && wavelengths.max() >= preset.windowMaxNm) {
                "Current wavelength calibration does not fully cover the preset's unmixing window"
            }
            require(unmixingMask.count { it } >= 3) {
                "Current wavelength calibration does not cover the preset's unmixing window"
            }
            val sampleMetrics = sampleSpectra.map { spectrum ->
                SpectralUnmixer.unmixSpectrum(
                    spectrum, emission, blankMean, unmixingMask
                ).alpha * preset.referenceAuc
            }
            val sampleArea = sampleMetrics.average()
            require(sampleArea.isFinite()) { "Sample signal is not finite" }
            val regression = ConcentrationRegressor.RegressionResult(
                preset.slope, preset.intercept, preset.rSquared, false, emptyList())
            val predicted = ConcentrationRegressor.predict(regression, sampleArea)
            require(predicted.isFinite()) { "Predicted concentration is not finite" }
            val points = preset.standardPoints.map { it.concentration to it.signal }
            val warnings = buildList {
                if (preset.rSquared < 0.95) add("Low R² (<0.95) — curve may not be reliable")
                if (sampleArea < 0.0) add("Negative sample area — prediction unreliable")
                if (predicted < 0.0) add("Negative prediction — check blank / calibration")
                if (predicted < points.minOf { it.first } || predicted > points.maxOf { it.first }) {
                    add("Prediction outside calibration range")
                }
            }
            val formula = String.format(
                Locale.US, "Unmixed area = %.4f * Conc + %.2f", preset.slope, preset.intercept)
            val rows = preset.standardPoints.map {
                String.format(Locale.US, "%.3f %s → %.1f", it.concentration, it.unit, it.signal)
            }
            _uiState.value = _uiState.value.copy(
                curveReferencePreset = preset.copy(fluorophore = curveFluorophore.name),
                reportData = _uiState.value.reportData.copy(
                    standardRows = rows,
                    regressionFormula = formula,
                    regressionR2 = String.format(Locale.US, "%.4f", preset.rSquared),
                    predictedConcentration = String.format(Locale.US, "%.3f", predicted),
                    curveSlope = preset.slope,
                    curveIntercept = preset.intercept,
                    curvePoints = points,
                    curveWarnings = warnings,
                    isLogFit = false
                )
            )
            setStep4(
                "COMPUTED|" + str(
                    R.string.st4_preset_applied,
                    String.format(Locale.US, "%.4f", preset.rSquared),
                    sampleMetrics.size
                ) + if (warnings.isNotEmpty()) str(R.string.st_warn_count, warnings.size) else ""
            )
        }.onFailure { error ->
            setStep4("FAILED|" + (error.message ?: error.javaClass.simpleName))
        }
    }

    fun saveCurvePreset(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val state = _uiState.value
                val payload = state.curveReferencePreset
                    ?: error("Build a Step 4 standard curve before saving a preset")
                val report = state.reportData
                val slope = report.curveSlope ?: error("Build a Step 4 standard curve before saving a preset")
                val intercept = report.curveIntercept ?: error("Build a Step 4 standard curve before saving a preset")
                require(name.isNotBlank()) { "Preset name cannot be empty" }
                payload.validate()
                presetDao.insert(
                    PresetEntity(
                        name = name.trim(),
                        type = PresetTypes.CURVE,
                        deviceModel = Build.MODEL,
                        slope = slope,
                        intercept = intercept,
                        quality = String.format(Locale.US, "%.4f", payload.rSquared),
                        spdSource = payload.fluorophore,
                        payloadJson = payload.toJson()
                    )
                )
            }.onFailure { error ->
                setStep4("FAILED|" + (error.message ?: error.javaClass.simpleName))
            }
        }
    }

    fun loadCurvePreset(presetEntity: PresetEntity) {
        runCatching {
            require(presetEntity.type == PresetTypes.CURVE) { "This is not a curve preset" }
            val payload = CurvePresetPayload.fromJson(
                presetEntity.payloadJson ?: error("Curve preset data is missing"))
            payload.spdCalibrationJson?.let {
                require(SpectralResponseCalibrator.fromJson(it) != null) {
                    "Saved SPD calibration in this curve preset is invalid"
                }
            }
            // Python FSSA 导出的 preset 带的是它自己的显示名（"mCherry"、
            // "Fluorescein (sodium salt)"…），先解析成 canonical 再往下走；
            // 解析不出来就明确报错，绝不停留在"看起来加载成功了"的状态
            val payloadFluorophore = requireKnownFluorophore(payload.fluorophore, "this curve preset")
            val selectedFluorophore = canonicalFluorophore(_uiState.value.reportData.sampleFluorophore)
            require(payloadFluorophore == selectedFluorophore) {
                "This curve is for ${payloadFluorophore.displayName}; select that fluorophore in Step 3"
            }
            val current = _uiState.value
            _uiState.value = current.copy(
                curveReferencePreset = payload.copy(fluorophore = payloadFluorophore.name),
                activeCurvePreset = presetEntity,
                reportData = current.reportData.copy(
                    standardRows = emptyList(),
                    regressionFormula = "—",
                    regressionR2 = "—",
                    predictedConcentration = "—",
                    curveSlope = null,
                    curveIntercept = null,
                    curvePoints = emptyList(),
                    curveWarnings = emptyList(),
                    isLogFit = false
                ).withCurvePreset(presetEntity, payload)
            )
            persistReportSnapshot()
            setStep4("PRESET|" + str(R.string.st_curve_preset_loaded, presetEntity.name))
        }.onFailure { error ->
            setStep4("FAILED|" + (error.message ?: error.javaClass.simpleName))
        }
    }

    fun clearCurvePreset() {
        val current = _uiState.value
        _uiState.value = current.copy(
            activeCurvePreset = null,
            curveReferencePreset = null,
            reportData = current.reportData.copy(
                standardRows = emptyList(),
                regressionFormula = "—",
                regressionR2 = "—",
                predictedConcentration = "—",
                curveSlope = null,
                curveIntercept = null,
                curvePoints = emptyList(),
                curveWarnings = emptyList(),
                isLogFit = false
            ).withoutCurvePreset()
        )
        setStep4("IDLE|" + str(R.string.st_idle4))
    }

    fun saveWavelengthPreset(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = _uiState.value.reportData
            presetDao.insert(
                PresetEntity(
                    name = name, type = PresetTypes.WAVELENGTH, deviceModel = Build.MODEL,
                    slope = r.slope, intercept = r.intercept, gErrorNm = r.gErrorNm,
                    quality = r.wavelengthQuality, roiStart = r.roiStart, roiEnd = r.roiEnd,
                    sensorWidth = r.sensorWidth, sensorHeight = r.sensorHeight,
                    peakR = r.peakR?.roundToInt(), peakG = r.peakG?.roundToInt(), peakB = r.peakB?.roundToInt(),
                    wlR = r.wlR, wlG = r.wlG, wlB = r.wlB,
                    exposureSeconds = r.exposureTime.removeSuffix(" s").toDoubleOrNull(),
                    // 【v2.2.6】DEF-A01：真值来自 analyzePositioning() 已置位的解码器标志
                    rbSwapped = FrameProfileExtractor.forceRbSwap
                )
            )
        }
    }

    fun saveSpdPreset(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = _uiState.value.reportData
            val result = spdResult
            presetDao.insert(
                PresetEntity(
                    name = name, type = PresetTypes.SPD, deviceModel = Build.MODEL,
                    coeffR = r.spdCoeffR.toDoubleOrNull(), coeffB = r.spdCoeffB.toDoubleOrNull(),
                    frames = r.spdFrames,
                    exposureSeconds = r.spdExposure.removeSuffix(" s").toDoubleOrNull(),
                    spdSource = r.spdSource,
                    // 【v2.2.6】DEF-A04：溯源字段冗余写进顶层列（payloadJson 里也有一份）
                    slope = result?.slope?.takeIf { !it.isNaN() } ?: r.slope,
                    intercept = result?.intercept?.takeIf { !it.isNaN() } ?: r.intercept,
                    gErrorNm = result?.gErrorNm ?: r.gErrorNm,
                    rbSwapped = result?.rbSwapped,
                    payloadJson = result?.let { SpectralResponseCalibrator.toJson(it) }
                )
            )
        }
    }

    fun loadWavelengthPreset(p: PresetEntity) {
        val slope = p.slope ?: return
        val intercept = p.intercept ?: return
        // 【v2.2.6】DEF-A01：恢复通道交换结论，否则 MCHERRY / CFP / mTurquoise2 读错通道
        FrameProfileExtractor.forceRbSwap = p.rbSwapped ?: false
        // 【v2.2.6】DEF-A02：ROI 真正下发。analysisXRoi 非 null 后 decode() 会把它作为
        // fixedXRegion 传给 FrameProfileExtractor.extract，从而跳过内容相关的
        // autoDetectXRoi —— 定位图（亮 LED）与样品图（弱荧光）不再算出不同 ROI。
        val rs = p.roiStart
        val re = p.roiEnd
        val restoredRoi = if (rs != null && re != null && rs <= re) rs to re else null
        if (restoredRoi != null) analysisXRoi = restoredRoi
        _uiState.value = _uiState.value.copy(
            reportData = _uiState.value.reportData.copy(
                wavelengthQuality = "PRESET",
                wavelengthGError = p.gErrorNm?.let { String.format(Locale.US, "%+.3f nm", it) } ?: "—",
                mappingFormula = String.format(Locale.US, "Pixel(p) = %.6f * Wavelength(nm) + %.4f", slope, intercept),
                exposureTime = p.exposureSeconds?.let { String.format(Locale.US, "%.4f s", it) } ?: "—",
                xRoi = if (p.roiStart != null && p.roiEnd != null)
                    "[${p.roiStart}, ${p.roiEnd}] (Width: ${p.roiEnd - p.roiStart + 1} px)" else "—",
                trainingPoints = "B(${p.wlB ?: 462.5}nm), R(${p.wlR ?: 622.5}nm)",
                validationPoint = "G(${p.wlG ?: 522.5}nm)",
                gResidual = "—",
                slope = slope, intercept = intercept, gErrorNm = p.gErrorNm,
                peakR = p.peakR?.toDouble(), peakG = p.peakG?.toDouble(), peakB = p.peakB?.toDouble(),
                peakWindows = emptyList(),
                peakDiagnostics = "Loaded from preset (peaks rounded to px)",
                roiStart = p.roiStart, roiEnd = p.roiEnd,
                sensorWidth = p.sensorWidth, sensorHeight = p.sensorHeight,
                wlR = p.wlR ?: 622.5, wlG = p.wlG ?: 522.5, wlB = p.wlB ?: 462.5,
                previewUri = null,
                redProfile = emptyList(), greenProfile = emptyList(), blueProfile = emptyList()
            ).withWavelengthPreset(p)
        )
        markLoadedCurveForReapply()
        setStep1(
            "PRESET|" + str(R.string.st_preset_loaded, p.quality ?: "—") +
                    (if (p.rbSwapped == true) " · " + str(R.string.st_preset_rb_restored) else "") +
                    (if (restoredRoi != null) " · " + str(
                        R.string.st_preset_roi_restored, restoredRoi.first, restoredRoi.second
                    ) else "")
        )
        // 【D1】快照 Step 2 溯源：下面的级联重跑会用新映射重算 SPD，但那不是用户
        // 重新拍灯，不能把 "Preset · X" 抹成 "Live calibration"。
        val spdProvenanceBeforeRerun = _uiState.value.reportData
        viewModelScope.launch(Dispatchers.IO) {
            if (_uiState.value.spdCaptures.isNotEmpty()) {
                analyzeSpd()
                _uiState.update { st ->
                    st.copy(
                        reportData = st.reportData.restoreSpdPresetProvenance(spdProvenanceBeforeRerun)
                    )
                }
            }
            analyzeSample()
        }
    }

    fun loadSpdPreset(p: PresetEntity) {
        val payload = p.payloadJson
        if (payload == null) {
            // 【v2.2.6】DEF-A03：没有 payload 必须显式失败，不能再伪装成"已加载"
            failSpdPreset(str(R.string.st_spd_no_payload))
            return
        }
        val restored = runCatching { SpectralResponseCalibrator.fromJsonStrict(payload) }
            .onFailure { e ->
                failSpdPreset(e.message ?: e.javaClass.simpleName)
            }.getOrNull() ?: return

        val report = _uiState.value.reportData
        val slope = report.slope
        val intercept = report.intercept
        val warnings = mutableListOf<String>()
        var finalResult = restored
        var recomputed = false
        var driftNm: Float? = null

        if (slope != null && intercept != null) {
            val currentN = report.sensorHeight ?: restored.totalCount

            // 【v2.2.6】Mode B 优先：先用当前映射重算，再做覆盖度校验。
            // 顺序很关键 —— 若先做 L2，几纳米的漂移就会让轴端点越界而被硬拒绝，
            // 与"漂移永不拒绝"的裁决冲突。重算后 preset 自带当前轴，L2 自然通过。
            val drift = restored.mappingDriftNm(slope, intercept)
            driftNm = drift
            val lamp = restored.lampR
            val lampReady = lamp != null && lamp.size == currentN
            if (lampReady && (drift == null || drift > SpectralResponseCalibrator.DRIFT_WARN_NM)) {
                val rebuilt = restored.recomputeWith(slope, intercept)
                if (rebuilt != null) {
                    finalResult = rebuilt
                    recomputed = true
                    warnings.add(str(R.string.st_spd_recomputed))
                }
            } else if (drift != null && drift > SpectralResponseCalibrator.DRIFT_WARN_NM) {
                warnings.add(str(R.string.st_spd_legacy_no_recompute))
            }

            // L3 -- drift is a soft hint, it NEVER rejects the preset.
            when (SpectralResponseCalibrator.driftLevel(drift)) {
                DriftLevel.WARN -> warnings.add(str(R.string.st_spd_drift_warn, drift!!))
                DriftLevel.SEVERE -> warnings.add(str(R.string.st_spd_drift_severe, drift!!))
                else -> Unit
            }

            // L2 -- coverage is the only remaining hard failure.
            if (!recomputed) {
                val coverage = SpectralResponseCalibrator.coverageError(finalResult, slope, intercept, currentN)
                if (coverage != null) {
                    failSpdPreset(coverage)
                    return
                }
            }
        }

        spdRbSwapRetried = finalResult.rbSwapped
        spdResult = finalResult
        val applied = applySpdResult(finalResult.copy(spdSource = p.spdSource ?: finalResult.spdSource))
        if (!applied) {
            // 【D4】一个采样点都没落地 ⇒ preset 实际未生效。此时若继续写
            // withSpdPreset，报告会显示 "Preset · X" 而 SPD 数据其实全空。
            spdRbSwapRetried = false
            spdResult = null
            _uiState.value = _uiState.value.copy(
                reportData = _uiState.value.reportData.withoutSpdPreset()
            )
            setStep2("FAILED|" + str(R.string.st_spd_preset_invalid, str(R.string.st_spd_not_applied)))
            return
        }
        _uiState.value = _uiState.value.copy(
            reportData = _uiState.value.reportData
                .withSpdPreset(p, driftNm)
                .copy(spdWarnings = warnings)
        )
        setStep2(
            "PRESET|" + str(R.string.st_preset_loaded, p.name) +
                    (if (recomputed) " [recomputed for current mapping]" else "") +
                    (if (warnings.isNotEmpty()) str(R.string.st_warn_count, warnings.size) else "")
        )
    }

    /** 【v2.2.6】SPD preset 不可用时：状态必须是 FAILED，且文案显式说明"未应用"。 */
    private fun failSpdPreset(reason: String) {
        spdRbSwapRetried = false
        spdResult = null
        resetPage2()
        setStep2("FAILED|" + str(R.string.st_spd_preset_invalid, reason))
        _uiState.value = _uiState.value.copy(
            reportData = _uiState.value.reportData.copy(
                spdResponse = "⚠ " + str(R.string.st_spd_not_applied) + " — " + reason,
                spdWarnings = listOf(reason)
            )
        )
    }

    fun updatePreset(p: PresetEntity) {
        viewModelScope.launch(Dispatchers.IO) { presetDao.update(p.copy(updatedAt = System.currentTimeMillis())) }
    }

    fun deletePreset(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { presetDao.deleteById(id) }
    }

    private suspend fun analyzePositioning() {
        runCatching {
            val uris = _uiState.value.positioningCaptures
            if (uris.isEmpty()) { setStep1("IDLE|" + str(R.string.st_idle1)); return }
            val ctx = getApplication<Application>()
            val batch = decode(ctx, uris)
            if (batch.frames.isEmpty()) {
                setStep1("FAILED|" + str(R.string.st_failed_decode, note(batch))); return
            }
            val s = _settings.value
            val wlR = s.wavelengthR.toDoubleOrNull() ?: 622.5
            val wlG = s.wavelengthG.toDoubleOrNull() ?: 522.5
            val wlB = s.wavelengthB.toDoubleOrNull() ?: 462.5

            val useGreenRedFit = _hasBlueFilter.value
            val res = WavelengthCalibrator.calibrate(batch.frames, wlR, wlG, wlB, useGreenRedFit)
            if (res == null) {
                setStep1("FAILED|" + str(R.string.st_failed_peak, note(batch))); return
            }

            // 【v2.2.6 关键】将 R/B 交换结论下发到解码器，Step 2/3 重新解码时标签即一致
            FrameProfileExtractor.forceRbSwap = res.rbSwapped

            val trainingPoints = if (useGreenRedFit) "G(${wlG}nm), R(${wlR}nm)" else "B(${wlB}nm), R(${wlR}nm)"
            val validationPoint = if (useGreenRedFit) "—" else "G(${wlG}nm)"

            _uiState.value = _uiState.value.copy(
                reportData = _uiState.value.reportData.copy(
                    wavelengthQuality = res.quality,
                    wavelengthGError = if (useGreenRedFit) "— (filter mode)" else String.format(Locale.US, "%+.3f nm", res.gErrorNm),
                    mappingFormula = String.format(Locale.US, "Pixel(p) = %.6f * Wavelength(nm) + %.4f", res.slope, res.intercept),
                    exposureTime = if (res.exposureSeconds > 0) String.format(Locale.US, "%.4f s", res.exposureSeconds) else "—",
                    xRoi = "[${res.roiStart}, ${res.roiEnd}] (Width: ${res.roiEnd - res.roiStart + 1} px)",
                    trainingPoints = trainingPoints,
                    validationPoint = validationPoint,
                    gResidual = String.format(Locale.US, "%+.2f pixels", res.gResidualPx),
                    slope = res.slope, intercept = res.intercept, gErrorNm = res.gErrorNm,
                    peakR = res.peakR, peakG = res.peakG, peakB = res.peakB,
                    peakWindows = res.peakWindows,
                    peakDiagnostics = if (res.peakFlags.isEmpty()) "—" else "⚠ " + res.peakFlags.joinToString("; "),
                    roiStart = res.roiStart, roiEnd = res.roiEnd,
                    sensorWidth = res.sensorWidth, sensorHeight = res.sensorHeight,
                    redProfile = res.red, greenProfile = res.green, blueProfile = res.blue,
                    previewUri = uris.first().toString(),
                    wlR = wlR, wlG = wlG, wlB = wlB
                ).withLiveWavelength(res.slope, res.intercept, res.roiStart, res.roiEnd, res.rbSwapped)
            )
            setStep1(
                "COMPUTED|" + str(R.string.st_computed_quality, res.quality, note(batch)) +
                        if (res.peakFlags.isNotEmpty()) str(R.string.st_suspect) else "" +
                                if (useGreenRedFit) " [filter mode]" else "" +
                                        if (res.rbSwapped) " [R/B auto-corrected]" else ""
            )
        }.onFailure { e ->
            setStep1("FAILED|" + (e.message ?: e.javaClass.simpleName))
            _uiState.value = _uiState.value.copy(
                reportData = _uiState.value.reportData.copy(
                    wavelengthQuality = "FAILED",
                    mappingFormula = "Analysis error: ${e.javaClass.simpleName}"
                )
            )
        }
    }

    private suspend fun analyzeSpd() {
        runCatching {
            spdRbSwapRetried = false
            spdResult = null
            val uris = _uiState.value.spdCaptures
            if (uris.isEmpty()) { spdResult = null; resetPage2(); setStep2("IDLE|" + str(R.string.st_idle2)); return }
            val ctx = getApplication<Application>()
            val lampBatch = decode(ctx, uris)
            if (lampBatch.frames.isEmpty()) {
                setStep2("FAILED|" + str(R.string.st_failed_decode, note(lampBatch))); return
            }
            val posBatch = decode(ctx, _uiState.value.positioningCaptures)
            if (posBatch.frames.isEmpty()) {
                setStep2("BLOCKED|" + str(R.string.st_blocked_pos, note(posBatch))); return
            }
            val slope = _uiState.value.reportData.slope
            val intercept = _uiState.value.reportData.intercept
            if (slope == null || intercept == null) {
                setStep2("BLOCKED|" + str(R.string.st_blocked_step1)); return
            }
            val spd = SpdLoader.load(ctx)
            if (spd == null) { setStep2("FAILED|" + str(R.string.st_failed_spd_source)); return }
            // 【v2.2.6】gErrorNm 一并写入，供 SPD preset 溯源
            val gErrorNm = _uiState.value.reportData.gErrorNm
            var res = SpectralResponseCalibrator.calibrate(
                lampBatch.frames, posBatch.frames, spd.second, slope, intercept, spd.first,
                gErrorNm = gErrorNm
            )
            if (res == null) {
                res = SpectralResponseCalibrator.calibrate(
                    swapRbChannels(lampBatch.frames), swapRbChannels(posBatch.frames),
                    spd.second, slope, intercept, spd.first,
                    gErrorNm = gErrorNm
                )
                spdRbSwapRetried = res != null
            }
            if (res == null) {
                setStep2("FAILED|" + str(R.string.st_failed_spd_degenerate, note(lampBatch))); return
            }
            res = res.copy(rbSwapped = spdRbSwapRetried)
            spdResult = res
            applySpdResult(res)
            _uiState.update { st -> st.copy(reportData = st.reportData.withLiveSpd(res.spdSource)) }
            setStep2("COMPUTED|" + str(
                R.string.st_computed_spd, res.frames,
                "${res.maskedCount}/${res.totalCount}", note(lampBatch)
            ) + if (spdRbSwapRetried) " [R/B swap retry]" else "")
        }.onFailure { e ->
            setStep2("FAILED|" + (e.message ?: e.javaClass.simpleName))
        }
    }

    private suspend fun analyzeSample() {
        runCatching {
            val st = _uiState.value
            if (st.blankCaptures.isEmpty() || st.sampleCaptures.isEmpty()) {
                setStep3("IDLE|" + str(R.string.st_idle3)); return
            }
            setStep3("PROCESSING|" + str(R.string.st_processing_frames, st.blankCaptures.size + st.sampleCaptures.size))
            val slope = st.reportData.slope
            val intercept = st.reportData.intercept
            if (slope == null || intercept == null) {
                setStep3("BLOCKED|" + str(R.string.st_blocked_step1)); return
            }
            val band = parseBand(st.reportData.sampleBand)
            if (band == null) { setStep3("BLOCKED|" + str(R.string.st_blocked_band)); return }
            val ctx = getApplication<Application>()
            val blankBatch = decode(ctx, st.blankCaptures)
            val sampleBatch = decode(ctx, st.sampleCaptures)
            if (blankBatch.frames.isEmpty() || sampleBatch.frames.isEmpty()) {
                setStep3("FAILED|" + str(R.string.st_failed_decode, note(blankBatch) + "; " + note(sampleBatch))); return
            }
            val spd = spdResult
            val initialSwap = spdRbSwapRetried
            fun analyze(swapRb: Boolean) = SampleAnalyzer.analyze(
                if (swapRb) swapRbChannels(blankBatch.frames) else blankBatch.frames,
                if (swapRb) swapRbChannels(sampleBatch.frames) else sampleBatch.frames,
                band.first, band.second, slope, intercept,
                fluorophore = st.reportData.sampleFluorophore,
                wavelengths = spd?.wavelengths,
                respR = spd?.respR, respG = spd?.respG, respB = spd?.respB
            )
            var retried = false
            var res = analyze(initialSwap)
            if (res == null) {
                res = analyze(!initialSwap)
                retried = res != null
            }
            if (res == null) { setStep3("FAILED|" + str(R.string.st_failed_window)); return }
            _uiState.value = _uiState.value.copy(
                reportData = _uiState.value.reportData.copy(
                    blankMeanArea = String.format(Locale.US, "%.1f", res.blankMeanArea),
                    sampleMeanArea = String.format(Locale.US, "%.1f", res.sampleMeanArea),
                    sampleIntegratedArea = String.format(
                        Locale.US, "%.1f (ch=%s, resp=%s)",
                        res.correctedArea, res.channel, if (res.responseApplied) "APPLIED" else "N/A"
                    ),
                    sampleSD = String.format(Locale.US, "%.1f", res.sampleSD),
                    replicates = res.replicates,
                    integrationWindow = "[${res.windowStartPx}, ${res.windowEndPx}] px",
                    sampleSpectrum = res.correctedSpectrum,
                    blankSpectrum = res.blankSpectrum,
                    correctedAreaValue = res.correctedArea,
                    samplePeakNm = String.format(Locale.US, "%.1f nm", res.peakNm),
                    samplePeakIntensity = String.format(Locale.US, "%.2f", res.peakIntensity),
                    sampleFwhm = String.format(Locale.US, "%.1f nm", res.fwhmNm),
                    sampleCentroid = String.format(Locale.US, "%.1f nm", res.centroidNm)
                )
            )
            // 【v2.2.6】曝光回显 + 缺失曝光告警，便于现场核验归一化是否生效
            val bExp = blankBatch.frames.firstOrNull()?.exposureSeconds ?: 0.0
            val sExp = sampleBatch.frames.firstOrNull()?.exposureSeconds ?: 0.0
            val noExp = (blankBatch.frames + sampleBatch.frames).count { it.exposureSeconds <= 0.0 }
            setStep3(
                "COMPUTED|" + str(
                    R.string.st_computed_sample, res.channel,
                    if (res.responseApplied) "APPLIED" else "N/A",
                    note(blankBatch), note(sampleBatch)
                ) + String.format(Locale.US, " [exp b=%.3fs s=%.3fs]", bExp, sExp) +
                        (if (retried) " [R/B swap retry]" else "") +
                        // 【v2.2.6】DEF-A03：未应用 SPD 校正时必须说明原因，而不是只显示 N/A
                        (res.spdSkipReason?.let { str(R.string.st_spd_skip_reason, it) } ?: "") +
                        (if (noExp > 0) " ⚠ $noExp frame(s) missing exposure, not normalized" else "")
            )
        }.onFailure { e ->
            setStep3("FAILED|" + (e.message ?: e.javaClass.simpleName))
            _uiState.value = _uiState.value.copy(
                reportData = _uiState.value.reportData.copy(
                    sampleIntegratedArea = "Analysis error: ${e.javaClass.simpleName}"
                )
            )
        }
    }

    private fun resetPage2() {
        _uiState.value = _uiState.value.copy(
            reportData = _uiState.value.reportData.copy(
                spdStatus = "Pending", spdCoeffR = "—", spdCoeffB = "—", spdResponse = "—",
                spdWarnings = emptyList(),
                spdFrames = 0, spdExposure = "—", spdEffectiveBand = "—", spdMasked = "—",
                spdHandoffs = "—", spdAgreement = "—", spdFusedPeak = "—", spdPeaks = "—",
                spdRawPeaks = "—", spdCorrPeaks = "—",
                spdCrossingBG = 483f, spdCrossingGR = 577f,
                spdLambda = emptyList(),
                spdPosR = emptyList(), spdPosG = emptyList(), spdPosB = emptyList(),
                spdRespR = emptyList(), spdRespG = emptyList(), spdRespB = emptyList(),
                spdCorrR = emptyList(), spdCorrG = emptyList(), spdCorrB = emptyList(),
                spdWB = emptyList(), spdWG = emptyList(), spdWR = emptyList(),
                spdFused = emptyList()
            )
        )
    }

    /** @return true 表示 SPD 数据真正写进了报告；false 表示没有任何可用采样点。 */
    private fun applySpdResult(res: SpdCalibrationResult): Boolean {
        val maskedIdx = res.wavelengths.indices.filter { res.mask.getOrElse(it) { false } }
        if (maskedIdx.isEmpty()) { resetPage2(); return false }
        val k = maxOf(1, maskedIdx.size / 180)
        val idx = maskedIdx.filterIndexed { i, _ -> i % k == 0 }
        fun pick(a: FloatArray): List<Float> = idx.map { a.getOrElse(it) { 0f } }
        fun peakLine(arr: FloatArray, fmt: String): String {
            var best = 0f; var bl = 0f
            for (i in maskedIdx) {
                val v = arr.getOrElse(i) { 0f }
                if (v > best) { best = v; bl = res.wavelengths[i] }
            }
            if (best <= 0f) return "—"
            return String.format(Locale.US, "$fmt@%.1f", best, bl)
        }
        _uiState.value = _uiState.value.copy(
            reportData = _uiState.value.reportData.copy(
                spdStatus = "Calibrated (${res.frames} lamp frame(s))",
                spdSource = res.spdSource,
                spdCoeffR = String.format(Locale.US, "%.0f a.u./SPD @ %.1f nm", res.peakRespR.first, res.peakRespR.second),
                spdCoeffB = String.format(Locale.US, "%.0f a.u./SPD @ %.1f nm", res.peakRespB.first, res.peakRespB.second),
                spdResponse = "Fused continuous curve F(λ) available (Report Page 2)",
                spdFrames = res.frames,
                spdExposure = if (res.exposureSeconds > 0) String.format(Locale.US, "%.4f s", res.exposureSeconds) else "—",
                spdEffectiveBand = String.format(Locale.US, "%.1f–%.1f nm", res.effectiveBandNm.first, res.effectiveBandNm.second),
                spdMasked = "${res.maskedCount}/${res.totalCount}",
                spdHandoffs = String.format(
                    Locale.US, "B→G: %.1f nm   G→R: %.1f nm   (diagnostic only)",
                    res.crossingBG, res.crossingGR
                ),
                spdAgreement = String.format(Locale.US, "mean pairwise |Δ| = %.2f%% of fused mean", res.agreementPct),
                spdFusedPeak = String.format(Locale.US, "%.4f a.u./s @ %.1f nm", res.fusedPeak.first, res.fusedPeak.second),
                spdPeaks = String.format(
                    Locale.US, "R %.0f@%.1f  G %.0f@%.1f  B %.0f@%.1f",
                    res.peakRespR.first, res.peakRespR.second,
                    res.peakRespG.first, res.peakRespG.second,
                    res.peakRespB.first, res.peakRespB.second
                ),
                spdRawPeaks = "R ${peakLine(res.posR, "%.1f")}  G ${peakLine(res.posG, "%.1f")}  B ${peakLine(res.posB, "%.1f")}",
                spdCorrPeaks = "R ${peakLine(res.corrR, "%.4f")}  G ${peakLine(res.corrG, "%.4f")}  B ${peakLine(res.corrB, "%.4f")}",
                spdCrossingBG = res.crossingBG,
                spdCrossingGR = res.crossingGR,
                spdLambda = pick(res.wavelengths),
                spdPosR = pick(res.posR), spdPosG = pick(res.posG), spdPosB = pick(res.posB),
                spdRespR = pick(res.respR), spdRespG = pick(res.respG), spdRespB = pick(res.respB),
                spdCorrR = pick(res.corrR), spdCorrG = pick(res.corrG), spdCorrB = pick(res.corrB),
                spdWB = pick(res.wB), spdWG = pick(res.wG), spdWR = pick(res.wR),
                spdFused = pick(res.fused)
            )
        )
        return true
    }

    private suspend fun persistAll() {
        persistenceMutex.withLock {
            runCatching {
                val p = _uiState.value.currentProject ?: return
                val st = _uiState.value
                dao.deleteCapturesByProject(p.id)
                dao.deleteStandardGroupsByProject(p.id)
                st.positioningCaptures.forEach { dao.insertCapture(CaptureEntity(projectId = p.id, step = "POSITIONING", uri = it.toString())) }
                st.spdCaptures.forEach { dao.insertCapture(CaptureEntity(projectId = p.id, step = "SPD", uri = it.toString())) }
                st.blankCaptures.forEach { dao.insertCapture(CaptureEntity(projectId = p.id, step = "BLANK", uri = it.toString())) }
                st.sampleCaptures.forEach { dao.insertCapture(CaptureEntity(projectId = p.id, step = "SAMPLE", uri = it.toString())) }
                for (g in st.standardGroups) {
                    val gid = dao.insertStandardGroup(StandardGroupEntity(projectId = p.id, concentration = g.concentration, unit = g.unit))
                    g.blankUris.forEach { dao.insertCapture(CaptureEntity(projectId = p.id, step = "STANDARD_BLANK", standardGroupId = gid, uri = it.toString())) }
                    g.sampleUris.forEach { dao.insertCapture(CaptureEntity(projectId = p.id, step = "STANDARD_SAMPLE", standardGroupId = gid, uri = it.toString())) }
                }
            }
        }
    }

    /**
     * 【v2.2.8】把当前报告快照写回项目行。
     *
     * 为什么除 `finishProject()` 之外还要在 Step 4 结果产出时再存：用户跑完标准曲线
     * 后很可能直接退出 App，根本不会走到 `finishProject()`。
     *
     * 固定切到 IO —— 快照里含三条通道 profile 与十余条 SPD 曲线，序列化是几十 KB
     * 量级。落库失败静默吞掉：一次现算出来的成功结果，不该因为存储层的偶发问题
     * 变成 UI 上的报错。
     */
    private fun persistReportSnapshot() {
        viewModelScope.launch(Dispatchers.IO) {
            persistReportSnapshotLocked(
                mutex = projectMetadataMutex,
                readProject = { _uiState.value.currentProject },
                serialize = { _uiState.value.reportData.toJsonString() },
                // 落库失败静默吞掉：一次现算出来的成功结果，不该因为存储层的偶发问题
                // 变成 UI 上的报错。
                writeProject = { project -> runCatching { dao.updateProject(project) } }
            )
        }
    }

    private fun parseBand(band: String): Pair<Double, Double>? {
        val m = Regex("(\\d+)–(\\d+)").find(band) ?: return null
        val (a, b) = m.destructured
        return a.toDoubleOrNull()?.let { bb -> b.toDoubleOrNull()?.let { bb to it } }
    }

    private fun pythonEmissionBand(name: String): Pair<Double, Double> =
        when (canonicalFluorophore(name)) {
            Fluorophore.YPET -> 527.0 to 20.0
            Fluorophore.MCHERRY -> 610.0 to 25.0
            Fluorophore.CFP -> 475.0 to 20.0
            Fluorophore.MTURQUOISE2 -> 482.0 to 20.0
            else -> 509.0 to 20.0
        }

    private fun fluorophoreBand(name: String): Pair<Int, Int> = when (canonicalFluorophore(name)) {
        Fluorophore.YPET -> 500 to 530
        Fluorophore.MCHERRY -> 590 to 630
        Fluorophore.CFP, Fluorophore.MTURQUOISE2 -> 460 to 490
        // 荧光素钠 520 nm 发射，落在 500–540；原先靠 else 兜底刚好正确，
        // 显式写出来，避免将来改 else 时悄悄改坏它
        Fluorophore.FLUORESCEIN -> 500 to 540
        else -> 500 to 540
    }

    /**
     * 解析「外部写进来的」荧光素名字（曲线预设的 `fluorophore` 字段）。
     *
     * 解析失败抛 [IllegalArgumentException] —— 调用方在 `runCatching` 里，
     * 这条消息会原样出现在 Step 4 的状态栏上，而不是静默降级到一个错的通道。
     */
    private fun requireKnownFluorophore(raw: String, context: String): Fluorophore =
        canonicalFluorophore(raw) ?: error(
            "Unrecognised fluorophore '$raw' in $context; supported: " +
                Fluorophore.values().joinToString(", ") { it.displayName }
        )
}
