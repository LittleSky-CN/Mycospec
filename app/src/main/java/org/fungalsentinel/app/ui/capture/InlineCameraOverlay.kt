package org.fungalsentinel.app.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
    val settings by AppPreferences.getSettings(context).collectAsState(initial = AppSettings())
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // 本地状态：打开时继承全局设置，拍摄中独立调整（不写回全局）
    var localAutoExposure by remember { mutableStateOf(settings.autoExposure) }
    var localExposureMs by remember { mutableStateOf(settings.exposureMs) }
    var localAutoFocus by remember { mutableStateOf(settings.autoFocus) }
    var localFocusDiopter by remember { mutableStateOf(settings.focusDiopter) }

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
    }

    // 全局设置变化时同步本地状态
    LaunchedEffect(settings) {
        localAutoExposure = settings.autoExposure
        localExposureMs = settings.exposureMs
        localAutoFocus = settings.autoFocus
        localFocusDiopter = settings.focusDiopter
    }

    // 本地状态变化时下发相机参数
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
            .border(2.dp, Color.Black, RoundedCornerShape(0.dp)) // 统一黑色边框
    ) {
        CameraPreviewView(cameraManager = cameraManager, modifier = Modifier.fillMaxSize())

        // 顶部：退出键 + 提示胶囊（避开状态栏）
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
                        text = if (isReady) "Align light & tap Shutter" else "Starting camera…",
                        color = AppColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // 快门：横屏在左，竖屏在下（与 DngCameraScreen 一致）
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
                enabled = isReady,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, disabledContainerColor = AppColors.SurfaceRaised),
                modifier = Modifier.size(76.dp).border(4.dp, AppColors.Accent, CircleShape)
            ) { }
        }

        // 底部控制面板（避开导航栏）
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
    }
}