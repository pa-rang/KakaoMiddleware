package com.example.kakaomiddleware

import android.content.Context

class PushStateStore(context: Context) {
    companion object {
        private const val PREF_NAME = "push_state"
        private const val KEY_INSTALLATION_ID = "firebase_installation_id"
        private const val KEY_REGISTERED_AT = "registered_at"
        private const val KEY_LAST_ERROR = "last_error"
    }

    private val preferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun rememberInstallationId(firebaseInstallationId: String) {
        preferences.edit().putString(KEY_INSTALLATION_ID, firebaseInstallationId).apply()
    }

    fun installationId(): String? =
        preferences.getString(KEY_INSTALLATION_ID, null)?.takeIf { it.isNotBlank() }

    fun markRegistered(now: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putLong(KEY_REGISTERED_AT, now)
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    fun markFailed(error: String) {
        preferences.edit().putString(KEY_LAST_ERROR, error.take(1000)).apply()
    }
}
