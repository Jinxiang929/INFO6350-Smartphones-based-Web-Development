package com.example.week7speechtools.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.week7speechtools.model.DevicePosition
import com.example.week7speechtools.repository.WhereAmIRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class WhereAmIState(
    val position: DevicePosition? = null,
    val locationMessage: String = "Waiting for location permission...",
    val publicIp: String = "Checking..."
)

class WhereAmIViewModel(
    private val repository: WhereAmIRepository
) : ViewModel() {

    private val _state = MutableStateFlow(WhereAmIState())
    val state = _state.asStateFlow()

    fun reset() {
        _state.value = WhereAmIState()
    }

    fun onLocationPermissionDenied() {
        _state.update {
            it.copy(
                position = null,
                locationMessage = "Location permission denied."
            )
        }
    }

    suspend fun loadLocation() {
        _state.update {
            it.copy(locationMessage = "Getting your location...")
        }

        try {
            val position = repository.getPosition()

            _state.update {
                it.copy(position = position, locationMessage = "")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: SecurityException) {
            onLocationPermissionDenied()
        } catch (error: IOException) {
            _state.update {
                it.copy(
                    locationMessage = error.message ?: "Location unavailable."
                )
            }
        } catch (error: Exception) {
            _state.update {
                it.copy(locationMessage = "Location unavailable.")
            }
        }
    }

    suspend fun loadPublicIp() {
        val ip = try {
            repository.getPublicIp()
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            "Unavailable"
        }

        _state.update { it.copy(publicIp = ip) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY])

                WhereAmIViewModel(
                    WhereAmIRepository(application)
                )
            }
        }
    }
}