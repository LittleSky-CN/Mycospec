package org.fungalsentinel.app.ui.settings

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fungalsentinel.app.R
import kotlin.math.roundToInt

/**
 * Dedicated half-screen calibration page.
 * - Forces landscape so users can mount the phone on the real rig.
 * - Live light-sensor readout to verify the cover trigger with hardware.
 * - Bottom slider moves the split boundary (replaces the old drag box).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HalfScreenPreviewScreen(
    coverRatio: Float,
    onRatioChange: (Float) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Force landscape while this page is alive
    DisposableEffect(Unit) {
        val previous = activity?.requestedOrientation
            ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose { activity?.requestedOrientation = previous }
    }

    // Live lux readout (same hysteresis as HalfScreenLayout)
    var lux by remember { mutableFloatStateOf(-1f) }
    var covered by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val ls = sm.getDefaultSensor(Sensor.TYPE_LIGHT)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent?) {
                val v = e?.values?.get(0) ?: return
                lux = v
                if (!covered && v < 10f) covered = true
                else if (covered && v > 30f) covered = false
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (ls != null) sm.registerListener(listener, ls, SensorManager.SENSOR_DELAY_UI)
        onDispose { if (ls != null) sm.unregisterListener(listener) }
    }

    var ratio by remember { mutableFloatStateOf(coverRatio) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.half_preview_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            // ── Live split preview ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Left: mock UI area (represents the real app UI)
                Box(
                    modifier = Modifier
                        .weight((1f - ratio).coerceAtLeast(0.05f))
                        .fillMaxHeight()
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "UI",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(10.dp)
                                    )
                            )
                        }
                        Text(
                            stringResource(R.string.half_preview_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Boundary line
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
                // Right: covered area
                Box(
                    modifier = Modifier
                        .weight(ratio.coerceAtLeast(0.05f))
                        .fillMaxHeight()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Covered\n${(ratio * 100).roundToInt()}%",
                        color = Color.Gray,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            // ── Bottom control bar: sensor state + slider ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (lux < 0f) "Light: n/a"
                        else "Light: %.0f lux — %s".format(
                            lux,
                            if (covered) stringResource(R.string.half_preview_covered)
                            else stringResource(R.string.half_preview_uncovered)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (covered) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(ratio * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = ratio,
                    onValueChange = {
                        ratio = it
                        onRatioChange(it)
                    },
                    valueRange = 0.1f..0.9f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}