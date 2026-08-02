package com.example.kakaomiddleware

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Short-lived durable record of KakaoTalk injections that have not yet been
 * acknowledged by the server.
 *
 * The record is written immediately after RemoteInput succeeds and removed only
 * after the matching ACK succeeds. If the process dies in between, a reclaimed
 * server message is acknowledged without injecting it a second time.
 */
class DeliveryJournal private constructor(context: Context) {
    companion object {
        private const val TAG = "DeliveryJournal"
        private const val PREF_NAME = "outbound_delivery_journal"
        private const val KEY_ENTRIES = "injected_entries"
        internal const val TTL_MS = 48L * 60L * 60L * 1000L
        internal const val MAX_ENTRIES = 200

        @Volatile
        private var instance: DeliveryJournal? = null

        fun getInstance(context: Context): DeliveryJournal =
            instance ?: synchronized(this) {
                instance ?: DeliveryJournal(context.applicationContext).also { instance = it }
            }

        internal fun pruneEntries(
            entries: Map<String, Long>,
            now: Long,
            ttlMs: Long = TTL_MS,
            maxEntries: Int = MAX_ENTRIES
        ): LinkedHashMap<String, Long> {
            return entries.asSequence()
                .filter { (_, injectedAt) -> injectedAt >= now - ttlMs }
                .sortedByDescending { (_, injectedAt) -> injectedAt }
                .take(maxEntries)
                .associateTo(linkedMapOf()) { it.key to it.value }
        }
    }

    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun wasInjected(messageId: String, now: Long = System.currentTimeMillis()): Boolean {
        val entries = prunedEntries(now)
        persist(entries)
        return entries.containsKey(messageId)
    }

    @Synchronized
    fun markInjected(messageId: String, now: Long = System.currentTimeMillis()): Boolean {
        val entries = loadEntries().toMutableMap()
        entries[messageId] = now
        return persist(pruneEntries(entries, now))
    }

    @Synchronized
    fun remove(messageId: String, now: Long = System.currentTimeMillis()): Boolean {
        val entries = prunedEntries(now)
        entries.remove(messageId)
        return persist(entries)
    }

    @Synchronized
    fun prune(now: Long = System.currentTimeMillis()): Boolean = persist(prunedEntries(now))

    private fun prunedEntries(now: Long): LinkedHashMap<String, Long> =
        pruneEntries(loadEntries(), now)

    private fun loadEntries(): Map<String, Long> {
        val raw = preferences.getString(KEY_ENTRIES, null) ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, json.getLong(key))
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Discarding an unreadable delivery journal", error)
            emptyMap()
        }
    }

    private fun persist(entries: Map<String, Long>): Boolean {
        val json = JSONObject()
        entries.forEach { (messageId, injectedAt) -> json.put(messageId, injectedAt) }
        return preferences.edit().putString(KEY_ENTRIES, json.toString()).commit()
    }
}
