package org.fungalsentinel.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Fluorophore(val displayName: String, val integrationRangeNm: String) {
    YPET("Ypet", "500–530 nm"),
    EGFP("EGFP", "500–540 nm"),
    MCHERRY("mCherry", "590–630 nm"),
    CFP("CFP", "460–490 nm"),
    MTURQUOISE2("mTurquoise2", "460–490 nm")
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