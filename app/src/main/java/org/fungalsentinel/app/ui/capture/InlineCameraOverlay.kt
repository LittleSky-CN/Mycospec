package org.fungalsentinel.app.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.fungalsentinel.app.camera.CameraManager
import org.fungalsentinel.app.camera.CameraPreviewView

/**
 * Full-screen inline RAW camera — mockup 2 layout:
 * flip icon (top-left, rear-only notice), dark-green hint pill (top-center),
 * glowing circular shutter (right-center), green down-arrow exit (bottom-right).
 */
@Composable
fun InlineCameraOverlay(
    onCaptured: (Uri) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val cameraManager = remember { CameraManager(context) }
    val isReady by cameraManager.isReady.collectAsState()

    val rearOnlyMessage = "RAW capture uses the rear camera only"

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        cameraManager.onDngSaved = { file, _ -> onCaptured(Uri.fromFile(file)) }
        cameraManager.onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
        org.fungalsentinel.app.data.AppPreferences.getSettings(context).collect { s ->
            cameraManager.updateParams(
                org.fungalsentinel.app.camera.CameraParams(
                    autoExposure = s.autoExposure,
                    exposureMs = s.exposureMs
                )
            )
        }
    }
    DisposableEffect(Unit) { onDispose { cameraManager.close() } }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreviewView(cameraManager = cameraManager, modifier = Modifier.fillMaxSize())

        // top-left: flip icon (rear-only notice)
        Icon(
            Icons.Default.FlipCameraAndroid,
            contentDescription = rearOnlyMessage,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
                .size(38.dp)
                .clickable { Toast.makeText(context, rearOnlyMessage, Toast.LENGTH_SHORT).show() }
        )

        // top-center: dark-green hint pill
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
                .background(Color(0xFF122B12), androidx.compose.foundation.shape.RoundedCornerShape(50.dp))
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isReady) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = Color(0xFF5CFF5C),
                        strokeWidth = 2.dp
                    )
                }
                Text(
                    text = if (isReady) "Align the light source, then tap Capture" else "Starting camera…",
                    color = Color(0xFFD9F7D0),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // right-center: glowing circular shutter
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 22.dp)
                .size(130.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            0f to MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f),
                            1f to Color.Transparent
                        )
                    )
            )
            Button(
                onClick = { cameraManager.captureRaw() },
                enabled = isReady,
                shape = androidx.compose.foundation.shape.CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFC9F5C9),
                    disabledContainerColor = Color(0xFF39493A)
                ),
                modifier = Modifier
                    .size(76.dp)
                    .border(4.dp, MaterialTheme.colorScheme.secondary, androidx.compose.foundation.shape.CircleShape)
            ) { }
        }

        // bottom-right: green down-arrow exit
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(46.dp)
                .background(MaterialTheme.colorScheme.secondary, androidx.compose.foundation.shape.CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.ArrowDownward, contentDescription = "Back", tint = Color.White)
        }
    }
}