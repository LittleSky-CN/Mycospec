package org.fungalsentinel.app.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.fungalsentinel.app.camera.CameraManager
import org.fungalsentinel.app.camera.CameraPreviewView

/** Full-screen inline camera; shutter gated on session readiness (fixes first-tap dead). */
@Composable
fun InlineCameraOverlay(onCaptured: (Uri) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val cameraManager = remember { CameraManager(context) }
    val isReady by cameraManager.isReady.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) { Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show(); onBack() }
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        cameraManager.onDngSaved = { file, _ -> onCaptured(Uri.fromFile(file)) }
        cameraManager.onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
    }
    DisposableEffect(Unit) { onDispose { cameraManager.close() } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreviewView(cameraManager = cameraManager, modifier = Modifier.fillMaxSize())
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isReady) {
                CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                Text("Starting camera…", color = Color.White)
            } else Text("Align the light source, then tap Capture", color = Color.White)
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Button(onClick = { cameraManager.captureRaw() }, enabled = isReady) {
                Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(8.dp))
                Text(if (isReady) "Capture" else "Starting…")
            }
            Spacer(Modifier.width(48.dp))
        }
    }
}