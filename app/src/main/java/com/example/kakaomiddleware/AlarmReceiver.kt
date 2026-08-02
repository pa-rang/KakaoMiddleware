package com.example.kakaomiddleware

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Ten-minute safety net for FCM delivery.
 *
 * The receiver deliberately performs no network, RemoteInput, or ACK work. It
 * only starts the same constrained WorkManager drain used by FCM, then schedules
 * its successor. This keeps both trigger paths on one idempotent implementation.
 */
class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AlarmReceiver"
        const val ALARM_REQUEST_CODE = 100

        fun scheduleNextAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                val remainder = get(Calendar.MINUTE) % 10
                add(Calendar.MINUTE, 10 - remainder)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                Intent(context, AlarmReceiver::class.java).apply {
                    putExtra("expectedTime", calendar.timeInMillis)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
            if (!canScheduleExact) {
                Log.w(TAG, "Exact alarm permission is unavailable; FCM remains the primary trigger")
                return
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
                val formatted = SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    Locale.getDefault()
                ).format(Date(calendar.timeInMillis))
                Log.d(TAG, "Next fallback drain scheduled for $formatted")
            } catch (error: SecurityException) {
                Log.e(TAG, "Could not schedule the fallback alarm", error)
            }
        }

        fun startPeriodicAlarm(context: Context) {
            cancelAlarm(context)
            scheduleNextAlarm(context)
            Log.i(TAG, "Ten-minute outbound fallback enabled")
        }

        fun cancelAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                Intent(context, AlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d(TAG, "Fallback alarm cancelled")
            }
        }

        fun isAlarmActive(context: Context): Boolean =
            PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                Intent(context, AlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) != null
    }

    override fun onReceive(context: Context, intent: Intent) {
        val now = System.currentTimeMillis()
        val expected = intent.getLongExtra("expectedTime", 0L)
        val delay = if (expected > 0L) "%.1fs".format(abs(now - expected) / 1000.0) else "n/a"
        Log.i(TAG, "Fallback alarm fired (delay=$delay); enqueueing outbound drain")

        OutboundWorkScheduler.enqueueDrain(context.applicationContext, expedited = false)
        scheduleNextAlarm(context.applicationContext)
    }
}
