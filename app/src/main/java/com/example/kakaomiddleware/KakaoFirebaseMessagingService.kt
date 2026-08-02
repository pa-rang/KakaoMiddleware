package com.example.kakaomiddleware

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class KakaoFirebaseMessagingService : FirebaseMessagingService() {
    companion object {
        private const val TAG = "KakaoFirebaseService"
    }

    /** Firebase Messaging 25+ registration callback; the value is an FID. */
    override fun onRegistered(installationId: String) {
        Log.i(TAG, "Firebase installation registered locally")
        PushStateStore(this).rememberInstallationId(installationId)
        OutboundWorkScheduler.enqueueRegistration(this, installationId)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        // FID mode is enabled in the manifest, so onRegistered() is the server
        // identity callback. Keep this override only to make an unexpected
        // legacy-token callback visible without accidentally uploading it.
        Log.d(TAG, "Ignoring a legacy FCM token callback while FID mode is enabled")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (!PushWakePayload.isSupported(remoteMessage.data)) {
            Log.w(TAG, "Ignoring unsupported FCM data payload")
            return
        }

        DispatchNotificationManager.getInstance(this).showWakeReceived()
        OutboundWorkScheduler.enqueueDrain(this, expedited = true)
    }

    override fun onDeletedMessages() {
        // FCM may collapse or delete wake hints. The database is authoritative,
        // so one full drain repairs every missing hint.
        Log.w(TAG, "FCM deleted messages; scheduling a queue repair")
        DispatchNotificationManager.getInstance(this).showWakeReceived()
        OutboundWorkScheduler.enqueueDrain(this, expedited = true)
    }

    override fun onUnregistered(installationId: String) {
        Log.w(TAG, "Firebase installation unregistered locally")
    }
}
