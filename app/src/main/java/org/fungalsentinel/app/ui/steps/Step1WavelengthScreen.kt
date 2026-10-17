package org.fungalsentinel.app.ui.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.fungalsentinel.app.R
import org.fungalsentinel.app.ui.capture.CaptureUploadScreen
import org.fungalsentinel.app.ui.components.*
import org.fungalsentinel.app.ui.theme.AppColors
import org.fungalsentinel.app.ui.theme.statusTextColor
import org.fungalsentinel.app.viewmodel.ProjectViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step1WavelengthScreen(
    onNext: () -> Unit,
    onBack: () -> Unit,
    onOpenReport: () -> Unit,
    projectViewModel: ProjectViewModel,
    isHalfScreen: Boolean = false,
    coverRatio: Float = 0.5f
) {
    val uiState by projectViewModel.uiState.collectAsState()
    val captures = uiState.positioningCaptures
    val report = uiState.reportData
    val wlPresets by projectViewModel.wlPresets.collectAsState()

    var showCapture by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showLoadDialog by remember { mutableStateOf(false) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val landscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val gap = if (landscape) 8.dp else 14.dp
    val pad = if (landscape) 10.dp else 16.dp

    val status = report.step1Status
    val token = status.substringBefore('|')
    val message = status.substringAfter('|', status)
    // 【统一】状态色收敛：绿=成功 / 半透明绿=处理中 / 红=失败 / 灰=空闲
    val statusColor = statusTextColor(token)

    val tutorial = TutorialHost(
        stepId = "step1_wavelength",
        titleRes = R.string.tutorial_step1_title,
        bodyRes = R.string.tutorial_step1_body
    )

    HalfScreenLayout(forceHalfScreen = isHalfScreen, coverRatio = coverRatio) { innerModifier ->
        if (showCapture) {
            CaptureUploadScreen(
                title = stringResource(R.string.step1_capture_title),
                subtitle = stringResource(R.string.step1_capture_subtitle),
                minCaptures = 1,
                maxCaptures = 5,
                capturedImages = captures,
                onImagesChanged = { projectViewModel.setPositioningCaptures(it) },
                onConfirm = { showCapture = false },
                onBack = { showCapture = false },
                onHelp = { tutorial.show() },
                modifier = innerModifier
            )
        } else {
            Column(
                modifier = innerModifier
                    .fillMaxSize()
                    .background(AppColors.Bg)
                    .verticalScroll(rememberScrollState())
                    .padding(pad),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                NeonHeader("STEP1:", stringResource(R.string.step1_short), onBack, tutorial)

                // 说明框：唯一强调色描边
                NeonOutlineBox(AppColors.Accent) {
                    Text(
                        text = stringResource(R.string.step1_description),
                        color = AppColors.TextPrimary,
                        style = if (landscape) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
                    )
                }

                // 状态框：中性描边，文本色由状态令牌决定
                NeonOutlineBox(AppColors.Outline) {
                    Text(
                        text = message,
                        color = statusColor,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                BoxWithConstraints {
                    val vertical = maxWidth < 460.dp
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        NeonStepIndicator(current = 1)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            NeonButtonGroup(vertical) { m ->
                                NeonYellowButton(
                                    text = stringResource(R.string.act_save_preset),
                                    onClick = { showSaveDialog = true },
                                    enabled = report.slope != null && report.wavelengthQuality != "PRESET",
                                    modifier = m
                                )
                                NeonYellowButton(
                                    text = stringResource(R.string.act_load_preset),
                                    onClick = { showLoadDialog = true },
                                    enabled = wlPresets.isNotEmpty(),
                                    modifier = m
                                )
                            }
                            NeonButtonGroup(vertical) { m ->
                                NeonYellowButton(stringResource(R.string.act_view_report), onOpenReport, modifier = m)
                                NeonYellowButton(stringResource(R.string.step1_start_capture), { showCapture = true }, modifier = m)
                                NeonYellowButton(stringResource(R.string.next_step), onNext, enabled = report.slope != null, modifier = m)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.act_save_preset)) },
            text = {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.lbl_preset_name)) }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) { projectViewModel.saveWavelengthPreset(name); showSaveDialog = false }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("×") } }
        )
    }
    if (showLoadDialog) {
        AlertDialog(
            onDismissRequest = { showLoadDialog = false },
            title = { Text(stringResource(R.string.act_load_preset)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    wlPresets.forEach { p ->
                        Card(onClick = { projectViewModel.loadWavelengthPreset(p); showLoadDialog = false }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall)
                                Text("${p.deviceModel} · ${p.quality ?: "—"}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLoadDialog = false }) { Text("OK") } }
        )
    }
}