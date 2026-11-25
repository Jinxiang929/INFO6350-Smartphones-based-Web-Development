package com.example.qrcode.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qrcode.data.QrRepository
import kotlinx.coroutines.launch
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive

data class QrUiState(
    val input: String = "",
    val image: Bitmap? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val scannedText: String? = null,
    val isDecodingImage: Boolean = false,
    val imageScanTexts: List<String> = emptyList(),
    val imageScanError: String? = null
)

class QrViewModel(private val repository: QrRepository) : ViewModel() {
    private var imageDecodeJob: Job? = null
    var uiState by mutableStateOf(QrUiState())
        private set

    fun onInputChange(value: String) {
        if (value == uiState.input || uiState.isLoading) return

        imageDecodeJob?.cancel()
        uiState = QrUiState(input = value)
    }

    fun onQrScanned(text: String) {
        if (uiState.scannedText != text) {
            uiState = uiState.copy(scannedText = text)
        }
    }

    fun onGenerate() {
        val text = uiState.input
        if (text.isBlank() || uiState.isLoading) return

        uiState = uiState.copy(isLoading = true, image = null, error = null)

        viewModelScope.launch {
            try {
                val image = repository.generateQr(text)
                uiState = uiState.copy(image = image)
            } catch (error: IOException) {
                uiState = uiState.copy(error = error.message ?: "Could not generate QR code.")
            } finally {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }

    fun onImageSelected(imageUri: Uri) {
        if (uiState.isDecodingImage) return

        uiState = uiState.copy(
            isDecodingImage = true,
            imageScanTexts = emptyList(),
            imageScanError = null
        )

        imageDecodeJob = viewModelScope.launch {
            val nextState = try {
                val texts = repository.decodeQr(imageUri)
                uiState.copy(imageScanTexts = texts)
            } catch (error: IOException) {
                uiState.copy(
                    imageScanError = error.message ?: "Could not decode the selected image."
                )
            }

            ensureActive()
            uiState = nextState.copy(isDecodingImage = false)
        }
    }
}