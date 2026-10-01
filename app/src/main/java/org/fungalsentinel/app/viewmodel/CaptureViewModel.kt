package org.fungalsentinel.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CaptureUiState(
    val capturedImages: List<Uri> = emptyList(),
    val maxCaptures: Int = 5
)

class CaptureViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    fun addImage(uri: Uri) {
        val current = _uiState.value.capturedImages
        if (current.size < _uiState.value.maxCaptures) {
            _uiState.value = _uiState.value.copy(capturedImages = current + uri)
        }
    }

    fun removeImage(uri: Uri) {
        _uiState.value = _uiState.value.copy(
            capturedImages = _uiState.value.capturedImages - uri
        )
    }

    fun clear() {
        _uiState.value = _uiState.value.copy(capturedImages = emptyList())
    }
}