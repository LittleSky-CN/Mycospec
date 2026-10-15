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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.fungalsentinel.app.R
import org.fungalsentinel.app.ui.components.HelpTopBar
import org.fungalsentinel.app.ui.components.TutorialHost
import org.fungalsentinel.app.ui.theme.AppColors
import org.fungalsentinel.app.ui.theme.statusTextColor
import org.fungalsentinel.app.viewmodel.ProjectViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    projectViewModel: ProjectViewModel,
    onBack: () -> Unit
) {
    val uiState by projectViewModel.uiState.collectAsState()
    val r = uiState.reportData

    val tutorial = TutorialHost(
        stepId = "report",
        titleRes = R.string.tutorial_report_title,
        bodyRes = R.string.tutorial_report_body
    )

    Scaffold(
        topBar = {
            HelpTopBar(
                title = "MycoSpec Analysis Report",
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
                text = "Project: ${uiState.currentProject?.name ?: "—"}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )

            // ═══════ PAGE 1 — Wavelength calibration (v1.4.5 estimator) ═══════
            PageCard(page = 1, title = "Step 1: Wavelength Calibration") {
                StatusLine(r.step1Status)
                if (r.previewUri != null && r.roiStart != null && r.roiEnd != null && r.sensorWidth != null) {
                    FigureLabel("Figure 1: Positioning preview with Auto ROI")
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
                            // 【收敛】ROI 覆盖改为强调绿半透明，替代亮绿 0x4400E676
                            drawRect(AppColors.Accent.copy(alpha = 0.27f), Offset(x0, 0f), Size(x1 - x0, size.height))
                        }
                    }
                    Text(
                        "Auto X ROI  R(${r.wlR}nm) G(${r.wlG}nm) B(${r.wlB}nm)",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (r.redProfile.isNotEmpty()) {
                    FigureLabel("Figure 2: Automatic 1D Profiles and Dynamic Peak Windows")
                    ProfileChart(
                        r.redProfile, r.greenProfile, r.blueProfile,
                        windows = r.peakWindows,
                        centroids = listOfNotNull(r.peakR, r.peakG, r.peakB)
                    )
                    Spacer(Modifier.height(8.dp))
                }
                // 【v2.2.2】适配滤光片模式：pB 可空
                if (r.slope != null && r.peakG != null && r.peakR != null) {
                    val isFilterMode = r.peakB == null
                    val title = if (isFilterMode)
                        "Figure 3: Two-point Fit (G+R) [Filter Mode]"
                    else
                        "Figure 3: Two-point Fit (B+R) with Third-point Validation (G)"
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
                SectionTitle("Wavelength Calibration Parameters")
                ReportLine("[Wavelength Mapping Equation]", r.mappingFormula)
                ReportLine("[Calibration Metadata]", "Exposure: ${r.exposureTime}   X ROI: ${r.xRoi}")
                ReportLine("[Validation Results]", "Train: ${r.trainingPoints}   Validate: ${r.validationPoint}")
                ReportLine("", "G Residual: ${r.gResidual}   G Error: ${r.wavelengthGError}")
                ReportLine("[Peak Estimator]", "Valley-bounded Gaussian core (v1.4.5); legacy centroid = diagnostic only")
                if (r.peakDiagnostics != "—") {
                    Text(
                        r.peakDiagnostics,
                        color = AppColors.Error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                QualityBadge(r.wavelengthQuality)
            }

            // ══════ PAGE 2 — SPD response & logistic fusion (v1.4.4) ═══════
            PageCard(page = 2, title = "Step 2: Spectral Response & Fusion (v1.4.4)") {
                StatusLine(r.step2Status)
                if (r.spdLambda.isNotEmpty()) {
                    if (r.spdPosR.isNotEmpty()) {
                        FigureLabel("Figure 4a: Positioning LEDs — RAW measured (independent data)")
                        MultiLineChartX(r.spdLambda, listOf(
                            r.spdPosR to AppColors.Chart.R,
                            r.spdPosG to AppColors.Chart.G,
                            r.spdPosB to AppColors.Chart.B
                        ), widths = listOf(1.5f, 1.5f, 1.5f))
                    }
                    FigureLabel("Figure 4b: SPD-corrected (thin) + ONE pointwise-fused curve (thick)")
                    MultiLineChartX(r.spdLambda, listOf(
                        r.spdCorrR to AppColors.Chart.R,
                        r.spdCorrG to AppColors.Chart.G,
                        r.spdCorrB to AppColors.Chart.B,
                        r.spdFused to AppColors.TextPrimary
                    ), widths = listOf(1.2f, 1.2f, 1.2f, 3f))
                    FigureLabel("Figure 4c: Camera absolute response = Lamp_c / SPD_true (masked)")
                    MultiLineChartX(r.spdLambda, listOf(
                        r.spdRespR to AppColors.Chart.R,
                        r.spdRespG to AppColors.Chart.G,
                        r.spdRespB to AppColors.Chart.B
                    ))
                    FigureLabel("Figure 4d: Logistic channel weights (sum = 1) with handoffs")
                    MultiLineChartX(r.spdLambda, listOf(
                        r.spdWB to AppColors.Chart.B,
                        r.spdWG to AppColors.Chart.G,
                        r.spdWR to AppColors.Chart.R
                    ), widths = listOf(2.5f, 2.5f, 2.5f),
                        vLines = listOf(r.spdHandoffBG, r.spdHandoffGR))
                    Spacer(Modifier.height(8.dp))
                }
                SectionTitle("SPD Response & Fusion Parameters")
                ReportLine("Status / source", "${r.spdStatus} · ${r.spdSource}")
                ReportLine("Frames / Exposure", "${r.spdFrames} / ${r.spdExposure}")
                ReportLine("[Effective Band]", "${r.spdEffectiveBand} | masked px ${r.spdMasked} | ratio 0.1")
                ReportLine("[Handoff Wavelengths]", r.spdHandoffs)
                ReportLine("[Cross-channel Agreement]", r.spdAgreement)
                ReportLine("[Fused Curve Peak]", r.spdFusedPeak)
                ReportLine("[Response Peaks (absolute)]", r.spdPeaks)
                ReportLine("[RAW Positioning Peaks]", r.spdRawPeaks)
                ReportLine("[Corrected Positioning Peaks]", r.spdCorrPeaks)
                ReportLine("[Method]", r.spdMethodNote)
            }

            // ═══════ PAGE 3 — Sample analysis ═══════
            PageCard(page = 3, title = "Step 3: Sample Analysis") {
                StatusLine(r.step3Status)
                if (r.sampleSpectrum.isNotEmpty()) {
                    FigureLabel("Figure 7: Blank-subtracted & SPD-corrected spectrum vs Blank")
                    ProfileChart(r.blankSpectrum, r.sampleSpectrum, emptyList())
                    Text(
                        "Green = corrected sample, Red = blank",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                }
                SectionTitle("Sample Analysis Parameters")
                ReportLine("Target fluorophore", "${r.sampleFluorophore} (${r.sampleBand})")
                ReportLine("Analysis channel", r.sampleIntegratedArea.substringAfter("ch=").substringBefore(","))
                ReportLine("SPD correction", r.sampleIntegratedArea.substringAfter("resp=").substringBefore(")"))
                ReportLine("[Peak Wavelength]", r.samplePeakNm)
                ReportLine("[Peak Intensity]", r.samplePeakIntensity)
                ReportLine("[FWHM]", r.sampleFwhm)
                ReportLine("[Centroid]", r.sampleCentroid)
                ReportLine("Integration window", r.integrationWindow)
                ReportLine("Blank mean area", r.blankMeanArea)
                ReportLine("Sample mean area", r.sampleMeanArea)
                ReportLine("[Corrected Integrated Area]", r.sampleIntegratedArea)
                ReportLine("Sample SD / Replicates", "${r.sampleSD} / ${r.replicates}")
            }

            // ═══════ PAGE 4 — Concentration regression ═══════
            PageCard(page = 4, title = "Step 4: Concentration Regression") {
                StatusLine(r.step4Status)
                if (r.curvePoints.isNotEmpty() && r.curveSlope != null) {
                    FigureLabel("Figure 8: Standard curve with unknown sample")
                    CurveChart(r.curvePoints, r.curveSlope, r.curveIntercept ?: 0.0)
                    Spacer(Modifier.height(8.dp))
                }
                SectionTitle("Concentration Parameters")
                r.standardRows.forEach { ReportLine("Standard", it) }
                ReportLine("[Regression Formula]", r.regressionFormula)
                ReportLine("R²", r.regressionR2)
                ReportLine("[Predicted Concentration]", r.predictedConcentration)
                // 【收敛】所有警告统一语义红，删除橙色分支
                r.curveWarnings.forEach { warning ->
                    Text(
                        text = "⚠ $warning",
                        color = AppColors.Error,
                        style = MaterialTheme.typography.bodySmall
                    )
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
    // 【统一】状态色收敛：绿=成功 / 半透明绿=处理中 / 红=失败 / 灰=空闲
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
                "— Page $page —",
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
    // 【收敛】PASS=绿 / WARNING=半透明绿 / FAILED=红 / 其它=灰
    val color = when (quality) {
        "PASS" -> AppColors.Accent
        "WARNING" -> AppColors.AccentSoft
        "FAILED" -> AppColors.Error
        else -> AppColors.TextSecondary
    }
    Text(
        "Calibration Quality: $quality",
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
        // 【收敛】通道窗色走 Chart 子对象
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
 * 【v2.2.2】pxB 可空；isGreenRedFit 时隐藏 B 点、修改标题文案。
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
            // 【收敛】拟合线改为中性灰蓝 Chart.Fit，替代橄榄绿 0xFF558B2F
            drawLine(
                AppColors.Chart.Fit,
                Offset(px(xMin), py((slope * xMin + intercept).toFloat())),
                Offset(px(xMax), py((slope * xMax + intercept).toFloat())),
                2f
            )
            // 【v2.2.2】滤光片模式下不绘制 B 点
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
        val fitText = if (isGreenRedFit) {
            String.format(
                Locale.US,
                "Fit: p = %.4fλ %+.2f   Train (G, R)   [Filter Mode]",
                slope, intercept
            )
        } else {
            String.format(
                Locale.US,
                "Fit: p = %.4fλ %+.2f   Train (B, R)   Validate (G)   Error: %+.2f nm",
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
        // 【收敛】回归线用中性 Chart.Fit
        drawLine(
            AppColors.Chart.Fit,
            Offset(px(xMin), py((slope * xMin + intercept).toFloat())),
            Offset(px(xMax), py((slope * xMax + intercept).toFloat())),
            2f
        )
        // 【收敛】标准点用唯一强调色 Accent
        points.forEach { p ->
            drawCircle(
                AppColors.Accent, 6f,
                Offset(px(p.first.toFloat()), py(p.second.toFloat()))
            )
        }
    }
}