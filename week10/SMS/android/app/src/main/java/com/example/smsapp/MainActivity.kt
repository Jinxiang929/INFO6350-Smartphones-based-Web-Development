package com.example.smsapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.example.smsapp.receiver.SmsReceiver
import com.example.smsapp.ui.ReceiveSmsScreen
import com.example.smsapp.ui.SendSmsScreen
import com.example.smsapp.viewmodel.SmsViewModel

class MainActivity : ComponentActivity() {
    private enum class Consent(val title: String, val message: String) {
        SEND("Send this SMS?", "Send the message to the number you entered."),
        RECEIVE("Allow receiving SMS?", "SMSapp will show incoming messages until you turn receiving off."),
        NOTIFICATIONS("Allow SMS notifications?", "Show a notification when SMSapp receives a message.")
    }

    private val model by viewModels<SmsViewModel>()
    private var showReceive by mutableStateOf(false)
    private var receivePermissionGranted by mutableStateOf(false)
    private var notificationsPermitted by mutableStateOf(false)
    private var permissionNotice by mutableStateOf("")
    private var pendingConsent by mutableStateOf<Consent?>(null)

    private val sendPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed ->
        if (allowed) model.send() else model.sendPermissionDenied()
    }

    private val receivePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed ->
        model.updateReceiveEnabled(allowed)
        permissionNotice = if (allowed) "" else "Android did not allow receiving SMS."
        refreshPermissionStatus()
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed ->
        model.updateNotificationsEnabled(allowed)
        permissionNotice = if (allowed) "" else "Android did not allow notifications."
        refreshPermissionStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showReceive = savedInstanceState?.getBoolean(OPEN_RECEIVE) ?: false
        handleNotificationIntent(intent)
        pendingConsent = savedInstanceState?.getString(STATE_CONSENT)?.let(Consent::valueOf)

        setContent {
            MaterialTheme {
                Scaffold { insets ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(insets).imePadding()
                            .verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("SMSapp", style = MaterialTheme.typography.headlineMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(!showReceive, { showReceive = false }, { Text("Send") })
                            FilterChip(showReceive, { showReceive = true }, { Text("Receive") })
                        }
                        if (showReceive) {
                            ReceiveSmsScreen(
                                model = model,
                                receiveAllowed = receivePermissionGranted && model.receiveEnabled,
                                notificationsAllowed = notificationsPermitted && model.notificationsEnabled,
                                permissionNotice = permissionNotice,
                                onReceiveChange = ::changeReceive,
                                onNotificationsChange = ::changeNotifications
                            )
                        } else {
                            SendSmsScreen(model) { pendingConsent = Consent.SEND }
                        }
                    }
                }

                pendingConsent?.let { consent ->
                    AlertDialog(
                        onDismissRequest = { pendingConsent = null },
                        title = { Text(consent.title) },
                        text = { Text(consent.message) },
                        confirmButton = {
                            TextButton(onClick = { allow(consent) }) { Text("Allow") }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingConsent = null }) { Text("Don't allow") }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent) {
        if (intent.getBooleanExtra(OPEN_RECEIVE, false)) {
            showReceive = true
            intent.removeExtra(OPEN_RECEIVE)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(OPEN_RECEIVE, showReceive)
        outState.putString(STATE_CONSENT, pendingConsent?.name)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
    }

    private fun hasPermission(permission: String) =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun allow(consent: Consent) {
        pendingConsent = null
        when (consent) {
            Consent.SEND -> {
                if (hasPermission(Manifest.permission.SEND_SMS)) model.send()
                else sendPermission.launch(Manifest.permission.SEND_SMS)
            }
            Consent.RECEIVE -> {
                if (hasPermission(Manifest.permission.RECEIVE_SMS)) {
                    model.updateReceiveEnabled(true)
                } else {
                    receivePermission.launch(Manifest.permission.RECEIVE_SMS)
                }
            }
            Consent.NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= 33 &&
                    !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                ) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                    model.updateNotificationsEnabled(true)
                } else {
                    permissionNotice = "Android has notifications turned off for this app."
                }
            }
        }
        refreshPermissionStatus()
    }

    private fun changeReceive(enabled: Boolean) {
        permissionNotice = ""
        if (enabled) {
            pendingConsent = Consent.RECEIVE
        } else {
            model.updateReceiveEnabled(false)
            dismissSmsNotification()
        }
    }

    private fun changeNotifications(enabled: Boolean) {
        permissionNotice = ""
        if (enabled) {
            pendingConsent = Consent.NOTIFICATIONS
        } else {
            model.updateNotificationsEnabled(false)
            dismissSmsNotification()
        }
    }

    private fun dismissSmsNotification() {
        NotificationManagerCompat.from(this).cancel(SmsReceiver.NOTIFICATION_ID)
    }

    private fun refreshPermissionStatus() {
        receivePermissionGranted = hasPermission(Manifest.permission.RECEIVE_SMS)
        notificationsPermitted = NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    companion object {
        const val OPEN_RECEIVE = "open_receive"
        private const val STATE_CONSENT = "pending_consent"
    }
}
