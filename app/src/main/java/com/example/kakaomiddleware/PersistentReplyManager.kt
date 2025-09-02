package com.example.kakaomiddleware

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 영구 저장소의 RemoteInput 정보를 활용한 메시지 전송 관리자
 * StatusBarNotification이 없어도 저장된 RemoteInput 정보로 메시지 전송 시도
 */
class PersistentReplyManager(private val context: Context) {
    
    companion object {
        private const val TAG = "PersistentReplyManager"
    }
    
    /**
     * 영구 저장소 기반 메시지 전송 (메인 메소드)
     * @param chatId 채팅방 ID
     * @param message 전송할 메시지
     * @return 전송 성공 여부
     */
    suspend fun sendMessageFromPersistentStorage(chatId: String, message: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🚀 Attempting persistent storage message send")
                Log.d(TAG, "   - ChatId: $chatId")
                Log.d(TAG, "   - Message: '$message'")
                
                val persistentStorage = PersistentRemoteInputStorage.getInstance(context)
                val remoteInputInfo = persistentStorage.getRemoteInputInfo(chatId)
                
                if (remoteInputInfo == null) {
                    Log.w(TAG, "❌ No RemoteInput info found for: $chatId")
                    return@withContext false
                }
                
                // 유효성 검사 제거 - 모든 저장된 정보는 항상 유효 (무한 보존)
                Log.d(TAG, "✅ Found RemoteInput info (infinite retention mode):")
                Log.d(TAG, "   - ChatName: ${remoteInputInfo.displayName}")
                Log.d(TAG, "   - RemoteInputKey: ${remoteInputInfo.remoteInputKey}")
                Log.d(TAG, "   - Age: ${remoteInputInfo.ageMinutes} minutes (always valid)")
                
                // 활성 알림을 찾아서 RemoteInput 하이재킹 시도
                val success = tryActiveNotificationHijacking(remoteInputInfo, message) ||
                             tryRemoteInputDirectSend(remoteInputInfo, message)
                
                if (success) {
                    Log.d(TAG, "✅ Message sent successfully via persistent storage")
                } else {
                    Log.e(TAG, "❌ All persistent storage send methods failed")
                }
                
                return@withContext success
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in sendMessageFromPersistentStorage", e)
                return@withContext false
            }
        }
    }
    
    /**
     * 방법 1: 활성 알림을 찾아서 RemoteInput 하이재킹 (최우선)
     */
    private fun tryActiveNotificationHijacking(info: RemoteInputInfo, message: String): Boolean {
        return try {
            Log.d(TAG, "🔧 Attempting active notification hijacking")
            
            // NotificationListenerService 인스턴스 가져오기
            val listenerService = getNotificationListenerService()
            if (listenerService == null) {
                Log.w(TAG, "❌ NotificationListenerService not available")
                return false
            }
            
            // 활성 알림에서 해당 채팅방 알림 찾기
            val activeNotification = ActiveNotificationFinder.findActiveNotificationForChat(
                listenerService, 
                info.chatId, 
                info.chatName
            )
            
            if (activeNotification == null) {
                Log.w(TAG, "❌ No active notification found for: ${info.chatName}")
                return false
            }
            
            Log.d(TAG, "✅ Found active notification, attempting RemoteInput hijacking")
            
            // RemoteInputHijacker 사용하여 실제 하이재킹
            val remoteInputHijacker = RemoteInputHijacker(context)
            val success = remoteInputHijacker.injectResponse(activeNotification, message)
            
            if (success) {
                Log.d(TAG, "✅ Active notification hijacking successful")
            } else {
                Log.w(TAG, "❌ Active notification hijacking failed")
            }
            
            return success
            
        } catch (e: Exception) {
            Log.w(TAG, "❌ Active notification hijacking failed", e)
            return false
        }
    }
    
    /**
     * NotificationListenerService 인스턴스 가져오기
     */
    private fun getNotificationListenerService(): KakaoNotificationListenerService? {
        return try {
            val instance = KakaoNotificationListenerService.getInstance()
            if (instance != null) {
                Log.d(TAG, "✅ NotificationListenerService instance found")
            } else {
                Log.w(TAG, "❌ NotificationListenerService instance not available")
            }
            instance
        } catch (e: Exception) {
            Log.e(TAG, "Error getting NotificationListenerService instance", e)
            null
        }
    }
    
    /**
     * 방법 2: 저장된 RemoteInput 정보로 직접 Intent 생성하여 전송 (폴백)
     */
    private fun tryRemoteInputDirectSend(info: RemoteInputInfo, message: String): Boolean {
        return try {
            Log.d(TAG, "🔧 Attempting direct RemoteInput send")
            
            // RemoteInput 객체 재생성
            val remoteInput = RemoteInput.Builder(info.remoteInputKey)
                .setLabel("답장")
                .build()
            
            // Intent와 Bundle 준비
            val intent = Intent().apply {
                // KakaoTalk 패키지 설정
                setPackage(info.packageName)
                action = "com.kakao.talk.intent.action.REPLY"
                putExtra("chat_id", info.chatId)
                putExtra("chat_name", info.chatName)
            }
            
            val bundle = Bundle().apply {
                putCharSequence(info.remoteInputKey, message)
            }
            
            // RemoteInput 결과 추가
            RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
            
            // Intent 전송
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            
            Log.d(TAG, "✅ Direct RemoteInput intent sent")
            return true
            
        } catch (e: Exception) {
            Log.w(TAG, "❌ Direct RemoteInput send failed", e)
            return false
        }
    }
    
    /**
     * 방법 2: KakaoTalk 앱으로 직접 메시지 전송 Intent
     */
    private fun tryKakaoTalkIntentSend(info: RemoteInputInfo, message: String): Boolean {
        return try {
            Log.d(TAG, "🔧 Attempting KakaoTalk intent send")
            
            val intent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                setPackage("com.kakao.talk")
                putExtra(Intent.EXTRA_TEXT, message)
                putExtra("chat_room_name", info.chatName)
                putExtra("chat_type", info.chatType)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
            
            Log.d(TAG, "✅ KakaoTalk intent sent")
            return true
            
        } catch (e: Exception) {
            Log.w(TAG, "❌ KakaoTalk intent send failed", e)
            return false
        }
    }
    
    /**
     * 영구 저장소에서 유효한 채팅방 목록 조회
     */
    fun getAvailableChatsFromStorage(): List<RemoteInputInfo> {
        return try {
            val persistentStorage = PersistentRemoteInputStorage.getInstance(context)
            val availableChats = persistentStorage.getValidRemoteInputs()
            
            Log.d(TAG, "📋 Found ${availableChats.size} available chats in persistent storage (infinite retention)")
            availableChats.forEach { info ->
                Log.v(TAG, "   - ${info.displayName} (${info.ageMinutes}분 전, always valid)")
            }
            
            availableChats
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available chats from storage", e)
            emptyList()
        }
    }
    
    /**
     * 특정 채팅방의 영구 저장소 정보 상세 조회
     */
    fun getChatInfoFromStorage(chatId: String): RemoteInputInfo? {
        return try {
            val persistentStorage = PersistentRemoteInputStorage.getInstance(context)
            val info = persistentStorage.getRemoteInputInfo(chatId)
            
            if (info != null) {
                Log.d(TAG, "📱 Chat info from storage (infinite retention):")
                Log.d(TAG, "   - ${info.displayName}")
                Log.d(TAG, "   - RemoteInputKey: ${info.remoteInputKey}")
                Log.d(TAG, "   - Age: ${info.ageMinutes} minutes (always valid)")
                Log.d(TAG, "   - Last update: ${info.formattedTime}")
            } else {
                Log.d(TAG, "❌ No chat info found for: $chatId")
            }
            
            info
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting chat info from storage for $chatId", e)
            null
        }
    }
    
    /**
     * 영구 저장소 통계 정보
     */
    fun getStorageStats(): Map<String, Int> {
        return try {
            val persistentStorage = PersistentRemoteInputStorage.getInstance(context)
            persistentStorage.getStorageStats()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting storage stats", e)
            emptyMap()
        }
    }
}