package com.example.feature.camera

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.image.DocumentDetector
import com.example.data.repository.DocumentRepository
import com.example.domain.model.DocumentCorners
import com.example.domain.model.FilterType
import com.example.domain.model.ScanMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CapturedPage(
    val bitmap: Bitmap,
    val detectedCorners: DocumentCorners?
)

data class CameraUiState(
    val scanMode: ScanMode = ScanMode.DOCUMENT,
    val isFlashOn: Boolean = false,
    val isAutoCaptureEnabled: Boolean = false,
    val detectedCorners: DocumentCorners? = null,
    val capturedPages: List<CapturedPage> = emptyList(),
    val isSaving: Boolean = false,
    val lastCapturedDocId: String? = null
)

class CameraViewModel(
    private val repository: DocumentRepository,
    initialScanMode: ScanMode = ScanMode.DOCUMENT
) : ViewModel() {

    private val detector = DocumentDetector()
    private val _uiState = MutableStateFlow(CameraUiState(scanMode = initialScanMode))
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    fun onScanModeChanged(mode: ScanMode) {
        _uiState.value = _uiState.value.copy(scanMode = mode)
    }

    fun toggleFlash() {
        _uiState.value = _uiState.value.copy(isFlashOn = !_uiState.value.isFlashOn)
    }

    fun toggleAutoCapture() {
        _uiState.value = _uiState.value.copy(isAutoCaptureEnabled = !_uiState.value.isAutoCaptureEnabled)
    }

    fun onAnalysisFrame(bitmap: Bitmap) {
        val corners = detector.detect(bitmap)
        _uiState.value = _uiState.value.copy(detectedCorners = corners)
    }

    fun onPageCaptured(bitmap: Bitmap, corners: DocumentCorners?) {
        val updated = _uiState.value.capturedPages + CapturedPage(bitmap, corners)
        _uiState.value = _uiState.value.copy(capturedPages = updated)
        detector.reset()
    }

    fun removeCapturedPage(index: Int) {
        if (index in _uiState.value.capturedPages.indices) {
            val updated = _uiState.value.capturedPages.toMutableList()
            updated.removeAt(index)
            _uiState.value = _uiState.value.copy(capturedPages = updated)
        }
    }

    fun saveDocument(
        title: String = "",
        filterType: FilterType = FilterType.AUTO_ENHANCE,
        onComplete: (String) -> Unit
    ) {
        val pages = _uiState.value.capturedPages
        if (pages.isEmpty()) return

        _uiState.value = _uiState.value.copy(isSaving = true)

        viewModelScope.launch {
            val pagePairs = pages.map { Pair(it.bitmap, it.detectedCorners) }
            val docId = repository.saveNewDocument(
                title = title.ifBlank { "KemSken_${System.currentTimeMillis()}" },
                capturedPages = pagePairs,
                filter = filterType
            )
            _uiState.value = _uiState.value.copy(isSaving = false, lastCapturedDocId = docId)
            onComplete(docId)
        }
    }

    class Factory(
        private val repository: DocumentRepository,
        private val initialScanMode: ScanMode = ScanMode.DOCUMENT
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CameraViewModel(repository, initialScanMode) as T
        }
    }
}
