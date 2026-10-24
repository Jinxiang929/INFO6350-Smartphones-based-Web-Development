
package com.example.retrofitclient.ui

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.retrofitclient.viewmodel.AuthUiState
import com.example.retrofitclient.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    viewModel: AuthViewModel
) {
    val context = LocalContext.current
    val activity = context as Activity

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "AI Chat",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Sign in to continue",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                viewModel.signInWithGoogle(activity)
            },
            enabled = uiState != AuthUiState.Loading &&
                    uiState != AuthUiState.LoggingOut
        ) {
            Text("Sign in with Google")
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (val state = uiState) {
            AuthUiState.Loading -> {
                CircularProgressIndicator()
            }

            is AuthUiState.Error -> {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            else -> Unit
        }
    }
}
