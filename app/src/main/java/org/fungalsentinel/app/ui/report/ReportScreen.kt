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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.fungalsentinel.app.ui.components.HelpTopBar
import org.fungalsentinel.app.ui.components.TutorialHost
import org.fungalsentinel.app.viewmodel.ProjectViewModel
import java.util.Locale

/**
 * Four-page analysis report (aligned with full_report.pdf layout):
 *  Page 1 wavelength calibration, Page 2 SPD response & v1.4 fusion,
 *  Page 3 sample analysis, Page 4 concentration regression.
 */
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
        titleRes = org.fungalsentinel.app.R.string.tutorial_report_title,
        bodyRes = org.fungalsentinel.app.R.string.tutorial_report_body
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
                fontWeight = FontWeight.Bold
            )

            // ═══════ PAGE 1 — Wavelength calibration ═══════
            PageCard(page = 1, title = "Step 1: Wavelength Calibration") {
                if (r.previewUri != null && r.roiStart != null && r.roiEnd != null && r.sensorWidth != null) {
                    FigureLabel("Figure 1: Positioning preview with Auto ROI")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AsyncImage(
                            r.previewUri, null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Canvas(Modifier.fillMaxSize()) {
                            val sw = r.sensorWidth.coerceAtLeast(1).toFloat()
                            val x0 = r.roiStart / sw * size.width
                            val x1 = r.roiEnd / sw * size.width
                            drawRect(Color(0x4400E676), Offset(x0, 0f), Size(x1 - x0, size.height))
                        }
                    }
                    Text(
                        "Auto X ROI  R(${r.wlR}nm) G(${r.wlG}nm) B(${r.wlB}nm)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (r.redProfile.isNotEmpty()) {
                    FigureLabel("Figure 2: Automatic 1D Profiles and Dynamic Peak Windows")
                    ProfileChart(r.redProfile, r.greenProfile, r.blueProfile)
                    Spacer(Modifier.height(8.dp))
                }
                if (r.slope != null && r.peakB != null && r.peakG != null && r.peakR != null) {
                    FigureLabel("Figure 3: Two-point Fit (B+R) with Third-point Validation (G)")
                    FitChart(
                        r.slope, r.intercept ?: 0.0, r.wlB, r.wlG, r.wlR,
                        r.peakB.toFloat(), r.peakG.toFloat(), r.peakR.toFloat(),
                        r.gErrorNm ?: 0.0
                    )
                    Spacer(Modifier.height(8.dp))
                }
                SectionTitle("Wavelength Calibration Parameters")
                ReportLine("[Wavelength Mapping Equation]", r.mappingFormula)
                ReportLine("[Calibration Metadata]", "Exposure: ${r.exposureTime}   X ROI: ${r.xRoi}")
                ReportLine("[Validation Results]", "Train: ${r.trainingPoints}   Validate: ${r.validationPoint}")
                ReportLine("", "G Residual: ${r.gResidual}   G Error: ${r.wavelengthGError}")
                QualityBadge(r.wavelengthQuality)
            }

            // ═══════ PAGE 2 — SPD response & per-wavelength fusion ═══════
            PageCard(page = 2, title = "Step 2: Spectral Response & Fusion") {
                if (r.spdLambda.isNotEmpty()) {
                    FigureLabel("Figure 4: Camera absolute response resp_c(λ) = Lamp_c/SPD_true (masked)")
                    MultiLineChartX(r.spdLambda, listOf(
                        r.spdRespR to Color.Red,
                        r.spdRespG to Color(0xFF2E7D32),
                        r.spdRespB to Color.Blue
                    ))
                    FigureLabel("Figure 5: SPD-corrected channels C_c(λ) + fused F(λ)")
                    MultiLineChartX(r.spdLambda, listOf(
                        r.spdCorrR to Color.Red,
                        r.spdCorrG to Color(0xFF2E7D32),
                        r.spdCorrB to Color.Blue,
                        r.spdFused to Color.Black
                    ))
                    FigureLabel("Figure 6: Per-wavelength channel weights (w_R+w_G+w_B = 1)")
                    MultiLineChartX(r.spdLambda, listOf(
                        r.spdWR to Color.Red,
                        r.spdWG to Color(0xFF2E7D32),
                        r.spdWB to Color.Blue
                    ))
                    Spacer(Modifier.height(8.dp))
                }
                SectionTitle("SPD Response & Fusion Parameters")
                ReportLine("Status / source", "${r.spdStatus} · ${r.spdSource}")
                ReportLine("Frames / Exposure", "${r.spdFrames} / ${r.spdExposure}")
                ReportLine("[Effective Band]", "${r.spdEffectiveBand} | masked px ${r.spdMasked} | ratio 0.1")
                ReportLine("[Channel Handoff]", r.spdHandoffs)
                ReportLine("[Weights @450/520/600 nm]", r.spdWeightsRef)
                ReportLine("[Response Peaks (absolute)]", r.spdPeaks)
                ReportLine("[Robustness mean|C−F|/max|F|, w>0.5]", r.spdRobustness)
                ReportLine("[Fused Curve Peak]", r.spdFusedPeak)
                ReportLine("[Method]", r.spdMethodNote)
            }

            // ═══════ PAGE 3 — Sample analysis ═══════
            PageCard(page = 3, title = "Step 3: Sample Analysis") {
                if (r.sampleSpectrum.isNotEmpty()) {
                    FigureLabel("Figure 7: Blank-subtracted spectrum vs Blank")
                    ProfileChart(r.sampleSpectrum, r.blankSpectrum, emptyList())
                    Text(
                        "Green = corrected sample, Red = blank",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                }
                SectionTitle("Sample Analysis Parameters")
                ReportLine("Target fluorophore", "${r.sampleFluorophore} (${r.sampleBand})")
                ReportLine("Integration window", r.integrationWindow)
                ReportLine("Blank mean area", r.blankMeanArea)
                ReportLine("Sample mean area", r.sampleMeanArea)
                ReportLine("[Corrected Integrated Area]", r.sampleIntegratedArea)
                ReportLine("Sample SD / Replicates", "${r.sampleSD} / ${r.replicates}")
            }

            // ═══════ PAGE 4 — Concentration regression ═══════
            PageCard(page = 4, title = "Step 4: Concentration Regression") {
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
                r.curveWarnings.forEach { w ->
                    Text(
                        "⚠️ $w",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

// ─────────── shared report widgets ───────────

@Composable
private fun PageCard(page: Int, title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "— Page $page —",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun FigureLabel(t: String) = Text(t, style = MaterialTheme.typography.labelMedium)

@Composable
private fun SectionTitle(t: String) =
    Text(t, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

@Composable
private fun ReportLine(label: String, value: String) {
    if (label.isEmpty()) {
        Text(value, style = MaterialTheme.typography.bodyMedium)
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun QualityBadge(quality: String) {
    val color = when (quality) {
        "PASS" -> Color(0xFF2E7D32)
        "WARNING" -> Color(0xFFF9A825)
        "FAILED" -> Color(0xFFB3261E)
        else -> Color.Gray
    }
    Text(
        "Calibration Quality: $quality",
        color = color,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleSmall
    )
}

@Composable
private fun ProfileChart(red: List<Float>, green: List<Float>, blue: List<Float>) {
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val all = red + green + blue
        val max = all.maxOrNull()?.coerceAtLeast(1f) ?: 1f
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
        line(red, Color.Red)
        line(green, Color(0xFF2E7D32))
        line(blue, Color.Blue)
    }
}

@Composable
private fun MultiLineChartX(
    x: List<Float>,
    series: List<Pair<List<Float>, Color>>,
    height: Dp = 150.dp
) {
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val ys = series.flatMap { it.first }
        if (x.size < 2 || ys.isEmpty()) return@Canvas
        val yMax = ys.maxOrNull()?.coerceAtLeast(1e-6f) ?: 1f
        for ((data, color) in series) {
            if (data.size < 2) continue
            val path = Path()
            val n = minOf(data.size, x.size)
            for (i in 0 until n) {
                val px = i.toFloat() / (n - 1) * size.width
                val py = size.height - (data[i] / yMax) * size.height * 0.95f
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, color, style = Stroke(width = 2f))
        }
    }
}

@Composable
private fun FitChart(
    slope: Double, intercept: Double,
    wlB: Double, wlG: Double, wlR: Double,
    pxB: Float, pxG: Float, pxR: Float, errorNm: Double
) {
    Column {
        Canvas(Modifier.fillMaxWidth().height(170.dp)) {
            val xMin = 440f
            val xMax = 660f
            val ys = listOf(
                pxB, pxG, pxR,
                (slope * xMin + intercept).toFloat(),
                (slope * xMax + intercept).toFloat()
            )
            val yMin = (ys.minOrNull() ?: 0f) - 80f
            val yMax = (ys.maxOrNull() ?: 1f) + 80f
            val ySpan = (yMax - yMin).coerceAtLeast(1f)
            fun px(wl: Float) = (wl - xMin) / (xMax - xMin) * size.width
            fun py(v: Float) = size.height - (v - yMin) / ySpan * size.height
            drawLine(
                Color(0xFF558B2F),
                Offset(px(xMin), py((slope * xMin + intercept).toFloat())),
                Offset(px(xMax), py((slope * xMax + intercept).toFloat())),
                2f
            )
            drawCircle(Color.Blue, 6f, Offset(px(wlB.toFloat()), py(pxB)))
            drawCircle(Color.Red, 6f, Offset(px(wlR.toFloat()), py(pxR)))
            drawCircle(
                Color(0xFF2E7D32), 6f,
                Offset(px(wlG.toFloat()), py(pxG)),
                style = Stroke(2f)
            )
        }
        Text(
            String.format(
                Locale.US,
                "Fit: p = %.4fλ %+.2f   Train (B, R)   Validate (G)   Error: %+.2f nm",
                slope, intercept, errorNm
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CurveChart(
    points: List<Pair<Double, Double>>,
    slope: Double, intercept: Double
) {
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
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
        drawLine(
            Color(0xFF558B2F),
            Offset(px(xMin), py((slope * xMin + intercept).toFloat())),
            Offset(px(xMax), py((slope * xMax + intercept).toFloat())),
            2f
        )
        points.forEach { p ->
            drawCircle(
                Color(0xFF1565C0), 6f,
                Offset(px(p.first.toFloat()), py(p.second.toFloat()))
            )
        }
    }
}