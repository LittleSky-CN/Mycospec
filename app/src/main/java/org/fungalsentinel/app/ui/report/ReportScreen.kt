package org.fungalsentinel.app.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.fungalsentinel.app.R
import org.fungalsentinel.app.ui.components.HalfScreenLayout
import org.fungalsentinel.app.ui.components.HelpTopBar
import org.fungalsentinel.app.ui.components.TutorialHost
import org.fungalsentinel.app.ui.theme.AppColors
import org.fungalsentinel.app.ui.theme.statusTextColor
import org.fungalsentinel.app.viewmodel.ProjectViewModel
import org.fungalsentinel.app.viewmodel.STEP_SOURCE_NOT_RUN
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    projectViewModel: ProjectViewModel,
    onBack: () -> Unit,
    isHalfScreen: Boolean = false,
    coverRatio: Float = 0.5f
) {
    val uiState by projectViewModel.uiState.collectAsState()
    val r = uiState.reportData
    // Data-layer sentinel values / nulls are replaced with readable text at render time, so English stored in legacy project JSON is also covered
    val sourceNotRun = stringResource(R.string.report_source_not_run)
    val methodNote = stringResource(R.string.report_spd_method_note)
    fun source(value: String) = if (value == STEP_SOURCE_NOT_RUN) sourceNotRun else value

    val tutorial = TutorialHost(
        stepId = "report",
        titleRes = R.string.tutorial_report_title,
        bodyRes = R.string.tutorial_report_body
    )

    HalfScreenLayout(forceHalfScreen = isHalfScreen, coverRatio = coverRatio) { screenModifier ->
        Scaffold(
            modifier = screenModifier.fillMaxSize(),
            topBar = {
                HelpTopBar(
                    title = stringResource(R.string.report_title),
                    tutorial = tutorial,
                    onBack = onBack
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
            Text(
                text = stringResource(
                    R.string.report_project,
                    uiState.currentProject?.name ?: stringResource(R.string.value_not_set)
                ),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )

            // ═══════ PAGE 1 — Wavelength calibration (parity: FSSA v1.4.5) ═══════
            PageCard(page = 1, title = stringResource(R.string.report_page1_title)) {
                StatusLine(r.step1Status)
                if (r.previewUri != null && r.roiStart != null && r.roiEnd != null && r.sensorWidth != null) {
                    FigureLabel(stringResource(R.string.report_fig1))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(AppColors.Surface)
                    ) {
                        AsyncImage(
                            model = r.previewUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            colorFilter = ColorFilter.colorMatrix(
                                ColorMatrix(
                                    floatArrayOf(
                                        2.4f, 0f, 0f, 0f, 24f,
                                        0f, 2.4f, 0f, 0f, 24f,
                                        0f, 0f, 2.4f, 0f, 24f,
                                        0f, 0f, 0f, 1f, 0f
                                    )
                                )
                            ),
                            modifier = Modifier.fillMaxSize()
                        )
                        Canvas(Modifier.fillMaxSize()) {
                            val sw = r.sensorWidth.coerceAtLeast(1).toFloat()
                            val x0 = r.roiStart / sw * size.width
                            val x1 = r.roiEnd / sw * size.width
                            // [Convergence] ROI overlay switched to translucent accent green, replacing bright green 0x4400E676
                            drawRect(AppColors.Accent.copy(alpha = 0.27f), Offset(x0, 0f), Size(x1 - x0, size.height))
                        }
                    }
                    Text(
                        stringResource(R.string.report_auto_roi, r.wlR, r.wlG, r.wlB),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (r.redProfile.isNotEmpty()) {
                    FigureLabel(stringResource(R.string.report_fig2))
                    ProfileChart(
                        r.redProfile, r.greenProfile, r.blueProfile,
                        windows = r.peakWindows,
                        centroids = listOfNotNull(r.peakR, r.peakG, r.peakB)
                    )
                    Spacer(Modifier.height(8.dp))
                }
                // [v2.2.2] Adapt to the filter mode: pB is nullable
                if (r.slope != null && r.peakG != null && r.peakR != null) {
                    val isFilterMode = r.peakB == null
                    val title = if (isFilterMode)
                        stringResource(R.string.report_fig3_filter)
                    else
                        stringResource(R.string.report_fig3)
                    FigureLabel(title)
                    FitChart(
                        slope = r.slope, intercept = r.intercept ?: 0.0,
                        wlB = r.wlB, wlG = r.wlG, wlR = r.wlR,
                        pxB = r.peakB?.toFloat(), pxG = r.peakG.toFloat(), pxR = r.peakR.toFloat(),
                        errorNm = r.gErrorNm ?: 0.0,
                        isGreenRedFit = isFilterMode
                    )
                    Spacer(Modifier.height(8.dp))
                }
                SectionTitle(stringResource(R.string.report_sec_wl_params))
                ReportLine(stringResource(R.string.report_lbl_source), source(r.step1Source))
                r.step1PresetName?.let { ReportLine(stringResource(R.string.report_lbl_preset_name), it) }
                r.step1PresetFile?.let { ReportLine(stringResource(R.string.report_lbl_preset_file_id), it) }
                r.step1ImportedAt?.let { ReportLine(stringResource(R.string.report_lbl_imported_at), it) }
                r.step1Params.forEach { (k, v) -> ReportLine(k, v) }
                ReportLine(stringResource(R.string.report_lbl_mapping_equation), r.mappingFormula)
                ReportLine(
                    stringResource(R.string.report_lbl_calibration_metadata),
                    stringResource(R.string.report_val_calibration_metadata, r.exposureTime, r.xRoi)
                )
                ReportLine(
                    stringResource(R.string.report_lbl_validation_results),
                    stringResource(R.string.report_val_validation_results, r.trainingPoints, r.validationPoint)
                )
                ReportLine(
                    "",
                    stringResource(R.string.report_val_g_residual, r.gResidual, r.wavelengthGError)
                )
                ReportLine(
                    stringResource(R.string.report_lbl_peak_estimator),
                    stringResource(R.string.report_val_peak_estimator)
                )
                if (r.peakDiagnostics != "—") {
                    Text(
                        r.peakDiagnostics,
                        color = AppColors.Error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                QualityBadge(r.wavelengthQuality)
            }

            // ══════ PAGE 2 — SPD response & response-proportional fusion ═══════
            PageCard(page = 2, title = stringResource(R.string.report_page2_title)) {
                StatusLine(r.step2Status)
                if (r.spdLambda.isNotEmpty()) {
                    if (r.spdPosR.isNotEmpty()) {
                        FigureLabel(stringResource(R.string.report_fig4a))
                        MultiLineChartX(r.spdLambda, listOf(
                            r.spdPosR to AppColors.Chart.R,
                            r.spdPosG to AppColors.Chart.G,
                            r.spdPosB to AppColors.Chart.B
                        ), widths = listOf(1.5f, 1.5f, 1.5f))
                    }
                    FigureLabel(stringResource(R.string.report_fig4b))
                    MultiLineChartX(r.spdLambda, listOf(
                        r.spdCorrR to AppColors.Chart.R,
                        r.spdCorrG to AppColors.Chart.G,
                        r.spdCorrB to AppColors.Chart.B,
                        r.spdFused to AppColors.TextPrimary
                    ), widths = listOf(1.2f, 1.2f, 1.2f, 3f))
                    FigureLabel(stringResource(R.string.report_fig4c))
                    MultiLineChartX(r.spdLambda, listOf(
                        r.spdRespR to AppColors.Chart.R,
                        r.spdRespG to AppColors.Chart.G,
                        r.spdRespB to AppColors.Chart.B
                    ))
                    FigureLabel(stringResource(R.string.report_fig4d))
                    MultiLineChartX(r.spdLambda, listOf(
                        r.spdWB to AppColors.Chart.B,
                        r.spdWG to AppColors.Chart.G,
                        r.spdWR to AppColors.Chart.R
                    ), widths = listOf(2.5f, 2.5f, 2.5f),
                        vLines = listOf(r.spdCrossingBG, r.spdCrossingGR))
                    Spacer(Modifier.height(8.dp))
                }
                SectionTitle(stringResource(R.string.report_sec_spd_params))
                ReportLine(
                    stringResource(R.string.report_lbl_status_source),
                    stringResource(R.string.report_val_status_source, r.spdStatus, source(r.spdSource))
                )
                r.spdPresetName?.let { ReportLine(stringResource(R.string.report_lbl_preset_name), it) }
                r.spdPresetFile?.let { ReportLine(stringResource(R.string.report_lbl_preset_file_id), it) }
                r.spdImportedAt?.let { ReportLine(stringResource(R.string.report_lbl_imported_at), it) }
                r.spdParams.forEach { (k, v) -> ReportLine(k, v) }
                ReportLine(
                    stringResource(R.string.report_lbl_frames_exposure),
                    stringResource(R.string.report_val_frames_exposure, r.spdFrames, r.spdExposure)
                )
                ReportLine(
                    stringResource(R.string.report_lbl_effective_band),
                    stringResource(R.string.report_val_effective_band, r.spdEffectiveBand, r.spdMasked)
                )
                ReportLine(stringResource(R.string.report_lbl_responsivity_crossings), r.spdHandoffs)
                ReportLine(stringResource(R.string.report_lbl_cross_channel_agreement), r.spdAgreement)
                ReportLine(stringResource(R.string.report_lbl_fused_curve_peak), r.spdFusedPeak)
                ReportLine(stringResource(R.string.report_lbl_response_peaks), r.spdPeaks)
                ReportLine(stringResource(R.string.report_lbl_raw_positioning_peaks), r.spdRawPeaks)
                ReportLine(stringResource(R.string.report_lbl_corrected_positioning_peaks), r.spdCorrPeaks)
                ReportLine(stringResource(R.string.report_lbl_method), r.spdMethodNote.ifBlank { methodNote })
            }

            // ═══════ PAGE 3 — Sample analysis ═══════
            PageCard(page = 3, title = stringResource(R.string.report_page3_title)) {
                StatusLine(r.step3Status)
                if (r.sampleSpectrum.isNotEmpty()) {
                    FigureLabel(stringResource(R.string.report_fig7))
                    ProfileChart(r.blankSpectrum, r.sampleSpectrum, emptyList())
                    Text(
                        stringResource(R.string.report_fig7_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                }
                SectionTitle(stringResource(R.string.report_sec_sample_params))
                ReportLine(
                    stringResource(R.string.lbl_target_fluorophore),
                    stringResource(R.string.report_val_fluorophore, r.sampleFluorophore, r.sampleBand)
                )
                ReportLine(stringResource(R.string.report_lbl_analysis_channel), r.sampleIntegratedArea.substringAfter("ch=").substringBefore(","))
                ReportLine(stringResource(R.string.report_lbl_spd_correction), r.sampleIntegratedArea.substringAfter("resp=").substringBefore(")"))
                ReportLine(stringResource(R.string.report_lbl_peak_wavelength), r.samplePeakNm)
                ReportLine(stringResource(R.string.report_lbl_peak_intensity), r.samplePeakIntensity)
                ReportLine(stringResource(R.string.report_lbl_fwhm), r.sampleFwhm)
                ReportLine(stringResource(R.string.report_lbl_centroid), r.sampleCentroid)
                ReportLine(stringResource(R.string.report_lbl_integration_window), r.integrationWindow)
                ReportLine(stringResource(R.string.report_lbl_blank_mean_area), r.blankMeanArea)
                ReportLine(stringResource(R.string.report_lbl_sample_mean_area), r.sampleMeanArea)
                ReportLine(stringResource(R.string.report_lbl_corrected_area), r.sampleIntegratedArea)
                ReportLine(
                    stringResource(R.string.report_lbl_sd_replicates),
                    stringResource(R.string.report_val_sd_replicates, r.sampleSD, r.replicates)
                )
            }

            // ═══════ PAGE 4 — Concentration regression ═══════
            PageCard(page = 4, title = stringResource(R.string.report_page4_title)) {
                StatusLine(r.step4Status)
                if (r.curvePoints.isNotEmpty() && r.curveSlope != null) {
                    FigureLabel(stringResource(R.string.report_fig8))
                    CurveChart(r.curvePoints, r.curveSlope, r.curveIntercept ?: 0.0)
                    Spacer(Modifier.height(8.dp))
                }
                SectionTitle(stringResource(R.string.report_sec_conc_params))
                ReportLine(stringResource(R.string.report_lbl_source), source(r.step4Source))
                r.step4PresetName?.let { ReportLine(stringResource(R.string.report_lbl_preset_name), it) }
                r.step4PresetFile?.let { ReportLine(stringResource(R.string.report_lbl_preset_file_id), it) }
                r.step4ImportedAt?.let { ReportLine(stringResource(R.string.report_lbl_imported_at), it) }
                r.step4Params.forEach { (k, v) -> ReportLine(k, v) }
                r.standardRows.forEach { ReportLine(stringResource(R.string.report_lbl_standard), it) }
                ReportLine(stringResource(R.string.report_lbl_regression_formula), r.regressionFormula)
                ReportLine(stringResource(R.string.result_r2), r.regressionR2)
                ReportLine(stringResource(R.string.report_lbl_predicted_conc), r.predictedConcentration)
                // [Convergence] All warnings unified to semantic red, the orange branch is removed
                r.curveWarnings.forEach { warning ->
                    Text(
                        text = stringResource(R.string.report_warning, warning),
                        color = AppColors.Error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    }
                }
            }
        }
    }
}

// ─────────── shared report widgets ───────────

@Composable
private fun StatusLine(s: String) {
    val token = s.substringBefore('|')
    val message = s.substringAfter('|', s)
    // [Unified] Status colors converge: green = success / translucent green = processing / red = failed / gray = idle
    Text(message, style = MaterialTheme.typography.bodySmall, color = statusTextColor(token))
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun PageCard(page: Int, title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.report_page, page),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextSecondary
            )
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppColors.Accent
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun FigureLabel(t: String) =
    Text(t, style = MaterialTheme.typography.labelMedium, color = AppColors.TextSecondary)

@Composable
private fun SectionTitle(t: String) =
    Text(t, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)

@Composable
private fun ReportLine(label: String, value: String) {
    if (label.isEmpty()) {
        Text(value, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextPrimary)
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
        }
    }
}

@Composable
private fun QualityBadge(quality: String) {
    // [Convergence] PASS = green / WARNING = translucent green / FAILED = red / others = gray
    val color = when (quality) {
        "PASS" -> AppColors.Accent
        "WARNING" -> AppColors.AccentSoft
        "FAILED" -> AppColors.Error
        else -> AppColors.TextSecondary
    }
    Text(
        stringResource(R.string.report_calibration_quality, quality),
        color = color,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleSmall
    )
}

/** Per-channel profiles with optional valley-bounded peak windows and centroid markers. */
@Composable
private fun ProfileChart(
    red: List<Float>,
    green: List<Float>,
    blue: List<Float>,
    windows: List<Pair<Int, Int>> = emptyList(),
    centroids: List<Double> = emptyList()
) {
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val all = red + green + blue
        val max = all.maxOrNull()?.coerceAtLeast(1e-6f) ?: 1f
        val n = maxOf(red.size, green.size, blue.size, 1)
        // [Convergence] Channel window colors now come from the Chart sub-object
        val winColors = listOf(AppColors.Chart.R, AppColors.Chart.G, AppColors.Chart.B)
        windows.forEachIndexed { i, (lo, hi) ->
            val x0 = lo.toFloat() / n * size.width
            val x1 = hi.toFloat() / n * size.width
            drawRect(winColors[i % 3].copy(alpha = 0.15f), Offset(x0, 0f), Size(x1 - x0, size.height))
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f)
        centroids.forEach { c ->
            val px = c.toFloat() / n * size.width
            drawLine(AppColors.TextSecondary.copy(alpha = 0.7f), Offset(px, 0f), Offset(px, size.height), 1.5f, pathEffect = dash)
        }
        fun line(data: List<Float>, color: Color) {
            if (data.size < 2) return
            val path = Path()
            for (i in data.indices) {
                val x = i.toFloat() / (data.size - 1) * size.width
                val y = size.height - (data[i] / max) * size.height * 0.95f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 2f))
        }
        line(red, AppColors.Chart.R)
        line(green, AppColors.Chart.G)
        line(blue, AppColors.Chart.B)
    }
}

/** Multi-series chart with per-series stroke widths and vertical marker lines. */
@Composable
private fun MultiLineChartX(
    x: List<Float>,
    series: List<Pair<List<Float>, Color>>,
    height: Dp = 150.dp,
    widths: List<Float> = emptyList(),
    vLines: List<Float> = emptyList()
) {
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val ys = series.flatMap { it.first }
        if (x.size < 2 || ys.isEmpty()) return@Canvas
        val yMax = ys.maxOrNull()?.coerceAtLeast(1e-6f) ?: 1f
        if (vLines.isNotEmpty()) {
            val xMin = x.minOrNull() ?: 0f
            val xMax = x.maxOrNull() ?: 1f
            val span = (xMax - xMin).coerceAtLeast(1e-6f)
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            for (v in vLines) {
                val px = (v - xMin) / span * size.width
                drawLine(AppColors.TextSecondary.copy(alpha = 0.5f), Offset(px, 0f), Offset(px, size.height), 1.5f, pathEffect = dash)
            }
        }
        series.forEachIndexed { si, (data, color) ->
            if (data.size < 2) return@forEachIndexed
            val w = widths.getOrElse(si) { 2f }
            val path = Path()
            val nn = minOf(data.size, x.size)
            for (i in 0 until nn) {
                val px = i.toFloat() / (nn - 1) * size.width
                val py = size.height - (data[i] / yMax) * size.height * 0.95f
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, color, style = Stroke(width = w))
        }
    }
}

/**
 * Two-point fit chart.
 * [v2.2.2] pxB is nullable; hides the B point and adjusts the title text when isGreenRedFit is true.
 */
@Composable
private fun FitChart(
    slope: Double, intercept: Double,
    wlB: Double, wlG: Double, wlR: Double,
    pxB: Float?, pxG: Float, pxR: Float, errorNm: Double,
    isGreenRedFit: Boolean = false
) {
    Column {
        Canvas(Modifier.fillMaxWidth().height(170.dp)) {
            val xMin = 440f
            val xMax = 660f
            val ys = listOfNotNull(
                pxB, pxG, pxR,
                (slope * xMin + intercept).toFloat(),
                (slope * xMax + intercept).toFloat()
            )
            val yMin = (ys.minOrNull() ?: 0f) - 80f
            val yMax = (ys.maxOrNull() ?: 1f) + 80f
            val ySpan = (yMax - yMin).coerceAtLeast(1f)
            fun px(wl: Float) = (wl - xMin) / (xMax - xMin) * size.width
            fun py(v: Float) = (v - yMin) / ySpan * size.height
            // [Convergence] Fit line switched to the neutral gray-blue Chart.Fit, replacing olive green 0xFF558B2F
            drawLine(
                AppColors.Chart.Fit,
                Offset(px(xMin), py((slope * xMin + intercept).toFloat())),
                Offset(px(xMax), py((slope * xMax + intercept).toFloat())),
                2f
            )
            // [v2.2.2] The B point is not drawn in filter mode
            if (!isGreenRedFit && pxB != null) {
                drawCircle(AppColors.Chart.B, 6f, Offset(px(wlB.toFloat()), py(pxB)))
            }
            drawCircle(AppColors.Chart.R, 6f, Offset(px(wlR.toFloat()), py(pxR)))
            drawCircle(
                AppColors.Chart.G, 6f,
                Offset(px(wlG.toFloat()), py(pxG)),
                style = Stroke(2f)
            )
        }
        // All numbers are formatted with Locale.US to keep the decimal separator consistent across languages
        val fitText = if (isGreenRedFit) {
            String.format(
                Locale.US,
                stringResource(R.string.report_fit_filter),
                slope, intercept
            )
        } else {
            String.format(
                Locale.US,
                stringResource(R.string.report_fit),
                slope, intercept, errorNm
            )
        }
        Text(
            fitText,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextSecondary
        )
    }
}

@Composable
private fun CurveChart(
    points: List<Pair<Double, Double>>,  // (Concentration or log10(Concentration), Area)
    slope: Double, intercept: Double
) {
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        if (points.isEmpty()) return@Canvas
        val xs = points.map { it.first.toFloat() }
        val ys = points.map { it.second.toFloat() }
        val xMin = (xs.minOrNull() ?: 0f) * 0.9f
        val xMax = (xs.maxOrNull() ?: 1f) * 1.1f
        val yMin = (ys.minOrNull() ?: 0f) * 0.9f
        val yMax = (ys.maxOrNull() ?: 1f) * 1.1f
        val xSpan = (xMax - xMin).coerceAtLeast(1e-6f)
        val ySpan = (yMax - yMin).coerceAtLeast(1e-6f)
        fun px(v: Float) = (v - xMin) / xSpan * size.width
        fun py(v: Float) = size.height - (v - yMin) / ySpan * size.height
        // [Convergence] The regression line uses the neutral Chart.Fit
        drawLine(
            AppColors.Chart.Fit,
            Offset(px(xMin), py((slope * xMin + intercept).toFloat())),
            Offset(px(xMax), py((slope * xMax + intercept).toFloat())),
            2f
        )
        // [Convergence] Standard points use the single accent color Accent
        points.forEach { p ->
            drawCircle(
                AppColors.Accent, 6f,
                Offset(px(p.first.toFloat()), py(p.second.toFloat()))
            )
        }
    }
}