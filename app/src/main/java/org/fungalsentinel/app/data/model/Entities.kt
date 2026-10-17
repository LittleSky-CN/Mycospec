package org.fungalsentinel.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Locale

/**
 * 荧光素的唯一真源（**项目里只有这一份 enum**）。
 *
 * canonical ID == [Fluorophore.name]（`YPET` / `EGFP` / `MCHERRY` / `CFP` /
 * `MTURQUOISE2` / `FLUORESCEIN`），原因：`projects.fluorophore` 存量数据里存的
 * 就是这些值，改 Python 端比迁移存量库便宜。
 */
enum class Fluorophore(val displayName: String, val integrationRangeNm: String) {
    YPET("Ypet", "500–530 nm"),
    EGFP("EGFP", "500–540 nm"),
    MCHERRY("mCherry", "590–630 nm"),
    CFP("CFP", "460–490 nm"),
    MTURQUOISE2("mTurquoise2", "460–490 nm"),
    FLUORESCEIN("Fluorescein", "500–540 nm")
}

/**
 * 归一化成查表键：大小写无关，去掉所有空白、连字符（`-` / en-dash / em-dash）、
 * 下划线、点、斜杠与圆括号。
 *
 * 例：`"m-Turquoise2"` / `"mturquoise2"` / `"MTURQUOISE2"` → `"mturquoise2"`，
 * `"Ypet (Venus variant)"` → `"ypetvenusvariant"`。
 */
private val FLUOROPHORE_NOISE = Regex("[\\s_./()–—-]+")

private fun normalizeFluorophoreKey(name: String): String =
    FLUOROPHORE_NOISE.replace(name.trim().lowercase(Locale.US), "")

/**
 * 显式别名表（键已过 [normalizeFluorophoreKey]），每类列全。
 *
 * 为什么用显式表而不是模糊/子串匹配：荧光素只有这 6 类，逐条列死就不会出现
 * 某条宽松规则把 `CFP` 误判成别的东西的情况。
 *
 * Python FSSA 侧改造成 canonical 之前，跨端曲线 preset 里带的就是它的显示名，
 * 那些写法在这里必须有一条实打实的别名。
 */
private val FLUOROPHORE_ALIASES: Map<String, Fluorophore> = mapOf(
    // YPET —— Python 显示名 "Ypet (Venus variant)"
    "ypet" to Fluorophore.YPET,
    "ypetvenusvariant" to Fluorophore.YPET,
    // EGFP
    "egfp" to Fluorophore.EGFP,
    // MCHERRY —— Python key "mCherry"
    "mcherry" to Fluorophore.MCHERRY,
    // CFP
    "cfp" to Fluorophore.CFP,
    // MTURQUOISE2 —— Python key "mTurquoise2"，常见变体 "m-turquoise2" 归一化后同键
    "mturquoise2" to Fluorophore.MTURQUOISE2,
    // FLUORESCEIN —— Python 显示名 "Fluorescein (sodium salt)"
    "fluorescein" to Fluorophore.FLUORESCEIN,
    "fluoresceinsodiumsalt" to Fluorophore.FLUORESCEIN
)

/**
 * 把荧光素的任意写法解析成 canonical [Fluorophore]。
 *
 * 这是**唯一**的「字符串 → 荧光素」入口：Step 3 的下拉、ViewModel 的波段查表、
 * 曲线预设的 `fluorophore` 字段都必须走这里，Python FSSA 导出的 preset
 * （`mCherry` / `Ypet (Venus variant)` / `Fluorescein (sodium salt)` …）才能落地。
 *
 * 认不出来就返回 `null` —— **由调用方**决定报错还是降级，本函数不静默兜底，
 * 这样外部 preset 里出现脏值时用户会看到明确提示而不是悄悄读到错的通道。
 */
fun canonicalFluorophore(name: String?): Fluorophore? {
    val key = normalizeFluorophoreKey(name ?: return null)
    if (key.isEmpty()) return null
    Fluorophore.values().firstOrNull { normalizeFluorophoreKey(it.name) == key }?.let { return it }
    return FLUOROPHORE_ALIASES[key]
}

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val fluorophore: String = Fluorophore.EGFP.name,
    val createdAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val status: String = "IN_PROGRESS",
    val hasBlueFilter: Boolean = false, // NEW: Filter mode flag
    /**
     * 【v2.2.8】已落库的报告快照。
     *
     * 为什么必须存这一列：`loadWavelengthPreset()` / `loadSpdPreset()` /
     * `loadCurvePreset()` 只更新 uiState 里的 [ReportData]，**不会**往 `captures`
     * 表写任何记录。也就是说「这个项目 Step 1 用了某某预设」这个事实除了
     * 内存里的 reportData 之外没有任何载体 —— 一旦不落库，从 History 重开项目
     * 时溯源信息就永久消失。
     *
     * 为 null 表示旧项目（MIGRATION_3_4 之前落库）或尚未产出报告，读取端按
     * 「恢复失败 → 退回 ReportData()」安全降级。
     */
    val reportDataJson: String? = null
)

@Entity(
    tableName = "captures",
    foreignKeys = [
        ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = StandardGroupEntity::class, parentColumns = ["id"], childColumns = ["standardGroupId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index(value = ["projectId"]), Index(value = ["standardGroupId"])]
)
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val step: String,
    val standardGroupId: Long? = null,
    val uri: String,
    val exposureTimeNs: Long = 0L,
    val iso: Int = 0,
    val focusDistance: Float = 0f,
    val whiteBalance: Int? = null,
    val sensorWidth: Int = 0,
    val sensorHeight: Int = 0,
    val saturationRatio: Float = 0f,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "calibrations",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["projectId"])]
)
data class CalibrationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val step: String,
    val quality: String,
    val gValidationErrorNm: Double? = null,
    val mappingFormula: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "standard_groups",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["projectId"])]
)
data class StandardGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val concentration: Double,
    val unit: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "results",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["projectId"])]
)
data class ResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val regressionFormula: String,
    val rSquared: Double,
    val predictedConcentration: Double?,
    val slope: Double,
    val intercept: Double,
    val createdAt: Long = System.currentTimeMillis()
)