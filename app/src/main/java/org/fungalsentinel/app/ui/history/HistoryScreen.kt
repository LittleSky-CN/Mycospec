package org.fungalsentinel.app.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fungalsentinel.app.R
import org.fungalsentinel.app.data.model.ProjectEntity
import org.fungalsentinel.app.data.preset.PresetEntity
import org.fungalsentinel.app.ui.components.HalfScreenLayout
import org.fungalsentinel.app.ui.components.HelpTopBar
import org.fungalsentinel.app.ui.components.TutorialHost
import org.fungalsentinel.app.viewmodel.ProjectViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    projects: List<ProjectEntity>,
    projectViewModel: ProjectViewModel,
    onBack: () -> Unit,
    onOpenProject: (ProjectEntity) -> Unit,
    onDelete: (ProjectEntity) -> Unit = {},
    onExport: (ProjectEntity) -> Unit = {},
    isHalfScreen: Boolean = false,
    coverRatio: Float = 0.5f
) {
    val wlPresets by projectViewModel.wlPresets.collectAsState()
    val spdPresets by projectViewModel.spdPresets.collectAsState()
    val curvePresets by projectViewModel.curvePresets.collectAsState()

    var tab by remember { mutableIntStateOf(0) }
    var editingPreset by remember { mutableStateOf<PresetEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<PresetEntity?>(null) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    val tutorial = TutorialHost(
        stepId = "history",
        titleRes = R.string.tutorial_history_title,
        bodyRes = R.string.tutorial_history_body
    )

    HalfScreenLayout(forceHalfScreen = isHalfScreen, coverRatio = coverRatio) { screenModifier ->
        Scaffold(
            modifier = screenModifier.fillMaxSize(),
            topBar = {
                HelpTopBar(
                    title = stringResource(R.string.history_title),
                    tutorial = tutorial,
                    onBack = onBack
                )
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.history_tab_projects)) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.history_tab_wl_presets)) })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(stringResource(R.string.history_tab_spd_presets)) })
                    Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text(stringResource(R.string.history_curve_presets)) })
                }

                when (tab) {
                    0 -> if (projects.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.history_empty))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(projects) { project ->
                                Card(
                                    onClick = {
                                        if (project.status == "IN_PROGRESS") onOpenProject(project)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(project.name, fontWeight = FontWeight.Bold)
                                            Text(
                                                dateFormat.format(Date(project.createdAt)),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                if (project.status == "IN_PROGRESS") {
                                                    stringResource(R.string.history_in_progress)
                                                } else {
                                                    stringResource(R.string.history_finished)
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (project.status == "IN_PROGRESS") {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                        Row {
                                            IconButton(onClick = { onExport(project) }) {
                                                Icon(Icons.Default.FileDownload, contentDescription = stringResource(R.string.action_export))
                                            }
                                            IconButton(onClick = { onDelete(project) }) {
                                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> PresetList(
                        presets = wlPresets,
                        emptyText = stringResource(R.string.history_wl_presets_empty),
                        onEdit = { editingPreset = it },
                        onDelete = { deleteTarget = it }
                    )

                    2 -> PresetList(
                        presets = spdPresets,
                        emptyText = stringResource(R.string.history_spd_presets_empty),
                        onEdit = { editingPreset = it },
                        onDelete = { deleteTarget = it }
                    )

                    3 -> PresetList(
                        presets = curvePresets,
                        emptyText = stringResource(R.string.history_curve_presets_empty),
                        onEdit = { editingPreset = it },
                        onDelete = { deleteTarget = it }
                    )
                }
            }
        }
    }

    // ── Edit preset dialog ──
    editingPreset?.let { preset ->
        key(preset.id) {
            PresetEditDialog(
                preset = preset,
                onDismiss = { editingPreset = null },
                onSave = { updated ->
                    projectViewModel.updatePreset(updated)
                    editingPreset = null
                }
            )
        }
    }

    // ── Delete confirm dialog ──
    deleteTarget?.let { preset ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.history_delete_preset_title)) },
            text = { Text(stringResource(R.string.history_delete_preset_msg, preset.name)) },
            confirmButton = {
                TextButton(onClick = {
                    projectViewModel.deletePreset(preset.id)
                    deleteTarget = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun PresetList(
    presets: List<PresetEntity>,
    emptyText: String,
    onEdit: (PresetEntity) -> Unit,
    onDelete: (PresetEntity) -> Unit
) {
    if (presets.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(presets) { p ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold)
                            Text(p.deviceModel, style = MaterialTheme.typography.bodySmall)
                            Text(
                                when (p.type) {
                                    "WAVELENGTH" -> stringResource(
                                        R.string.history_preset_summary_wl,
                                        p.slope?.let { String.format(Locale.US, "%.4f", it) } ?: "—",
                                        p.intercept?.let { String.format(Locale.US, "%.2f", it) } ?: "—"
                                    )
                                    "CURVE" -> stringResource(
                                        R.string.history_preset_summary_curve,
                                        p.slope?.let { String.format(Locale.US, "%.4f", it) } ?: "—",
                                        p.intercept?.let { String.format(Locale.US, "%.2f", it) } ?: "—",
                                        p.quality ?: "—"
                                    )
                                    else -> stringResource(
                                        R.string.history_preset_summary_spd,
                                        p.coeffR?.let { String.format(Locale.US, "%.4f", it) } ?: "—",
                                        p.coeffB?.let { String.format(Locale.US, "%.4f", it) } ?: "—"
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row {
                            IconButton(onClick = { onEdit(p) }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                            }
                            IconButton(onClick = { onDelete(p) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetEditDialog(
    preset: PresetEntity,
    onDismiss: () -> Unit,
    onSave: (PresetEntity) -> Unit
) {
    var name by remember { mutableStateOf(preset.name) }
    var slope by remember { mutableStateOf(preset.slope?.toString() ?: "") }
    var intercept by remember { mutableStateOf(preset.intercept?.toString() ?: "") }
    var coeffR by remember { mutableStateOf(preset.coeffR?.toString() ?: "") }
    var coeffB by remember { mutableStateOf(preset.coeffB?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_edit_preset_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.lbl_preset_name)) }, singleLine = true)
                if (preset.type == "WAVELENGTH") {
                    OutlinedTextField(slope, { slope = it }, label = { Text(stringResource(R.string.history_slope_label)) }, singleLine = true)
                    OutlinedTextField(intercept, { intercept = it }, label = { Text(stringResource(R.string.history_intercept_label)) }, singleLine = true)
                } else if (preset.type == "SPD") {
                    OutlinedTextField(coeffR, { coeffR = it }, label = { Text(stringResource(R.string.history_rg_coefficient)) }, singleLine = true)
                    OutlinedTextField(coeffB, { coeffB = it }, label = { Text(stringResource(R.string.history_bg_coefficient)) }, singleLine = true)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    preset.copy(
                        name = name,
                        slope = slope.toDoubleOrNull() ?: preset.slope,
                        intercept = intercept.toDoubleOrNull() ?: preset.intercept,
                        coeffR = coeffR.toDoubleOrNull() ?: preset.coeffR,
                        coeffB = coeffB.toDoubleOrNull() ?: preset.coeffB
                    )
                )
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}