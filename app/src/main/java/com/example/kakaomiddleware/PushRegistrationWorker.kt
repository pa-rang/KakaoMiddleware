package com.example.kakaomiddleware

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.IOException

class PushRegistrationWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    companion object {
        private const val TAG = "PushRegistrationWorker"
    }

    override suspend fun doWork(): Result {
        val installationId =
            inputData.getString(OutboundWorkScheduler.KEY_FIREBASE_INSTALLATION_ID)
                ?.takeIf { it.isNotBlank() }
                ?: return Result.failure()
        val stateStore = PushStateStore(applicationContext)
        stateStore.rememberInstallationId(installationId)

        return try {
            DeviceApiService(applicationContext).registerPush(installationId)
            stateStore.markRegistered()
            // Registration may have happened after an earlier wake was dropped.
            OutboundWorkScheduler.enqueueDrain(applicationContext, expedited = false)
            Log.i(TAG, "Firebase installation registered with the server")
            Result.success()
        } catch (error: Exception) {
            stateStore.markFailed(error.message ?: error.javaClass.simpleName)
            Log.e(TAG, "Push registration failed", error)
            if (error is DeviceApiException && !error.isRetryable) {
                DispatchNotificationManager.getInstance(applicationContext)
                    .showRegistrationFailure()
                Result.failure()
            } else if (error is IOException) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
