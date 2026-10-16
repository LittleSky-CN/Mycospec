package org.fungalsentinel.app.ui.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fungalsentinel.app.R
import org.fungalsentinel.app.ui.capture.CaptureUploadScreen
import org.fungalsentinel.app.ui.components.*
import org.fungalsentinel.app.ui.theme.AppColors
import org.fungalsentinel.app.ui.theme.statusTextColor
import org.fungalsentinel.app.viewmodel.ProjectViewModel

enum class Fluorophore(val displayName: String, val bandNm: String) {
    YPET("Ypet", "500-530 nm"), EGFP("EGFP", "500-540 nm"), MCHERRY("mCherry", "590-630nm"),
    CFP("CFP", "460-490 nm"), MTURQUOISE2("mTurquoise2", "460-490 nm"), FLUORESCEIN("Fluorescein", "500-540 nm")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step3SampleScreen(
    onNext: () -> Unit, onBack: () -> Unit, onOpenReport: () -> Unit,
    projectViewModel: ProjectViewModel, isHalfScreen: Boolean = false,
    blankMode: String = "SINGLE", coverRatio: Float = 0.5f
) {
    val uiState by projectViewModel.uiState.collectAsState()
    val blankCaptures = uiState.blankCaptures
    val sampleCaptures = uiState.sampleCaptures
    val report = uiState.reportData

    val reportFluor = Fluorophore.values().find { it.name == report.sampleFluorophore }
    var selectedFluorophore by remember(report.sampleFluorophore) { mutableStateOf(reportFluor) }
    var showBlankCapture by remember { mutableStateOf(false) }
    var showSampleCapture by remember { mutableStateOf(false) }
    var fluorophoreExpanded by remember { mutableStateOf(false) }
    val computed = report.step3Status.startsWith("COMPUTED") && selectedFluorophore != null

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val landscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val gap = if (landscape) 8.dp else 12.dp
    val pad = if (landscape) 10.dp else 16.dp

    val status = report.step3Status
    val token = status.substringBefore('|')
    val message = status.substringAfter('|', status)
    val statusColor = statusTextColor(token)

    val tutorial = TutorialHost("step3_sample", R.string.tutorial_step3_title, R.string.tutorial_step3_body)

    HalfScreenLayout(forceHalfScreen = isHalfScreen, coverRatio = coverRatio) { innerModifier ->
        if (showBlankCapture) {
            CaptureUploadScreen(
                title = stringResource(R.string.step3_capture_blank_title),
                subtitle = stringResource(R.string.step3_capture_blank_title),
                minCaptures = 1, maxCaptures = 5, capturedImages = blankCaptures,
                onImagesChanged = { projectViewModel.setBlankCaptures(it) },
                onConfirm = { showBlankCapture = false }, onBack = { showBlankCapture = false },
                onHelp = { tutorial.show() }, modifier = innerModifier
            )
        } else if (showSampleCapture) {
            CaptureUploadScreen(
                title = stringResource(R.string.step3_capture_sample_title),
                subtitle = stringResource(R.string.step3_capture_sample_title),
                minCaptures = 1, maxCaptures = 5, capturedImages = sampleCaptures,
                onImagesChanged = { projectViewModel.setSampleCaptures(it) },
                onConfirm = { showSampleCapture = false }, onBack = { showSampleCapture = false },
                onHelp = { tutorial.show() }, modifier = innerModifier
            )
        } else {
            Column(
                Modifier.fillMaxSize().background(AppColors.Bg)
                    .verticalScroll(rememberScrollState()).padding(pad),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                NeonHeader("STEP3:", stringResource(R.string.step3_short), onBack, tutorial)
                NeonOutlineBox(AppColors.Accent) {
                    Text(
                        if (blankMode == "FULL") stringResource(R.string.note_blank_full) else stringResource(R.string.note_blank_single),
                        color = AppColors.TextPrimary, style = MaterialTheme.typography.bodySmall
                    )
                }
                NeonOutlineBox(AppColors.Outline) {
                    Text(message, color = statusColor, style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                BoxWithConstraints {
                    val compact = maxWidth < 460.dp
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NeonStepIndicator(current = 3)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            NeonRowBlock {
                                if (compact) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(stringResource(R.string.lbl_target_fluorophore), color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        FluorophoreDropdown(selectedFluorophore, fluorophoreExpanded, { fluorophoreExpanded = it },
                                            { f -> selectedFluorophore = f; projectViewModel.setFluorophore(f.name) }, true)
                                    }
                                } else {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(R.string.lbl_target_fluorophore), color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        FluorophoreDropdown(selectedFluorophore, fluorophoreExpanded, { fluorophoreExpanded = it },
                                            { f -> selectedFluorophore = f; projectViewModel.setFluorophore(f.name) }, false)
                                    }
                                }
                            }
                            NeonRowBlock {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.lbl_blank_captures), color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("${blankCaptures.size}/5", color = AppColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Button({ showBlankCapture = true }, enabled = blankCaptures.size < 5, shape = RoundedCornerShape(18.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = AppColors.OnAccent),
                                        modifier = Modifier.height(36.dp)) { Text(stringResource(R.string.act_capture), fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                }
                            }
                            NeonRowBlock {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.lbl_sample_captures), color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("${sampleCaptures.size}/5", color = AppColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Button({ showSampleCapture = true }, enabled = sampleCaptures.size < 5, shape = RoundedCornerShape(18.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = AppColors.OnAccent),
                                        modifier = Modifier.height(36.dp)) { Text(stringResource(R.string.act_capture), fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                }
                            }
                            NeonButtonGroup(compact) { m ->
                                NeonYellowButton(stringResource(R.string.act_view_report), onOpenReport, modifier = m)
                                NeonYellowButton(stringResource(R.string.next_step), onNext, enabled = computed, modifier = m)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FluorophoreDropdown(
    selected: Fluorophore?, expanded: Boolean, onExpanded: (Boolean) -> Unit,
    onSelect: (Fluorophore) -> Unit, fillWidth: Boolean
) {
    Box {
        Box(
            Modifier.then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .background(AppColors.Surface, RoundedCornerShape(8.dp))
                .border(1.dp, if (selected == null) AppColors.Error else AppColors.Outline, RoundedCornerShape(8.dp))
                .clickable { onExpanded(true) }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selected?.let { "${it.displayName} (${it.bandNm})" } ?: "— 请选择荧光物质 —",
                    color = if (selected == null) AppColors.Error else AppColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                )
                Icon(Icons.Default.ArrowDropDown, null, tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded, { onExpanded(false) }, Modifier.background(AppColors.Surface)) {
            Fluorophore.values().forEach { f ->
                DropdownMenuItem(
                    text = { Text("${f.displayName} (${f.bandNm})", color = AppColors.TextPrimary,
                        fontWeight = if (f == selected) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp) },
                    onClick = { onSelect(f); onExpanded(false) }
                )
            }
        }
    }
}
@Composable
private fun NeonRowBlock(content: @Composable RowScope.() -> Unit) {
    Box {
        Box(
            Modifier.matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .background(AppColors.SurfaceRaised, RoundedCornerShape(8.dp))
        )
        Row(
            Modifier.fillMaxWidth()
                .background(AppColors.Surface, RoundedCornerShape(8.dp))
                .border(1.dp, AppColors.Outline, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}