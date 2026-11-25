package com.example.qrcode.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.example.qrcode.viewmodel.QrUiState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun QrScreen(
    state: QrUiState,
    onInputChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onQrScanned: (String) -> Unit,
    onImageSelected: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    var cameraSession by remember { mutableIntStateOf(0) }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(onImageSelected)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("QR Code", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = state.input,
            onValueChange = { value ->
                if (value != state.input) {
                    cameraSession += 1
                    onInputChange(value)
                }
            },
            label = { Text("Text or URL") },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = onGenerate,
            enabled = state.input.isNotBlank() && !state.isLoading
        ) {
            Text(if (state.isLoading) "Generating…" else "Generate")
        }

        Button(
            onClick = { imagePicker.launch(arrayOf("image/*")) },
            enabled = !state.isDecodingImage
        ) {
            Text(if (state.isDecodingImage) "Decoding…" else "Choose Image")
        }

        state.imageScanError?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }

        if (state.imageScanTexts.isNotEmpty()) {
            Text("Image scan result", style = MaterialTheme.typography.titleMedium)
            state.imageScanTexts.forEach { text ->
                Text(text)
            }
        }

        val session = cameraSession
        key(session) {
            QrCameraSection(
                scannedText = state.scannedText,
                onQrScanned = { text ->
                    if (session == cameraSession) {
                        onQrScanned(text)
                    }
                }
            )
        }

        state.error?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }

        state.image?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Generated QR code",
                modifier = Modifier
                    .size(250.dp)
                    .align(Alignment.CenterHorizontally)
            )
        }
    }
}