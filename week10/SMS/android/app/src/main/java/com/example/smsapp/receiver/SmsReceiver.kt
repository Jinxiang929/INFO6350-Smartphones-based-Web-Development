package com.example.smsapp.receiver

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import com.example.smsapp.MainActivity
import com.example.smsapp.repository.SmsMessage
import com.example.smsapp.repository.SmsRepository

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val repository = SmsRepository(context)
        if (!repository.receiveEnabled()) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val first = parts.firstOrNull() ?: return
        val message = SmsMessage(
            first.displayOriginatingAddress ?: "Unknown",
            parts.joinToString("") { it.displayMessageBody.orEmpty() }
        )
        repository.recordReceived(message)
        if (!repository.notificationsEnabled()) return

        // Receiving and showing notifications are controlled by separate permissions.
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                INCOMING_SMS_CHANNEL,
                "Incoming SMS alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        if (!manager.areNotificationsEnabled()) return

        val openApp = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.OPEN_RECEIVE, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tapAction = PendingIntent.getActivity(
            context,
            0,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, INCOMING_SMS_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("You have a message from ${message.phone}")
            .setContentText("Tap to read in SMSapp.")
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(tapAction)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val NOTIFICATION_ID = 1
        private const val INCOMING_SMS_CHANNEL = "incoming_sms_alerts"
    }
}
