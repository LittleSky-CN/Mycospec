package org.fungalsentinel.app.ui.dng

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DngCameraScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val cameraManager = remember { CameraManager(context) }
    val captureProcessor = remember { RawCaptureProcessor(context) }
    var lastSavedFile by remember { mutableStateOf<String?>(null) }

    // ← 修复：将字符串资源提升到 LaunchedEffect 外部
    val savedMessage = stringResource(R.string.dng_saved)
    val failedMessage = "Failed to save to gallery"

    LaunchedEffect(Unit) {
        cameraManager.onDngSaved = { file, _ ->  // ← 修复：使用 _ 忽略未使用的 metadata
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
            TopAppBar(
                title = { Text(stringResource(R.string.dng_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
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
                Button(onClick = { cameraManager.captureRaw() }) {
                    Icon(Icons.Default.CameraAlt, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.dng_capture))
                }
            }
        }
    }
}