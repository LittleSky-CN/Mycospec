package org.fungalsentinel.app.ui.components

import android.content.Context
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 半屏模式布局包装器。
 *
 * 逻辑规则：
 * 1. 设置开关关闭 (forceHalfScreen = false) -> 始终全屏。
 * 2. 设置开关开启 (forceHalfScreen = true)：
 *    - 竖屏状态 -> 强制全屏（确保初始界面等竖屏页面正常显示）。
 *    - 横屏状态 -> 监听光线传感器，当前置摄像头被遮光盖遮挡时，进入半屏模式。
 *
 * @param forceHalfScreen  设置中的半屏模式开关
 * @param coverRatio       遮挡比例 0.0–1.0（默认 0.5 = 右半侧）
 * @param content          实际页面内容
 */
@Composable
fun HalfScreenLayout(
    forceHalfScreen: Boolean = false,
    coverRatio: Float = 0.5f,
    content: @Composable (Modifier) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    // 判断当前是否为横屏
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var isCovered by remember { mutableStateOf(false) }

    // 核心条件：只有在 设置开启 且 处于横屏 时，才启用传感器监听
    val shouldListenSensor = forceHalfScreen && isLandscape

    DisposableEffect(shouldListenSensor) {
        if (!shouldListenSensor) {
            // 竖屏或设置关闭时，强制重置为全屏
            isCovered = false
            return@DisposableEffect onDispose { }
        }

        // 横屏且设置开启，注册光线传感器
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    // 阈值 5 lux：遮光盖遮挡时通常 < 2 lux
                    isCovered = it.values[0] < 5f
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (lightSensor != null) {
            sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            // 如果设备没有光线传感器，默认不遮挡
            isCovered = false
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    // 宽度动画过渡
    val availableWidth by animateDpAsState(
        targetValue = if (isCovered) screenWidth * (1f - coverRatio) else screenWidth,
        label = "halfScreenWidth"
    )

    // 根据状态渲染布局
    if (isCovered) {
        // 半屏模式：左侧 UI，右侧留白
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(availableWidth)
                    .fillMaxHeight()
            ) {
                content(Modifier.fillMaxSize())
            }
            Box(
                modifier = Modifier
                    .offset(x = availableWidth)
                    .width(screenWidth - availableWidth)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.background)
            )
        }
    } else {
        // 全屏模式：直接渲染内容
        content(Modifier.fillMaxSize())
    }
}