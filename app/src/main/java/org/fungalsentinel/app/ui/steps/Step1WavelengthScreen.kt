package org.fungalsentinel.app.ui.steps

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.fungalsentinel.app.R
import org.fungalsentinel.app.ui.capture.CaptureUploadScreen
import org.fungalsentinel.app.ui.components.HalfScreenLayout
import org.fungalsentinel.app.ui.components.HelpTopBar
import org.fungalsentinel.app.ui.components.StepIndicator
import org.fungalsentinel.app.ui.components.TutorialHost
import org.fungalsentinel.app.viewmodel.ProjectViewModel

/**
 * Step 1 — Wavelength calibration.
 * Captures live in the ViewModel (survive navigation & auto-save to Room).
 * Results are computed immediately on every batch change and shown in Report Page 1.
 */
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

    val tutorial = TutorialHost(
        stepId = "step1_wavelength",
        titleRes = R.string.tutorial_step1_title,
        bodyRes = R.string.tutorial_step1_body
    )

    HalfScreenLayout(forceHalfScreen = isHalfScreen) { innerModifier ->
        if (showCapture) {
            // ── Capture sub-page ──
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
            // ── Main page ──
            Scaffold(
                modifier = innerModifier,
                topBar = {
                    HelpTopBar(
                        title = stringResource(R.string.step1_title),
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
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StepIndicator(currentStep = 1, totalSteps = 4)

                    Text(
                        text = stringResource(R.string.step1_description),
                        style = MaterialTheme.typography.bodyLarge
                    )

                    // Status line
                    if (report.wavelengthQuality == "PRESET") {
                        Text(
                            text = "Calibration loaded from preset — no new captures needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (captures.isNotEmpty()) {
                        Text(
                            text = "${captures.size} frame(s) loaded — calibration computed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // ── Preset row ──
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { showSaveDialog = true },
                            enabled = report.slope != null && report.wavelengthQuality != "PRESET",
                            modifier = Modifier.weight(1f)
                        ) { Text("Save preset") }
                        OutlinedButton(
                            onClick = { showLoadDialog = true },
                            enabled = wlPresets.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text("Load preset") }
                    }

                    // ── Report ──
                    Button(
                        onClick = onOpenReport,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(Icons.Default.Assessment, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("View Report")
                    }

                    // ── Start capture ──
                    Button(
                        onClick = { showCapture = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text(stringResource(R.string.step1_start_capture))
                    }

                    // ── Next (captures OR a loaded preset both unlock) ──
                    if (captures.isNotEmpty() || report.slope != null) {
                        Button(
                            onClick = onNext,
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Text(stringResource(R.string.next_step))
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    // ── Save preset dialog ──
    if (showSaveDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save wavelength preset") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Preset name (e.g. PhoneA-rig1)") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            projectViewModel.saveWavelengthPreset(name)
                            showSaveDialog = false
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Load preset dialog ──
    if (showLoadDialog) {
        AlertDialog(
            onDismissRequest = { showLoadDialog = false },
            title = { Text("Load wavelength preset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    wlPresets.forEach { p ->
                        Card(
                            onClick = {
                                projectViewModel.loadWavelengthPreset(p)
                                showLoadDialog = false
                            }
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${p.deviceModel} · quality ${p.quality ?: "—"}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLoadDialog = false }) { Text("Close") }
            }
        )
    }
}