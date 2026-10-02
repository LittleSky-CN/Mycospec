package org.fungalsentinel.app.ui.steps

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fungalsentinel.app.R
import org.fungalsentinel.app.ui.capture.CaptureUploadScreen
import org.fungalsentinel.app.ui.components.HalfScreenLayout
import org.fungalsentinel.app.ui.components.HelpTopBar
import org.fungalsentinel.app.ui.components.StepIndicator
import org.fungalsentinel.app.ui.components.TutorialHost
import org.fungalsentinel.app.viewmodel.ProjectViewModel

/**
 * Fluorophore options with integration ranges (FSSA v1.3.4 aligned).
 * NOTE: en-dash ranges fixed (previous build had mojibake strings).
 */
enum class Fluorophore(val displayName: String, val integrationRangeNm: String) {
    YPET("Ypet", "500–530 nm"),
    EGFP("EGFP", "500–540 nm"),
    MCHERRY("mCherry", "590–630 nm"),
    CFP("CFP", "460–490 nm"),
    MTURQUOISE2("mTurquoise2", "460–490 nm")
}

/**
 * Step 3 — Sample analysis (blank + unknown sample).
 * Integration window is derived from the Step 1 mapping; results -> Report Page 3.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step3SampleScreen(
    onNext: () -> Unit,
    onBack: () -> Unit,
    onOpenReport: () -> Unit,
    projectViewModel: ProjectViewModel,
    isHalfScreen: Boolean = false,
    blankMode: String = "SINGLE"
) {
    val uiState by projectViewModel.uiState.collectAsState()
    val blankCaptures = uiState.blankCaptures
    val sampleCaptures = uiState.sampleCaptures

    var selectedFluorophore by remember { mutableStateOf(Fluorophore.EGFP) }
    var showBlankCapture by remember { mutableStateOf(false) }
    var showSampleCapture by remember { mutableStateOf(false) }

    val tutorial = TutorialHost(
        stepId = "step3_sample",
        titleRes = R.string.tutorial_step3_title,
        bodyRes = R.string.tutorial_step3_body
    )

    // Push default fluorophore once so Report has a band before user changes it
    LaunchedEffect(Unit) {
        projectViewModel.setFluorophore(selectedFluorophore.name)
    }

    HalfScreenLayout(forceHalfScreen = isHalfScreen) { innerModifier ->
        if (showBlankCapture) {
            CaptureUploadScreen(
                title = stringResource(R.string.step3_capture_blank_title),
                subtitle = "Blank control (no target fluorophore)",
                minCaptures = 1,
                maxCaptures = 5,
                capturedImages = blankCaptures,
                onImagesChanged = { projectViewModel.setBlankCaptures(it) },
                onConfirm = { showBlankCapture = false },
                onBack = { showBlankCapture = false },
                onHelp = { tutorial.show() },
                modifier = innerModifier
            )
        } else if (showSampleCapture) {
            CaptureUploadScreen(
                title = stringResource(R.string.step3_capture_sample_title),
                subtitle = "Unknown sample",
                minCaptures = 1,
                maxCaptures = 5,
                capturedImages = sampleCaptures,
                onImagesChanged = { projectViewModel.setSampleCaptures(it) },
                onConfirm = { showSampleCapture = false },
                onBack = { showSampleCapture = false },
                onHelp = { tutorial.show() },
                modifier = innerModifier
            )
        } else {
            Scaffold(
                modifier = innerModifier,
                topBar = {
                    HelpTopBar(
                        title = stringResource(R.string.step3_title),
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
                    StepIndicator(currentStep = 3, totalSteps = 4)

                    Text(
                        text = stringResource(R.string.step3_description),
                        style = MaterialTheme.typography.bodyLarge
                    )

                    // Blank mode note (Settings-driven)
                    Text(
                        text = if (blankMode == "FULL") {
                            "Blank mode: FULL — Step 4 captures a separate blank per standard group."
                        } else {
                            "Blank mode: SINGLE — one shared blank workflow; Step 4 still captures per-group blanks."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // ── Fluorophore selector ──
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Target fluorophore",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            var expanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = !expanded }
                            ) {
                                OutlinedTextField(
                                    value = "${selectedFluorophore.displayName} (${selectedFluorophore.integrationRangeNm})",
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    Fluorophore.values().forEach { fluorophore ->
                                        DropdownMenuItem(
                                            text = {
                                                Text("${fluorophore.displayName} — ${fluorophore.integrationRangeNm}")
                                            },
                                            onClick = {
                                                selectedFluorophore = fluorophore
                                                projectViewModel.setFluorophore(fluorophore.name)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Blank batch card ──
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Blank captures", fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${blankCaptures.size} / 5",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { showBlankCapture = true },
                                enabled = blankCaptures.size < 5
                            ) { Text("Capture") }
                        }
                    }

                    // ── Sample batch card ──
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Sample captures", fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${sampleCaptures.size} / 5",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { showSampleCapture = true },
                                enabled = sampleCaptures.size < 5
                            ) { Text("Capture") }
                        }
                    }

                    if (blankCaptures.isNotEmpty() && sampleCaptures.isNotEmpty()) {
                        Text(
                            text = "Analysis computed. Open Report (Page 3) for integrated area, SD and spectrum.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
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

                    // ── Next ──
                    if (blankCaptures.isNotEmpty() && sampleCaptures.isNotEmpty()) {
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
}