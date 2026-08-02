package com.example.kakaomiddleware

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

object PushRegistrationManager {
    private const val TAG = "PushRegistrationManager"

    fun initialize(context: Context) {
        val appContext = context.applicationContext

        // Re-register the last FID with whichever server endpoint is active now.
        reregisterKnownInstallation(appContext)

        if (FirebaseApp.getApps(appContext).isEmpty()) {
            Log.w(TAG, "Firebase is not configured; google-services.json is required for FCM")
            return
        }

        try {
            FirebaseMessaging.getInstance().register()
                .addOnSuccessListener {
                    Log.i(TAG, "Firebase registration requested")
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "Firebase registration request failed", error)
                    PushStateStore(appContext).markFailed(
                        error.message ?: error.javaClass.simpleName
                    )
                }
        } catch (error: Exception) {
            // Missing/mismatched Firebase project configuration must not prevent
            // the middleware's existing notification-listener features.
            Log.e(TAG, "Could not initialize Firebase Messaging", error)
        }
    }

    fun reregisterKnownInstallation(context: Context) {
        val appContext = context.applicationContext
        PushStateStore(appContext).installationId()?.let { installationId ->
            OutboundWorkScheduler.enqueueRegistration(appContext, installationId)
        }
    }
}
