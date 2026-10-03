package org.fungalsentinel.app.ui.settings

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fungalsentinel.app.FssaApplication
import org.fungalsentinel.app.R
import org.fungalsentinel.app.analysis.SpdLoader
import org.fungalsentinel.app.ui.components.HelpTopBar
import org.fungalsentinel.app.ui.components.TutorialHost
import org.fungalsentinel.app.util.LanguageManager
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln

// Log-scale exposure slider helpers (1 ms .. 600 000 ms)
private const val EXP_MIN = 1.0
private const val EXP_MAX = 600_000.0

private fun expFromSlider(s: Float): Long =
    exp(ln(EXP_MIN) + s * (ln(EXP_MAX) - ln(EXP_MIN))).toLong().coerceAtLeast(1L)

private fun sliderFromExp(ms: Long): Float =
    ((ln(ms.toDouble().coerceIn(EXP_MIN, EXP_MAX)) - ln(EXP_MIN)) /
            (ln(EXP_MAX) - ln(EXP_MIN))).toFloat()

private fun formatExposure(ms: Long): String = when {
    ms < 1_000 -> "$ms ms"
    ms < 60_000 -> String.format(Locale.US, "%.1f s", ms / 1000.0)
    else -> String.format(Locale.US, "%.1f min", ms / 60_000.0)
}

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
    autoExposure: Boolean = true,
    exposureMs: Long = 100L,
    onWavelengthChange: (String, String, String) -> Unit = { _, _, _ -> },
    onHalfScreenChange: (Boolean, Float) -> Unit = { _, _ -> },
    onBlankModeChange: (String) -> Unit = {},
    onCalibrationProtectionChange: (Boolean) -> Unit = {},
    onAutoExposureChange: (Boolean) -> Unit = {},
    onExposureChange: (Long) -> Unit = {},
    onOpenPreview: () -> Unit = {},
    onBack: () -> Unit
) {
    var wR by remember { mutableStateOf(wavelengthR) }
    var wG by remember { mutableStateOf(wavelengthG) }
    var wB by remember { mutableStateOf(wavelengthB) }
    var halfScreen by remember { mutableStateOf(halfScreenEnabled) }
    var blank by remember { mutableStateOf(blankMode) }
    var protection by remember { mutableStateOf(calibrationProtection) }
    var ae by remember { mutableStateOf(autoExposure) }
    var expMs by remember { mutableStateOf(exposureMs) }
    var sliderPos by remember { mutableStateOf(sliderFromExp(exposureMs)) }

    val context = LocalContext.current
    val app = context.applicationContext as? FssaApplication
    val logSize by app?.logSize?.collectAsState(initial = 0L)
        ?: remember { mutableStateOf(0L) }

    var currentLang by remember {
        mutableStateOf(
            runCatching { LanguageManager.get(context) }
                .getOrDefault(LanguageManager.SYSTEM)
        )
    }
    val selectLanguage: (String) -> Unit = { code ->
        runCatching { LanguageManager.set(context, code) }
        currentLang = code
        (context as? Activity)?.recreate()
    }

    var spdSourceName by remember {
        mutableStateOf(
            if (SpdLoader.customFile(context).exists()) SpdLoader.CUSTOM_NAME
            else "true_spd.csv (bundled)"
        )
    }
    val spdImporter = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val err = SpdLoader.installCustom(context, it)
            if (err == null) {
                spdSourceName = SpdLoader.CUSTOM_NAME
                Toast.makeText(context, "SPD CSV installed", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            }
        }
    }

    val tutorial = TutorialHost(
        stepId = "settings",
        titleRes = R.string.tutorial_settings_title,
        bodyRes = R.string.tutorial_settings_body
    )

    Scaffold(
        topBar = {
            HelpTopBar(
                title = stringResource(R.string.settings_title),
                tutorial = tutorial,
                onBack = onBack
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
            SettingsSection(stringResource(R.string.settings_language)) {
                LanguageRadio(LanguageManager.SYSTEM, stringResource(R.string.language_system), currentLang, selectLanguage)
                LanguageRadio("en", stringResource(R.string.language_english), currentLang, selectLanguage)
                LanguageRadio("zh", stringResource(R.string.language_chinese), currentLang, selectLanguage)
                LanguageRadio("es", stringResource(R.string.language_spanish), currentLang, selectLanguage)
                LanguageRadio("ny", stringResource(R.string.language_chichewa), currentLang, selectLanguage)
            }

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
                Text(
                    text = "Current source: $spdSourceName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { spdImporter.launch("text/*") }) {
                    Text(stringResource(R.string.settings_import_spd))
                }
            }

            SettingsSection(stringResource(R.string.settings_exposure)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.settings_auto_exposure),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = ae, onCheckedChange = {
                        ae = it
                        onAutoExposureChange(it)
                    })
                }
                if (!ae) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.settings_exposure_time),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = formatExposure(expMs),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = sliderPos,
                        onValueChange = { s ->
                            sliderPos = s
                            val ms = expFromSlider(s)
                            expMs = ms
                            onExposureChange(ms)
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("1 ms", style = MaterialTheme.typography.bodySmall)
                        Text("1 s", style = MaterialTheme.typography.bodySmall)
                        Text("10 min", style = MaterialTheme.typography.bodySmall)
                    }
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
                        onHalfScreenChange(it, coverRatio)
                    })
                }
                if (halfScreen) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onOpenPreview,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_open_preview))
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

            app?.let { application ->
                SettingsSection("Diagnostic log / 诊断日志") {
                    Text(
                        text = "Rolling local log (2 MB max). Never uploaded automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { application.shareLog(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save diagnostic log (${logSize / 1024} KB)")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { application.clearLog() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear diagnostic log")
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}