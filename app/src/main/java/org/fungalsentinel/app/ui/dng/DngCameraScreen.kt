package org.fungalsentinel.app.ui.dng

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.fungalsentinel.app.R
import org.fungalsentinel.app.camera.CameraManager
import org.fungalsentinel.app.camera.CameraPreviewView
import org.fungalsentinel.app.camera.RawCaptureProcessor
import org.fungalsentinel.app.ui.components.HelpTopBar
import org.fungalsentinel.app.ui.components.TutorialHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DngCameraScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val cameraManager = remember { CameraManager(context) }
    val captureProcessor = remember { RawCaptureProcessor(context) }
    val isReady by cameraManager.isReady.collectAsState()
    var lastSavedFile by remember { mutableStateOf<String?>(null) }

    // Resolve strings outside LaunchedEffect (Compose rule)
    val savedMessage = stringResource(R.string.dng_saved)
    val failedMessage = "Failed to save to gallery"

    val tutorial = TutorialHost(
        stepId = "dng_camera",
        titleRes = R.string.tutorial_dng_title,
        bodyRes = R.string.tutorial_dng_body
    )
    // Apply exposure settings (AE toggle / manual time) from DataStore
    LaunchedEffect(Unit) {
        org.fungalsentinel.app.data.AppPreferences.getSettings(context).collect { s ->
            cameraManager.updateParams(
                org.fungalsentinel.app.camera.CameraParams(
                    autoExposure = s.autoExposure,
                    exposureMs = s.exposureMs
                )
            )
        }
    }
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
        cameraManager.onError = { error ->
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose { cameraManager.close() }
    }

    Scaffold(
        topBar = {
            HelpTopBar(
                title = stringResource(R.string.dng_title),
                tutorial = tutorial,
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            CameraPreviewView(
                cameraManager = cameraManager,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = lastSavedFile ?: "No captures yet",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = { cameraManager.captureRaw() },
                    enabled = isReady
                ) {
                    Icon(Icons.Default.CameraAlt, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isReady) stringResource(R.string.dng_capture) else "Starting…")
                }
            }
        }
    }
}