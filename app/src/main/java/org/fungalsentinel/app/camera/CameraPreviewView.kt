package org.fungalsentinel.app.camera

import android.content.res.Configuration
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

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
    onSurfaceReady: (android.view.Surface) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            SurfaceView(ctx).apply {
                // Surface 就绪后打开相机
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        onSurfaceReady(holder.surface)
                        cameraManager.openCamera(holder.surface)
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder, format: Int, width: Int, height: Int
                    ) {
                        // 尺寸变化时重建预览（CameraManager 内部处理）
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        cameraManager.close()
                    }
                })
            }
        },
        update = { /* 不需要更新 */ },
        modifier = modifier.onSizeChanged { _: IntSize ->
            // 尺寸变化时可触发 CameraManager 重新配置
        }
    )

    // 生命周期感知：App 进入后台时关闭相机，回到前台时重新打开
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> cameraManager.close()
                Lifecycle.Event.ON_START -> {
                    // 重新打开需要 Surface，此处仅做状态重置
                    // 真正的重新打开由 SurfaceView 的 surfaceCreated 触发
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cameraManager.close()
        }
    }
}