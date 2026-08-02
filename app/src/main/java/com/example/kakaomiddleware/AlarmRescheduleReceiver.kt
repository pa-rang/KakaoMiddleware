package com.example.kakaomiddleware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Restores process-local push registration and alarms cleared by reboot/update. */
class AlarmRescheduleReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AlarmRescheduleReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        Log.i(TAG, "Restoring outbound delivery after ${intent.action}")
        AlarmReceiver.startPeriodicAlarm(context.applicationContext)
        PushRegistrationManager.initialize(context.applicationContext)
        OutboundWorkScheduler.enqueueDrain(context.applicationContext, expedited = false)
    }
}
