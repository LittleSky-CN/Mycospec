package org.fungalsentinel.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Locale

/**
 * The single source of truth for fluorophores (**the only such enum in the project**).
 *
 * The canonical ID is [Fluorophore.name] (`YPET` / `EGFP` / `MCHERRY` / `CFP` /
 * `MTURQUOISE2` / `FLUORESCEIN`), because those are exactly the values already
 * stored in the existing `projects.fluorophore` data — changing the Python side
 * is cheaper than migrating the existing database.
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
 * Normalises a name into a lookup key: case-insensitive, with all whitespace,
 * hyphens (`-` / en-dash / em-dash), underscores, dots, slashes and parentheses
 * removed.
 *
 * For example: `"m-Turquoise2"` / `"mturquoise2"` / `"MTURQUOISE2"` →
 * `"mturquoise2"`, and `"Ypet (Venus variant)"` → `"ypetvenusvariant"`.
 */
private val FLUOROPHORE_NOISE = Regex("[\\s_./()–—-]+")

private fun normalizeFluorophoreKey(name: String): String =
    FLUOROPHORE_NOISE.replace(name.trim().lowercase(Locale.US), "")

/**
 * Explicit alias table (keys already passed through [normalizeFluorophoreKey]),
 * listed exhaustively per fluorophore.
 *
 * Why an explicit table instead of fuzzy / substring matching: there are only
 * these 6 fluorophores, so listing them exhaustively rules out a loose rule
 * misjudging `CFP` as something else.
 *
 * Until the Python FSSA side is reworked to canonical, cross-platform curve
 * presets carry its display names, so every one of those spellings needs a real
 * alias here.
 */
private val FLUOROPHORE_ALIASES: Map<String, Fluorophore> = mapOf(
    // YPET — Python display name "Ypet (Venus variant)"
    "ypet" to Fluorophore.YPET,
    "ypetvenusvariant" to Fluorophore.YPET,
    // EGFP
    "egfp" to Fluorophore.EGFP,
    // MCHERRY —— Python key "mCherry"
    "mcherry" to Fluorophore.MCHERRY,
    // CFP
    "cfp" to Fluorophore.CFP,
    // MTURQUOISE2 — Python key "mTurquoise2"; the common variant "m-turquoise2"
    // normalises to the same key
    "mturquoise2" to Fluorophore.MTURQUOISE2,
    // FLUORESCEIN — Python display name "Fluorescein (sodium salt)"
    "fluorescein" to Fluorophore.FLUORESCEIN,
    "fluoresceinsodiumsalt" to Fluorophore.FLUORESCEIN
)

/**
 * Resolves any spelling of a fluorophore to the canonical [Fluorophore].
 *
 * This is the **only** "string → fluorophore" entry point: the Step 3 dropdown,
 * the ViewModel's band lookup and the `fluorophore` field of a curve preset must
 * all go through here, so that presets exported by Python FSSA (`mCherry` /
 * `Ypet (Venus variant)` / `Fluorescein (sodium salt)` …) resolve correctly.
 *
 * Returns `null` when it cannot resolve — **the caller** decides whether to fail
 * or degrade; this function does not silently fall back, so a dirty value in an
 * external preset gives the user a clear message instead of quietly reading the
 * wrong channel.
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
     * [v2.2.8] The persisted report snapshot.
     *
     * Why this column has to exist: `loadWavelengthPreset()` / `loadSpdPreset()` /
     * `loadCurvePreset()` only update the [ReportData] held in uiState and
     * **never** write any record to the `captures` table. In other words the fact
     * "this project used preset X in Step 1" has no carrier other than the
     * in-memory reportData — if it is not persisted, the provenance is lost
     * forever once the project is reopened from History.
     *
     * null means an old project (persisted before MIGRATION_3_4) or a report that
     * has not been produced yet; readers degrade safely with
     * "restore failed → fall back to ReportData()".
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