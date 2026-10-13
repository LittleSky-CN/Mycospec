package org.fungalsentinel.app.ui.steps

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.fungalsentinel.app.R
import org.fungalsentinel.app.ui.capture.CaptureUploadScreen
import org.fungalsentinel.app.ui.components.*
import org.fungalsentinel.app.viewmodel.ProjectViewModel
import org.fungalsentinel.app.viewmodel.StandardGroupInput

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
    val report = uiState.reportData

    var editingConc by remember { mutableStateOf("") }
    var editingUnit by remember { mutableStateOf("mg/L") }
    var editingBlank by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var editingSample by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var concError by remember { mutableStateOf(false) }
    var showBlankCapture by remember { mutableStateOf(false) }
    var showSampleCapture by remember { mutableStateOf(false) }

    val concValid = editingConc.toDoubleOrNull() != null
    val canAdd = concValid && editingBlank.isNotEmpty() && editingSample.isNotEmpty()
    val curveReady = report.curveSlope != null

    val status = report.step4Status
    val token = status.substringBefore('|')
    val message = status.substringAfter('|', status)
    val statusColor = when (token) {
        "FAILED", "BLOCKED" -> MaterialTheme.colorScheme.error
        "COMPUTED", "PRESET" -> Color(0xFF5CFF5C)
        "PROCESSING" -> Color(0xFF5CA8FF)
        else -> Color(0xFFF5F5C0)
    }

    val tutorial = TutorialHost(
        stepId = "step4_standard",
        titleRes = R.string.tutorial_step4_title,
        bodyRes = R.string.tutorial_step4_body
    )

    HalfScreenLayout(forceHalfScreen = isHalfScreen) { innerModifier ->
        if (showBlankCapture) {
            CaptureUploadScreen(
                title = "${stringResource(R.string.step3_capture_blank_title)} — ${editingConc.ifBlank { "?" }} $editingUnit",
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
                title = "${stringResource(R.string.step3_capture_sample_title)} — ${editingConc.ifBlank { "?" }} $editingUnit",
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
            Column(
                modifier = innerModifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NeonHeader("STEP4:", stringResource(R.string.step4_short), onBack, tutorial)

                NeonOutlineBox(MaterialTheme.colorScheme.secondary) {
                    Text(
                        text = stringResource(R.string.step4_description),
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

                if (standardGroups.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.lbl_standard_groups, standardGroups.size),
                        color = Color(0xFFF5F5C0),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    standardGroups.forEach { group ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${group.concentration} ${group.unit}", color = Color(0xFFF5F5C0), fontWeight = FontWeight.Bold)
                                    Text(
                                        stringResource(R.string.lbl_group_counts, group.blankUris.size, group.sampleUris.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    projectViewModel.setStandardGroups(standardGroups.filter { it.id != group.id })
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFF5F5C0))
                                }
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(stringResource(R.string.act_add_group), color = Color(0xFFF5F5C0), fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = editingConc,
                            onValueChange = { editingConc = it; concError = false },
                            label = { Text(stringResource(R.string.step4_concentration_label)) },
                            isError = concError,
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { showBlankCapture = true },
                                enabled = concValid && editingBlank.size < 5,
                                modifier = Modifier.weight(1f)
                            ) { Text("${stringResource(R.string.lbl_blank_captures)} (${editingBlank.size})") }
                            Button(
                                onClick = { showSampleCapture = true },
                                enabled = concValid && editingSample.size < 5,
                                modifier = Modifier.weight(1f)
                            ) { Text("${stringResource(R.string.lbl_sample_captures)} (${editingSample.size})") }
                        }
                        Button(
                            onClick = {
                                val conc = editingConc.toDoubleOrNull()
                                if (conc == null) { concError = true; return@Button }
                                val newId = (standardGroups.maxOfOrNull { it.id } ?: 0) + 1
                                projectViewModel.setStandardGroups(
                                    standardGroups + StandardGroupInput(newId, conc, editingUnit, editingBlank, editingSample)
                                )
                                editingConc = ""; editingBlank = emptyList(); editingSample = emptyList(); concError = false
                            },
                            enabled = canAdd,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.act_add_group))
                        }
                    }
                }

                if (standardGroups.size == 1) {
                    Text(
                        text = stringResource(R.string.note_step4_min),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    NeonStepIndicator(current = 4)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            NeonYellowButton(stringResource(R.string.act_view_report), onOpenReport, modifier = Modifier.weight(1f))
                            NeonYellowButton(stringResource(R.string.step4_build_curve), { projectViewModel.buildCurve() },
                                enabled = standardGroups.size >= 2, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            NeonYellowButton(stringResource(R.string.next_step), onNext, enabled = curveReady, modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}