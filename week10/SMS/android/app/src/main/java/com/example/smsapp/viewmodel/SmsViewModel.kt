package com.example.smsapp.viewmodel

import android.app.Activity
import android.app.Application
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smsapp.repository.SmsMessage
import com.example.smsapp.repository.SmsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.IOException

class SmsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SmsRepository(application)

    var phone by mutableStateOf("")
    var body by mutableStateOf("")
    var sendStatus by mutableStateOf("")
        private set
    var sending by mutableStateOf(false)
        private set
    var syncing by mutableStateOf(false)
        private set
    var syncStatus by mutableStateOf("")
        private set
    var latest by mutableStateOf(repository.latest())
        private set
    var latestSent by mutableStateOf(repository.latestSent())
        private set

    var receiveEnabled by mutableStateOf(repository.receiveEnabled())
        private set
    var notificationsEnabled by mutableStateOf(repository.notificationsEnabled())
        private set

    // Changes made by the background receiver also update the visible screen.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        latest = repository.latest()
        latestSent = repository.latestSent()
    }

    init {
        repository.preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun updateReceiveEnabled(enabled: Boolean) {
        repository.setReceiveEnabled(enabled)
        receiveEnabled = enabled
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        repository.setNotificationsEnabled(enabled)
        notificationsEnabled = enabled
    }

    fun sendPermissionDenied() {
        sendStatus = "SMS permission is required to send a message."
    }

    fun send() {
        if (sending || phone.isBlank() || body.isBlank()) return
        val message = SmsMessage(phone.trim(), body)
        sending = true
        sendStatus = "Sending…"
        viewModelScope.launch {
            try {
                sendStatus = when (val code = repository.sendSms(message)) {
                    Activity.RESULT_OK -> {
                        repository.recordSent(message)
                        "SMS sent."
                    }
                    null -> "No send confirmation yet. Check the receiving device."
                    else -> "Android reported a send error ($code)."
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: SecurityException) {
                sendPermissionDenied()
            } catch (_: RuntimeException) {
                sendStatus = "Could not send. Check the number and SMS service."
            } finally {
                sending = false
            }
        }
    }

    fun sync() {
        if (syncing) return
        val snapshot = repository.stats()
        syncing = true
        syncStatus = "Syncing…"
        viewModelScope.launch {
            try {
                repository.syncStats(snapshot)
                syncStatus = "SMS statistics saved."
            } catch (_: IOException) {
                syncStatus = "Sync failed. Check that the Python server is running."
            } finally {
                syncing = false
            }
        }
    }

    override fun onCleared() {
        repository.preferences.unregisterOnSharedPreferenceChangeListener(listener)
        super.onCleared()
    }
}
