package com.example.smsapp.repository

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class SmsMessage(val phone: String, val body: String)
data class SmsStats(val sent: Int, val received: Int)

class SmsRepository(private val context: Context) {
    val preferences = context.getSharedPreferences("sms", Context.MODE_PRIVATE)

    fun receiveEnabled() = preferences.getBoolean("receive_enabled", false)

    fun setReceiveEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("receive_enabled", enabled).apply()
    }

    fun notificationsEnabled() = preferences.getBoolean("notifications_enabled", false)

    fun setNotificationsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("notifications_enabled", enabled).apply()
    }

    fun latest(): SmsMessage? {
        val body = preferences.getString("latest_body", null) ?: return null
        return SmsMessage(preferences.getString("latest_phone", "Unknown").orEmpty(), body)
    }

    fun latestSent(): SmsMessage? {
        val phone = preferences.getString("latest_sent_phone", null) ?: return null
        val body = preferences.getString("latest_sent_body", null) ?: return null
        return SmsMessage(phone, body)
    }

    fun stats() = SmsStats(preferences.getInt("sent", 0), preferences.getInt("received", 0))

    fun recordReceived(message: SmsMessage) {
        preferences.edit()
            .putString("latest_phone", message.phone)
            .putString("latest_body", message.body)
            .putInt("received", stats().received + 1)
            .apply()
    }

    fun recordSent(message: SmsMessage) {
        preferences.edit()
            .putString("latest_sent_phone", message.phone)
            .putString("latest_sent_body", message.body)
            .putInt("sent", stats().sent + 1)
            .apply()
    }

    @Suppress("DEPRECATION")
    suspend fun sendSms(message: SmsMessage): Int? {
        val manager = if (Build.VERSION.SDK_INT >= 31) {
            checkNotNull(context.getSystemService(SmsManager::class.java))
        } else {
            SmsManager.getDefault()
        }
        val parts = manager.divideMessage(message.body)
        val action = "${context.packageName}.SMS_SENT.${UUID.randomUUID()}"
        val result = CompletableDeferred<Int>()
        var remaining = parts.size

        // Android returns a separate send result for each segment.
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val code = resultCode
                if (code != Activity.RESULT_OK || --remaining == 0) result.complete(code)
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val callbacks = arrayListOf<PendingIntent>()
        try {
            for (index in parts.indices) {
                callbacks += PendingIntent.getBroadcast(
                    context, index, Intent(action).setPackage(context.packageName),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
                )
            }
            if (parts.size == 1) {
                manager.sendTextMessage(message.phone, null, parts[0], callbacks[0], null)
            } else {
                manager.sendMultipartTextMessage(message.phone, null, parts, callbacks, null)
            }
            return withTimeoutOrNull(30_000L) { result.await() }
        } finally {
            context.unregisterReceiver(receiver)
            callbacks.forEach { it.cancel() }
        }
    }

    suspend fun syncStats(stats: SmsStats) = withContext(Dispatchers.IO) {
        val connection = URL("http://10.0.2.2:5070/api/sms-stats")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "PUT"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val json = JSONObject().put("sent", stats.sent).put("received", stats.received)
            connection.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }
}
