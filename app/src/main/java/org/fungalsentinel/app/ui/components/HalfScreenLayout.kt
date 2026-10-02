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

@Composable
fun HalfScreenLayout(forceHalfScreen: Boolean = false, coverRatio: Float = 0.5f, content: @Composable (Modifier) -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var isCovered by remember { mutableStateOf(false) }

    DisposableEffect(forceHalfScreen, isLandscape) {
        if (forceHalfScreen) { isCovered = true; return@DisposableEffect onDispose { } }
        if (!isLandscape) { isCovered = false; return@DisposableEffect onDispose { } }
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) { event?.let { isCovered = it.values[0] < 5f } }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (lightSensor != null) sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    val availableWidth by animateDpAsState(targetValue = if (isCovered) screenWidth * (1f - coverRatio) else screenWidth, label = "halfScreenWidth")
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.width(availableWidth).fillMaxHeight()) { content(Modifier.fillMaxSize()) }
        if (isCovered) Box(modifier = Modifier.offset(x = availableWidth).width(screenWidth - availableWidth).fillMaxHeight().background(MaterialTheme.colorScheme.background))
    }
}