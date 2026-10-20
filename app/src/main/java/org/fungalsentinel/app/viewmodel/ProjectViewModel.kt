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
    // Empty by default: real values are always written by the ViewModel from
    // resources (init / resetPage2) to avoid leftover English text.
    val spdStatus: String = "",
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
    // Empty by default: real text is written by the ViewModel from
    // R.string.report_spd_method_note to avoid leftover English text
    val spdMethodNote: String = "",
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
    /** [v2.2.6] Soft hints (L2/L3) raised while an SPD preset is loading; never blocking. */
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

    // ─── Step 2 provenance (spdSource already exists; completing the rest) ───
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
 * [v2.2.7] Provenance writers for Step 1 / 2 / 4 — "where did this data come from".
 *
 * These are top-level pure functions rather than inline code in the ViewModel
 * because `ProjectViewModel` depends on `Application` + Room and cannot be
 * instantiated in a JVM unit test; these writers only take data classes, so
 * `ReportProvenanceTest` can assert against the production code itself.
 */
private fun nowStamp(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        .format(java.util.Date())

/** A Room preset has no real file path, so a stable row reference stands in for it. */
private fun presetFileRef(id: Long): String = "preset://$id"

/**
 * The source shown in the report when a step has never been run.
 *
 * Defaults such as "Live calibration" / "Live standard curve" /
 * "true_spd.csv (bundled)" are no longer acceptable: `ReportScreen` renders the
 * Source row unconditionally, so a step that was never run would claim out of
 * thin air that it is a live calibration — more misleading than leaving it blank.
 */
/**
 * Data-layer sentinel value: the provenance placeholder written when a step has
 * not been run. Tests assert against it, so it stays a constant; the readable UI
 * text is written by the ViewModel from `R.string.report_source_not_run` on
 * init / reset.
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
    p: PresetEntity, at: String = nowStamp(), src: String? = null
): ReportData = copy(
    step1Source = src ?: "Preset · ${p.name}",
    step1PresetName = p.name,
    step1PresetFile = presetFileRef(p.id),
    step1ImportedAt = at,
    step1Params = step1Params(p.slope, p.intercept, p.roiStart, p.roiEnd, p.rbSwapped)
)

/** Live calibration must clear every preset trace, otherwise a stale preset name lingers in the report. */
internal fun ReportData.withLiveWavelength(
    slope: Double, intercept: Double, roiStart: Int?, roiEnd: Int?, rbSwapped: Boolean,
    at: String = nowStamp(), src: String? = null
): ReportData = copy(
    step1Source = src ?: "Live calibration",
    step1PresetName = null,
    step1PresetFile = null,
    step1ImportedAt = at,
    step1Params = step1Params(slope, intercept, roiStart, roiEnd, rbSwapped)
)

internal fun ReportData.withSpdPreset(
    p: PresetEntity, driftNm: Float?, at: String = nowStamp(), src: String? = null
): ReportData = copy(
    spdSource = src ?: "Preset · ${p.name}",
    spdPresetName = p.name,
    spdPresetFile = presetFileRef(p.id),
    spdImportedAt = at,
    spdParams = linkedMapOf(
        "drift_nm" to (driftNm?.let { String.format(Locale.US, "%.3f", it) } ?: "unknown"),
        "drift_level" to SpectralResponseCalibrator.driftLevel(driftNm).name
    )
)

/**
 * Live SPD calibration: the source is labelled "Live calibration" but the
 * reference spectrum file name (`true_spd.csv (bundled)`) is kept, otherwise the
 * report loses the only field that identifies the reference spectrum.
 */
internal fun ReportData.withLiveSpd(csvSource: String, at: String = nowStamp(), src: String? = null): ReportData = copy(
    spdSource = src ?: "Live calibration · $csvSource",
    spdPresetName = null,
    spdPresetFile = null,
    spdImportedAt = at,
    spdParams = linkedMapOf("spd_csv" to csvSource)
)

/**
 * [D1] Restores the Step 2 preset provenance.
 *
 * Scenario: after Step 1 loads a wavelength preset, `loadWavelengthPreset`
 * re-runs `analyzeSpd()` with the new mapping, and the `withLiveSpd` at the end
 * of `analyzeSpd` nulls `spdPresetName/File` and rewrites `spdSource` to
 * "Live calibration · …". That re-run only recomputes with the new wavelength
 * mapping — the user did not re-shoot the lamp — so the report would silently
 * lose the "Preset · X" source.
 *
 * Restored only when preset provenance actually existed before the re-run — a
 * re-run that was live to begin with is unaffected.
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
 * [v2.2.8] Whether reopening a project should overwrite the recomputed result
 * with the provenance from the stored snapshot.
 *
 * `openProject()` mixes two use cases whose correct answers are opposite:
 *
 * | Project status | Purpose                  | Overwrite?                              |
 * |----------------|--------------------------|-----------------------------------------|
 * | `IN_PROGRESS`  | Continue unfinished work | **Yes** — the data comes from a live re-run |
 * | `FINISHED`     | View a historical report | **No** — the user came to read the report  |
 *
 * Extracted as a pure function so the gate itself can be asserted on the JVM:
 * `ProjectViewModel` depends on `Application` + two Room databases and cannot be
 * instantiated in a unit test, and writing the gate backwards is the easiest
 * mistake to make in this fix.
 */
internal fun shouldRestoreProvenance(status: String?, restored: ReportData?): Boolean =
    status == "FINISHED" && restored != null

/**
 * [v2.2.8-D1] Guarded write entry point for the project row (projects table) —
 * every "read → modify → write" of the snapshot / workflow status must finish
 * inside the same critical section, otherwise updates are lost (lost-update).
 *
 * ## Why mutual exclusion is required
 *
 * The `projects` row carries two things at once: the **report snapshot** (stored
 * once per Step 4 result so the user can quit the app right after finishing
 * without losing anything) and the **workflow status** (`FINISHED` / `finishedAt`
 * written on Finish). Both write paths run in fire-and-forget coroutines, so
 * either one can read a row the other has already changed. When each writes back
 * the `copy()` it holds, **the later write wipes the earlier result entirely**:
 *
 * ```
 * persistReportSnapshot : reads  ProjectEntity(status=IN_PROGRESS, …)   ← stale
 * finishProject         : writes ProjectEntity(status=FINISHED,   …)
 * persistReportSnapshot : writes ProjectEntity(status=IN_PROGRESS, …)   ← wipes FINISHED
 * ```
 *
 * The consequence is **silent**: no crash, no error, but
 * `shouldRestoreProvenance()` requires `status == "FINISHED"`, and once the
 * status is pushed back to `IN_PROGRESS` the Step 1/2/4 provenance is not
 * restored when the project is reopened from History — the very symptom being
 * fixed here, just via another trigger path.
 *
 * ## Deadlock analysis (do not wrap another lock of the same kind outside)
 *
 * The two `…Locked()` functions **take the lock themselves** instead of
 * requiring the caller to hold it: `Mutex` is not reentrant, so entering with the
 * lock already held self-deadlocks immediately. For the same reason
 * `finishProjectLocked` does **not** `join` the fire-and-forget
 * `persistReportSnapshot()` coroutine (it may be waiting on this very lock). The
 * two paths are mutually excluded by **sharing the same `mutex`**, not by
 * "queueing outside and waiting inside".
 *
 * While the lock is held, the snapshot is serialized under
 * `withContext(Dispatchers.IO)` (tens of KB): the lock migrates with the
 * coroutine, so there is no self-deadlock; the only cost is that other metadata
 * writes queue up during serialization. That trade-off is deliberate — tens of
 * milliseconds of queueing is negligible next to the correctness gain of never
 * reading a stale row inside the critical section.
 *
 * ## Scope (deliberately narrow)
 *
 * This covers the two paths that can overwrite each other — `finishProject()`
 * and `persistReportSnapshot()`. The third write site, `setSampleFluorophore()`
 * (writes back after changing fluorophore / band), is **not** routed through
 * here: it holds the same `projectMetadataMutex` and likewise re-reads the latest
 * uiState inside the lock before writing (see the comment there), the same
 * semantics as this function; it touches neither the snapshot nor the status, so
 * forcing a `transform` on it would only make it more convoluted. If it ever
 * starts writing the snapshot, it must go through here.
 *
 * @return whether a write actually happened. False when `readProject()` returns
 *         null (the project was cleared or does not exist); in that case
 *         `writeProject` is never called.
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
 * [v2.2.8-D1] Writes the current report snapshot back to the project row (used by
 * [ProjectViewModel.persistReportSnapshot]).
 *
 * Stored once more when a Step 4 result is produced, on top of
 * `finishProject()`: after running the standard curve a user is likely to quit
 * the app outright and never reach `finishProject()`.
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
 * [v2.2.8-D1] Atomically completes "write snapshot + mark FINISHED" (used by
 * [ProjectViewModel.finishProject]).
 *
 * The key point is reading out the project row and writing back `status` /
 * `finishedAt` **inside the same critical section**: once those three are split,
 * an in-flight [persistReportSnapshotLocked] gets a chance to push the status
 * back with a stale copy.
 *
 * Ordering is the caller's responsibility — uiState may only be cleared after
 * this function returns (by then both snapshot and status are persisted).
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
 * [v2.2.8] Copies the Step 1/2/4 **provenance** (12 fields in total) from [from].
 *
 * ## Deliberately only these 12 fields, no values
 *
 * `slope` / `intercept` / `mappingFormula` / `redProfile` / `spdFused` /
 * `curveSlope` / `regressionFormula` / `predictedConcentration` and the rest all
 * keep whatever the caller (that is, `analyzePositioning()` / `analyzeSpd()` /
 * `analyzeSample()`) recomputed. Values must reflect the current real state;
 * provenance is the historical fact of "how this project was originally
 * produced". Restoring both together would give a report whose values are
 * recomputed while its labels claim a preset — exactly the provenance lie that
 * v2.2.7 just fixed.
 *
 * ## Why `*Params` is **not** on the list either (12, not 15 — not an omission)
 *
 * The three `Map<String, String>` look like provenance but actually **carry
 * values**: `withLiveWavelength` rewrites `step1Params` with the recomputed
 * slope / intercept (see `step1Params()`, `:202`), and `withLiveSpd` /
 * `withLiveCurve` do the same. Restoring them as well would produce
 *
 * ```
 * step1Source          = "Preset · Lab WL 2026"   ← restored
 * step1Params[slope_k] = "-2.738911"              ← restored (preset value)
 * slope                = -2.710000                ← recomputed
 * ```
 *
 * i.e. the slope in params and the slope in the report body would disagree.
 *
 * Excluding them keeps both scenarios self-consistent (whether or not a
 * positioning image was captured):
 *
 * | Scenario                                  | `step1Source`        | `step1Params[slope_k]`      | `slope`       |
 * |-------------------------------------------|----------------------|-----------------------------|---------------|
 * | Captured + preset imported (recomputed)   | Preset · X (restored)| recomputed (not restored)   | recomputed    |
 * | Preset only, no capture (not recomputed)  | Preset · X (baseline)| preset value (baseline kept)| preset value  |
 *
 * Note the second row: with no capture `analyzePositioning()` returns early at
 * `:1228`, nobody overwrites the preset params in the baseline, and the preset
 * values survive. **So excluding params does not hide preset parameters for
 * "preset only" projects** — the one case where the preset values are not shown
 * is when the values were recomputed anyway, and then not showing them is
 * correct.
 *
 * ## How this differs from `restoreSpdPresetProvenance` (hence **not** merged)
 *
 * - That one is **guarded** (restores only when `before.spdPresetName != null`)
 *   and serves the cascaded re-run in `loadWavelengthPreset` — that path is a
 *   live operation to begin with, so protection is needed only when preset
 *   provenance really exists.
 * - This one moves those 12 fields **unconditionally**, because
 *   `shouldRestoreProvenance()` already gates it upstream. Merging them would
 *   make the D1 path restore Step 1 / Step 4 provenance too, which overreaches.
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
 * [v2.2.8] Report baseline when a project is reopened: the snapshot is the base,
 * then fluorophore / band are replaced with the values from the project entity
 * (the entity is the authoritative source for those two; the snapshot is not
 * trusted).
 *
 * Falls back to a blank report when the snapshot is null — projects stored
 * before v4 take that path.
 *
 * Extracted as a pure function so the "preset only, no capture" path (Case B)
 * can be asserted on the JVM: on that path `analyzePositioning()` returns early
 * at `:1228` and the final report **is** this baseline verbatim, so if the
 * baseline lost params nothing could bring them back.
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
    p: PresetEntity, payload: CurvePresetPayload, at: String = nowStamp(), src: String? = null
): ReportData = copy(
    step4Source = src ?: "Preset · ${p.name}",
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
    reg: ConcentrationRegressor.RegressionResult, nStandards: Int, at: String = nowStamp(), src: String? = null
): ReportData = copy(
    step4Source = src ?: "Live standard curve",
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

/** After the user clears the curve preset, the report must not keep "Preset · X". */
internal fun ReportData.withoutCurvePreset(): ReportData = copy(
    // [D2] With the preset cleared, Step 4 holds no curve data at all, so it must
    // not claim "Live standard curve"
    step4Source = STEP_SOURCE_NOT_RUN,
    step4PresetName = null,
    step4PresetFile = null,
    step4ImportedAt = null,
    step4Params = emptyMap()
)

/**
 * [D4] When an SPD preset never actually landed (no usable sample points), the
 * report must not keep "Preset · X". There is no preset provenance to fall back
 * to, so it reverts to the neutral not-run marker.
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
                step4Status = "IDLE|" + str(R.string.st_idle4),
                spdStatus = str(R.string.report_spd_status_pending)
            )
        )
    }

    /**
     * Resolves one string resource.
     *
     * **Must not be written as `getString(id, *a)`**: even with an empty `a` that
     * hits the `Resources.getString(int, Object...)` vararg overload, which
     * **unconditionally** runs `String.format(...)`. With `%1$s` in the template
     * and an empty argument array that throws `MissingFormatArgumentException`
     * (visible as a crash on a real device).
     *
     * With no arguments it goes through the single-argument `getString(id)`,
     * which does no formatting, so it is both safe and leaves the raw template
     * available to the outer `String.format(Locale.US, …)`.
     */
    private fun str(id: Int, vararg a: Any): String {
        val ctx = getApplication<Application>()
        return if (a.isEmpty()) ctx.getString(id) else ctx.getString(id, *a)
    }
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
        FrameProfileExtractor.forceRbSwap = false   // [v2.2.6] Reset the channel-swap flag for a new project
        analysisXRoi = null
        spdRbSwapRetried = false
        viewModelScope.launch {
            val id = dao.insertProject(ProjectEntity(name = name))
            _uiState.value = ProjectUiState(currentProject = ProjectEntity(id = id, name = name))
        }
    }

    fun finishProject() {
        viewModelScope.launch {
            // [v2.2.8] The report snapshot must be persisted **before** uiState is
            // cleared — after that the data is gone.
            // [v2.2.8-D1] This step must also share the same projectMetadataMutex
            // with the Step 4 snapshot write: otherwise a persistReportSnapshot()
            // still in flight writes back the stale row it read and pushes
            // status / finishedAt back to IN_PROGRESS / null, which closes the
            // gate in shouldRestoreProvenance() and loses the provenance again.
            // See the KDoc of finishProjectLocked() for the deadlock analysis.
            val written = finishProjectLocked(
                mutex = projectMetadataMutex,
                readProject = { _uiState.value.currentProject },
                serialize = {
                    // Serialization is on the order of 60 KB (three channel profiles
                    // plus six SPD curves), so switch to IO.
                    withContext(Dispatchers.IO) { _uiState.value.reportData.toJsonString() }
                },
                writeProject = { updated -> dao.updateProject(updated) }
            )
            // Preserve the original semantics when no project is in progress:
            // nothing is persisted and no status is touched.
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
                FrameProfileExtractor.forceRbSwap = false   // [v2.2.6] Reset first, then let Step 1 decide again
                analysisXRoi = null
                spdRbSwapRetried = false
                val band = fluorophoreBand(project.fluorophore)
                // [v2.2.8] Restore the whole report from the stored snapshot —
                // this is the only carrier of the Step 1/2/4 preset provenance
                // (none of the three load*Preset functions writes to the captures table).
                // If the restore fails (pre-v4 projects / malformed JSON / unknown
                // schema) `restored` is null and it falls back to a blank report,
                // behaving exactly as before this change — never crash the app
                // over old data.
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
                // [v2.2.8] The three calls above overwrite the provenance with
                // "Live calibration" through live recomputation.
                // A FINISHED project is **viewing a historical report**, not
                // redoing the work, so move the original provenance back.
                // Only the 12 provenance fields are moved; values keep whatever
                // was recomputed above.
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
        // The single write site normalises to canonical here — this guarantees
        // that projects.fluorophore always stores Fluorophore.X.name (existing
        // data is already canonical, so no migration is needed)
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
                // Overexposure / signal inversion check: concentration rises but
                // the signal drops significantly
                val sortedPoints = points.sortedBy { it.first }
                var saturationDetected = false
                for (i in 1 until sortedPoints.size) {
                    if (sortedPoints[i].first > sortedPoints[i - 1].first &&
                        sortedPoints[i].second < sortedPoints[i - 1].second * 0.8
                    ) { saturationDetected = true; break }
                }
                if (saturationDetected)
                    warnings.add(str(R.string.warn_saturation))
                if (points.any { it.second < 0.0 }) warnings.add(str(R.string.warn_negative_area))
                if (sampleArea < 0.0) warnings.add(str(R.string.warn_negative_sample))
                if (reg.r2 < 0.95) warnings.add(str(R.string.warn_low_r2))
                if (reg.slope < 0 && !useLogFit) warnings.add(str(R.string.warn_negative_slope))
                if (predictedConc < 0) warnings.add(str(R.string.warn_negative_pred))
                val xs = points.map { it.first }
                if (xs.isNotEmpty() && (predictedConc < xs.min() || predictedConc > xs.max())) {
                    warnings.add(str(R.string.warn_out_of_range))
                }

                val formulaStr = String.format(
                    Locale.US,
                    str(if (useLogFit) R.string.result_formula_log else R.string.result_formula_linear),
                    reg.slope, reg.intercept
                )
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
                    ).withLiveCurve(reg, points.size, src = str(R.string.report_source_live_curve))
                )
                persistReportSnapshot()
                setStep4(
                    "COMPUTED|" + str(R.string.st4_computed, String.format(Locale.US, "%.4f", reg.r2), points.size) +
                            (if (useLogFit) str(R.string.st_log_fit) else "") +
                            (if (warnings.isNotEmpty()) str(R.string.st_warn_count, warnings.size) else "") +
                            (if (retried) str(R.string.st_rb_swap_retry) else "")
                )
            }.onFailure { e ->
                setStep4("FAILED|" + (e.message ?: e.javaClass.simpleName))
                _uiState.value = _uiState.value.copy(
                    reportData = _uiState.value.reportData.copy(
                        regressionFormula = str(R.string.report_analysis_error, e.javaClass.simpleName)
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
            // A cross-platform preset may carry a non-canonical fluorophore name
            // such as "mCherry"; always resolve it first, and fail with a clear
            // error when it cannot be resolved instead of silently landing on the
            // wrong channel
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
                if (preset.rSquared < 0.95) add(str(R.string.warn_low_r2))
                if (sampleArea < 0.0) add(str(R.string.warn_negative_sample))
                if (predicted < 0.0) add(str(R.string.warn_negative_pred))
                if (predicted < points.minOf { it.first } || predicted > points.maxOf { it.first }) {
                    add(str(R.string.warn_out_of_range))
                }
            }
            val formula = String.format(
                Locale.US, str(R.string.result_formula_linear), preset.slope, preset.intercept)
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
            // Presets exported by Python FSSA carry their own display names
            // ("mCherry", "Fluorescein (sodium salt)"…); resolve them to canonical
            // before going further, and fail explicitly when resolution fails
            // rather than stopping in a "looks like it loaded" state
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
                ).withCurvePreset(presetEntity, payload, src = str(R.string.report_source_preset, presetEntity.name))
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
                    // [v2.2.6] DEF-A01: the true value comes from the decoder flag
                    // already set by analyzePositioning()
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
                    // [v2.2.6] DEF-A04: provenance fields are redundantly written to
                    // the top-level columns (a copy also lives in payloadJson)
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
        // [v2.2.6] DEF-A01: restore the channel-swap decision, otherwise
        // MCHERRY / CFP / mTurquoise2 read the wrong channel
        FrameProfileExtractor.forceRbSwap = p.rbSwapped ?: false
        // [v2.2.6] DEF-A02: the ROI is now actually applied. Once analysisXRoi is
        // non-null, decode() passes it to FrameProfileExtractor.extract as
        // fixedXRegion, which skips the content-dependent autoDetectXRoi — the
        // positioning image (bright LED) and the sample image (weak fluorescence)
        // no longer end up with different ROIs.
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
                    str(R.string.report_val_x_roi, p.roiStart, p.roiEnd, p.roiEnd - p.roiStart + 1) else "—",
                trainingPoints = str(R.string.report_val_training_points, p.wlB ?: 462.5, p.wlR ?: 622.5),
                validationPoint = str(R.string.report_val_validation_point, p.wlG ?: 522.5),
                gResidual = "—",
                slope = slope, intercept = intercept, gErrorNm = p.gErrorNm,
                peakR = p.peakR?.toDouble(), peakG = p.peakG?.toDouble(), peakB = p.peakB?.toDouble(),
                peakWindows = emptyList(),
                peakDiagnostics = str(R.string.report_peaks_from_preset),
                roiStart = p.roiStart, roiEnd = p.roiEnd,
                sensorWidth = p.sensorWidth, sensorHeight = p.sensorHeight,
                wlR = p.wlR ?: 622.5, wlG = p.wlG ?: 522.5, wlB = p.wlB ?: 462.5,
                previewUri = null,
                redProfile = emptyList(), greenProfile = emptyList(), blueProfile = emptyList()
            ).withWavelengthPreset(p, src = str(R.string.report_source_preset, p.name))
        )
        markLoadedCurveForReapply()
        setStep1(
            "PRESET|" + str(R.string.st_preset_loaded, p.quality ?: "—") +
                    (if (p.rbSwapped == true) " · " + str(R.string.st_preset_rb_restored) else "") +
                    (if (restoredRoi != null) " · " + str(
                        R.string.st_preset_roi_restored, restoredRoi.first, restoredRoi.second
                    ) else "")
        )
        // [D1] Snapshot the Step 2 provenance: the cascaded re-run below
        // recomputes SPD with the new mapping, but that is not the user
        // re-shooting the lamp, so "Preset · X" must not be overwritten with
        // "Live calibration".
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
            // [v2.2.6] DEF-A03: with no payload it must fail explicitly instead of
            // pretending to be "loaded"
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
        val finalResult = restored
        var driftNm: Float? = null

        if (slope != null && intercept != null) {
            val currentN = report.sensorHeight ?: restored.sensorHeight ?: restored.gridPoints

            // L3 -- drift is measured FIRST but it NEVER rejects the preset.
            // A v3 preset is indexed by wavelength, so a drifted mapping no
            // longer deforms anything: it only drives a hint.
            val drift = restored.mappingDriftNm(slope, intercept)
            driftNm = drift
            when (SpectralResponseCalibrator.driftLevel(drift)) {
                DriftLevel.WARN -> warnings.add(str(R.string.st_spd_drift_warn, drift!!))
                DriftLevel.SEVERE -> warnings.add(str(R.string.st_spd_drift_severe, drift!!))
                else -> Unit
            }

            // L2 -- coverage is the only remaining hard failure, and it is
            // evaluated in terms of the WAVELENGTHS the analysis needs, not the
            // raw pixel-axis endpoints (a whole-sensor axis routinely runs past
            // a preset's 380..780 nm grid without hurting anything).
            val coverage = SpectralResponseCalibrator.coverageError(finalResult, slope, intercept, currentN)
            if (coverage != null) {
                failSpdPreset(coverage)
                return
            }
        }

        spdRbSwapRetried = finalResult.rbSwapped
        spdResult = finalResult
        val applied = applySpdResult(finalResult.copy(spdSource = p.spdSource ?: finalResult.spdSource))
        if (!applied) {
            // [D4] Not a single sample point landed, so the preset never took
            // effect. Continuing to withSpdPreset here would show "Preset · X"
            // while the SPD data is in fact empty.
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
                .withSpdPreset(p, driftNm, src = str(R.string.report_source_preset, p.name))
                .copy(spdWarnings = warnings)
        )
        setStep2(
            "PRESET|" + str(R.string.st_preset_loaded, p.name) +
                    (if (warnings.isNotEmpty()) str(R.string.st_warn_count, warnings.size) else "")
        )
    }

    /**
     * Export the current SPD calibration as a schema-v3 JSON file through SAF.
     * Nothing is written to the database: the file IS the preset.
     */
    fun exportSpdPreset(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = spdResult
            if (result == null) {
                setStep2("FAILED|" + str(R.string.st_spd_export_nothing))
                return@launch
            }
            val written = runCatching {
                val json = SpectralResponseCalibrator.toJson(result)
                require(json != "{}") { "could not serialise the SPD calibration" }
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                } ?: error("could not open output stream")
            }
            setStep2(
                if (written.isSuccess) "PRESET|" + str(R.string.st_spd_export_ok)
                else "FAILED|" + str(R.string.st_spd_export_failed, written.exceptionOrNull()?.message ?: "unknown error")
            )
        }
    }

    /**
     * Import a schema-v3 SPD preset file through SAF.
     *
     * The payload is validated BEFORE anything reaches the database, so a bad
     * file can never pollute the preset table. Both the Room top-level summary
     * columns and the payload itself are filled from the file, otherwise the
     * preset list would show "—" for everything the payload does carry.
     */
    fun importSpdPreset(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val parsed = runCatching {
                val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?.toString(Charsets.UTF_8) ?: error("could not read the selected file")
                SpectralResponseCalibrator.fromJsonStrict(text)
            }
            val spd = parsed.getOrElse { e ->
                withContext(Dispatchers.Main) { failSpdPreset(e.message ?: e.javaClass.simpleName) }
                return@launch
            }

            val name = runCatching { ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) c.getString(i) else null
            } }.getOrNull()
                ?.removeSuffix(".json")?.removeSuffix(".JSON")
                ?.takeIf { it.isNotBlank() }
                ?: "imported-spd-" + System.currentTimeMillis()

            val entity = PresetEntity(
                name = name, type = PresetTypes.SPD,
                // The row describes who holds the copy; the ORIGINATING device
                // travels inside the payload as `device_model`.
                deviceModel = Build.MODEL,
                frames = spd.frames,
                exposureSeconds = spd.exposureSeconds.takeIf { it.isFinite() && it > 0.0 },
                spdSource = spd.spdSource,
                slope = spd.slope.takeIf { it.isFinite() },
                intercept = spd.intercept.takeIf { it.isFinite() },
                gErrorNm = spd.gErrorNm,
                rbSwapped = spd.rbSwapped,
                roiStart = spd.xRoiStart, roiEnd = spd.xRoiEnd,
                sensorHeight = spd.sensorHeight
            )
            // Store the payload exactly as THIS app writes it, so a Room row and
            // the file it came from cannot drift apart.
            val json = SpectralResponseCalibrator.toJson(spd, deviceModel = entity.deviceModel)
            val inserted = runCatching { presetDao.insert(entity.copy(payloadJson = json)) }.getOrNull()
            if (inserted == null) {
                withContext(Dispatchers.Main) { failSpdPreset(str(R.string.st_spd_import_store_failed)) }
                return@launch
            }
            withContext(Dispatchers.Main) {
                loadSpdPreset(entity.copy(payloadJson = json, id = inserted))
            }
        }
    }

    /** [v2.2.6] When an SPD preset is unusable: the status must be FAILED and the text must state explicitly that it was not applied. */
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

            // [v2.2.6] Key: push the R/B swap decision down to the decoder so the
            // labels stay consistent when Step 2/3 decodes again
            FrameProfileExtractor.forceRbSwap = res.rbSwapped

            val trainingPoints = if (useGreenRedFit)
                str(R.string.report_val_training_points, wlG, wlR)
            else str(R.string.report_val_training_points, wlB, wlR)
            val validationPoint = if (useGreenRedFit) "—" else str(R.string.report_val_validation_point, wlG)

            _uiState.value = _uiState.value.copy(
                reportData = _uiState.value.reportData.copy(
                    wavelengthQuality = res.quality,
                    wavelengthGError = if (useGreenRedFit) str(R.string.step1_g_error_filter)
                    else String.format(Locale.US, "%+.3f nm", res.gErrorNm),
                    mappingFormula = String.format(Locale.US, "Pixel(p) = %.6f * Wavelength(nm) + %.4f", res.slope, res.intercept),
                    exposureTime = if (res.exposureSeconds > 0) String.format(Locale.US, "%.4f s", res.exposureSeconds) else "—",
                    xRoi = str(
                        R.string.report_val_x_roi, res.roiStart, res.roiEnd,
                        res.roiEnd - res.roiStart + 1
                    ),
                    trainingPoints = trainingPoints,
                    validationPoint = validationPoint,
                    gResidual = String.format(Locale.US, str(R.string.report_val_g_residual_px), res.gResidualPx),
                    slope = res.slope, intercept = res.intercept, gErrorNm = res.gErrorNm,
                    peakR = res.peakR, peakG = res.peakG, peakB = res.peakB,
                    peakWindows = res.peakWindows,
                    peakDiagnostics = if (res.peakFlags.isEmpty()) "—" else "⚠ " + res.peakFlags.joinToString("; "),
                    roiStart = res.roiStart, roiEnd = res.roiEnd,
                    sensorWidth = res.sensorWidth, sensorHeight = res.sensorHeight,
                    redProfile = res.red, greenProfile = res.green, blueProfile = res.blue,
                    previewUri = uris.first().toString(),
                    wlR = wlR, wlG = wlG, wlB = wlB
                ).withLiveWavelength(
                    res.slope, res.intercept, res.roiStart, res.roiEnd, res.rbSwapped,
                    src = str(R.string.report_source_live)
                )
            )
            setStep1(
                "COMPUTED|" + str(R.string.st_computed_quality, res.quality, note(batch)) +
                        if (res.peakFlags.isNotEmpty()) str(R.string.st_suspect) else "" +
                                if (useGreenRedFit) str(R.string.st_filter_mode) else "" +
                                        if (res.rbSwapped) str(R.string.st_rb_auto_corrected) else ""
            )
        }.onFailure { e ->
            setStep1("FAILED|" + (e.message ?: e.javaClass.simpleName))
            _uiState.value = _uiState.value.copy(
                reportData = _uiState.value.reportData.copy(
                    wavelengthQuality = "FAILED",
                    mappingFormula = str(R.string.report_analysis_error, e.javaClass.simpleName)
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
            // [v2.2.6] gErrorNm is written along with it, for SPD preset provenance
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
            _uiState.update { st ->
                st.copy(
                    reportData = st.reportData.withLiveSpd(
                        res.spdSource, src = str(R.string.report_source_live_detail, res.spdSource)
                    )
                )
            }
            setStep2("COMPUTED|" + str(
                R.string.st_computed_spd, res.frames,
                "${res.maskedCount}/${res.gridPoints}", note(lampBatch)
            ) + if (spdRbSwapRetried) str(R.string.st_rb_swap_retry) else "")
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
                        Locale.US, str(R.string.report_val_integrated_area),
                        res.correctedArea, res.channel, if (res.responseApplied) "APPLIED" else "N/A"
                    ),
                    sampleSD = String.format(Locale.US, "%.1f", res.sampleSD),
                    replicates = res.replicates,
                    integrationWindow = str(R.string.report_val_integration_window, res.windowStartPx, res.windowEndPx),
                    sampleSpectrum = res.correctedSpectrum,
                    blankSpectrum = res.blankSpectrum,
                    correctedAreaValue = res.correctedArea,
                    samplePeakNm = String.format(Locale.US, "%.1f nm", res.peakNm),
                    samplePeakIntensity = String.format(Locale.US, "%.2f", res.peakIntensity),
                    sampleFwhm = String.format(Locale.US, "%.1f nm", res.fwhmNm),
                    sampleCentroid = String.format(Locale.US, "%.1f nm", res.centroidNm)
                )
            )
            // [v2.2.6] Exposure echo plus a missing-exposure warning, so
            // normalization can be verified on site
            val bExp = blankBatch.frames.firstOrNull()?.exposureSeconds ?: 0.0
            val sExp = sampleBatch.frames.firstOrNull()?.exposureSeconds ?: 0.0
            val noExp = (blankBatch.frames + sampleBatch.frames).count { it.exposureSeconds <= 0.0 }
            setStep3(
                "COMPUTED|" + str(
                    R.string.st_computed_sample, res.channel,
                    if (res.responseApplied) "APPLIED" else "N/A",
                    note(blankBatch), note(sampleBatch)
                ) + String.format(Locale.US, str(R.string.st_exp_echo), bExp, sExp) +
                        (if (retried) str(R.string.st_rb_swap_retry) else "") +
                        // [v2.2.6] DEF-A03: when SPD correction is not applied the
                        // reason must be stated, not just "N/A"
                        (res.spdSkipReason?.let { str(R.string.st_spd_skip_reason, it) } ?: "") +
                        (if (noExp > 0) str(R.string.st_missing_exposure, noExp) else "")
            )
        }.onFailure { e ->
            setStep3("FAILED|" + (e.message ?: e.javaClass.simpleName))
            _uiState.value = _uiState.value.copy(
                reportData = _uiState.value.reportData.copy(
                    sampleIntegratedArea = str(R.string.report_analysis_error, e.javaClass.simpleName)
                )
            )
        }
    }

    private fun resetPage2() {
        _uiState.value = _uiState.value.copy(
            reportData = _uiState.value.reportData.copy(
                spdStatus = str(R.string.report_spd_status_pending), spdCoeffR = "—", spdCoeffB = "—", spdResponse = "—",
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

    /** @return true when the SPD data was actually written to the report; false when there is no usable sample point. */
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
                spdStatus = str(R.string.report_spd_status_calibrated, res.frames),
                spdSource = res.spdSource,
                spdCoeffR = String.format(Locale.US, "%.0f a.u./SPD @ %.1f nm", res.peakRespR.first, res.peakRespR.second),
                spdCoeffB = String.format(Locale.US, "%.0f a.u./SPD @ %.1f nm", res.peakRespB.first, res.peakRespB.second),
                spdResponse = str(R.string.report_spd_response_fused),
                spdFrames = res.frames,
                spdExposure = if (res.exposureSeconds > 0) String.format(Locale.US, "%.4f s", res.exposureSeconds) else "—",
                spdEffectiveBand = String.format(Locale.US, "%.1f–%.1f nm", res.effectiveBandNm.first, res.effectiveBandNm.second),
                spdMasked = "${res.maskedCount}/${res.gridPoints}",
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
     * [v2.2.8] Writes the current report snapshot back to the project row.
     *
     * Why store it again when a Step 4 result is produced, on top of
     * `finishProject()`: after running the standard curve a user is likely to
     * quit the app outright and never reach `finishProject()`.
     *
     * Always switches to IO — the snapshot holds three channel profiles and a
     * dozen SPD curves, so serialization is on the order of tens of KB.
     * Persistence failures are swallowed silently: a successfully computed result
     * should not turn into a UI error because of a transient storage problem.
     */
    private fun persistReportSnapshot() {
        viewModelScope.launch(Dispatchers.IO) {
            persistReportSnapshotLocked(
                mutex = projectMetadataMutex,
                readProject = { _uiState.value.currentProject },
                serialize = { _uiState.value.reportData.toJsonString() },
                // Persistence failures are swallowed silently: a successfully
                // computed result should not turn into a UI error because of a
                // transient storage problem.
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
        // Sodium fluorescein emits at 520 nm, which falls inside 500–540; the else
        // branch used to cover it correctly by accident, so state it explicitly to
        // avoid silently breaking it when else changes later
        Fluorophore.FLUORESCEIN -> 500 to 540
        else -> 500 to 540
    }

    /**
     * Resolves a fluorophore name that came from outside (the `fluorophore` field
     * of a curve preset).
     *
     * An unrecognised name throws [IllegalArgumentException] — callers sit inside
     * `runCatching`, so this message shows up verbatim in the Step 4 status bar
     * instead of silently degrading to the wrong channel.
     */
    private fun requireKnownFluorophore(raw: String, context: String): Fluorophore =
        canonicalFluorophore(raw) ?: error(
            "Unrecognised fluorophore '$raw' in $context; supported: " +
                Fluorophore.values().joinToString(", ") { it.displayName }
        )
}
