package com.example.week7speechtools.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.week7speechtools.network.RetrofitClient
import com.example.week7speechtools.repository.HealthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull

enum class BackendConnectionStatus {
    CHECKING,
    CONNECTED,
    NOT_CONNECTED
}

class HealthViewModel(
    private val repository: HealthRepository
) : ViewModel() {

    val connectionStatus: StateFlow<BackendConnectionStatus> =
        flow {
            while (true) {
                emit(checkBackend())
                delay(CHECK_INTERVAL_MILLIS)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 0,
                replayExpirationMillis = 0
            ),
            initialValue = BackendConnectionStatus.CHECKING
        )

    private suspend fun checkBackend(): BackendConnectionStatus {
        return try {
            val healthy = withTimeoutOrNull(CHECK_TIMEOUT_MILLIS) {
                repository.checkHealth().success
            } == true

            if (healthy) {
                BackendConnectionStatus.CONNECTED
            } else {
                BackendConnectionStatus.NOT_CONNECTED
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            BackendConnectionStatus.NOT_CONNECTED
        }
    }

    companion object {
        private const val CHECK_INTERVAL_MILLIS = 5_000L
        private const val CHECK_TIMEOUT_MILLIS = 3_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HealthViewModel(
                    repository = HealthRepository(RetrofitClient.apiService)
                )
            }
        }
    }
}