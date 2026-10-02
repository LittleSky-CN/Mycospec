package org.fungalsentinel.app.ui.steps

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.fungalsentinel.app.R
import org.fungalsentinel.app.ui.capture.CaptureUploadScreen
import org.fungalsentinel.app.ui.components.HalfScreenLayout
import org.fungalsentinel.app.ui.components.HelpTopBar
import org.fungalsentinel.app.ui.components.StepIndicator
import org.fungalsentinel.app.ui.components.TutorialHost
import org.fungalsentinel.app.viewmodel.ProjectViewModel
import org.fungalsentinel.app.viewmodel.StandardGroupInput

/**
 * Step 4 — Standard curve.
 * Committed groups live in the ViewModel (auto-saved, survive navigation).
 * Only the group currently being edited is local state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step4StandardCurveScreen(
    onNext: () -> Unit,
    onBack: () -> Unit,
    onOpenReport: () -> Unit,
    projectViewModel: ProjectViewModel,
    isHalfScreen: Boolean = false
) {
    val uiState by projectViewModel.uiState.collectAsState()
    val standardGroups = uiState.standardGroups

    // Editing state (pre-commit only)
    var editingConc by remember { mutableStateOf("") }
    var editingUnit by remember { mutableStateOf("mg/L") }
    var editingBlank by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var editingSample by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var concError by remember { mutableStateOf(false) }
    var showBlankCapture by remember { mutableStateOf(false) }
    var showSampleCapture by remember { mutableStateOf(false) }
    var curveBuilt by remember { mutableStateOf(false) }

    val concValid = editingConc.toDoubleOrNull() != null
    val canAdd = concValid && editingBlank.isNotEmpty() && editingSample.isNotEmpty()

    val tutorial = TutorialHost(
        stepId = "step4_standard",
        titleRes = R.string.tutorial_step4_title,
        bodyRes = R.string.tutorial_step4_body
    )

    HalfScreenLayout(forceHalfScreen = isHalfScreen) { innerModifier ->
        if (showBlankCapture) {
            CaptureUploadScreen(
                title = "Standard Blank — ${editingConc.ifBlank { "?" }} $editingUnit",
                subtitle = "Blank control for this concentration",
                minCaptures = 1,
                maxCaptures = 5,
                capturedImages = editingBlank,
                onImagesChanged = { editingBlank = it },
                onConfirm = { showBlankCapture = false },
                onBack = { showBlankCapture = false },
                onHelp = { tutorial.show() },
                modifier = innerModifier
            )
        } else if (showSampleCapture) {
            CaptureUploadScreen(
                title = "Standard Sample — ${editingConc.ifBlank { "?" }} $editingUnit",
                subtitle = "Sample for this concentration",
                minCaptures = 1,
                maxCaptures = 5,
                capturedImages = editingSample,
                onImagesChanged = { editingSample = it },
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
                        title = stringResource(R.string.step4_title),
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
                    StepIndicator(currentStep = 4, totalSteps = 4)

                    Text(
                        text = stringResource(R.string.step4_description),
                        style = MaterialTheme.typography.bodyLarge
                    )

                    // ── Committed groups list ──
                    if (standardGroups.isNotEmpty()) {
                        Text(
                            text = "Standard groups (${standardGroups.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        standardGroups.forEach { group ->
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${group.concentration} ${group.unit}",
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Blank: ${group.blankUris.size} | Sample: ${group.sampleUris.size}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            projectViewModel.setStandardGroups(
                                                standardGroups.filter { it.id != group.id }
                                            )
                                            curveBuilt = false
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                                    }
                                }
                            }
                        }
                    }

                    // ── Add-new-group editor ──
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Add new standard group", fontWeight = FontWeight.Bold)

                            OutlinedTextField(
                                value = editingConc,
                                onValueChange = {
                                    editingConc = it
                                    concError = false
                                },
                                label = { Text(stringResource(R.string.step4_concentration_label)) },
                                isError = concError,
                                supportingText = { if (concError) Text("Invalid number") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = editingUnit,
                                onValueChange = { editingUnit = it },
                                label = { Text(stringResource(R.string.step4_unit_label)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { showBlankCapture = true },
                                    enabled = concValid && editingBlank.size < 5,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Blank (${editingBlank.size})") }
                                Button(
                                    onClick = { showSampleCapture = true },
                                    enabled = concValid && editingSample.size < 5,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Sample (${editingSample.size})") }
                            }

                            Button(
                                onClick = {
                                    val conc = editingConc.toDoubleOrNull()
                                    if (conc == null) {
                                        concError = true
                                        return@Button
                                    }
                                    val newId = (standardGroups.maxOfOrNull { it.id } ?: 0) + 1
                                    projectViewModel.setStandardGroups(
                                        standardGroups + StandardGroupInput(
                                            id = newId,
                                            concentration = conc,
                                            unit = editingUnit,
                                            blankUris = editingBlank,
                                            sampleUris = editingSample
                                        )
                                    )
                                    editingConc = ""
                                    editingBlank = emptyList()
                                    editingSample = emptyList()
                                    concError = false
                                    curveBuilt = false
                                },
                                enabled = canAdd,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add standard group")
                            }
                        }
                    }

                    if (standardGroups.size == 1) {
                        Text(
                            text = "Add at least 2 concentration groups to build the curve (3+ recommended).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (curveBuilt) {
                        Text(
                            text = "Curve built. Open Report (Page 4) for regression, R² and prediction.",
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

                    // ── Build curve ──
                    Button(
                        onClick = {
                            projectViewModel.buildCurve()
                            curveBuilt = true
                        },
                        enabled = standardGroups.size >= 2,
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text(stringResource(R.string.step4_build_curve))
                    }

                    // ── Next ──
                    if (standardGroups.size >= 2) {
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