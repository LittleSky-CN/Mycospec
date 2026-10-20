package org.fungalsentinel.app.ui.dng

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.fungalsentinel.app.R
import org.fungalsentinel.app.camera.CameraManager
import org.fungalsentinel.app.camera.CameraParams
import org.fungalsentinel.app.camera.CameraPreviewView
import org.fungalsentinel.app.camera.RawCaptureProcessor
import org.fungalsentinel.app.data.AppPreferences
import org.fungalsentinel.app.data.AppSettings
import org.fungalsentinel.app.ui.capture.CameraControlPanel
import org.fungalsentinel.app.ui.components.HalfScreenLayout
import org.fungalsentinel.app.ui.components.TutorialButton
import org.fungalsentinel.app.ui.components.TutorialHost
import org.fungalsentinel.app.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DngCameraScreen(
    onBack: () -> Unit,
    isHalfScreen: Boolean = false,
    coverRatio: Float = 0.5f
) {
    val context = LocalContext.current
    val cameraManager = remember { CameraManager(context) }
    val captureProcessor = remember { RawCaptureProcessor(context) }
    val isReady by cameraManager.isReady.collectAsState()
    val isCapturing by cameraManager.isCapturing.collectAsState()
    var hasCameraPermission by remember { mutableStateOf(cameraManager.hasCameraPermission()) }
    val settings by AppPreferences.getSettings(context).collectAsState(initial = AppSettings())
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val tutorial = TutorialHost(
        stepId = "dng_camera",
        titleRes = R.string.tutorial_dng_title,
        bodyRes = R.string.tutorial_dng_body
    )

    var localAutoExposure by remember { mutableStateOf(settings.autoExposure) }
    var localExposureMs by remember { mutableStateOf(settings.exposureMs) }
    var localAutoFocus by remember { mutableStateOf(settings.autoFocus) }
    var localFocusDiopter by remember { mutableStateOf(settings.focusDiopter) }
    var lastSavedFile by remember { mutableStateOf<String?>(null) }

    val savedMessage = stringResource(R.string.dng_saved)
    val failedMessage = stringResource(R.string.dng_save_failed)

    LaunchedEffect(Unit) {
        cameraManager.onDngSaved = { file, _ ->
            val uri = captureProcessor.saveToGallery(file, "DNG_Camera", "RAW")
            if (uri != null) {
                lastSavedFile = file.name
                Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, failedMessage, Toast.LENGTH_SHORT).show()
            }
        }
        cameraManager.onError = { error -> Toast.makeText(context, error, Toast.LENGTH_SHORT).show() }
        cameraManager.onSaturationWarning = {
            Toast.makeText(context, R.string.warn_saturation, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(settings) {
        localAutoExposure = settings.autoExposure
        localExposureMs = settings.exposureMs
        localAutoFocus = settings.autoFocus
        localFocusDiopter = settings.focusDiopter
    }

    LaunchedEffect(localAutoExposure, localExposureMs, localAutoFocus, localFocusDiopter) {
        cameraManager.updateParams(
            CameraParams(
                autoExposure = localAutoExposure,
                exposureMs = localExposureMs,
                autoFocus = localAutoFocus,
                focusDiopter = localFocusDiopter
            )
        )
    }
    DisposableEffect(Unit) { onDispose { cameraManager.close() } }

    HalfScreenLayout(forceHalfScreen = isHalfScreen, coverRatio = coverRatio) { screenModifier ->
      Box(modifier = screenModifier.fillMaxSize().background(Color.Black).border(2.dp, Color.Black, RoundedCornerShape(0.dp))) {
        CameraPreviewView(
            cameraManager = cameraManager,
            modifier = Modifier.fillMaxSize(),
            onPermissionChanged = { hasCameraPermission = it }
        )

        if (hasCameraPermission) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .padding(start = 14.dp)
                            .size(46.dp)
                            .background(AppColors.Surface, CircleShape)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.back), tint = AppColors.TextPrimary)
                    }
                    TutorialButton(
                        onClick = tutorial::show,
                        modifier = Modifier.padding(end = 14.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp)
                        .background(AppColors.Surface, RoundedCornerShape(50.dp))
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isReady) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = AppColors.Accent,
                                strokeWidth = 2.dp
                            )
                        }
                        Text(
                            text = stringResource(
                                when {
                                    isCapturing -> R.string.camera_capturing
                                    isReady -> R.string.camera_hint_align
                                    else -> R.string.camera_starting
                                }
                            ),
                            color = AppColors.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            val shutterModifier = if (isLandscape) {
                Modifier.align(Alignment.CenterStart).padding(start = 24.dp)
            } else {
                Modifier.align(Alignment.BottomCenter).padding(bottom = 150.dp)
            }
            Box(modifier = shutterModifier.size(130.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                0f to AppColors.Accent.copy(alpha = 0.4f),
                                1f to Color.Transparent
                            )
                        )
                )
                Button(
                    onClick = { cameraManager.captureRaw() },
                    enabled = isReady && !isCapturing,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Accent.copy(alpha = 0.8f),
                        disabledContainerColor = AppColors.SurfaceRaised
                    ),
                    modifier = Modifier.size(76.dp).border(4.dp, AppColors.Accent, CircleShape)
                ) { }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            ) {
                CameraControlPanel(
                    settings = AppSettings(
                        autoExposure = localAutoExposure,
                        exposureMs = localExposureMs,
                        autoFocus = localAutoFocus,
                        focusDiopter = localFocusDiopter
                    ),
                    onAutoExposureChange = { localAutoExposure = it },
                    onExposureChange = { localExposureMs = it },
                    onAutoFocusChange = { localAutoFocus = it },
                    onFocusChange = { localFocusDiopter = it },
                    modifier = Modifier
                        .heightIn(max = maxHeight * 0.55f)
                        .verticalScroll(rememberScrollState())
                )
            }

            if (lastSavedFile != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 130.dp)
                        .background(AppColors.Surface.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dng_last_saved, lastSavedFile ?: ""),
                        color = AppColors.Accent,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(AppColors.Surface, CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.back), tint = AppColors.TextPrimary)
                }
                TutorialButton(onClick = tutorial::show)
            }
        }
    }
}
}