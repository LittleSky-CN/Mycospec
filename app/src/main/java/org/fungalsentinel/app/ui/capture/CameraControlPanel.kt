package org.fungalsentinel.app.ui.capture

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fungalsentinel.app.R
import org.fungalsentinel.app.data.AppSettings
import org.fungalsentinel.app.ui.theme.AppColors
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln

private const val EXP_MIN = 1.0
private const val EXP_MAX = 600_000.0
private fun expFromSlider(s: Float): Long = exp(ln(EXP_MIN) + s * (ln(EXP_MAX) - ln(EXP_MIN))).toLong().coerceAtLeast(1L)
private fun sliderFromExp(ms: Long): Float =
    ((ln(ms.toDouble().coerceIn(EXP_MIN, EXP_MAX)) - ln(EXP_MIN)) / (ln(EXP_MAX) - ln(EXP_MIN))).toFloat()
private fun formatExposure(ms: Long): String = when {
    ms < 1_000 -> "$ms ms"
    ms < 60_000 -> String.format(Locale.US, "%.1f s", ms / 1000.0)
    else -> String.format(Locale.US, "%.1f min", ms / 60_000.0)
}
private fun formatFocus(d: Float): String = if (d <= 0.01f) "∞" else String.format(Locale.US, "%.2f m", 1.0 / d)

@Composable
fun CameraControlPanel(
    settings: AppSettings,
    onAutoExposureChange: (Boolean) -> Unit,
    onExposureChange: (Long) -> Unit,
    onAutoFocusChange: (Boolean) -> Unit,
    onFocusChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier.fillMaxWidth()
            .background(AppColors.Surface.copy(alpha = 0.92f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .border(1.dp, AppColors.Outline, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("AE", color = AppColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Switch(settings.autoExposure, onAutoExposureChange, colors = SwitchDefaults.colors(
                            checkedThumbColor = AppColors.OnAccent, checkedTrackColor = AppColors.Accent,
                            uncheckedThumbColor = AppColors.TextSecondary, uncheckedTrackColor = AppColors.SurfaceRaised))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("AF", color = AppColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Switch(settings.autoFocus, onAutoFocusChange, colors = SwitchDefaults.colors(
                            checkedThumbColor = AppColors.OnAccent, checkedTrackColor = AppColors.Accent,
                            uncheckedThumbColor = AppColors.TextSecondary, uncheckedTrackColor = AppColors.SurfaceRaised))
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, stringResource(R.string.camera_toggle_controls), tint = AppColors.TextPrimary)
                }
            }
            AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!settings.autoExposure) ExposureSlider(settings.exposureMs, onExposureChange)
                    if (!settings.autoFocus) FocusSlider(settings.focusDiopter, onFocusChange)
                }
            }
        }
    }
}

@Composable
private fun ExposureSlider(valueMs: Long, onValueChange: (Long) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.camera_label_exp), color = AppColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
            Text(formatExposure(valueMs), color = AppColors.TextPrimary, fontSize = 12.sp, modifier = Modifier.width(72.dp))
            Slider(sliderFromExp(valueMs), { onValueChange(expFromSlider(it)) }, valueRange = 0f..1f, modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = AppColors.Accent, activeTrackColor = AppColors.Accent, inactiveTrackColor = AppColors.Outline))
        }
        Row(Modifier.fillMaxWidth().padding(start = 108.dp, end = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1 ms", color = AppColors.TextSecondary, fontSize = 10.sp)
            Text("1 s", color = AppColors.TextSecondary, fontSize = 10.sp)
            Text("10 min", color = AppColors.TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun FocusSlider(valueDiopter: Float, onValueChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.camera_label_foc), color = AppColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
            Text(formatFocus(valueDiopter), color = AppColors.TextPrimary, fontSize = 12.sp, modifier = Modifier.width(72.dp))
            Slider(valueDiopter, onValueChange, valueRange = 0f..5f, modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = AppColors.Accent, activeTrackColor = AppColors.Accent, inactiveTrackColor = AppColors.Outline))
        }
        Row(Modifier.fillMaxWidth().padding(start = 108.dp, end = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("∞", color = AppColors.TextSecondary, fontSize = 10.sp)
            Text("0.2 m", color = AppColors.TextSecondary, fontSize = 10.sp)
        }
    }
}