package com.example.kakaomiddleware

import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Resolves which chat room a KakaoTalk notification belongs to.
 *
 * KakaoTalk 26.7.0 (installed 2026-08-12) stopped writing the group room name
 * into the notification body: `android.subText` is now null and
 * `android.conversationTitle` is absent, so the only place the room name still
 * exists is the conversation shortcut the notification points at
 * (`sbn.tag` == shortcut id == KakaoTalk chat id). On Android 11+ a
 * notification listener can read that shortcut's label through
 * `Ranking.getConversationShortcutInfo()`, and the label is byte-for-byte the
 * room name the server already knows.
 *
 * Every caller that needs a group's name must go through [resolveGroupName] so
 * capture and reply-injection cannot disagree about what a room is called. The
 * chain, in order:
 *
 *  1. `android.subText` — the pre-26.7.0 location; costs nothing to keep first.
 *  2. `android.conversationTitle` — MessagingStyle's own slot for the name.
 *  3. The conversation shortcut label — where available; some frameworks
 *     (Samsung Android 11) ship without the API despite its SDK level.
 *  4. A persisted `tag → name` map remembered from earlier resolutions.
 *
 * On a device where 1–3 all fail the map is the only source, and it can be
 * seeded from the host with `scripts/seed-room-name-map.sh`, which copies the
 * system's own shortcut registry (tag → room name) into these prefs over adb.
 * The map is deliberately a *fallback*, never an override: a room rename
 * flows in through 1–3 and re-learns the entry.
 */
object KakaoRoomNameResolver {
    private const val TAG = "KakaoRoomNameResolver"
    private const val PREFS_NAME = "kakao_room_name_map"

    fun resolveGroupName(
        service: NotificationListenerService,
        sbn: StatusBarNotification
    ): String? {
        val extras = sbn.notification.extras
        val subText = extras.getCharSequence("android.subText")?.toString()?.takeIf { it.isNotBlank() }
        val conversationTitle =
            extras.getCharSequence("android.conversationTitle")?.toString()?.takeIf { it.isNotBlank() }

        val fromExtras = subText ?: conversationTitle
        if (fromExtras != null) {
            remember(service, sbn.tag, fromExtras)
            return fromExtras
        }

        val fromShortcut = conversationShortcutLabel(service, sbn)
        if (fromShortcut != null) {
            Log.d(TAG, "Resolved group name via conversation shortcut: '$fromShortcut' (tag=${sbn.tag})")
            remember(service, sbn.tag, fromShortcut)
            return fromShortcut
        }

        val remembered = recall(service, sbn.tag)
        if (remembered != null) {
            Log.d(TAG, "Resolved group name via remembered tag map: '$remembered' (tag=${sbn.tag})")
        }
        return remembered
    }

    /**
     * True once this framework has proven it lacks the ranking→shortcut API, so
     * the failed lookup happens exactly once per process instead of per message.
     * The Samsung A50 (Android 11) throws NoSuchMethodError here even though the
     * SDK marks the method as API 30 — which is also why the catch below must be
     * Throwable: an Error would sail past `catch (Exception)` and take the whole
     * listener service down with it.
     */
    @Volatile
    private var rankingShortcutUnsupported = false

    private fun conversationShortcutLabel(
        service: NotificationListenerService,
        sbn: StatusBarNotification
    ): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || rankingShortcutUnsupported) return null
        return try {
            val ranking = NotificationListenerService.Ranking()
            if (!service.currentRanking.getRanking(sbn.key, ranking)) {
                Log.w(TAG, "No ranking entry for ${sbn.key}")
                return null
            }
            ranking.conversationShortcutInfo?.shortLabel?.toString()?.takeIf { it.isNotBlank() }
        } catch (e: LinkageError) {
            rankingShortcutUnsupported = true
            Log.w(TAG, "Ranking shortcut API missing on this framework - relying on the tag map: ${e.message}")
            null
        } catch (e: Throwable) {
            Log.e(TAG, "Conversation shortcut lookup failed: ${e.message}")
            null
        }
    }

    private fun remember(context: Context, tag: String?, name: String) {
        if (tag.isNullOrBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(tag, null) != name) {
            prefs.edit().putString(tag, name).apply()
        }
    }

    private fun recall(context: Context, tag: String?): String? {
        if (tag.isNullOrBlank()) return null
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(tag, null)
    }
}
