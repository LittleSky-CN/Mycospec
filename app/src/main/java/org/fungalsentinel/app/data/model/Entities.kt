package org.fungalsentinel.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Fluorophore options (Step 4)
 */
enum class Fluorophore(val displayName: String, val integrationRangeNm: String) {
    YPET("Ypet", "500–530 nm"),
    EGFP("EGFP", "500–540 nm"),
    MCHERRY("mCherry", "590–630 nm"),
    CFP("CFP", "460–490 nm"),
    MTURQUOISE2("mTurquoise2", "460–490 nm")
}

/**
 * Project entity
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val fluorophore: String = Fluorophore.EGFP.name,
    val createdAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val status: String = "IN_PROGRESS"
)

/**
 * Capture entity — structured metadata instead of JSON string
 */
@Entity(
    tableName = "captures",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StandardGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["standardGroupId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["projectId"]),
        Index(value = ["standardGroupId"])
    ]
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

/**
 * Calibration result (Step 2 wavelength + Step 3 SPD)
 */
@Entity(
    tableName = "calibrations",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class CalibrationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val step: String,  // WAVELENGTH | SPD
    val quality: String,  // PASS | WARNING | FAILED
    val gValidationErrorNm: Double? = null,
    val mappingFormula: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Standard group (Step 5)
 */
@Entity(
    tableName = "standard_groups",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class StandardGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val concentration: Double,
    val unit: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Regression result (Step 5 final)
 */
@Entity(
    tableName = "results",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
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