package org.fungalsentinel.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.fungalsentinel.app.R
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    wavelengthR: String = "622.5",
    wavelengthG: String = "522.5",
    wavelengthB: String = "462.5",
    halfScreenEnabled: Boolean = false,
    coverRatio: Float = 0.5f,
    blankMode: String = "SINGLE",
    calibrationProtection: Boolean = true,
    onWavelengthChange: (String, String, String) -> Unit = { _, _, _ -> },
    onHalfScreenChange: (Boolean, Float) -> Unit = { _, _ -> },
    onBlankModeChange: (String) -> Unit = {},
    onCalibrationProtectionChange: (Boolean) -> Unit = {},
    onBack: () -> Unit
) {
    var wR by remember { mutableStateOf(wavelengthR) }
    var wG by remember { mutableStateOf(wavelengthG) }
    var wB by remember { mutableStateOf(wavelengthB) }
    var halfScreen by remember { mutableStateOf(halfScreenEnabled) }
    var ratio by remember { mutableFloatStateOf(coverRatio) }
    var blank by remember { mutableStateOf(blankMode) }
    var protection by remember { mutableStateOf(calibrationProtection) }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp.dp

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsSection(stringResource(R.string.settings_wavelength)) {
                WavelengthInput("R (nm)", wR) { wR = it; onWavelengthChange(wR, wG, wB) }
                WavelengthInput("G (nm)", wG) { wG = it; onWavelengthChange(wR, wG, wB) }
                WavelengthInput("B (nm)", wB) { wB = it; onWavelengthChange(wR, wG, wB) }
            }

            SettingsSection(stringResource(R.string.settings_spd)) {
                Text(
                    text = stringResource(R.string.settings_spd_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { /* TODO: import SPD CSV */ }) {
                    Text(stringResource(R.string.settings_import_spd))
                }
            }

            SettingsSection(stringResource(R.string.settings_half_screen)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.settings_half_screen_toggle),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = halfScreen, onCheckedChange = {
                        halfScreen = it
                        onHalfScreenChange(it, ratio)
                    })
                }

                if (halfScreen) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.settings_half_screen_preview),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.height(8.dp))

                    // 修改：预览区域高度变为屏幕高度的 60%，方便全屏调试
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(screenHeight * 0.6f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    ) {
                        val coverWidth = (screenWidth * ratio).roundToInt()

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width((screenWidth - coverWidth).dp)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "UI", fontWeight = FontWeight.Bold)
                        }

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(screenWidth - coverWidth, 0) }
                                .fillMaxHeight()
                                .width(coverWidth.dp)
                                .background(Color.Black.copy(alpha = 0.15f))
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val newCoverPx = coverWidth - dragAmount.x.roundToInt()
                                        ratio = (newCoverPx.toFloat() / screenWidth)
                                            .coerceIn(0.1f, 0.9f)
                                        onHalfScreenChange(halfScreen, ratio)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Covered\n${(ratio * 100).roundToInt()}%",
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            SettingsSection(stringResource(R.string.settings_blank_mode)) {
                Text(
                    text = stringResource(R.string.settings_blank_mode_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(
                        selected = blank == "SINGLE",
                        onClick = { blank = "SINGLE"; onBlankModeChange("SINGLE") },
                        label = { Text(stringResource(R.string.blank_mode_single)) }
                    )
                    FilterChip(
                        selected = blank == "FULL",
                        onClick = { blank = "FULL"; onBlankModeChange("FULL") },
                        label = { Text(stringResource(R.string.blank_mode_full)) }
                    )
                }
            }

            SettingsSection(stringResource(R.string.settings_calibration_protection)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.settings_calibration_protection_desc),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = protection, onCheckedChange = {
                        protection = it
                        onCalibrationProtectionChange(it)
                    })
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun WavelengthInput(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}