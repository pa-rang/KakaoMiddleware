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