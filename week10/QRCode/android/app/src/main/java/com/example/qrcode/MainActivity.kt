package com.example.qrcode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.qrcode.data.QrRepository
import com.example.qrcode.ui.QrScreen
import com.example.qrcode.ui.theme.QRCodeTheme
import com.example.qrcode.viewmodel.QrViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val qrViewModel = viewModel {
                QrViewModel(QrRepository(application.contentResolver))
            }

            QRCodeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    QrScreen(
                        state = qrViewModel.uiState,
                        onInputChange = qrViewModel::onInputChange,
                        onGenerate = qrViewModel::onGenerate,
                        onQrScanned = qrViewModel::onQrScanned,
                        onImageSelected = qrViewModel::onImageSelected,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}