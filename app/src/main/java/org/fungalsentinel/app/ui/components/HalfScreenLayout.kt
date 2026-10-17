package org.fungalsentinel.app.ui.components

import android.content.Context
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Half-screen layout.
 * Trigger rule: Settings toggle ON AND landscape AND light sensor covered.
 * Hysteresis: covered < 10 lux, uncovered > 30 lux (no flicker at threshold).
 * 【v2.2.3】Added neon border to prevent edge-touch issues.
 */
@Composable
fun HalfScreenLayout(
    forceHalfScreen: Boolean = false,
    coverRatio: Float = 0.5f,
    content: @Composable (Modifier) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var isCovered by remember { mutableStateOf(false) }
    val currentCovered by rememberUpdatedState(isCovered)
    val shouldListen = forceHalfScreen && isLandscape
    val safeCoverRatio = coverRatio.coerceIn(0f, 0.95f)

    DisposableEffect(shouldListen) {
        if (!shouldListen) {
            isCovered = false
            return@DisposableEffect onDispose { }
        }
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (lightSensor == null) {
            isCovered = false
            return@DisposableEffect onDispose { }
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val lux = event?.values?.get(0) ?: return
                if (!currentCovered && lux < 10f) isCovered = true
                else if (currentCovered && lux > 30f) isCovered = false
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    val borderModifier = Modifier
        .fillMaxSize()
        .padding(4.dp)

    BoxWithConstraints(modifier = borderModifier) {
        val containerWidth = maxWidth
        val availableWidth by animateDpAsState(
            targetValue = if (isCovered) containerWidth * (1f - safeCoverRatio) else containerWidth,
            label = "halfScreenWidth"
        )

        if (isCovered) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.width(availableWidth).fillMaxHeight()) {
                    content(Modifier.fillMaxSize())
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(containerWidth - availableWidth)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.background)
                )
            }
        } else {
            content(Modifier.fillMaxSize())
        }
    }
}