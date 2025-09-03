package com.example.kakaomiddleware

import android.content.Context
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * KakaoTalk 메시지 전송 관리자
 * 
 * 단일화된 메시지 전송 로직:
 * 1. 메모리 캐시(NotificationStorage)에서 StatusBarNotification 탐색
 * 2. 활성 알림에서 직접 탐색 후 자동 캐시에 저장
 * 
 * ChatRepository와 NotificationStorage를 통합하여 안정적이고 빠른 메시지 전송 제공
 */
class ReplyManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ReplyManager"
        
        @Volatile
        private var instance: ReplyManager? = null
        
        /**
         * 싱글톤 인스턴스 반환
         */
        fun getInstance(context: Context): ReplyManager {
            return instance ?: synchronized(this) {
                instance ?: ReplyManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val chatRepository = ChatRepository.getInstance(context)
    
    /**
     * 특정 채팅방에 메시지 발송 (단일화된 로직)
     * 1단계: 메모리 캐시에서 알림 탐색
     * 2단계: 활성 알림에서 탐색 후 캐시에 저장
     * @param chatId 채팅방 ID (예: "personal_홍길동", "group_개발팀")
     * @param message 발송할 메시지
     * @return 발송 성공 여부
     */
    suspend fun sendMessageToChat(chatId: String, message: String): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                Log.d(TAG, "🚀 Attempting to send message to $chatId: '$message'")
                
                // 1단계: 메모리 캐시에서 찾기 (가장 빠름)
                val cachedNotification = NotificationStorage.getLatestNotification(chatId)
                if (cachedNotification != null) {
                    Log.d(TAG, "✅ Using cached notification for $chatId")
                    val success = sendViaRemoteInput(cachedNotification, message)
                    if (success) {
                        updateSentMessageStats(chatId)
                        Log.d(TAG, "✅ Message sent successfully via cache to $chatId")
                    }
                    return@withContext success
                }
                
                // 2단계: 활성 알림에서 찾기 (폴백)
                Log.d(TAG, "🔍 No cached notification, searching active notifications...")
                val activeNotification = findActiveNotificationForChat(chatId)
                if (activeNotification != null) {
                    Log.d(TAG, "✅ Found active notification, caching for future use")
                    NotificationStorage.storeNotification(chatId, activeNotification)
                    val success = sendViaRemoteInput(activeNotification, message)
                    if (success) {
                        updateSentMessageStats(chatId)
                        Log.d(TAG, "✅ Message sent successfully via active notification to $chatId")
                    }
                    return@withContext success
                }
                
                // 실패: 활성 알림이 없으면 전송 불가
                Log.w(TAG, "❌ No notification available for chat: $chatId")
                Log.w(TAG, "   해당 채팅방에 새 메시지가 오면 답장을 보낼 수 있습니다.")
                return@withContext false
                
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message to chat: $chatId", e)
                return@withContext false
            }
        }
    }
    
    /**
     * 여러 채팅방에 동시에 메시지 발송 (방송 기능)
     * @param chatIds 대상 채팅방 ID 목록
     * @param message 발송할 메시지
     * @return 성공한 채팅방 개수
     */
    suspend fun broadcastMessage(chatIds: List<String>, message: String): Int {
        var successCount = 0
        
        chatIds.forEach { chatId ->
            try {
                if (sendMessageToChat(chatId, message)) {
                    successCount++
                }
                // 각 전송 간 짧은 대기 (KakaoTalk 서버 부하 방지)
                Thread.sleep(200)
            } catch (e: Exception) {
                Log.e(TAG, "Error broadcasting to $chatId", e)
            }
        }
        
        Log.d(TAG, "📢 Broadcast completed: $successCount/${chatIds.size} messages sent")
        return successCount
    }
    
    /**
     * 모든 활성 채팅방 목록 조회
     * @return 채팅방 요약 정보 목록
     */
    fun getAvailableChats(): List<ChatSummary> {
        return chatRepository.getAllActiveChatContexts().map { context ->
            ChatSummary(
                chatId = context.chatId,
                chatName = context.chatName,
                chatType = context.chatType,
                lastSender = context.lastSender,
                lastUpdateTime = context.lastUpdateTime
            )
        }
    }
    
    /**
     * 그룹 채팅방만 조회
     */
    fun getGroupChats(): List<ChatSummary> {
        return getAvailableChats().filter { it.chatType == ChatContext.ChatType.GROUP }
    }
    
    /**
     * 개인 채팅방만 조회
     */
    fun getPersonalChats(): List<ChatSummary> {
        return getAvailableChats().filter { it.chatType == ChatContext.ChatType.PERSONAL }
    }
    
    /**
     * 특정 타입의 채팅방만 조회
     */
    fun getChatsByType(chatType: ChatContext.ChatType): List<ChatSummary> {
        return getAvailableChats().filter { it.chatType == chatType }
    }
    
    /**
     * 최근 활성 채팅방 조회 (지정된 시간 내)
     * @param hoursAgo 몇 시간 전까지의 채팅방
     */
    fun getRecentActiveChats(hoursAgo: Int = 24): List<ChatSummary> {
        val cutoffTime = System.currentTimeMillis() - (hoursAgo * 60 * 60 * 1000L)
        return getAvailableChats().filter { it.lastUpdateTime > cutoffTime }
    }
    
    /**
     * 채팅방 검색
     * @param query 검색어
     */
    fun searchChats(query: String): List<ChatSummary> {
        val lowercaseQuery = query.lowercase()
        return getAvailableChats().filter { summary ->
            summary.chatName.lowercase().contains(lowercaseQuery) ||
            summary.lastSender.lowercase().contains(lowercaseQuery)
        }
    }
    
    /**
     * 활성 알림에서 직접 채팅방 알림 찾기
     * @param chatId 채팅방 ID
     * @return 해당하는 StatusBarNotification (없으면 null)
     */
    private fun findActiveNotificationForChat(chatId: String): StatusBarNotification? {
        return try {
            Log.d(TAG, "🔍 Searching active notifications for chat: $chatId")
            
            // ChatRepository에서 채팅방 정보 찾기
            val chatContext = chatRepository.getChatContext(chatId)
            if (chatContext == null) {
                Log.w(TAG, "❌ No chat context found for: $chatId")
                return null
            }
            
            val chatName = chatContext.chatName
            Log.d(TAG, "   - Chat name: $chatName")
            
            // 활성 알림에서 찾기
            val listenerService = KakaoNotificationListenerService.getInstance()
            if (listenerService == null) {
                Log.w(TAG, "❌ NotificationListenerService not available")
                return null
            }
            
            val activeNotification = ActiveNotificationFinder.findActiveNotificationForChat(
                listenerService,
                chatId,
                chatName
            )
            
            if (activeNotification != null) {
                Log.d(TAG, "✅ Found active notification for: $chatName")
            } else {
                Log.w(TAG, "❌ No active notification found for: $chatName")
            }
            
            return activeNotification
            
        } catch (e: Exception) {
            Log.e(TAG, "Error finding active notification for $chatId", e)
            return null
        }
    }
    
    /**
     * RemoteInput을 이용한 실제 메시지 전송 (공통 로직)
     * @param sbn StatusBarNotification 객체
     * @param message 전송할 메시지
     * @return 전송 성공 여부
     */
    private fun sendViaRemoteInput(sbn: StatusBarNotification, message: String): Boolean {
        return try {
            Log.d(TAG, "🔧 Sending message via RemoteInput")
            Log.d(TAG, "   - Message: '$message'")
            Log.d(TAG, "   - Notification key: ${sbn.key}")
            Log.d(TAG, "   - Post time: ${sbn.postTime}")
            
            val remoteInputHijacker = RemoteInputHijacker(context)
            val success = remoteInputHijacker.injectResponse(sbn, message)
            
            if (success) {
                Log.d(TAG, "✅ Message sent successfully via RemoteInput")
            } else {
                Log.e(TAG, "❌ RemoteInput message send failed")
            }
            
            success
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message via RemoteInput", e)
            false
        }
    }
    
    
    
    /**
     * 데이터베이스에서 사용 가능한 채팅방 목록 조회
     */
    fun getAvailableChatsFromStorage(): List<ChatSummary> {
        return try {
            getAvailableChats() // 이미 ChatRepository를 사용하는 기존 메서드
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available chats from storage", e)
            emptyList()
        }
    }
    
    /**
     * 저장소 통계 정보 조회 (데이터베이스 기반)
     */
    fun getStorageStats(): Map<String, Int> {
        return try {
            val availableChats = getAvailableChats()
            val activeChats = availableChats // ChatRepository에서 가져오는 것은 모두 활성 상태
            
            mapOf(
                "totalRemoteInputs" to availableChats.size,
                "activeRemoteInputs" to activeChats.size
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting storage stats", e)
            emptyMap()
        }
    }
    
    /**
     * 전송 성공 통계 업데이트 (향후 분석용)
     */
    private fun updateSentMessageStats(chatId: String) {
        try {
            val statsPrefs = context.getSharedPreferences("reply_stats", Context.MODE_PRIVATE)
            val currentCount = statsPrefs.getInt("sent_$chatId", 0)
            
            statsPrefs.edit()
                .putInt("sent_$chatId", currentCount + 1)
                .putLong("last_sent_$chatId", System.currentTimeMillis())
                .apply()
                
            Log.v(TAG, "Updated stats for $chatId: ${currentCount + 1} messages sent")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update stats for $chatId", e)
        }
    }
    
    /**
     * 채팅방 컨텍스트 유효성 검사
     * @param chatId 검사할 채팅방 ID
     * @return 유효성 검사 결과
     */
    fun validateChatContext(chatId: String): ChatContextValidation {
        val context = chatRepository.getChatContext(chatId)
        
        return when {
            context == null -> ChatContextValidation(
                isValid = false,
                reason = "Chat context not found"
            )
            !context.isActive -> ChatContextValidation(
                isValid = false,
                reason = "Chat context is inactive"
            )
            context.remoteInputKey.isEmpty() -> ChatContextValidation(
                isValid = false,
                reason = "RemoteInput key is empty"
            )
            System.currentTimeMillis() - context.lastUpdateTime > 24 * 60 * 60 * 1000 -> ChatContextValidation(
                isValid = false,
                reason = "Context is too old (>24h)"
            )
            else -> ChatContextValidation(
                isValid = true,
                reason = "Valid context",
                chatContext = context
            )
        }
    }
    
    /**
     * 저장소 통계 조회 (디버깅/모니터링용)
     */
    fun getReplyManagerStats(): Map<String, Any> {
        val storageStats = chatRepository.getStorageStats()
        val availableChats = getAvailableChats()
        
        return mapOf(
            "totalAvailableChats" to availableChats.size,
            "groupChats" to availableChats.count { it.chatType == ChatContext.ChatType.GROUP },
            "personalChats" to availableChats.count { it.chatType == ChatContext.ChatType.PERSONAL },
            "recentActiveChats" to getRecentActiveChats(24).size,
            "storageStats" to storageStats
        )
    }
}

/**
 * 채팅방 컨텍스트 유효성 검사 결과
 */
data class ChatContextValidation(
    val isValid: Boolean,
    val reason: String,
    val chatContext: ChatContext? = null
)