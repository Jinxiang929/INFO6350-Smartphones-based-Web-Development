
package com.example.retrofitclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.retrofitclient.ui.LoginScreen
import com.example.retrofitclient.ui.theme.RetrofitClientTheme
import com.example.retrofitclient.viewmodel.AuthUiState
import com.example.retrofitclient.viewmodel.AuthViewModel
import com.example.retrofitclient.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RetrofitClientTheme {
                val authState by authViewModel.uiState
                    .collectAsStateWithLifecycle()

                when (val state = authState) {

                    is AuthUiState.Authenticated -> {
                        key(state.user.id) {
                            ChatScreen(
                                viewModel = chatViewModel,
                                onLogout = {
                                    authViewModel.logout(this@MainActivity)
                                }
                            )
                        }
                    }

                    AuthUiState.LoggingOut -> {
                        Text("Logging out...")
                    }

                    else -> {
                        LoginScreen(
                            viewModel = authViewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onLogout: () -> Unit
) {
    var prompt by remember { mutableStateOf("") }

    val responseText by viewModel.responseText
        .collectAsStateWithLifecycle()

    val isLoading by viewModel.isLoading
        .collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Retrofit Client",
                style = MaterialTheme.typography.headlineMedium
            )

            OutlinedButton(onClick = onLogout) {
                Text("Logout")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Enter your message") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (prompt.isNotBlank()) {
                    viewModel.sendMessage(prompt)
                    prompt = ""
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Sending..." else "Send")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Response:",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = responseText)
    }
}
