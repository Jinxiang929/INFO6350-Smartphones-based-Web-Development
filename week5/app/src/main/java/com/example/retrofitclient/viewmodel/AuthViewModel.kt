
package com.example.retrofitclient.viewmodel

import android.app.Activity
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.retrofitclient.model.GoogleUser
import com.example.retrofitclient.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState

    data class Authenticated(
        val user: GoogleUser
    ) : AuthUiState

    data object LoggingOut : AuthUiState

    data class Error(
        val message: String
    ) : AuthUiState
}

class AuthViewModel : ViewModel() {

    private val repository = AuthRepository()

    private val _uiState =
        MutableStateFlow<AuthUiState>(AuthUiState.Idle)

    val uiState: StateFlow<AuthUiState> =
        _uiState.asStateFlow()

    fun signInWithGoogle(activity: Activity) {
        if (
            _uiState.value == AuthUiState.Loading ||
            _uiState.value == AuthUiState.LoggingOut ||
            _uiState.value is AuthUiState.Authenticated
        ) {
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading

            try {
                // Step 1: Get Google ID Token
                val googleIdToken =
                    repository.signInWithGoogle(activity)

                // Step 2: Verify Google ID Token with Flask
                val response =
                    repository.authenticateWithBackend(googleIdToken)

                // Step 3: Update authentication state
                if (response.success) {
                    _uiState.value =
                        AuthUiState.Authenticated(response.user)
                } else {
                    _uiState.value =
                        AuthUiState.Error("Google sign-in failed")
                }

            } catch (e: GetCredentialCancellationException) {
                _uiState.value = AuthUiState.Idle

            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(
                    e.message ?: "Google sign-in failed"
                )
            }
        }
    }

    fun logout(activity: Activity) {
        if (_uiState.value !is AuthUiState.Authenticated) {
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.LoggingOut

            try {
                // Clear Credential Manager session
                repository.logout(activity)

            } catch (e: Exception) {
                // Local logout should still complete
            } finally {
                // Return to LoginScreen
                _uiState.value = AuthUiState.Idle
            }
        }
    }

    fun resetLoginState() {
        _uiState.value = AuthUiState.Idle
    }
}
