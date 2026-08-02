package com.example.kakaomiddleware

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.ForegroundInfo

/** A single quiet status notification for FCM wake and drain results. */
class DispatchNotificationManager private constructor(private val context: Context) {
    companion object {
        private const val CHANNEL_ID = "outbound_dispatch"
        private const val STATUS_NOTIFICATION_ID = 7102
        private const val FOREGROUND_NOTIFICATION_ID = 7103
        private const val PREF_NAME = "dispatch_notification"
        private const val KEY_PERMISSION_ASKED = "permission_asked"

        fun getInstance(context: Context): DispatchNotificationManager =
            DispatchNotificationManager(context.applicationContext)

        fun shouldRequestPermission(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) return false
            return !context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_PERMISSION_ASKED, false)
        }

        fun markPermissionAsked(context: Context) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PERMISSION_ASKED, true)
                .apply()
        }
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Outgoing KakaoTalk delivery",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows quiet wake and delivery status"
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
            )
        }
    }

    fun showWakeReceived() {
        show(
            title = "KakaoTalk message received",
            text = "Checking the outbound queue…",
            timeoutMs = 2L * 60L * 1000L,
            ongoing = true
        )
    }

    fun showResult(delivered: Int, failed: Int) {
        val text = when {
            delivered > 0 && failed == 0 -> "$delivered message(s) delivered"
            delivered > 0 -> "$delivered delivered, $failed deferred"
            failed > 0 -> "$failed message(s) deferred"
            else -> "No pending messages"
        }
        show("KakaoTalk delivery finished", text, timeoutMs = 30_000L, ongoing = false)
    }

    fun showRegistrationFailure() {
        show(
            title = "Push registration failed",
            text = "Check the server key and Firebase configuration",
            timeoutMs = 30_000L,
            ongoing = false
        )
    }

    /** Required by expedited WorkManager jobs on Android versions before 12. */
    fun foregroundInfo(): ForegroundInfo = ForegroundInfo(
        FOREGROUND_NOTIFICATION_ID,
        buildNotification(
            title = "KakaoTalk delivery in progress",
            text = "Checking the outbound queue…",
            ongoing = true,
            timeoutMs = null
        )
    )

    private fun show(title: String, text: String, timeoutMs: Long, ongoing: Boolean) {
        if (!canNotify()) return

        notificationManager.notify(
            STATUS_NOTIFICATION_ID,
            buildNotification(title, text, ongoing, timeoutMs)
        )
    }

    private fun buildNotification(
        title: String,
        text: String,
        ongoing: Boolean,
        timeoutMs: Long?
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        builder
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && timeoutMs != null) {
            builder.setTimeoutAfter(timeoutMs)
        }
        return builder.build()
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
