package org.fungalsentinel.app.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.fungalsentinel.app.R

/**
 * Camera2 预览视图。
 *
 * 特性：
 * - 自动处理生命周期（onStart/onStop 重新打开/关闭相机）
 * - 支持 Surface 尺寸变化时重建预览
 * - 提供 Surface 给 CameraManager
 */
@Composable
fun CameraPreviewView(
    cameraManager: CameraManager,
    modifier: Modifier = Modifier,
    onSurfaceReady: (android.view.Surface) -> Unit = {},
    onPermissionChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var surface by remember { mutableStateOf<android.view.Surface?>(null) }
    var lifecycleStarted by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    val currentOnSurfaceReady by rememberUpdatedState(onSurfaceReady)
    val currentOnPermissionChanged by rememberUpdatedState(onPermissionChanged)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(hasPermission) {
        currentOnPermissionChanged(hasPermission)
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    lifecycleStarted = true
                    hasPermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) surface?.takeIf { it.isValid }?.let(cameraManager::openCamera)
                }
                Lifecycle.Event.ON_STOP -> {
                    lifecycleStarted = false
                    cameraManager.close()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cameraManager.close()
        }
    }

    if (hasPermission) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            surface = holder.surface
                            currentOnSurfaceReady(holder.surface)
                            if (lifecycleStarted && hasPermission) {
                                cameraManager.openCamera(holder.surface)
                            }
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder, format: Int, width: Int, height: Int
                        ) = Unit

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            if (surface === holder.surface) surface = null
                            cameraManager.close()
                        }
                    })
                }
            },
            update = {},
            modifier = modifier
        )
    } else {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Text(context.getString(R.string.camera_permission_required))
            Spacer(Modifier.height(8.dp))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text(context.getString(R.string.camera_permission_retry))
            }
            TextButton(onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) {
                Text(context.getString(R.string.camera_permission_settings))
            }
        }
    }
}