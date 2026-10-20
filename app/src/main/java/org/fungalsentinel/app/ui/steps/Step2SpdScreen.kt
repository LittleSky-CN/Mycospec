package org.fungalsentinel.app.ui.steps

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.fungalsentinel.app.R
import org.fungalsentinel.app.ui.capture.CaptureUploadScreen
import org.fungalsentinel.app.ui.components.*
import org.fungalsentinel.app.ui.theme.AppColors
import org.fungalsentinel.app.ui.theme.statusTextColor
import org.fungalsentinel.app.viewmodel.ProjectViewModel

/**
 * SAF "save file" contract that suggests a MycoSpec SPD file name
 * (`mycospec-spd-<timestamp>.json`) instead of leaving the box empty.
 */
private class CreateJsonDocumentWithName : ActivityResultContracts.CreateDocument("application/json") {
    override fun createIntent(context: android.content.Context, input: String): Intent =
        super.createIntent(context, input)
            .putExtra(Intent.EXTRA_TITLE, "mycospec-spd-${System.currentTimeMillis()}.json")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step2SpdScreen(
    onNext: () -> Unit,
    onBack: () -> Unit,
    onOpenReport: () -> Unit,
    projectViewModel: ProjectViewModel,
    isHalfScreen: Boolean = false,
    coverRatio: Float = 0.5f
) {
    val uiState by projectViewModel.uiState.collectAsState()
    val captures = uiState.spdCaptures
    val report = uiState.reportData
    val spdPresets by projectViewModel.spdPresets.collectAsState()

    var showCapture by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showLoadDialog by remember { mutableStateOf(false) }

    // schema v3 preset files go through SAF: they are the interchange format
    // with the desktop (FSSA) build, not just local Room rows.
    val exportLauncher = rememberLauncherForActivityResult(CreateJsonDocumentWithName()) { uri ->
        if (uri != null) projectViewModel.exportSpdPreset(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) projectViewModel.importSpdPreset(uri)
    }

    val presetLoaded = report.spdStatus.startsWith("PRESET") || report.step2Status.startsWith("PRESET")
    // spdStatus stores localized text (e.g. "Calibrado" / "Yakonzedwa" /... in non-English locales),
    // so using startsWith("Calibrated") on it would break the export/import/next-step buttons in non-English languages.
    // The COMPUTED prefix of step2Status and spdFrames are language-independent, so they are used instead.
    val calibrated = report.spdFrames > 0 || report.step2Status.startsWith("COMPUTED")

    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val gap = if (landscape) 8.dp else 14.dp
    val pad = if (landscape) 10.dp else 16.dp

    val status = report.step2Status
    val token = status.substringBefore('|')
    val message = status.substringAfter('|', status)
    // [Unified] Status colors converge
    val statusColor = statusTextColor(token)

    val tutorial = TutorialHost(
        stepId = "step2_spd",
        titleRes = R.string.tutorial_step2_title,
        bodyRes = R.string.tutorial_step2_body
    )

    HalfScreenLayout(forceHalfScreen = isHalfScreen, coverRatio = coverRatio) { innerModifier ->
        if (showCapture) {
            CaptureUploadScreen(
                title = stringResource(R.string.step2_capture_title),
                subtitle = stringResource(R.string.step2_capture_subtitle),
                minCaptures = 1,
                maxCaptures = 5,
                capturedImages = captures,
                onImagesChanged = { projectViewModel.setSpdCaptures(it) },
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
                NeonHeader("STEP2:", stringResource(R.string.step2_short), onBack, tutorial)

                // Info box: bordered with the single accent color
                NeonOutlineBox(AppColors.Accent) {
                    Text(
                        text = stringResource(R.string.step2_description),
                        color = AppColors.TextPrimary,
                        style = if (landscape) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.note_step2_keep),
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Status box: neutral border
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
                        NeonStepIndicator(current = 2)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            NeonButtonGroup(vertical) { m ->
                                NeonYellowButton(
                                    text = stringResource(R.string.act_save_preset),
                                    onClick = { showSaveDialog = true },
                                    enabled = calibrated && !presetLoaded,
                                    modifier = m
                                )
                                NeonYellowButton(
                                    text = stringResource(R.string.act_load_preset),
                                    onClick = { showLoadDialog = true },
                                    enabled = spdPresets.isNotEmpty(),
                                    modifier = m
                                )
                            }
                            NeonButtonGroup(vertical) { m ->
                                NeonYellowButton(
                                    text = stringResource(R.string.act_export_spd_preset),
                                    onClick = { exportLauncher.launch("application/json") },
                                    enabled = calibrated || presetLoaded,
                                    modifier = m
                                )
                                NeonYellowButton(
                                    text = stringResource(R.string.act_import_spd_preset),
                                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                                    modifier = m
                                )
                            }
                            NeonButtonGroup(vertical) { m ->
                                NeonYellowButton(stringResource(R.string.act_view_report), onOpenReport, modifier = m)
                                NeonYellowButton(stringResource(R.string.step2_start_capture), { showCapture = true }, modifier = m)
                                NeonYellowButton(stringResource(R.string.next_step), onNext, enabled = calibrated || presetLoaded, modifier = m)
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
            text = { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.lbl_preset_name)) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) { projectViewModel.saveSpdPreset(name); showSaveDialog = false }
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text(stringResource(R.string.action_dismiss)) } }
        )
    }
    if (showLoadDialog) {
        AlertDialog(
            onDismissRequest = { showLoadDialog = false },
            title = { Text(stringResource(R.string.act_load_preset)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    spdPresets.forEach { p ->
                        Card(onClick = { projectViewModel.loadSpdPreset(p); showLoadDialog = false }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall)
                                Text("${p.deviceModel} · ${p.frames ?: 0}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLoadDialog = false }) { Text(stringResource(R.string.action_ok)) } }
        )
    }
}