package com.example.kakaomiddleware

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import java.io.IOException

class OutboundDrainWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    companion object {
        private const val TAG = "OutboundDrainWorker"
        private const val MAX_BATCHES_PER_RUN = 10
        private val ACK_RETRY_DELAYS_MS = longArrayOf(500L, 1_000L)
    }

    private val api = DeviceApiService(appContext)
    private val journal = DeliveryJournal.getInstance(appContext)
    private val replyManager = ReplyManager.getInstance(appContext)
    private val notifications = DispatchNotificationManager.getInstance(appContext)

    override suspend fun doWork(): Result {
        journal.prune()
        var delivered = 0
        var deferred = 0

        repeat(MAX_BATCHES_PER_RUN) {
            val messages = try {
                api.claimOutbound()
            } catch (error: Exception) {
                Log.e(TAG, "Could not claim outbound messages", error)
                notifications.showResult(delivered, deferred)
                return resultFor(error)
            }

            if (messages.isEmpty()) {
                notifications.showResult(delivered, deferred)
                return Result.success()
            }

            for (message in messages) {
                if (journal.wasInjected(message.id)) {
                    Log.i(TAG, "Skipping duplicate injection and repairing ACK: ${message.id}")
                    val ackError = acknowledgeWithRetry(message, ok = true)
                    if (ackError == null) {
                        journal.remove(message.id)
                        delivered++
                        continue
                    }
                    scheduleAckRecovery()
                    notifications.showResult(delivered, deferred)
                    return resultFor(ackError)
                }

                val injected = replyManager.sendMessageToChat(
                    message.chatRoomName,
                    message.messageContent
                )
                if (injected) {
                    if (!journal.markInjected(message.id)) {
                        Log.e(TAG, "Could not persist delivery journal entry: ${message.id}")
                    }

                    val ackError = acknowledgeWithRetry(message, ok = true)
                    if (ackError == null) {
                        journal.remove(message.id)
                        delivered++
                    } else {
                        scheduleAckRecovery()
                        notifications.showResult(delivered, deferred)
                        return resultFor(ackError)
                    }
                } else {
                    deferred++
                    val ackError = acknowledgeWithRetry(
                        message,
                        ok = false,
                        error = "No active KakaoTalk notification was available for this chat"
                    )
                    val delayMinutes = if (message.attemptCount <= 1) 5L else 10L
                    OutboundWorkScheduler.enqueueRecovery(applicationContext, delayMinutes)
                    if (ackError != null && !isRetryable(ackError)) {
                        notifications.showResult(delivered, deferred)
                        return Result.failure()
                    }
                }
            }
        }

        // A bounded run prevents a producer that never stops from monopolizing a
        // worker. Continue in a fresh work item without losing queue state.
        OutboundWorkScheduler.enqueueDrain(applicationContext, expedited = false)
        notifications.showResult(delivered, deferred)
        return Result.success()
    }

    private suspend fun acknowledgeWithRetry(
        message: ClaimedOutboundMessage,
        ok: Boolean,
        error: String? = null
    ): Throwable? {
        var lastError: Throwable? = null
        repeat(ACK_RETRY_DELAYS_MS.size + 1) { attempt ->
            try {
                api.acknowledge(message, ok, error)
                return null
            } catch (caught: Exception) {
                lastError = caught
                if (!isRetryable(caught)) return caught
                if (attempt < ACK_RETRY_DELAYS_MS.size) {
                    delay(ACK_RETRY_DELAYS_MS[attempt])
                }
            }
        }
        return lastError
    }

    private fun scheduleAckRecovery() {
        // Server dispatch leases expire before this check. The journal makes the
        // recovery an ACK-only operation instead of a duplicate RemoteInput.
        OutboundWorkScheduler.enqueueRecovery(applicationContext, delayMinutes = 3L)
    }

    private fun resultFor(error: Throwable): Result =
        if (isRetryable(error)) Result.retry() else Result.failure()

    private fun isRetryable(error: Throwable): Boolean = when (error) {
        is DeviceApiException -> error.isRetryable
        is IOException -> true
        else -> false
    }
}
