package org.fungalsentinel.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.fungalsentinel.app.data.AppSettings
import org.fungalsentinel.app.data.AppPreferences

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            AppPreferences.getSettings(application).collect { _settings.value = it }
        }
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        val new = transform(_settings.value)
        _settings.value = new
        viewModelScope.launch { AppPreferences.saveSettings(getApplication(), new) }
    }

    fun updateWavelengths(r: String, g: String, b: String) = update { it.copy(wavelengthR = r, wavelengthG = g, wavelengthB = b) }
    fun updateHalfScreen(enabled: Boolean, ratio: Float) = update { it.copy(halfScreenEnabled = enabled, coverRatio = ratio) }
    fun updateBlankMode(mode: String) = update { it.copy(blankMode = mode) }
    fun updateCalibrationProtection(enabled: Boolean) = update { it.copy(calibrationProtection = enabled) }
    fun updateAutoExposure(enabled: Boolean) = update { it.copy(autoExposure = enabled) }
    fun updateExposureMs(ms: Long) = update { it.copy(exposureMs = ms) }
}