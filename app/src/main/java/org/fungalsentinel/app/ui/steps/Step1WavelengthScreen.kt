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
fun Step1WavelengthScreen(
    onNext: () -> Unit,
    onBack: () -> Unit,
    onOpenReport: () -> Unit,
    projectViewModel: ProjectViewModel,
    isHalfScreen: Boolean = false
) {
    val uiState by projectViewModel.uiState.collectAsState()
    val captures = uiState.positioningCaptures
    var showCapture by remember { mutableStateOf(false) }

    TutorialOverlay(
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
                onLaunchCamera = { /* TODO: Camera2 */ },
                onImagesChanged = { projectViewModel.setPositioningCaptures(it) },
                onConfirm = { showCapture = false },
                onBack = { showCapture = false },
                modifier = innerModifier
            )
        } else {
            Scaffold(
                modifier = innerModifier,
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.step1_title)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                }
            ) { padding ->
                // ← 修复：添加 verticalScroll 防止按钮被挤出屏幕
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
                    if (captures.isNotEmpty()) {
                        Text(
                            text = "${captures.size} frame(s) loaded — calibration computed. Open Report to view results.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.height(16.dp))

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
                    ) {
                        Text(stringResource(R.string.step1_start_capture))
                    }

                    if (captures.isNotEmpty()) {
                        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Text(stringResource(R.string.next_step))
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }

                    // 底部留白，防止最后一个按钮被系统导航栏遮挡
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}