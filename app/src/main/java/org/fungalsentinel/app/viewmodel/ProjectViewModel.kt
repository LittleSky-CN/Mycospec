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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fungalsentinel.app.FssaApplication
import org.fungalsentinel.app.R
import org.fungalsentinel.app.analysis.ConcentrationRegressor
import org.fungalsentinel.app.analysis.FrameProfile
import org.fungalsentinel.app.analysis.FrameProfileExtractor
import org.fungalsentinel.app.analysis.SampleAnalyzer
import org.fungalsentinel.app.analysis.SpdCalibrationResult
import org.fungalsentinel.app.analysis.SpdLoader
import org.fungalsentinel.app.analysis.SpectralResponseCalibrator
import org.fungalsentinel.app.analysis.WavelengthCalibrator
import org.fungalsentinel.app.data.AppSettings
import org.fungalsentinel.app.data.AppPreferences
import org.fungalsentinel.app.data.model.CaptureEntity
import org.fungalsentinel.app.data.model.ProjectEntity
import org.fungalsentinel.app.data.model.StandardGroupEntity
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
    val spdSource: String = "true_spd.csv (bundled)",
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
    val spdMethodNote: String = "Numerator = Pos (independent LEDs); pointwise ops only; logistic handoff weights (15 nm)",
    val spdHandoffBG: Float = 483f,
    val spdHandoffGR: Float = 577f,
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
    val isLogFit: Boolean = false,
    val step1Status: String = "IDLE",
    val step2Status: String = "IDLE",
    val step3Status: String = "IDLE",
    val step4Status: String = "IDLE"
)

data class ProjectUiState(
    val currentProject: ProjectEntity? = null,
    val positioningCaptures: List<Uri> = emptyList(),
    val spdCaptures: List<Uri> = emptyList(),
    val blankCaptures: List<Uri> = emptyList(),
    val sampleCaptures: List<Uri> = emptyList(),
    val standardGroups: List<StandardGroupInput> = emptyList(),
    val reportData: ReportData = ReportData()
)

class ProjectViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as FssaApplication).database.projectDao()
    private val presetDao = (application as FssaApplication).presetDatabase.presetDao()

    private val _uiState = MutableStateFlow(ProjectUiState())
    val uiState: StateFlow<ProjectUiState> = _uiState.asStateFlow()
    private val _settings = MutableStateFlow(AppSettings())

    private val _hasBlueFilter = MutableStateFlow(false)
    val hasBlueFilter: StateFlow<Boolean> = _hasBlueFilter.asStateFlow()

    val wlPresets: StateFlow<List<PresetEntity>> =
        presetDao.observeByType(PresetTypes.WAVELENGTH)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val spdPresets: StateFlow<List<PresetEntity>> =
        presetDao.observeByType(PresetTypes.SPD)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var spdResult: SpdCalibrationResult? = null
    private var spdRbSwapRetried = false

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
        uris.forEachIndexed { i, u ->
            val p = FrameProfileExtractor.extract(ctx, u)
            if (p != null) ok.add(p) else bad.add("#${i + 1}")
        }
        return DecodeBatch(ok, uris.size, bad)
    }

    private fun swapRbChannels(frames: List<FrameProfile>): List<FrameProfile> =
        frames.map { it.copy(redProfile = it.blueProfile, blueProfile = it.redProfile) }

    fun createProject(name: String, hasBlueFilter: Boolean = false) {
        _hasBlueFilter.value = hasBlueFilter
        FrameProfileExtractor.forceRbSwap = false   // 【v2.2.6】新项目重置通道交换标志
        spdRbSwapRetried = false
        viewModelScope.launch {
            val id = dao.insertProject(ProjectEntity(name = name))
            _uiState.value = ProjectUiState(currentProject = ProjectEntity(id = id, name = name))
        }
    }

    fun finishProject() {
        viewModelScope.launch {
            val current = _uiState.value.currentProject ?: return@launch
            dao.updateProject(current.copy(status = "FINISHED", finishedAt = System.currentTimeMillis()))
            _uiState.value = ProjectUiState()
            _hasBlueFilter.value = false
            FrameProfileExtractor.forceRbSwap = false
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
                spdRbSwapRetried = false
                _uiState.value = ProjectUiState(project, pos, spd, blank, sample, groups)
                if (pos.isNotEmpty()) setStep1("PROCESSING|" + str(R.string.st_processing_frames, pos.size))
                if (spd.isNotEmpty()) setStep2("PROCESSING|" + str(R.string.st_processing_frames, spd.size))
                if (blank.isNotEmpty() || sample.isNotEmpty()) setStep3("PROCESSING|" + str(R.string.st_processing_frames, blank.size + sample.size))
                analyzePositioning()
                if (spd.isNotEmpty()) analyzeSpd()
                analyzeSample()
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
        _uiState.value = _uiState.value.copy(positioningCaptures = uris)
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
        setStep3("PROCESSING|" + str(R.string.st_processing_frames, uris.size))
        viewModelScope.launch(Dispatchers.IO) { persistAll(); analyzeSample() }
    }

    fun setSampleCaptures(uris: List<Uri>) {
        _uiState.value = _uiState.value.copy(sampleCaptures = uris)
        setStep3("PROCESSING|" + str(R.string.st_processing_frames, uris.size))
        viewModelScope.launch(Dispatchers.IO) { persistAll(); analyzeSample() }
    }

    fun setFluorophore(name: String) {
        val band = when (name) {
            "YPET" -> 500 to 530
            "EGFP" -> 500 to 540
            "MCHERRY" -> 590 to 630
            "CFP" -> 460 to 490
            "MTURQUOISE2" -> 460 to 490
            "FLUORESCEIN" -> 500 to 540
            else -> 500 to 540
        }
        _uiState.value = _uiState.value.copy(
            reportData = _uiState.value.reportData.copy(
                sampleFluorophore = name,
                sampleBand = "${band.first}–${band.second} nm"
            )
        )
        viewModelScope.launch(Dispatchers.IO) { analyzeSample() }
    }

    fun setStandardGroups(groups: List<StandardGroupInput>) {
        _uiState.value = _uiState.value.copy(standardGroups = groups)
        viewModelScope.launch(Dispatchers.IO) { persistAll() }
    }

    fun buildCurve() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val st = _uiState.value
                val slope = st.reportData.slope ?: run { setStep4("BLOCKED|" + str(R.string.st_blocked_step1)); return@launch }
                val intercept = st.reportData.intercept ?: run { setStep4("BLOCKED|" + str(R.string.st_blocked_step1)); return@launch }
                if (st.standardGroups.size < 2) { setStep4("BLOCKED|" + str(R.string.st4_blocked_groups)); return@launch }

                val singleBlank = _settings.value.blankMode == "SINGLE"
                if (singleBlank && st.blankCaptures.isEmpty()) {
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
                val band = parseBand(st.reportData.sampleBand) ?: (500.0 to 540.0)
                fun collectPoints(swapRb: Boolean): Triple<List<Pair<Double, Double>>, List<String>, Int> {
                    val points = mutableListOf<Pair<Double, Double>>()
                    val rows = mutableListOf<String>()
                    var skipped = 0
                    for ((g, blankFrames, sampleFrames) in decodedGroups) {
                        if (blankFrames.isEmpty() || sampleFrames.isEmpty()) { skipped++; continue }
                        val analysisBlank = if (swapRb) swapRbChannels(blankFrames) else blankFrames
                        val analysisSample = if (swapRb) swapRbChannels(sampleFrames) else sampleFrames
                        val a = SampleAnalyzer.analyze(
                            analysisBlank, analysisSample, band.first, band.second, slope, intercept,
                            fluorophore = st.reportData.sampleFluorophore,
                            wavelengths = spd?.wavelengths,
                            respR = spd?.respR, respG = spd?.respG, respB = spd?.respB
                        ) ?: run { skipped++; continue }
                        points.add(g.concentration to a.correctedArea)
                        rows.add(String.format(Locale.US, "%.3f %s → %.1f", g.concentration, g.unit, a.correctedArea))
                    }
                    return Triple(points, rows, skipped)
                }

                val initialSwap = spdRbSwapRetried
                var candidate = collectPoints(initialSwap)
                var regression = if (candidate.first.size >= 2) ConcentrationRegressor.fit(candidate.first) else null
                var retried = false
                if (regression == null) {
                    val retryCandidate = collectPoints(!initialSwap)
                    val retryReg = if (retryCandidate.first.size >= 2) {
                        ConcentrationRegressor.fit(retryCandidate.first)
                    } else null
                    if (retryReg != null) {
                        candidate = retryCandidate
                        regression = retryReg
                        retried = true
                    }
                }
                val points = candidate.first
                val rows = candidate.second
                val skipped = candidate.third
                if (points.size < 2) { setStep4("FAILED|" + str(R.string.st4_failed_groups, points.size)); return@launch }

                // 【v2.2.6 核心修复】直接传原始 points，由 ConcentrationRegressor 内部决定是否取 log，
                // 避免“外层先 log10 + 内层再 log10”的双重对数错误（曾导致预测值变成 log 值）
                val reg = regression ?: run {
                    setStep4("FAILED|" + str(R.string.st4_failed_reg)); return@launch
                }
                val useLogFit = reg.isLogarithmic

                val sampleArea = st.reportData.correctedAreaValue
                val predictedConc = sampleArea?.let { ConcentrationRegressor.predict(reg, it) }

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
                if (sampleArea != null && sampleArea < 0.0) warnings.add("Negative sample area — prediction unreliable")
                if (reg.r2 < 0.95) warnings.add("Low R² (<0.95) — curve may not be reliable")
                if (reg.slope < 0 && !useLogFit) warnings.add("Negative slope detected — check for overexposure or incorrect blank subtraction.")
                predictedConc?.let { p ->
                    if (p < 0) warnings.add("Negative prediction — check blank / calibration")
                    val xs = points.map { it.first }
                    if (xs.isNotEmpty() && (p < xs.min() || p > xs.max())) warnings.add("Prediction outside calibration range")
                }

                val formulaStr = if (useLogFit) {
                    String.format(Locale.US, "Area = %.4f * log10(Conc) + %.2f", reg.slope, reg.intercept)
                } else {
                    String.format(Locale.US, "Area = %.4f * Conc + %.2f", reg.slope, reg.intercept)
                }

                _uiState.value = _uiState.value.copy(
                    reportData = _uiState.value.reportData.copy(
                        standardRows = rows,
                        regressionFormula = formulaStr,
                        regressionR2 = String.format(Locale.US, "%.4f", reg.r2),
                        predictedConcentration = predictedConc?.let { String.format(Locale.US, "%.3f", it) } ?: "—",
                        curveSlope = reg.slope,
                        curveIntercept = reg.intercept,
                        curvePoints = points.sortedBy { it.first },
                        curveWarnings = warnings,
                        isLogFit = useLogFit
                    )
                )
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
                    exposureSeconds = r.exposureTime.removeSuffix(" s").toDoubleOrNull()
                )
            )
        }
    }

    fun saveSpdPreset(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = _uiState.value.reportData
            presetDao.insert(
                PresetEntity(
                    name = name, type = PresetTypes.SPD, deviceModel = Build.MODEL,
                    coeffR = r.spdCoeffR.toDoubleOrNull(), coeffB = r.spdCoeffB.toDoubleOrNull(),
                    frames = r.spdFrames,
                    exposureSeconds = r.spdExposure.removeSuffix(" s").toDoubleOrNull(),
                    spdSource = r.spdSource,
                    payloadJson = spdResult?.let { SpectralResponseCalibrator.toJson(it) }
                )
            )
        }
    }

    fun loadWavelengthPreset(p: PresetEntity) {
        val slope = p.slope ?: return
        val intercept = p.intercept ?: return
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
            )
        )
        setStep1("PRESET|" + str(R.string.st_preset_loaded, p.quality ?: "—"))
        viewModelScope.launch(Dispatchers.IO) {
            if (_uiState.value.spdCaptures.isNotEmpty()) analyzeSpd()
            analyzeSample()
        }
    }

    fun loadSpdPreset(p: PresetEntity) {
        val restored = p.payloadJson?.let { SpectralResponseCalibrator.fromJson(it) }
        spdRbSwapRetried = restored?.rbSwapped ?: false
        spdResult = restored
        if (restored != null) {
            applySpdResult(restored.copy(spdSource = p.spdSource ?: restored.spdSource))
            setStep2("PRESET|" + str(R.string.st_preset_loaded, p.name))
        } else {
            setStep2("PRESET|" + str(R.string.st_preset_loaded, p.name))
            _uiState.value = _uiState.value.copy(
                reportData = _uiState.value.reportData.copy(
                    spdCoeffR = p.coeffR?.let { String.format(Locale.US, "%.4f", it) } ?: "—",
                    spdCoeffB = p.coeffB?.let { String.format(Locale.US, "%.4f", it) } ?: "—",
                    spdResponse = "Loaded from legacy preset",
                    spdFrames = p.frames ?: 0,
                    spdExposure = p.exposureSeconds?.let { String.format(Locale.US, "%.4f s", it) } ?: "—",
                    spdSource = p.spdSource ?: "true_spd.csv (bundled)"
                )
            )
        }
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
                )
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
            var res = SpectralResponseCalibrator.calibrate(
                lampBatch.frames, posBatch.frames, spd.second, slope, intercept, spd.first
            )
            if (res == null) {
                res = SpectralResponseCalibrator.calibrate(
                    swapRbChannels(lampBatch.frames), swapRbChannels(posBatch.frames),
                    spd.second, slope, intercept, spd.first
                )
                spdRbSwapRetried = res != null
            }
            if (res == null) {
                setStep2("FAILED|" + str(R.string.st_failed_spd_degenerate, note(lampBatch))); return
            }
            res = res.copy(rbSwapped = spdRbSwapRetried)
            spdResult = res
            applySpdResult(res)
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
                spdFrames = 0, spdExposure = "—", spdEffectiveBand = "—", spdMasked = "—",
                spdHandoffs = "—", spdAgreement = "—", spdFusedPeak = "—", spdPeaks = "—",
                spdRawPeaks = "—", spdCorrPeaks = "—",
                spdHandoffBG = 483f, spdHandoffGR = 577f,
                spdLambda = emptyList(),
                spdPosR = emptyList(), spdPosG = emptyList(), spdPosB = emptyList(),
                spdRespR = emptyList(), spdRespG = emptyList(), spdRespB = emptyList(),
                spdCorrR = emptyList(), spdCorrG = emptyList(), spdCorrB = emptyList(),
                spdWB = emptyList(), spdWG = emptyList(), spdWR = emptyList(),
                spdFused = emptyList()
            )
        )
    }

    private fun applySpdResult(res: SpdCalibrationResult) {
        val maskedIdx = res.wavelengths.indices.filter { res.mask.getOrElse(it) { false } }
        if (maskedIdx.isEmpty()) { resetPage2(); return }
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
                    Locale.US, "B→G: %.1f nm   G→R: %.1f nm   (logistic width %.0f nm)",
                    res.handoffBG, res.handoffGR, res.logisticWidthNm
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
                spdHandoffBG = res.handoffBG,
                spdHandoffGR = res.handoffGR,
                spdLambda = pick(res.wavelengths),
                spdPosR = pick(res.posR), spdPosG = pick(res.posG), spdPosB = pick(res.posB),
                spdRespR = pick(res.respR), spdRespG = pick(res.respG), spdRespB = pick(res.respB),
                spdCorrR = pick(res.corrR), spdCorrG = pick(res.corrG), spdCorrB = pick(res.corrB),
                spdWB = pick(res.wB), spdWG = pick(res.wG), spdWR = pick(res.wR),
                spdFused = pick(res.fused)
            )
        )
    }

    private suspend fun persistAll() {
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

    private fun parseBand(band: String): Pair<Double, Double>? {
        val m = Regex("(\\d+)–(\\d+)").find(band) ?: return null
        val (a, b) = m.destructured
        return a.toDoubleOrNull()?.let { bb -> b.toDoubleOrNull()?.let { bb to it } }
    }
}