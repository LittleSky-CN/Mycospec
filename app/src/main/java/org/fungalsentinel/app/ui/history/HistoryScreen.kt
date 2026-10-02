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
    onExport: (ProjectEntity) -> Unit = {}
) {
    val wlPresets by projectViewModel.wlPresets.collectAsState()
    val spdPresets by projectViewModel.spdPresets.collectAsState()

    var tab by remember { mutableIntStateOf(0) }
    var editingPreset by remember { mutableStateOf<PresetEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<PresetEntity?>(null) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    val tutorial = TutorialHost(
        stepId = "history",
        titleRes = R.string.tutorial_history_title,
        bodyRes = R.string.tutorial_history_body
    )

    Scaffold(
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
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Projects") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("λ Presets") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("SPD Presets") })
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
                                                "In progress — tap to continue"
                                            } else {
                                                "Finished"
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
                                            Icon(Icons.Default.FileDownload, contentDescription = "Export")
                                        }
                                        IconButton(onClick = { onDelete(project) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> PresetList(
                    presets = wlPresets,
                    emptyText = "No wavelength presets yet. Save one from Step 1.",
                    onEdit = { editingPreset = it },
                    onDelete = { deleteTarget = it }
                )

                2 -> PresetList(
                    presets = spdPresets,
                    emptyText = "No SPD presets yet. Save one from Step 2.",
                    onEdit = { editingPreset = it },
                    onDelete = { deleteTarget = it }
                )
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
            title = { Text("Delete preset") },
            text = { Text("Delete \"${preset.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    projectViewModel.deletePreset(preset.id)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
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
                                if (p.type == "WAVELENGTH") {
                                    "slope=${p.slope?.let { String.format("%.4f", it) } ?: "—"} " +
                                            "intercept=${p.intercept?.let { String.format("%.2f", it) } ?: "—"}"
                                } else {
                                    "R=${p.coeffR?.let { String.format("%.4f", it) } ?: "—"} " +
                                            "B=${p.coeffB?.let { String.format("%.4f", it) } ?: "—"}"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row {
                            IconButton(onClick = { onEdit(p) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { onDelete(p) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
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
        title = { Text("Edit preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                if (preset.type == "WAVELENGTH") {
                    OutlinedTextField(slope, { slope = it }, label = { Text("Slope (px/nm)") }, singleLine = true)
                    OutlinedTextField(intercept, { intercept = it }, label = { Text("Intercept (px)") }, singleLine = true)
                } else {
                    OutlinedTextField(coeffR, { coeffR = it }, label = { Text("R/G coefficient") }, singleLine = true)
                    OutlinedTextField(coeffB, { coeffB = it }, label = { Text("B/G coefficient") }, singleLine = true)
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
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}