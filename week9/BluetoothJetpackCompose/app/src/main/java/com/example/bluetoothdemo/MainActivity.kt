package com.example.bluetoothdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.bluetoothdemo.data.BluetoothRepository
import com.example.bluetoothdemo.ui.BluetoothScreen
import com.example.bluetoothdemo.viewmodel.BluetoothViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val factory = viewModelFactory {
            initializer {
                BluetoothViewModel(BluetoothRepository(applicationContext))
            }
        }
        val viewModel = ViewModelProvider(this, factory)[BluetoothViewModel::class.java]

        setContent {
            MaterialTheme {
                BluetoothScreen(viewModel)
            }
        }
    }
}