package com.example.kakaomiddleware

import java.text.SimpleDateFormat
import java.util.*

/**
 * StatusBarNotification에서 추출한 RemoteInput 핵심 정보
 * SharedPreferences에 저장 가능한 직렬화 가능한 데이터 클래스
 */
data class RemoteInputInfo(
    val chatId: String,                    // 채팅방 ID
    val chatType: String,                  // "personal" 또는 "group"
    val chatName: String,                  // 채팅방 이름
    val remoteInputKey: String,            // RemoteInput의 resultKey
    val notificationKey: String,           // StatusBarNotification의 key
    val packageName: String,               // "com.kakao.talk"
    val postTime: Long,                    // 알림 생성 시간
    val lastUpdateTime: Long,              // 마지막 업데이트 시간
    val isActive: Boolean = true,          // 활성 상태
    
    // Notification extras 정보
    val title: String?,                    // 발신자 이름
    val text: String?,                     // 메시지 내용
    val subText: String?,                  // 그룹명
    val isGroupConversation: Boolean,      // 그룹 채팅 여부
    
    // PendingIntent 재료 정보 (재현용)
    val pendingIntentRequestCode: Int = 0, // PendingIntent의 요청 코드
    val pendingIntentAction: String? = null, // Intent의 Action
    val pendingIntentExtras: Map<String, String> = emptyMap() // Intent의 주요 Extra 데이터
) {
    
    /**
     * 포맷된 시간 문자열
     */
    val formattedTime: String
        get() = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())
            .format(Date(lastUpdateTime))
    
    /**
     * 알림 경과 시간 (분 단위)
     */
    val ageMinutes: Long
        get() = (System.currentTimeMillis() - postTime) / (60 * 1000)
    
    /**
     * 알림이 유효한지 확인 (항상 유효 - 무한 보존)
     */
    val isValid: Boolean
        get() = true  // 모든 저장된 RemoteInput 정보는 항상 유효
    
    /**
     * 표시용 채팅방 이름
     */
    val displayName: String
        get() = when (chatType) {
            "personal" -> "👤 $chatName"
            "group" -> "👥 $chatName"
            else -> chatName
        }
    
    companion object {
        /**
         * StatusBarNotification에서 RemoteInputInfo 추출
         */
        fun fromStatusBarNotification(chatId: String, sbn: android.service.notification.StatusBarNotification): RemoteInputInfo? {
            try {
                val notification = sbn.notification
                val extras = notification.extras
                
                // RemoteInput 정보 추출
                val actions = notification.actions
                val replyAction = actions?.find { it.remoteInputs?.isNotEmpty() == true }
                val remoteInput = replyAction?.remoteInputs?.get(0)
                
                if (remoteInput == null) {
                    return null // RemoteInput이 없으면 저장 불가
                }
                
                // 채팅방 타입 판단
                val isGroup = extras?.getBoolean("android.isGroupConversation", false) ?: false
                val chatType = if (isGroup) "group" else "personal"
                
                // 채팅방 이름 추출
                val title = extras?.getString("android.title") ?: ""
                val subText = extras?.getString("android.subText") ?: ""
                val chatName = if (isGroup) subText else title
                
                return RemoteInputInfo(
                    chatId = chatId,
                    chatType = chatType,
                    chatName = chatName,
                    remoteInputKey = remoteInput.resultKey ?: "reply_message",
                    notificationKey = sbn.key ?: "",
                    packageName = sbn.packageName ?: "",
                    postTime = sbn.postTime,
                    lastUpdateTime = System.currentTimeMillis(),
                    title = title,
                    text = extras?.getString("android.text"),
                    subText = subText,
                    isGroupConversation = isGroup
                )
                
            } catch (e: Exception) {
                return null
            }
        }
    }
}