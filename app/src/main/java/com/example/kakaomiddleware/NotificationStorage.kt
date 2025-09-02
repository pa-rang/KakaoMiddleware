package com.example.kakaomiddleware

import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * StatusBarNotification 캐시 관리자
 * 각 채팅방별로 최신 StatusBarNotification을 메모리에 캐시하여
 * ReplyManager에서 실시간 RemoteInput 하이재킹에 사용
 */
object NotificationStorage {
    
    private const val TAG = "NotificationStorage"
    private const val MAX_CACHED_NOTIFICATIONS = 50 // 메모리 제한
    
    // 채팅방 ID -> 최신 StatusBarNotification 매핑
    private val notificationCache = ConcurrentHashMap<String, StatusBarNotification>()
    
    /**
     * 채팅방별 최신 StatusBarNotification 저장
     * @param chatId 채팅방 ID
     * @param sbn StatusBarNotification 객체
     */
    fun storeNotification(chatId: String, sbn: StatusBarNotification) {
        try {
            // 캐시 크기 제한 (메모리 보호)
            if (notificationCache.size >= MAX_CACHED_NOTIFICATIONS) {
                // 가장 오래된 항목 제거 (단순한 FIFO 방식)
                val oldestKey = notificationCache.keys.firstOrNull()
                oldestKey?.let { 
                    notificationCache.remove(it)
                    Log.d(TAG, "🗑️ Removed oldest notification: $it")
                }
            }
            
            notificationCache[chatId] = sbn
            Log.d(TAG, "💾 Stored notification for: $chatId (total: ${notificationCache.size})")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error storing notification for $chatId", e)
        }
    }
    
    /**
     * 특정 채팅방의 최신 StatusBarNotification 조회
     * @param chatId 채팅방 ID
     * @return 최신 StatusBarNotification (없으면 null)
     */
    fun getLatestNotification(chatId: String): StatusBarNotification? {
        return try {
            val notification = notificationCache[chatId]
            if (notification != null) {
                Log.d(TAG, "📱 Retrieved cached notification for: $chatId")
            } else {
                Log.d(TAG, "❌ No cached notification found for: $chatId")
            }
            notification
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving notification for $chatId", e)
            null
        }
    }
    
    /**
     * 모든 캐시된 채팅방 ID 목록 반환
     */
    fun getCachedChatIds(): Set<String> {
        return notificationCache.keys.toSet()
    }
    
    /**
     * 특정 채팅방의 알림 캐시 제거
     * @param chatId 제거할 채팅방 ID
     */
    fun removeNotification(chatId: String) {
        try {
            val removed = notificationCache.remove(chatId)
            if (removed != null) {
                Log.d(TAG, "🗑️ Removed notification cache for: $chatId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing notification for $chatId", e)
        }
    }
    
    /**
     * 전체 캐시 초기화
     */
    fun clearAll() {
        try {
            val count = notificationCache.size
            notificationCache.clear()
            Log.d(TAG, "🧹 Cleared all notification cache ($count items)")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing notification cache", e)
        }
    }
    
    /**
     * 캐시 통계 정보 반환
     */
    fun getCacheStats(): Map<String, Any> {
        return mapOf(
            "totalCachedNotifications" to notificationCache.size,
            "maxCacheSize" to MAX_CACHED_NOTIFICATIONS,
            "cachedChatIds" to getCachedChatIds().toList()
        )
    }
    
    /**
     * 현재 저장된 모든 StatusBarNotification 객체들을 로그로 출력 (디버깅용)
     */
    fun logAllStoredNotifications() {
        try {
            Log.d(TAG, "📋 === NotificationStorage Debug Info ===")
            Log.d(TAG, "📊 Total cached notifications: ${notificationCache.size}")
            Log.d(TAG, "📊 Max cache size: $MAX_CACHED_NOTIFICATIONS")
            
            if (notificationCache.isEmpty()) {
                Log.d(TAG, "🔍 No notifications currently stored in cache")
                return
            }
            
            notificationCache.forEach { (chatId, sbn) ->
                Log.d(TAG, "📱 ChatId: $chatId")
                Log.d(TAG, "  ├─ PackageName: ${sbn.packageName}")
                Log.d(TAG, "  ├─ PostTime: ${sbn.postTime} (${getTimeAgo(sbn.postTime)} ago)")
                Log.d(TAG, "  ├─ Key: ${sbn.key}")
                Log.d(TAG, "  ├─ Tag: ${sbn.tag}")
                Log.d(TAG, "  ├─ Id: ${sbn.id}")
                
                // Notification 내용 확인
                val notification = sbn.notification
                Log.d(TAG, "  ├─ Notification extras:")
                Log.d(TAG, "  │   ├─ Title: ${notification.extras?.getString("android.title")}")
                Log.d(TAG, "  │   ├─ Text: ${notification.extras?.getString("android.text")}")
                Log.d(TAG, "  │   ├─ SubText: ${notification.extras?.getString("android.subText")}")
                Log.d(TAG, "  │   └─ IsGroupConversation: ${notification.extras?.getBoolean("android.isGroupConversation", false)}")
                
                // Actions (RemoteInput 확인)
                val actions = notification.actions
                if (actions != null && actions.isNotEmpty()) {
                    Log.d(TAG, "  ├─ Actions: ${actions.size} found")
                    actions.forEachIndexed { index, action ->
                        Log.d(TAG, "  │   ├─ Action[$index]: ${action.title}")
                        val remoteInputs = action.remoteInputs
                        if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                            remoteInputs.forEachIndexed { riIndex, ri ->
                                Log.d(TAG, "  │   │   └─ RemoteInput[$riIndex]: key='${ri.resultKey}', label='${ri.label}'")
                            }
                        } else {
                            Log.d(TAG, "  │   │   └─ No RemoteInputs")
                        }
                    }
                } else {
                    Log.d(TAG, "  └─ No Actions found")
                }
                Log.d(TAG, "  ")
            }
            Log.d(TAG, "📋 === End of NotificationStorage Debug ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error logging stored notifications", e)
        }
    }
    
    /**
     * 시간 경과를 사람이 읽기 쉬운 형태로 반환
     */
    private fun getTimeAgo(postTime: Long): String {
        val currentTime = System.currentTimeMillis()
        val diffMs = currentTime - postTime
        
        return when {
            diffMs < 1000 -> "방금 전"
            diffMs < 60 * 1000 -> "${diffMs / 1000}초"
            diffMs < 60 * 60 * 1000 -> "${diffMs / (60 * 1000)}분"
            diffMs < 24 * 60 * 60 * 1000 -> "${diffMs / (60 * 60 * 1000)}시간"
            else -> "${diffMs / (24 * 60 * 60 * 1000)}일"
        }
    }
    
    /**
     * 오래된 알림 정리 (5분 이상 된 알림은 무효할 가능성이 높음)
     */
    fun cleanupOldNotifications() {
        try {
            val cutoffTime = System.currentTimeMillis() - (5 * 60 * 1000L) // 5분
            var removedCount = 0
            
            // ConcurrentHashMap을 안전하게 순회하면서 정리
            val iterator = notificationCache.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val sbn = entry.value
                
                // StatusBarNotification의 postTime을 확인 (API level에 따라 다를 수 있음)
                if (sbn.postTime < cutoffTime) {
                    iterator.remove()
                    removedCount++
                }
            }
            
            if (removedCount > 0) {
                Log.d(TAG, "🧹 Cleaned up $removedCount old notifications")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}