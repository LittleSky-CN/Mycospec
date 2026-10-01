package org.fungalsentinel.app.ui.steps

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import org.fungalsentinel.app.ui.components.StepIndicator
import org.fungalsentinel.app.ui.components.TutorialOverlay
import org.fungalsentinel.app.viewmodel.ProjectViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step2SpdScreen(
    onNext: () -> Unit,
    onBack: () -> Unit,
    onOpenReport: () -> Unit,
    projectViewModel: ProjectViewModel,
    isHalfScreen: Boolean = false
) {
    val uiState by projectViewModel.uiState.collectAsState()
    val spdPresets by projectViewModel.spdPresets.collectAsState()
    val captures = uiState.spdCaptures
    val report = uiState.reportData

    var showCapture by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showLoadDialog by remember { mutableStateOf(false) }

    val presetLoaded = report.spdStatus.startsWith("PRESET")

    TutorialOverlay(
        stepId = "step2_spd",
        titleRes = R.string.tutorial_step2_title,
        bodyRes = R.string.tutorial_step2_body
    )

    HalfScreenLayout(forceHalfScreen = isHalfScreen) { innerModifier ->
        if (showCapture) {
            CaptureUploadScreen(
                title = stringResource(R.string.step2_capture_title),
                subtitle = stringResource(R.string.step2_capture_subtitle),
                capturedImages = captures,
                onImagesChanged = { projectViewModel.setSpdCaptures(it) },
                onConfirm = { showCapture = false },
                onBack = { showCapture = false },
                modifier = innerModifier
            )
        } else {
            Scaffold(
                modifier = innerModifier,
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.step2_title)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
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
                    StepIndicator(currentStep = 2, totalSteps = 4)
                    // FIX: named argument `style =`
                    Text(
                        text = stringResource(R.string.step2_description),
                        style = MaterialTheme.typography.bodyLarge
                    )

                    if (presetLoaded) {
                        Text(
                            text = "Response loaded from preset — no new captures needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (captures.isNotEmpty()) {
                        Text(
                            text = "${captures.size} frame(s) loaded — response computed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showSaveDialog = true },
                            enabled = report.spdCoeffR != "—" && !presetLoaded,
                            modifier = Modifier.weight(1f)
                        ) { Text("Save preset") }
                        OutlinedButton(
                            onClick = { showLoadDialog = true },
                            enabled = spdPresets.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text("Load preset") }
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = onOpenReport,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Assessment, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("View Report")
                    }

                    Button(
                        onClick = { showCapture = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text(stringResource(R.string.step2_start_capture)) }

                    if (captures.isNotEmpty() || presetLoaded) {
                        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp)) {
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

    if (showSaveDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save SPD preset") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Preset name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            projectViewModel.saveSpdPreset(name)
                            showSaveDialog = false
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") } }
        )
    }

    if (showLoadDialog) {
        AlertDialog(
            onDismissRequest = { showLoadDialog = false },
            title = { Text("Load SPD preset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    spdPresets.forEach { p ->
                        Card(onClick = {
                            projectViewModel.loadSpdPreset(p)
                            showLoadDialog = false
                        }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${p.deviceModel} · ${p.frames ?: 0} frames",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLoadDialog = false }) { Text("Close") } }
        )
    }
}