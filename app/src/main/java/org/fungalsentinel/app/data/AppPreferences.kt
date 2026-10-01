package org.fungalsentinel.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.fungalsentinel.app.data.model.Fluorophore

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_settings"
)

/**
 * DNG 保存策略（对应 README Settings）
 */
enum class DngPolicy {
    ALL,            // 所有 RAW 捕获都保存 DNG
    SAMPLES_ONLY,   // 仅未知 Sample 和标准 Sample
    NONE            // 不保存 DNG，仅内存分析
}

/**
 * 应用设置数据类
 */
data class AppSettings(
    // 波长参数（Step 2）
    val wavelengthR: String = "622.5",
    val wavelengthG: String = "522.5",
    val wavelengthB: String = "462.5",

    // 半屏模式
    val halfScreenEnabled: Boolean = false,
    val coverRatio: Float = 0.5f,

    // 空白样模式（Step 4）
    val blankMode: String = "SINGLE",  // SINGLE | FULL

    // 校准保护（Step 2 → Step 3）
    val calibrationProtection: Boolean = true,

    // DNG 策略
    val dngPolicy: String = DngPolicy.ALL.name,

    // 默认荧光物（Step 4）
    val defaultFluorophore: String = Fluorophore.EGFP.name,

    // 诊断日志
    val diagnosticLogEnabled: Boolean = true
)

object AppPreferences {
    // Keys
    private val KEY_WAVELENGTH_R = stringPreferencesKey("wavelength_r")
    private val KEY_WAVELENGTH_G = stringPreferencesKey("wavelength_g")
    private val KEY_WAVELENGTH_B = stringPreferencesKey("wavelength_b")
    private val KEY_HALF_SCREEN = booleanPreferencesKey("half_screen_enabled")
    private val KEY_COVER_RATIO = floatPreferencesKey("cover_ratio")
    private val KEY_BLANK_MODE = stringPreferencesKey("blank_mode")
    private val KEY_CALIBRATION_PROTECTION = booleanPreferencesKey("calibration_protection")
    private val KEY_DNG_POLICY = stringPreferencesKey("dng_policy")
    private val KEY_DEFAULT_FLUOROPHORE = stringPreferencesKey("default_fluorophore")
    private val KEY_DIAGNOSTIC_LOG = booleanPreferencesKey("diagnostic_log_enabled")

    fun getSettings(context: Context): Flow<AppSettings> =
        context.settingsDataStore.data.map { prefs ->
            AppSettings(
                wavelengthR = prefs[KEY_WAVELENGTH_R] ?: "622.5",
                wavelengthG = prefs[KEY_WAVELENGTH_G] ?: "522.5",
                wavelengthB = prefs[KEY_WAVELENGTH_B] ?: "462.5",
                halfScreenEnabled = prefs[KEY_HALF_SCREEN] ?: false,
                coverRatio = prefs[KEY_COVER_RATIO] ?: 0.5f,
                blankMode = prefs[KEY_BLANK_MODE] ?: "SINGLE",
                calibrationProtection = prefs[KEY_CALIBRATION_PROTECTION] ?: true,
                dngPolicy = prefs[KEY_DNG_POLICY] ?: DngPolicy.ALL.name,
                defaultFluorophore = prefs[KEY_DEFAULT_FLUOROPHORE] ?: Fluorophore.EGFP.name,
                diagnosticLogEnabled = prefs[KEY_DIAGNOSTIC_LOG] ?: true
            )
        }

    suspend fun updateWavelengths(context: Context, r: String, g: String, b: String) {
        context.settingsDataStore.edit {
            it[KEY_WAVELENGTH_R] = r
            it[KEY_WAVELENGTH_G] = g
            it[KEY_WAVELENGTH_B] = b
        }
    }

    suspend fun updateHalfScreen(context: Context, enabled: Boolean, ratio: Float) {
        context.settingsDataStore.edit {
            it[KEY_HALF_SCREEN] = enabled
            it[KEY_COVER_RATIO] = ratio
        }
    }

    suspend fun updateBlankMode(context: Context, mode: String) {
        context.settingsDataStore.edit {
            it[KEY_BLANK_MODE] = mode
        }
    }

    suspend fun updateCalibrationProtection(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit {
            it[KEY_CALIBRATION_PROTECTION] = enabled
        }
    }

    suspend fun updateDngPolicy(context: Context, policy: DngPolicy) {
        context.settingsDataStore.edit {
            it[KEY_DNG_POLICY] = policy.name
        }
    }

    suspend fun updateDefaultFluorophore(context: Context, fluorophore: Fluorophore) {
        context.settingsDataStore.edit {
            it[KEY_DEFAULT_FLUOROPHORE] = fluorophore.name
        }
    }

    suspend fun updateDiagnosticLog(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit {
            it[KEY_DIAGNOSTIC_LOG] = enabled
        }
    }
}