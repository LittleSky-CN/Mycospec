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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Half-screen layout.
 * Trigger rule: Settings toggle ON  AND  landscape  AND  light sensor covered.
 * Hysteresis: covered < 10 lux, uncovered > 30 lux (no flicker at threshold).
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
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var isCovered by remember { mutableStateOf(false) }

    val shouldListen = forceHalfScreen && isLandscape

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
                if (!isCovered && lux < 10f) isCovered = true
                else if (isCovered && lux > 30f) isCovered = false
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    val availableWidth by animateDpAsState(
        targetValue = if (isCovered) screenWidth * (1f - coverRatio) else screenWidth,
        label = "halfScreenWidth"
    )

    if (isCovered) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.width(availableWidth).fillMaxHeight()) {
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
        content(Modifier.fillMaxSize())
    }
}