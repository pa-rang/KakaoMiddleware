package com.example.kakaomiddleware

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * 활성 알림에서 StatusBarNotification을 찾는 유틸리티
 * NotificationListenerService의 getActiveNotifications()를 활용
 */
object ActiveNotificationFinder {
    private const val TAG = "ActiveNotificationFinder"
    
    /**
     * 특정 채팅방의 활성 알림을 찾아 반환
     * @param chatId 채팅방 ID
     * @param chatName 채팅방 이름 (검색 조건)
     * @return 해당하는 StatusBarNotification (없으면 null)
     */
    fun findActiveNotificationForChat(
        listenerService: NotificationListenerService?, 
        chatId: String,
        chatName: String
    ): StatusBarNotification? {
        
        if (listenerService == null) {
            Log.w(TAG, "NotificationListenerService is not available")
            return null
        }
        
        return try {
            Log.d(TAG, "🔍 Searching active notifications for chat: $chatName")
            
            val activeNotifications = listenerService.activeNotifications
            Log.d(TAG, "📱 Found ${activeNotifications.size} active notifications")
            
            // KakaoTalk 패키지의 알림만 필터링
            val kakaoNotifications = activeNotifications.filter { sbn ->
                sbn.packageName == "com.kakao.talk"
            }
            
            Log.d(TAG, "💬 Found ${kakaoNotifications.size} KakaoTalk notifications")
            
            // 채팅방 이름으로 매칭되는 알림 찾기
            kakaoNotifications.forEach { sbn ->
                val notification = sbn.notification
                val extras = notification.extras
                
                val title = extras?.getString("android.title") ?: ""
                val subText = extras?.getString("android.subText") ?: ""
                val isGroup = extras?.getBoolean("android.isGroupConversation", false) ?: false
                
                // 채팅방 이름 매칭
                val notificationChatName = if (isGroup) subText else title
                
                Log.v(TAG, "📋 Checking notification: '$notificationChatName' vs '$chatName'")
                
                if (notificationChatName == chatName) {
                    Log.d(TAG, "✅ Found matching notification for: $chatName")
                    Log.d(TAG, "   - Key: ${sbn.key}")
                    Log.d(TAG, "   - PostTime: ${sbn.postTime}")
                    Log.d(TAG, "   - HasActions: ${notification.actions?.isNotEmpty() == true}")
                    
                    // RemoteInput 액션이 있는지 확인
                    val hasRemoteInput = notification.actions?.any { action ->
                        action.remoteInputs?.isNotEmpty() == true
                    } ?: false
                    
                    if (hasRemoteInput) {
                        Log.d(TAG, "✅ Notification has RemoteInput - can be used for reply")
                        return sbn
                    } else {
                        Log.w(TAG, "⚠️ Notification found but has no RemoteInput")
                    }
                }
            }
            
            Log.w(TAG, "❌ No active notification found for chat: $chatName")
            return null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching active notifications", e)
            return null
        }
    }
    
    /**
     * 현재 활성 알림 상태를 로그로 출력 (디버깅용)
     */
    fun logActiveNotifications(listenerService: NotificationListenerService?) {
        if (listenerService == null) {
            Log.w(TAG, "NotificationListenerService is not available")
            return
        }
        
        try {
            Log.d(TAG, "📋 === Active Notifications Debug ===")
            
            val activeNotifications = listenerService.activeNotifications
            Log.d(TAG, "📊 Total active notifications: ${activeNotifications.size}")
            
            val kakaoNotifications = activeNotifications.filter { it.packageName == "com.kakao.talk" }
            Log.d(TAG, "💬 KakaoTalk notifications: ${kakaoNotifications.size}")
            
            kakaoNotifications.forEach { sbn ->
                val notification = sbn.notification
                val extras = notification.extras
                
                Log.d(TAG, "📱 Notification:")
                Log.d(TAG, "  ├─ Key: ${sbn.key}")
                Log.d(TAG, "  ├─ PostTime: ${sbn.postTime}")
                Log.d(TAG, "  ├─ Title: ${extras?.getString("android.title")}")
                Log.d(TAG, "  ├─ SubText: ${extras?.getString("android.subText")}")
                Log.d(TAG, "  ├─ IsGroup: ${extras?.getBoolean("android.isGroupConversation", false)}")
                Log.d(TAG, "  └─ HasRemoteInput: ${notification.actions?.any { it.remoteInputs?.isNotEmpty() == true }}")
            }
            
            Log.d(TAG, "📋 === End of Active Notifications Debug ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error logging active notifications", e)
        }
    }
}