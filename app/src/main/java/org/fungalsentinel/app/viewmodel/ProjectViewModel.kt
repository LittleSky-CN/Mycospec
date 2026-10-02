package org.fungalsentinel.app.viewmodel

import android.app.Application
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
import org.fungalsentinel.app.analysis.ConcentrationRegressor
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

data class StandardGroupInput(
    val id: Int, val concentration: Double, val unit: String,
    val blankUris: List<Uri>, val sampleUris: List<Uri>
)

data class ReportData(
    // Page 1
    val wavelengthQuality: String = "—", val wavelengthGError: String = "— nm",
    val mappingFormula: String = "—", val exposureTime: String = "—", val xRoi: String = "—",
    val trainingPoints: String = "—", val validationPoint: String = "—", val gResidual: String = "—",
    val slope: Double? = null, val intercept: Double? = null, val gErrorNm: Double? = null,
    val peakR: Int? = null, val peakG: Int? = null, val peakB: Int? = null,
    val roiStart: Int? = null, val roiEnd: Int? = null,
    val sensorWidth: Int? = null, val sensorHeight: Int? = null,
    val redProfile: List<Float> = emptyList(), val greenProfile: List<Float> = emptyList(), val blueProfile: List<Float> = emptyList(),
    val previewUri: String? = null,
    val wlR: Double = 622.5, val wlG: Double = 522.5, val wlB: Double = 462.5,
    // Page 2 (v1.4 fusion)
    val spdStatus: String = "Pending", val spdSource: String = "true_spd.csv (bundled)",
    val spdCoeffR: String = "—", val spdCoeffB: String = "—", val spdResponse: String = "—",
    val spdFrames: Int = 0, val spdExposure: String = "—", val spdEffectiveBand: String = "—",
    val spdMasked: String = "—", val spdHandoffs: String = "—", val spdWeightsRef: String = "—",
    val spdPeaks: String = "—", val spdRobustness: String = "—", val spdFusedPeak: String = "—",
    val spdMethodNote: String = "Independent-data correction; per-channel ops; no smoothing; 5% floor",
    val spdLambda: List<Float> = emptyList(),
    val spdRespR: List<Float> = emptyList(), val spdRespG: List<Float> = emptyList(), val spdRespB: List<Float> = emptyList(),
    val spdCorrR: List<Float> = emptyList(), val spdCorrG: List<Float> = emptyList(), val spdCorrB: List<Float> = emptyList(),
    val spdWR: List<Float> = emptyList(), val spdWG: List<Float> = emptyList(), val spdWB: List<Float> = emptyList(),
    val spdFused: List<Float> = emptyList(),
    // Page 3
    val sampleFluorophore: String = "—", val sampleBand: String = "—",
    val blankMeanArea: String = "—", val sampleMeanArea: String = "—", val sampleIntegratedArea: String = "—",
    val sampleSD: String = "—", val replicates: Int = 0, val integrationWindow: String = "—",
    val sampleSpectrum: List<Float> = emptyList(), val blankSpectrum: List<Float> = emptyList(),
    val correctedAreaValue: Double? = null,
    // Page 4
    val standardRows: List<String> = emptyList(), val regressionFormula: String = "—",
    val regressionR2: String = "—", val predictedConcentration: String = "—",
    val curveSlope: Double? = null, val curveIntercept: Double? = null,
    val curvePoints: List<Pair<Double, Double>> = emptyList(), val curveWarnings: List<String> = emptyList()
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

    val wlPresets: StateFlow<List<PresetEntity>> = presetDao.observeByType(PresetTypes.WAVELENGTH)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val spdPresets: StateFlow<List<PresetEntity>> = presetDao.observeByType(PresetTypes.SPD)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var spdResult: SpdCalibrationResult? = null

    init {
        viewModelScope.launch { AppPreferences.getSettings(application).collect { _settings.value = it } }
    }

    fun createProject(name: String) {
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
            spdResult = null
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
                _uiState.value = ProjectUiState(project, pos, spd, blank, sample, groups)
                analyzePositioning()
                if (spd.isNotEmpty()) analyzeSpd()
                analyzeSample()
                val step = when { pos.isEmpty() -> 1; spd.isEmpty() -> 2; sample.isEmpty() -> 3; else -> 4 }
                withContext(Dispatchers.Main) { onReady(step) }
            }
        }
    }

    fun setPositioningCaptures(uris: List<Uri>) {
        _uiState.value = _uiState.value.copy(positioningCaptures = uris)
        viewModelScope.launch(Dispatchers.IO) { persistAll(); analyzePositioning(); if (_uiState.value.spdCaptures.isNotEmpty()) analyzeSpd(); analyzeSample() }
    }
    fun setSpdCaptures(uris: List<Uri>) {
        _uiState.value = _uiState.value.copy(spdCaptures = uris)
        viewModelScope.launch(Dispatchers.IO) { persistAll(); analyzeSpd() }
    }
    fun setBlankCaptures(uris: List<Uri>) {
        _uiState.value = _uiState.value.copy(blankCaptures = uris)
        viewModelScope.launch(Dispatchers.IO) { persistAll(); analyzeSample() }
    }
    fun setSampleCaptures(uris: List<Uri>) {
        _uiState.value = _uiState.value.copy(sampleCaptures = uris)
        viewModelScope.launch(Dispatchers.IO) { persistAll(); analyzeSample() }
    }
    fun setFluorophore(name: String) {
        val band = when (name) { "YPET" -> 500 to 530; "EGFP" -> 500 to 540; "MCHERRY" -> 590 to 630; "CFP" -> 460 to 490; "MTURQUOISE2" -> 460 to 490; else -> 500 to 540 }
        _uiState.value = _uiState.value.copy(reportData = _uiState.value.reportData.copy(sampleFluorophore = name, sampleBand = "${band.first}–${band.second} nm"))
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
                val slope = st.reportData.slope ?: return@launch
                val intercept = st.reportData.intercept ?: return@launch
                val ctx = getApplication<Application>()
                val points = mutableListOf<Pair<Double, Double>>()
                val rows = mutableListOf<String>()
                for (g in st.standardGroups) {
                    val blanks = g.blankUris.mapNotNull { FrameProfileExtractor.extract(ctx, it) }
                    val samples = g.sampleUris.mapNotNull { FrameProfileExtractor.extract(ctx, it) }
                    val band = parseBand(st.reportData.sampleBand) ?: (500.0 to 540.0)
                    val a = SampleAnalyzer.analyze(blanks, samples, band.first, band.second, slope, intercept) ?: continue
                    points.add(g.concentration to a.correctedArea)
                    rows.add(String.format(Locale.US, "%.3f %s → %.1f", g.concentration, g.unit, a.correctedArea))
                }
                val reg = ConcentrationRegressor.fit(points) ?: return@launch
                val predicted = st.reportData.correctedAreaValue?.let { ConcentrationRegressor.predict(reg, it) }
                val warnings = reg.warnings.toMutableList()
                predicted?.let { p ->
                    if (p < 0) warnings.add("Negative prediction — check blank / calibration")
                    val xs = points.map { it.first }
                    if (xs.isNotEmpty() && (p < xs.min() || p > xs.max())) warnings.add("Prediction outside calibration range")
                }
                _uiState.value = _uiState.value.copy(reportData = _uiState.value.reportData.copy(
                    standardRows = rows,
                    regressionFormula = String.format(Locale.US, "Area = %.4f * Conc + %.2f", reg.slope, reg.intercept),
                    regressionR2 = String.format(Locale.US, "%.4f", reg.r2),
                    predictedConcentration = predicted?.let { String.format(Locale.US, "%.3f", it) } ?: "—",
                    curveSlope = reg.slope, curveIntercept = reg.intercept, curvePoints = points, curveWarnings = warnings
                ))
            }.onFailure { e -> _uiState.value = _uiState.value.copy(reportData = _uiState.value.reportData.copy(regressionFormula = "Analysis error: ${e.javaClass.simpleName}")) }
        }
    }

    fun saveWavelengthPreset(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = _uiState.value.reportData
            presetDao.insert(PresetEntity(name = name, type = PresetTypes.WAVELENGTH, deviceModel = Build.MODEL,
                slope = r.slope, intercept = r.intercept, gErrorNm = r.gErrorNm, quality = r.wavelengthQuality,
                roiStart = r.roiStart, roiEnd = r.roiEnd, sensorWidth = r.sensorWidth, sensorHeight = r.sensorHeight,
                peakR = r.peakR, peakG = r.peakG, peakB = r.peakB, wlR = r.wlR, wlG = r.wlG, wlB = r.wlB,
                exposureSeconds = r.exposureTime.removeSuffix(" s").toDoubleOrNull()
            ))
        }
    }

    fun saveSpdPreset(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = _uiState.value.reportData
            presetDao.insert(PresetEntity(name = name, type = PresetTypes.SPD, deviceModel = Build.MODEL,
                coeffR = r.spdCoeffR.toDoubleOrNull(), coeffB = r.spdCoeffB.toDoubleOrNull(), frames = r.spdFrames,
                exposureSeconds = r.spdExposure.removeSuffix(" s").toDoubleOrNull(), spdSource = r.spdSource,
                payloadJson = spdResult?.let { SpectralResponseCalibrator.toJson(it) }
            ))
        }
    }

    fun loadWavelengthPreset(p: PresetEntity) {
        val slope = p.slope ?: return; val intercept = p.intercept ?: return
        _uiState.value = _uiState.value.copy(reportData = _uiState.value.reportData.copy(
            wavelengthQuality = "PRESET",
            wavelengthGError = p.gErrorNm?.let { String.format(Locale.US, "%+.3f nm", it) } ?: "—",
            mappingFormula = String.format(Locale.US, "Pixel(p) = %.6f * Wavelength(nm) + %.4f", slope, intercept),
            exposureTime = p.exposureSeconds?.let { String.format(Locale.US, "%.4f s", it) } ?: "—",
            xRoi = if (p.roiStart != null && p.roiEnd != null) "[${p.roiStart}, ${p.roiEnd}] (Width: ${p.roiEnd - p.roiStart + 1} px)" else "—",
            trainingPoints = "B(${p.wlB ?: 462.5}nm), R(${p.wlR ?: 622.5}nm)", validationPoint = "G(${p.wlG ?: 522.5}nm)",
            gResidual = "—", slope = slope, intercept = intercept, gErrorNm = p.gErrorNm,
            peakR = p.peakR, peakG = p.peakG, peakB = p.peakB, roiStart = p.roiStart, roiEnd = p.roiEnd,
            sensorWidth = p.sensorWidth, sensorHeight = p.sensorHeight,
            wlR = p.wlR ?: 622.5, wlG = p.wlG ?: 522.5, wlB = p.wlB ?: 462.5,
            previewUri = null, redProfile = emptyList(), greenProfile = emptyList(), blueProfile = emptyList()
        ))
        viewModelScope.launch(Dispatchers.IO) { if (_uiState.value.spdCaptures.isNotEmpty()) analyzeSpd(); analyzeSample() }
    }

    fun loadSpdPreset(p: PresetEntity) {
        val restored = p.payloadJson?.let { SpectralResponseCalibrator.fromJson(it) }
        if (restored != null) { spdResult = restored; applySpdResult(restored.copy(spdSource = p.spdSource ?: restored.spdSource)) }
        else { _uiState.value = _uiState.value.copy(reportData = _uiState.value.reportData.copy(
            spdStatus = "PRESET (${p.name})", spdCoeffR = p.coeffR?.let { String.format(Locale.US, "%.4f", it) } ?: "—",
            spdCoeffB = p.coeffB?.let { String.format(Locale.US, "%.4f", it) } ?: "—", spdResponse = "Loaded from legacy preset",
            spdFrames = p.frames ?: 0, spdExposure = p.exposureSeconds?.let { String.format(Locale.US, "%.4f s", it) } ?: "—",
            spdSource = p.spdSource ?: "true_spd.csv (bundled)"
        )) }
    }

    fun updatePreset(p: PresetEntity) { viewModelScope.launch(Dispatchers.IO) { presetDao.update(p.copy(updatedAt = System.currentTimeMillis())) } }
    fun deletePreset(id: Long) { viewModelScope.launch(Dispatchers.IO) { presetDao.deleteById(id) } }

    private suspend fun analyzePositioning() {
        runCatching {
            val uris = _uiState.value.positioningCaptures; if (uris.isEmpty()) return
            val ctx = getApplication<Application>()
            val profiles = uris.mapNotNull { FrameProfileExtractor.extract(ctx, it) }
            val s = _settings.value
            val wlR = s.wavelengthR.toDoubleOrNull() ?: 622.5; val wlG = s.wavelengthG.toDoubleOrNull() ?: 522.5; val wlB = s.wavelengthB.toDoubleOrNull() ?: 462.5
            val res = WavelengthCalibrator.calibrate(profiles, wlR, wlG, wlB) ?: return
            _uiState.value = _uiState.value.copy(reportData = _uiState.value.reportData.copy(
                wavelengthQuality = res.quality, wavelengthGError = String.format(Locale.US, "%+.3f nm", res.gErrorNm),
                mappingFormula = String.format(Locale.US, "Pixel(p) = %.6f * Wavelength(nm) + %.4f", res.slope, res.intercept),
                exposureTime = if (res.exposureSeconds > 0) String.format(Locale.US, "%.4f s", res.exposureSeconds) else "—",
                xRoi = "[${res.roiStart}, ${res.roiEnd}] (Width: ${res.roiEnd - res.roiStart + 1} px)",
                trainingPoints = "B(${wlB}nm), R(${wlR}nm)", validationPoint = "G(${wlG}nm)",
                gResidual = String.format(Locale.US, "%+.2f pixels", res.gResidualPx),
                slope = res.slope, intercept = res.intercept, gErrorNm = res.gErrorNm,
                peakR = res.peakR, peakG = res.peakG, peakB = res.peakB, roiStart = res.roiStart, roiEnd = res.roiEnd,
                sensorWidth = res.sensorWidth, sensorHeight = res.sensorHeight,
                redProfile = res.red, greenProfile = res.green, blueProfile = res.blue, previewUri = uris.first().toString(),
                wlR = wlR, wlG = wlG, wlB = wlB
            ))
        }.onFailure { e -> _uiState.value = _uiState.value.copy(reportData = _uiState.value.reportData.copy(wavelengthQuality = "FAILED", mappingFormula = "Analysis error: ${e.javaClass.simpleName}")) }
    }

    private suspend fun analyzeSpd() {
        runCatching {
            val uris = _uiState.value.spdCaptures; if (uris.isEmpty()) { spdResult = null; resetPage2(); return }
            val ctx = getApplication<Application>()
            val lamp = uris.mapNotNull { FrameProfileExtractor.extract(ctx, it) }
            val pos = _uiState.value.positioningCaptures.mapNotNull { FrameProfileExtractor.extract(ctx, it) }
            val slope = _uiState.value.reportData.slope; val intercept = _uiState.value.reportData.intercept
            if (lamp.isEmpty() || pos.isEmpty() || slope == null || intercept == null) { setSpdStatus("Requires Step 1 calibration first"); return }
            val spd = SpdLoader.load(ctx); if (spd == null) { setSpdStatus("SPD source missing"); return }
            val res = SpectralResponseCalibrator.calibrate(lamp, pos, spd.second, slope, intercept, spd.first)
            if (res == null) { setSpdStatus("SPD calibration failed (mask too small?)"); return }
            spdResult = res; applySpdResult(res)
        }.onFailure { e -> setSpdStatus("Analysis error: ${e.javaClass.simpleName}") }
    }

    private suspend fun analyzeSample() {
        runCatching {
            val st = _uiState.value; if (st.blankCaptures.isEmpty() || st.sampleCaptures.isEmpty()) return
            val slope = st.reportData.slope ?: return; val intercept = st.reportData.intercept ?: return
            val band = parseBand(st.reportData.sampleBand) ?: return
            val ctx = getApplication<Application>()
            val blanks = st.blankCaptures.mapNotNull { FrameProfileExtractor.extract(ctx, it) }
            val samples = st.sampleCaptures.mapNotNull { FrameProfileExtractor.extract(ctx, it) }
            val res = SampleAnalyzer.analyze(blanks, samples, band.first, band.second, slope, intercept) ?: return
            _uiState.value = _uiState.value.copy(reportData = _uiState.value.reportData.copy(
                blankMeanArea = String.format(Locale.US, "%.1f", res.blankMeanArea), sampleMeanArea = String.format(Locale.US, "%.1f", res.sampleMeanArea),
                sampleIntegratedArea = String.format(Locale.US, "%.1f", res.correctedArea), sampleSD = String.format(Locale.US, "%.1f", res.sampleSD),
                replicates = res.replicates, integrationWindow = "[${res.windowStartPx}, ${res.windowEndPx}] px",
                sampleSpectrum = res.correctedSpectrum, blankSpectrum = res.blankSpectrum, correctedAreaValue = res.correctedArea
            ))
        }.onFailure { e -> _uiState.value = _uiState.value.copy(reportData = _uiState.value.reportData.copy(sampleIntegratedArea = "Analysis error: ${e.javaClass.simpleName}")) }
    }

    private fun setSpdStatus(s: String) { _uiState.value = _uiState.value.copy(reportData = _uiState.value.reportData.copy(spdStatus = s)) }
    private fun resetPage2() { _uiState.value = _uiState.value.copy(reportData = _uiState.value.reportData.copy(spdStatus = "Pending", spdCoeffR = "—", spdCoeffB = "—", spdResponse = "—", spdFrames = 0, spdExposure = "—", spdEffectiveBand = "—", spdMasked = "—", spdHandoffs = "—", spdWeightsRef = "—", spdPeaks = "—", spdRobustness = "—", spdFusedPeak = "—", spdLambda = emptyList(), spdRespR = emptyList(), spdRespG = emptyList(), spdRespB = emptyList(), spdCorrR = emptyList(), spdCorrG = emptyList(), spdCorrB = emptyList(), spdWR = emptyList(), spdWG = emptyList(), spdWB = emptyList(), spdFused = emptyList())) }
    private fun applySpdResult(res: SpdCalibrationResult) {
        val k = maxOf(1, res.wavelengths.size / 180); fun sample(a: FloatArray) = a.filterIndexed { i, _ -> i % k == 0 }
        fun w(t: Triple<Float, Float, Float>) = String.format(Locale.US, "(%.3f, %.3f, %.3f)", t.first, t.second, t.third)
        _uiState.value = _uiState.value.copy(reportData = _uiState.value.reportData.copy(
            spdStatus = "Calibrated (${res.frames} lamp frame(s))", spdSource = res.spdSource,
            spdCoeffR = String.format(Locale.US, "%.0f a.u./SPD @ %.1f nm", res.peakRespR.first, res.peakRespR.second),
            spdCoeffB = String.format(Locale.US, "%.0f a.u./SPD @ %.1f nm", res.peakRespB.first, res.peakRespB.second),
            spdResponse = "Fused continuous curve F(λ) available", spdFrames = res.frames,
            spdExposure = if (res.exposureSeconds > 0) String.format(Locale.US, "%.4f s", res.exposureSeconds) else "—",
            spdEffectiveBand = String.format(Locale.US, "%.1f–%.1f nm", res.effectiveBandNm.first, res.effectiveBandNm.second),
            spdMasked = "${res.maskedCount}/${res.totalCount}",
            spdHandoffs = buildString { res.handoffBG?.let { append(String.format(Locale.US, "B→G: %.1f nm   ", it)) }; res.handoffGR?.let { append(String.format(Locale.US, "G→R: %.1f nm", it)) }; if (isEmpty()) append("—") },
            spdWeightsRef = "@450 ${w(res.weightsAt450)}  @520 ${w(res.weightsAt520)}  @600 ${w(res.weightsAt600)}",
            spdPeaks = String.format(Locale.US, "R %.0f@%.1f  G %.0f@%.1f  B %.0f@%.1f", res.peakRespR.first, res.peakRespR.second, res.peakRespG.first, res.peakRespG.second, res.peakRespB.first, res.peakRespB.second),
            spdRobustness = String.format(Locale.US, "R %.2f%%(%d)  G %.2f%%(%d)  B %.2f%%(%d)", res.robustR.first, res.robustR.second, res.robustG.first, res.robustG.second, res.robustB.first, res.robustB.second),
            spdFusedPeak = String.format(Locale.US, "%.4f a.u./s @ %.1f nm", res.fusedPeak.first, res.fusedPeak.second),
            spdLambda = sample(res.wavelengths), spdRespR = sample(res.respR), spdRespG = sample(res.respG), spdRespB = sample(res.respB),
            spdCorrR = sample(res.corrR), spdCorrG = sample(res.corrG), spdCorrB = sample(res.corrB),
            spdWR = sample(res.wR), spdWG = sample(res.wG), spdWB = sample(res.wB), spdFused = sample(res.fused)
        ))
    }

    private suspend fun persistAll() {
        runCatching {
            val p = _uiState.value.currentProject ?: return; val st = _uiState.value
            dao.deleteCapturesByProject(p.id); dao.deleteStandardGroupsByProject(p.id)
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

    private fun parseBand(band: String): Pair<Double, Double>? { val m = Regex("(\\d+)–(\\d+)").find(band) ?: return null; val (a, b) = m.destructured; return a.toDoubleOrNull()?.let { bb -> b.toDoubleOrNull()?.let { bb to it } } }
}