package com.example.kakaomiddleware

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object OutboundWorkScheduler {
    private const val DRAIN_WORK = "outbound-drain"
    private const val RECOVERY_WORK = "outbound-drain-recovery"
    private const val REGISTRATION_WORK = "push-registration"
    internal const val KEY_FIREBASE_INSTALLATION_ID = "firebase_installation_id"

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun enqueueDrain(context: Context, expedited: Boolean) {
        val builder = OneTimeWorkRequestBuilder<OutboundDrainWorker>()
            .setConstraints(networkConstraint)
        if (expedited) {
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            DRAIN_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            builder.build()
        )
    }

    /** Repair an explicit Kakao notification miss after the server backoff. */
    fun enqueueRecovery(context: Context, delayMinutes: Long) {
        val request = OneTimeWorkRequestBuilder<OutboundDrainWorker>()
            .setConstraints(networkConstraint)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            RECOVERY_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun enqueueRegistration(context: Context, firebaseInstallationId: String) {
        val request = OneTimeWorkRequestBuilder<PushRegistrationWorker>()
            .setConstraints(networkConstraint)
            .setInputData(
                Data.Builder()
                    .putString(KEY_FIREBASE_INSTALLATION_ID, firebaseInstallationId)
                    .build()
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            REGISTRATION_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
