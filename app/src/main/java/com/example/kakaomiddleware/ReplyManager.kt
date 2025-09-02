package com.example.kakaomiddleware

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 저장된 채팅방 컨텍스트를 이용하여 임의 답장을 보내는 관리자
 * ChatRepository에 저장된 RemoteInput 정보를 활용하여 KakaoTalk 채팅방에 메시지 전송
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
     * 특정 채팅방에 메시지 발송
     * @param chatId 채팅방 ID (예: "personal_홍길동", "group_개발팀")
     * @param message 발송할 메시지
     * @return 발송 성공 여부
     */
    suspend fun sendMessageToChat(chatId: String, message: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val chatContext = chatRepository.getChatContext(chatId)
                if (chatContext == null || !chatContext.isActive) {
                    Log.e(TAG, "❌ Chat context not found or inactive: $chatId")
                    return@withContext false
                }
                
                Log.d(TAG, "🚀 Attempting to send message to ${chatContext.displayName}: '$message'")
                
                // ⚠️ 핵심 제약사항: 
                // PendingIntent는 원본 StatusBarNotification에서만 유효하며,
                // 시간이 지나면 무효화될 수 있음
                // 
                // 현재 구현은 개념 증명용이며, 실제로는 더 복잡한 접근이 필요할 수 있음
                
                val success = sendRemoteInputMessage(chatContext, message)
                
                if (success) {
                    Log.d(TAG, "✅ Message sent successfully to $chatId")
                    
                    // 성공적으로 전송된 경우 통계 업데이트
                    updateSentMessageStats(chatContext)
                    
                } else {
                    Log.e(TAG, "❌ Failed to send message to $chatId")
                }
                
                success
                
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message to chat: $chatId", e)
                false
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
     * RemoteInput을 이용한 실제 메시지 전송
     * 최신 StatusBarNotification을 이용해 실제 RemoteInput 하이재킹 수행
     */
    private suspend fun sendRemoteInputMessage(chatContext: ChatContext, message: String): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                Log.d(TAG, "🔧 Attempting RemoteInput message injection")
                Log.d(TAG, "   - Chat: ${chatContext.displayName}")
                Log.d(TAG, "   - Key: ${chatContext.remoteInputKey}")
                Log.d(TAG, "   - Message: '$message'")
                
                // 전략: ChatContextStorage에서 최신 알림을 가져와서 실시간 하이재킹
                val latestNotification = getLatestNotificationForChat(chatContext.chatId)
                
                if (latestNotification != null) {
                    // 공통 메시지 전송 로직 사용
                    val success = sendMessageViaStatusBarNotification(latestNotification, message, "NotificationStorage")
                    return@withContext success
                } else {
                    // 최신 알림이 없는 경우 - 영구 저장소 기반 전송 시도 (개선된 로직 사용)
                    Log.w(TAG, "⚠️ No recent notification available for ${chatContext.chatId}")
                    Log.i(TAG, "🔄 Attempting message send via persistent storage fallback")
                    
                    val persistentSuccess = sendMessageViaPersistentStorageOnly(chatContext.chatId, message)
                    
                    if (persistentSuccess) {
                        Log.d(TAG, "✅ Successfully sent message via persistent storage fallback")
                        return@withContext true
                    } else {
                        Log.w(TAG, "❌ Both notification hijacking and persistent storage failed")
                        Log.w(TAG, "   해당 채팅방에 새 메시지가 오면 답장을 보낼 수 있습니다.")
                        return@withContext false
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in sendRemoteInputMessage", e)
                return@withContext false
            }
        }
    }
    
    /**
     * 특정 채팅방의 최신 StatusBarNotification 조회
     * NotificationStorage에서 캐시된 최신 알림을 가져옴
     */
    private fun getLatestNotificationForChat(chatId: String): StatusBarNotification? {
        Log.d(TAG, "🔍 Looking for latest notification for chat: $chatId")
        
        // 현재 저장된 모든 알림 로그 출력 (디버깅용)
        NotificationStorage.logAllStoredNotifications()
        
        val notification = NotificationStorage.getLatestNotification(chatId)
        
        if (notification != null) {
            Log.d(TAG, "✅ Found cached notification for: $chatId")
            
            // 알림이 너무 오래된 경우 경고 (5분 이상)
            val ageMinutes = (System.currentTimeMillis() - notification.postTime) / (60 * 1000)
            if (ageMinutes > 5) {
                Log.w(TAG, "⚠️ Notification is $ageMinutes minutes old - may be invalid")
            }
            
        } else {
            Log.w(TAG, "❌ No cached notification for: $chatId")
            
            // 영구 저장소에서 RemoteInput 정보 확인
            try {
                val persistentStorage = PersistentRemoteInputStorage.getInstance(context)
                val remoteInputInfo = persistentStorage.getRemoteInputInfo(chatId)
                
                if (remoteInputInfo != null) {
                    Log.i(TAG, "📝 Found persistent RemoteInput info for: $chatId (infinite retention)")
                    Log.i(TAG, "   - Age: ${remoteInputInfo.ageMinutes}분 (${remoteInputInfo.formattedTime})")
                    Log.i(TAG, "   - RemoteInputKey: ${remoteInputInfo.remoteInputKey}")
                    Log.i(TAG, "   - Status: Always valid (no expiration)")
                    
                    // 영구 저장소의 모든 정보도 로그 출력
                    persistentStorage.logAllStoredRemoteInputs()
                } else {
                    Log.w(TAG, "   영구 저장소에도 RemoteInput 정보가 없습니다.")
                    Log.w(TAG, "   해당 채팅방에서 메시지를 받은 후 답장을 시도해주세요.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking persistent RemoteInput storage", e)
            }
        }
        
        return notification
    }
    
    /**
     * StatusBarNotification을 사용한 공통 메시지 전송 로직
     * @param sbn StatusBarNotification 객체
     * @param message 전송할 메시지
     * @param source 전송 소스 (로깅용)
     * @return 전송 성공 여부
     */
    private fun sendMessageViaStatusBarNotification(sbn: StatusBarNotification, message: String, source: String): Boolean {
        return try {
            Log.d(TAG, "🔧 Sending message via $source")
            Log.d(TAG, "   - Message: '$message'")
            Log.d(TAG, "   - Notification key: ${sbn.key}")
            Log.d(TAG, "   - Post time: ${sbn.postTime}")
            
            val remoteInputHijacker = RemoteInputHijacker(context)
            val success = remoteInputHijacker.injectResponse(sbn, message)
            
            if (success) {
                Log.d(TAG, "✅ Successfully sent message via $source")
            } else {
                Log.e(TAG, "❌ Message send failed via $source")
            }
            
            success
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message via $source", e)
            false
        }
    }
    
    /**
     * 영구 저장소만을 사용한 메시지 전송 (NotificationStorage 무시)
     * @param chatId 채팅방 ID
     * @param message 전송할 메시지
     * @return 전송 성공 여부
     */
    suspend fun sendMessageViaPersistentStorageOnly(chatId: String, message: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔄 Attempting PERSISTENT STORAGE based message send")
                Log.d(TAG, "   - ChatId: $chatId")
                Log.d(TAG, "   - Message: '$message'")
                
                // 1. 먼저 NotificationStorage에서 캐시된 알림 확인
                val cachedNotification = NotificationStorage.getLatestNotification(chatId)
                if (cachedNotification != null) {
                    Log.d(TAG, "✅ Found cached notification, using it directly")
                    val success = sendMessageViaStatusBarNotification(cachedNotification, message, "PersistentStorage-Cached")
                    return@withContext success
                }
                
                // 2. 캐시에 없으면 영구 저장소에서 정보 조회
                val persistentStorage = PersistentRemoteInputStorage.getInstance(context)
                val remoteInputInfo = persistentStorage.getRemoteInputInfo(chatId)
                
                if (remoteInputInfo == null) {
                    Log.w(TAG, "❌ No RemoteInput info found for: $chatId")
                    return@withContext false
                }
                
                Log.d(TAG, "✅ Found RemoteInput info: ${remoteInputInfo.displayName}")
                
                // 3. 활성 알림에서 해당 채팅방 알림 찾기
                val listenerService = KakaoNotificationListenerService.getInstance()
                if (listenerService == null) {
                    Log.w(TAG, "❌ NotificationListenerService not available")
                    return@withContext false
                }
                
                val activeNotification = ActiveNotificationFinder.findActiveNotificationForChat(
                    listenerService,
                    remoteInputInfo.chatId,
                    remoteInputInfo.chatName
                )
                
                if (activeNotification == null) {
                    Log.w(TAG, "❌ No active notification found for: ${remoteInputInfo.chatName}")
                    return@withContext false
                }
                
                Log.d(TAG, "✅ Found active notification, caching it for reuse")
                
                // 4. 활성 알림을 NotificationStorage에 저장 (연속 전송 지원)
                NotificationStorage.storeNotification(chatId, activeNotification)
                
                // 5. 이제 캐시된 알림으로 전송 (일반 전송과 동일한 방식)
                val success = sendMessageViaStatusBarNotification(activeNotification, message, "PersistentStorage-Active")
                
                if (success) {
                    Log.d(TAG, "✅ Persistent storage message send successful")
                } else {
                    Log.e(TAG, "❌ Persistent storage message send failed")
                }
                
                return@withContext success
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in sendMessageViaPersistentStorageOnly", e)
                return@withContext false
            }
        }
    }
    
    /**
     * 영구 저장소에서 사용 가능한 채팅방 목록 조회
     */
    fun getAvailableChatsFromPersistentStorage(): List<RemoteInputInfo> {
        return try {
            val persistentReplyManager = PersistentReplyManager(context)
            persistentReplyManager.getAvailableChatsFromStorage()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available chats from persistent storage", e)
            emptyList()
        }
    }
    
    /**
     * 영구 저장소 통계 정보 조회
     */
    fun getPersistentStorageStats(): Map<String, Int> {
        return try {
            val persistentReplyManager = PersistentReplyManager(context)
            persistentReplyManager.getStorageStats()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting persistent storage stats", e)
            emptyMap()
        }
    }
    
    /**
     * 전송 성공 통계 업데이트 (향후 분석용)
     */
    private fun updateSentMessageStats(chatContext: ChatContext) {
        // SharedPreferences에 통계 저장
        val statsPrefs = context.getSharedPreferences("reply_stats", Context.MODE_PRIVATE)
        val currentCount = statsPrefs.getInt("sent_${chatContext.chatId}", 0)
        
        statsPrefs.edit()
            .putInt("sent_${chatContext.chatId}", currentCount + 1)
            .putLong("last_sent_${chatContext.chatId}", System.currentTimeMillis())
            .apply()
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