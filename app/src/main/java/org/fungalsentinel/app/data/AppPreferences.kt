package org.fungalsentinel.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val wavelengthR: String = "622.5",
    val wavelengthG: String = "522.5",
    val wavelengthB: String = "462.5",
    val halfScreenEnabled: Boolean = false,
    val coverRatio: Float = 0.5f,
    val blankMode: String = "SINGLE",
    val calibrationProtection: Boolean = true,
    val autoExposure: Boolean = true,
    val exposureMs: Long = 100L
)

object AppPreferences {
    private val KEY_R = stringPreferencesKey("wavelength_r")
    private val KEY_G = stringPreferencesKey("wavelength_g")
    private val KEY_B = stringPreferencesKey("wavelength_b")
    private val KEY_HALF = booleanPreferencesKey("half_screen")
    private val KEY_RATIO = floatPreferencesKey("cover_ratio")
    private val KEY_BLANK = stringPreferencesKey("blank_mode")
    private val KEY_PROTECT = booleanPreferencesKey("calibration_protection")
    private val KEY_AE = booleanPreferencesKey("auto_exposure")
    private val KEY_EXP_MS = longPreferencesKey("exposure_ms")

    fun getSettings(context: Context): Flow<AppSettings> =
        context.dataStore.data.map { p ->
            AppSettings(
                wavelengthR = p[KEY_R] ?: "622.5",
                wavelengthG = p[KEY_G] ?: "522.5",
                wavelengthB = p[KEY_B] ?: "462.5",
                halfScreenEnabled = p[KEY_HALF] ?: false,
                coverRatio = p[KEY_RATIO] ?: 0.5f,
                blankMode = p[KEY_BLANK] ?: "SINGLE",
                calibrationProtection = p[KEY_PROTECT] ?: true,
                autoExposure = p[KEY_AE] ?: true,
                exposureMs = p[KEY_EXP_MS] ?: 100L
            )
        }

    suspend fun saveSettings(context: Context, settings: AppSettings) {
        context.dataStore.edit { p ->
            p[KEY_R] = settings.wavelengthR
            p[KEY_G] = settings.wavelengthG
            p[KEY_B] = settings.wavelengthB
            p[KEY_HALF] = settings.halfScreenEnabled
            p[KEY_RATIO] = settings.coverRatio
            p[KEY_BLANK] = settings.blankMode
            p[KEY_PROTECT] = settings.calibrationProtection
            p[KEY_AE] = settings.autoExposure
            p[KEY_EXP_MS] = settings.exposureMs
        }
    }
}