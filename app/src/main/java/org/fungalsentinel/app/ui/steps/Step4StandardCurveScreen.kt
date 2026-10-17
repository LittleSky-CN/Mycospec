package org.fungalsentinel.app.ui.steps

import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalConfiguration
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
import org.fungalsentinel.app.ui.theme.AppColors
import org.fungalsentinel.app.ui.theme.statusTextColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step4StandardCurveScreen(
    onNext: () -> Unit, onBack: () -> Unit, onOpenReport: () -> Unit,
    projectViewModel: ProjectViewModel, isHalfScreen: Boolean = false, blankMode: String = "SINGLE", coverRatio: Float = 0.5f
) {
    val uiState by projectViewModel.uiState.collectAsState()
    val standardGroups = uiState.standardGroups
    val report = uiState.reportData
    val curvePresets by projectViewModel.curvePresets.collectAsState()
    val activeCurvePreset = uiState.activeCurvePreset
    val singleBlank = blankMode == "SINGLE"
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var editingConc by remember { mutableStateOf("") }
    val defaultUnit = stringResource(R.string.default_concentration_unit)
    var editingUnit by remember { mutableStateOf(defaultUnit) }
    var editingBlank by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var editingSample by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showBlankCapture by remember { mutableStateOf(false) }
    var showSampleCapture by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showLoadPresetDialog by remember { mutableStateOf(false) }

    val concentration = editingConc.toDoubleOrNull()
    val concValid = concentration != null && concentration.isFinite()
    val canAdd = concValid && editingUnit.isNotBlank() && editingSample.isNotEmpty() &&
        (singleBlank || editingBlank.isNotEmpty())
    val buildGroupCount = standardGroups.size + if (canAdd) 1 else 0
    val curveReady = report.curveSlope != null
    val curvePresetCanSave = curveReady && uiState.curveReferencePreset != null
    val curvePresetCanApply = activeCurvePreset != null &&
        uiState.blankCaptures.isNotEmpty() && uiState.sampleCaptures.isNotEmpty() &&
        report.step3Status.startsWith("COMPUTED")

    val status = report.step4Status
    val token = status.substringBefore('|')
    val message = status.substringAfter('|', status)
    val statusColor = statusTextColor(token)
    val captureConcentration = if (editingConc.isBlank()) {
        stringResource(R.string.value_not_set)
    } else {
        editingConc
    }

    val tutorial = TutorialHost(stepId = "step4_standard", titleRes = R.string.tutorial_step4_title, bodyRes = R.string.tutorial_step4_body)

    HalfScreenLayout(forceHalfScreen = isHalfScreen, coverRatio = coverRatio) { innerModifier ->
        if (showBlankCapture) {
            CaptureUploadScreen(
                title = stringResource(
                    R.string.step4_capture_title,
                    stringResource(R.string.lbl_blank_captures),
                    captureConcentration,
                    editingUnit
                ),
                minCaptures = 1, maxCaptures = 5, capturedImages = editingBlank,
                onImagesChanged = { editingBlank = it }, onConfirm = { showBlankCapture = false },
                onBack = { showBlankCapture = false }, onHelp = { tutorial.show() }, modifier = innerModifier
            )
        } else if (showSampleCapture) {
            CaptureUploadScreen(
                title = stringResource(
                    R.string.step4_capture_title,
                    stringResource(R.string.lbl_sample_captures),
                    captureConcentration,
                    editingUnit
                ),
                minCaptures = 1, maxCaptures = 5, capturedImages = editingSample,
                onImagesChanged = { editingSample = it }, onConfirm = { showSampleCapture = false },
                onBack = { showSampleCapture = false }, onHelp = { tutorial.show() }, modifier = innerModifier
            )
        } else {
            Column(
                modifier = innerModifier.fillMaxSize().background(AppColors.Bg)
                    .verticalScroll(rememberScrollState())
                    .padding(if (isLandscape) 10.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 12.dp)
            ) {
                NeonHeader("STEP4:", stringResource(R.string.step4_short), onBack, tutorial)
                NeonOutlineBox(AppColors.Accent) {
                    Text(text = stringResource(R.string.step4_description), color = AppColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                }
                NeonOutlineBox(AppColors.Outline) {
                    Text(text = message, color = statusColor, style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }

                if (activeCurvePreset == null && standardGroups.isNotEmpty()) {
                    Text(text = stringResource(R.string.lbl_standard_groups, standardGroups.size), color = AppColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    standardGroups.forEach { group ->
                        NeonOutlineBox(AppColors.Outline) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${group.concentration} ${group.unit}", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = if (singleBlank) "${stringResource(R.string.lbl_sample_captures)}: ${group.sampleUris.size}"
                                        else stringResource(R.string.lbl_group_counts, group.blankUris.size, group.sampleUris.size),
                                        style = MaterialTheme.typography.bodySmall, color = AppColors.TextPrimary.copy(alpha = 0.7f)
                                    )
                                }
                                IconButton(onClick = { projectViewModel.setStandardGroups(standardGroups.filter { it.id != group.id }) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.delete_standard_group),
                                        tint = AppColors.Error
                                    )
                                }

                            }

                        }
                    }
                }

                if (activeCurvePreset == null) {
                    NeonOutlineBox(AppColors.Outline) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.act_add_group), color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = editingConc, onValueChange = { editingConc = it },
                            label = { Text(stringResource(R.string.step4_concentration_label)) },
                            isError = editingConc.isNotBlank() && !concValid,
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                        if (editingConc.isNotBlank() && !concValid) {
                            Text(
                                text = stringResource(R.string.step4_concentration_invalid),
                                color = AppColors.Error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        OutlinedTextField(value = editingUnit, onValueChange = { editingUnit = it },
                            label = { Text(stringResource(R.string.step4_unit_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            if (!singleBlank) {
                                NeonYellowButton(
                                    "${stringResource(R.string.lbl_blank_captures)} (${editingBlank.size})",
                                    onClick = { showBlankCapture = true },
                                    enabled = concValid && editingBlank.size < 5,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            NeonYellowButton(
                                "${stringResource(R.string.lbl_sample_captures)} (${editingSample.size})",
                                onClick = { showSampleCapture = true },
                                enabled = concValid && editingSample.size < 5,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        NeonYellowButton(
                            text = stringResource(R.string.act_add_group),
                            onClick = {
                                val conc = editingConc.toDoubleOrNull()
                                if (conc != null) {
                                    val newId = (standardGroups.maxOfOrNull { it.id } ?: 0) + 1
                                    projectViewModel.setStandardGroups(standardGroups + StandardGroupInput(
                                        id = newId, concentration = conc, unit = editingUnit,
                                        blankUris = if (singleBlank) emptyList() else editingBlank, sampleUris = editingSample
                                    ))
                                    editingConc = ""; editingBlank = emptyList(); editingSample = emptyList()
                                }
                            },
                            enabled = canAdd,
                            icon = Icons.Default.Add,
                            modifier = Modifier.fillMaxWidth()
                        )
                        }
                    }

                    if (standardGroups.size == 1) {
                        Text(text = stringResource(R.string.note_step4_min), style = MaterialTheme.typography.bodySmall, color = AppColors.TextPrimary.copy(alpha = 0.7f))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    NeonStepIndicator(current = 4)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            NeonYellowButton(stringResource(R.string.act_view_report), onOpenReport, modifier = Modifier.weight(1f))
                            NeonYellowButton(
                                if (activeCurvePreset != null) stringResource(R.string.step4_apply_preset)
                                else stringResource(R.string.step4_build_curve),
                                onClick = {
                                    if (activeCurvePreset != null) {
                                        projectViewModel.buildCurve()
                                    } else {
                                        val pendingGroup = if (canAdd) {
                                            StandardGroupInput(
                                                id = (standardGroups.maxOfOrNull { it.id } ?: 0) + 1,
                                                concentration = editingConc.toDouble(),
                                                unit = editingUnit,
                                                blankUris = if (singleBlank) emptyList() else editingBlank,
                                                sampleUris = editingSample
                                            )
                                        } else null
                                        if (pendingGroup != null) {
                                            editingConc = ""
                                            editingBlank = emptyList()
                                            editingSample = emptyList()
                                        }
                                        projectViewModel.buildCurve(pendingGroup)
                                    }
                                },
                                enabled = if (activeCurvePreset != null) curvePresetCanApply else buildGroupCount >= 2,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        NeonButtonGroup(isLandscape) { m ->
                            NeonYellowButton(
                                stringResource(R.string.act_save_preset),
                                onClick = { showSavePresetDialog = true },
                                enabled = curvePresetCanSave,
                                modifier = m
                            )
                            NeonYellowButton(
                                stringResource(R.string.act_load_preset),
                                onClick = { showLoadPresetDialog = true },
                                enabled = curvePresets.isNotEmpty(),
                                modifier = m
                            )
                        }
                        if (activeCurvePreset != null) {
                            NeonOutlineBox(AppColors.Outline) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        stringResource(R.string.step4_curve_preset_active, activeCurvePreset.name),
                                        color = AppColors.TextPrimary,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { projectViewModel.clearCurvePreset() }) {
                                        Text(stringResource(R.string.act_clear_preset))
                                    }
                                }
                                Text(
                                    stringResource(R.string.step4_preset_standard_ignored),
                                    color = AppColors.TextPrimary.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
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

    if (showSavePresetDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text(stringResource(R.string.act_save_preset)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.lbl_preset_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            projectViewModel.saveCurvePreset(name)
                            showSavePresetDialog = false
                        }
                    },
                    enabled = name.isNotBlank()
                ) { Text(stringResource(R.string.act_save_preset)) }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) { Text("×") }
            }
        )
    }
    if (showLoadPresetDialog) {
        AlertDialog(
            onDismissRequest = { showLoadPresetDialog = false },
            title = { Text(stringResource(R.string.act_load_preset)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    curvePresets.forEach { preset ->
                        Card(
                            onClick = {
                                projectViewModel.loadCurvePreset(preset)
                                showLoadPresetDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(preset.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(
                                        R.string.step4_curve_preset_summary,
                                        preset.spdSource ?: "—",
                                        preset.deviceModel,
                                        preset.quality ?: "—"
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLoadPresetDialog = false }) { Text("×") }
            }
        )
    }
}