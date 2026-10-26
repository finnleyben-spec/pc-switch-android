package dev.finnley.pcswitch.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.finnley.pcswitch.MainActivity
import dev.finnley.pcswitch.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NotificationHelper {

    private const val CHANNEL_ID = "pcswitch_switch_changes"
    private const val CHANNEL_NAME = "Schalter-Änderungen"
    private const val CHANNEL_DESC = "Benachrichtigungen bei Schalter-Zustandsänderungen"
    private var channelCreated = false

    /**
     * Ensure the notification channel exists. Call this at app startup.
     */
    fun ensureChannel(context: Context) {
        if (channelCreated) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESC
        }
        manager.createNotificationChannel(channel)
        channelCreated = true
    }

    /**
     * Check if notification permission is granted (Android 13+).
     */
    fun hasPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Send a notification for a switch state change.
     */
    fun sendSwitchNotification(
        context: Context,
        switchName: String,
        newState: String,
        switchId: String? = null
    ) {
        if (!hasPermission(context)) {
            Toast.makeText(
                context,
                "Benachrichtigungsberechtigung nicht gewährt",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        ensureChannel(context)

        val stateText = when (newState) {
            "on" -> "AN"
            "off" -> "AUS"
            else -> newState
        }

        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(Date())

        // Create pending intent to open the app
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_switch_id", switchId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_power)
            .setContentTitle("${switchName}: ${stateText}")
            .setContentText("Schalter-Zustand geändert um $timestamp")
            .setWhen(System.currentTimeMillis())
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        val manager = NotificationManagerCompat.from(context)
        val notifyId = switchName.hashCode()
        manager.notify(notifyId, notification)
    }
}
