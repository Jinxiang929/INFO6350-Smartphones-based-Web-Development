package com.example.week7speechtools.viewmodel

import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.week7speechtools.model.AuthUser
import com.example.week7speechtools.network.RetrofitClient
import com.example.week7speechtools.repository.AuthRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class AuthUiState(
    val isBusy: Boolean = false,
    val user: AuthUser? = null,
    val message: String = "Sign in to use the speech tools.",
    val isError: Boolean = false
)

class AuthViewModel(
    private val repository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())

    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var authJob: Job? = null

    fun beginGoogleSignIn(): Boolean {
        if (_uiState.value.isBusy || _uiState.value.user != null) {
            return false
        }

        repository.clearSession()

        _uiState.value = AuthUiState(
            isBusy = true,
            message = "Choose a Google account..."
        )

        return true
    }

    fun signInWithGoogleToken(idToken: String) {
        _uiState.value = _uiState.value.copy(
            isBusy = true,
            message = "Signing in...",
            isError = false
        )

        authJob = viewModelScope.launch {
            try {
                val user = repository.signInWithGoogle(idToken)

                _uiState.value = AuthUiState(
                    user = user,
                    message = "Signed in successfully."
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                repository.clearSession()

                val message = when (error) {
                    is HttpException -> {
                        if (error.code() == 401) {
                            "Google authentication failed. Please try again."
                        } else {
                            "Sign-in failed. The server returned HTTP ${error.code()}."
                        }
                    }

                    is IOException -> {
                        "Unable to connect. Check the backend and network connection."
                    }

                    else -> {
                        "Unable to process the sign-in response."
                    }
                }

                _uiState.value = AuthUiState(
                    message = message,
                    isError = true
                )
            }
        }
    }

    fun logout() {
        if (_uiState.value.isBusy || _uiState.value.user == null) {
            return
        }

        _uiState.value = _uiState.value.copy(
            isBusy = true,
            message = "Signing out...",
            isError = false
        )

        authJob = viewModelScope.launch {
            val message = repository.logout()

            _uiState.value = AuthUiState(
                message = message
            )
        }
    }

    fun onSessionExpired() {
        authJob?.cancel()
        repository.clearSession()

        _uiState.value = AuthUiState(
            message = "Your session expired. Please sign in again.",
            isError = true
        )
    }

    fun onGoogleSignInCancelled() {
        if (_uiState.value.user == null) {
            _uiState.value = AuthUiState(
                message = "Sign-in cancelled."
            )
        }
    }

    fun showSignInError(message: String) {
        _uiState.value = _uiState.value.copy(
            isBusy = false,
            message = message,
            isError = true
        )
    }

    companion object {

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY])

                AuthViewModel(
                    repository = AuthRepository(
                        apiService = RetrofitClient.apiService,
                        credentialManager = CredentialManager.create(
                            application
                        )
                    )
                )
            }
        }
    }
}