package com.example.week7speechtools.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.week7speechtools.network.RetrofitClient
import com.example.week7speechtools.repository.HealthRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class HealthUiState(
    val isLoading: Boolean = false,
    val message: String = "Tap Check Backend to test the connection.",
    val isError: Boolean = false
)

class HealthViewModel(
    private val repository: HealthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthUiState())

    val uiState: StateFlow<HealthUiState> = _uiState.asStateFlow()

    fun checkHealth() {
        if (_uiState.value.isLoading) {
            return
        }

        _uiState.value = HealthUiState(
            isLoading = true,
            message = "Connecting to the backend..."
        )

        viewModelScope.launch {
            try {
                val response = repository.checkHealth()

                _uiState.value = if (response.success) {
                    HealthUiState(
                        message = response.message.ifBlank {
                            "Backend connection successful."
                        }
                    )
                } else {
                    HealthUiState(
                        message = response.message.ifBlank {
                            "The backend reported an unsuccessful result."
                        },
                        isError = true
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: HttpException) {
                _uiState.value = HealthUiState(
                    message = "The backend returned HTTP ${error.code()}.",
                    isError = true
                )
            } catch (error: IOException) {
                _uiState.value = HealthUiState(
                    message = (
                            "Unable to connect. Check that Flask is running " +
                                    "and local network access is allowed."
                            ),
                    isError = true
                )
            } catch (error: Exception) {
                _uiState.value = HealthUiState(
                    message = "Unable to process the backend response.",
                    isError = true
                )
            }
        }
    }

    fun onLocalNetworkPermissionDenied() {
        _uiState.value = HealthUiState(
            message = (
                    "Local network permission is required. " +
                            "Allow nearby device access in the app settings."
                    ),
            isError = true
        )
    }

    companion object {

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HealthViewModel(
                    repository = HealthRepository(
                        apiService = RetrofitClient.apiService
                    )
                )
            }
        }
    }
}