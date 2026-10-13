package org.fungalsentinel.app.ui.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.fungalsentinel.app.R
import org.fungalsentinel.app.ui.capture.CaptureUploadScreen
import org.fungalsentinel.app.ui.components.*
import org.fungalsentinel.app.viewmodel.ProjectViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step1WavelengthScreen(
    onNext: () -> Unit,
    onBack: () -> Unit,
    onOpenReport: () -> Unit,
    projectViewModel: ProjectViewModel,
    isHalfScreen: Boolean = false
) {
    val uiState by projectViewModel.uiState.collectAsState()
    val captures = uiState.positioningCaptures
    val report = uiState.reportData
    val wlPresets by projectViewModel.wlPresets.collectAsState()

    var showCapture by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showLoadDialog by remember { mutableStateOf(false) }

    val status = report.step1Status
    val token = status.substringBefore('|')
    val message = status.substringAfter('|', status)
    val statusColor = when (token) {
        "FAILED", "BLOCKED" -> MaterialTheme.colorScheme.error
        "COMPUTED", "PRESET" -> Color(0xFF5CFF5C)
        "PROCESSING" -> Color(0xFF5CA8FF)
        else -> Color(0xFFF5F5C0)
    }

    val tutorial = TutorialHost(
        stepId = "step1_wavelength",
        titleRes = R.string.tutorial_step1_title,
        bodyRes = R.string.tutorial_step1_body
    )

    HalfScreenLayout(forceHalfScreen = isHalfScreen) { innerModifier ->
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
                    .background(Color.Black)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                NeonHeader("STEP1:", stringResource(R.string.step1_short), onBack, tutorial)

                NeonOutlineBox(MaterialTheme.colorScheme.secondary) {
                    Text(
                        text = stringResource(R.string.step1_description),
                        color = Color(0xFFF5F5C0),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                NeonOutlineBox(Color.White) {
                    Text(
                        text = message,
                        color = statusColor,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    NeonStepIndicator(current = 1)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            NeonYellowButton(
                                text = stringResource(R.string.act_save_preset),
                                onClick = { showSaveDialog = true },
                                enabled = report.slope != null && report.wavelengthQuality != "PRESET",
                                modifier = Modifier.weight(1f)
                            )
                            NeonYellowButton(
                                text = stringResource(R.string.act_load_preset),
                                onClick = { showLoadDialog = true },
                                enabled = wlPresets.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            NeonYellowButton(stringResource(R.string.act_view_report), onOpenReport, modifier = Modifier.weight(1f))
                            NeonYellowButton(stringResource(R.string.step1_start_capture), { showCapture = true }, modifier = Modifier.weight(1f))
                            NeonYellowButton(stringResource(R.string.next_step), onNext, enabled = report.slope != null, modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
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