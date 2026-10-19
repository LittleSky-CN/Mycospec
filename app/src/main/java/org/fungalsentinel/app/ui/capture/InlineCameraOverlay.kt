package org.fungalsentinel.app.ui.capture

import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.fungalsentinel.app.data.AppPreferences
import org.fungalsentinel.app.data.AppSettings
import org.fungalsentinel.app.ui.theme.AppColors

@Composable
fun InlineCameraOverlay(
    onCaptured: (Uri) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val cameraManager = remember { CameraManager(context) }
    val isReady by cameraManager.isReady.collectAsState()
    val isCapturing by cameraManager.isCapturing.collectAsState()
    var hasCameraPermission by remember { mutableStateOf(cameraManager.hasCameraPermission()) }
    val settings by AppPreferences.getSettings(context).collectAsState(initial = AppSettings())
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // local state: inherits the global settings when opened, adjusted independently during capture (never written back globally)
    var localAutoExposure by remember { mutableStateOf(settings.autoExposure) }
    var localExposureMs by remember { mutableStateOf(settings.exposureMs) }
    var localAutoFocus by remember { mutableStateOf(settings.autoFocus) }
    var localFocusDiopter by remember { mutableStateOf(settings.focusDiopter) }

    LaunchedEffect(Unit) {
        cameraManager.onDngSaved = { file, _ -> onCaptured(Uri.fromFile(file)) }
        cameraManager.onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
        cameraManager.onSaturationWarning = {
            Toast.makeText(context, R.string.warn_saturation, Toast.LENGTH_LONG).show()
        }
    }

    // sync the local state when the global settings change
    LaunchedEffect(settings) {
        localAutoExposure = settings.autoExposure
        localExposureMs = settings.exposureMs
        localAutoFocus = settings.autoFocus
        localFocusDiopter = settings.focusDiopter
    }

    // push camera parameters down when the local state changes
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .border(2.dp, Color.Black, RoundedCornerShape(0.dp)) // uniform black border
    ) {
        CameraPreviewView(
            cameraManager = cameraManager,
            modifier = Modifier.fillMaxSize(),
            onPermissionChanged = { hasCameraPermission = it }
        )

        if (hasCameraPermission) {
        // top: exit key + hint pill (keeps clear of the status bar)
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
                        .background(MaterialTheme.colorScheme.secondary, CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp)
                    .background(AppColors.Surface, RoundedCornerShape(50.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isReady) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = AppColors.Accent, strokeWidth = 2.dp)
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

        // shutter: left in landscape, bottom in portrait (same as DngCameraScreen)
        val shutterModifier = if (isLandscape) {
            Modifier.align(Alignment.CenterStart).padding(start = 24.dp)
        } else {
            Modifier.align(Alignment.BottomCenter).padding(bottom = 150.dp)
        }
        Box(modifier = shutterModifier.size(130.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.radialGradient(0f to AppColors.Accent.copy(alpha = 0.4f), 1f to Color.Transparent))
            )
            Button(
                onClick = { cameraManager.captureRaw() },
                enabled = isReady && !isCapturing,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, disabledContainerColor = AppColors.SurfaceRaised),
                modifier = Modifier.size(76.dp).border(4.dp, AppColors.Accent, CircleShape)
            ) { }
        }

        // bottom control panel (keeps clear of the navigation bar)
        Box(modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
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
                onFocusChange = { localFocusDiopter = it }
            )
        }
        } else {
            Box(
                modifier = Modifier.align(Alignment.TopStart).padding(top = 36.dp, start = 12.dp)
                    .size(46.dp).background(MaterialTheme.colorScheme.secondary, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Back", tint = Color.White)
            }
        }
    }
}