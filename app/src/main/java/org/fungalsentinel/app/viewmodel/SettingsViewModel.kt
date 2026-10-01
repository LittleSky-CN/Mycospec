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
            AppPreferences.getSettings(application).collect {
                _settings.value = it
            }
        }
    }

    fun updateWavelengths(r: String, g: String, b: String) {
        viewModelScope.launch {
            AppPreferences.updateWavelengths(getApplication(), r, g, b)
        }
    }

    fun updateHalfScreen(enabled: Boolean, ratio: Float) {
        viewModelScope.launch {
            AppPreferences.updateHalfScreen(getApplication(), enabled, ratio)
        }
    }

    fun updateBlankMode(mode: String) {
        viewModelScope.launch {
            AppPreferences.updateBlankMode(getApplication(), mode)
        }
    }

    fun updateCalibrationProtection(enabled: Boolean) {
        viewModelScope.launch {
            AppPreferences.updateCalibrationProtection(getApplication(), enabled)
        }
    }
}