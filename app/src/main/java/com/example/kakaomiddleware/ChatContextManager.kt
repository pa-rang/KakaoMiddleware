package com.example.kakaomiddleware

import android.app.RemoteInput
import android.content.Context
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * StatusBarNotification에서 채팅방 컨텍스트를 추출하고 저장하는 관리자
 * KakaoTalk 알림에서 RemoteInput 정보를 추출하여 나중에 답장할 수 있도록 컨텍스트를 보관
 */
class ChatContextManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ChatContextManager"
        private const val KAKAOTALK_PACKAGE = "com.kakao.talk"
    }
    
    private val chatRepository = ChatRepository.getInstance(context)
    
    /**
     * StatusBarNotification에서 채팅방 컨텍스트 추출 및 저장
     * @param sbn StatusBarNotification 객체
     * @param chatName 채팅방 이름
     * @param chatType 채팅방 타입 (개인/그룹)
     * @param senderName 발신자 이름
     * @return 추출된 ChatContext (실패시 null)
     */
    fun extractAndSaveChatContext(
        sbn: StatusBarNotification,
        chatName: String,
        chatType: ChatContext.ChatType,
        senderName: String
    ): ChatContext? {
        
        if (sbn.packageName != KAKAOTALK_PACKAGE) {
            Log.w(TAG, "❌ Not a KakaoTalk notification: ${sbn.packageName}")
            return null
        }
        
        try {
            // RemoteInput 추출
            val remoteInput = extractRemoteInput(sbn)
            if (remoteInput == null) {
                Log.w(TAG, "❌ No RemoteInput found in notification for $chatName")
                return null
            }
            
            val chatId = ChatContext.generateChatId(chatType, chatName)
            
            // 기존 컨텍스트 확인 (업데이트인지 신규 생성인지)
            val existingContext = chatRepository.getChatContext(chatId)
            
            val chatContext = ChatContext(
                chatId = chatId,
                chatType = chatType,
                chatName = chatName,
                lastSender = senderName,
                remoteInputKey = remoteInput.resultKey,
                lastUpdateTime = System.currentTimeMillis(),
                isActive = true
            )
            
            if (existingContext != null) {
                // 기존 컨텍스트 업데이트
                chatRepository.updateChatContext(chatContext)
                Log.d(TAG, "🔄 Updated chat context: $chatId (sender: $senderName)")
            } else {
                // 새로운 컨텍스트 생성
                chatRepository.saveChatContext(chatContext)
                Log.d(TAG, "✅ Created new chat context: $chatId (type: $chatType)")
            }
            
            return chatContext
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting chat context for $chatName", e)
            return null
        }
    }
    
    /**
     * StatusBarNotification에서 RemoteInput 추출
     * @param sbn StatusBarNotification 객체
     * @return RemoteInput 객체 (없으면 null)
     */
    private fun extractRemoteInput(sbn: StatusBarNotification): RemoteInput? {
        try {
            val actions = sbn.notification.actions
            if (actions == null) {
                Log.d(TAG, "No actions found in notification")
                return null
            }
            
            // RemoteInput이 있는 액션 찾기 (보통 답장 액션)
            for (action in actions) {
                val remoteInputs = action.remoteInputs
                if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                    val remoteInput = remoteInputs[0]
                    Log.d(TAG, "🔍 Found RemoteInput: ${remoteInput.resultKey}")
                    return remoteInput
                }
            }
            
            Log.d(TAG, "No RemoteInput found in notification actions")
            return null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting RemoteInput", e)
            return null
        }
    }
    
    /**
     * 채팅방 컨텍스트 추출 가능 여부 확인
     * @param sbn StatusBarNotification 객체
     * @return true if 추출 가능, false otherwise
     */
    fun canExtractChatContext(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName != KAKAOTALK_PACKAGE) {
            return false
        }
        
        val actions = sbn.notification.actions
        if (actions == null) {
            return false
        }
        
        // RemoteInput이 있는 액션이 존재하는지 확인
        return actions.any { it.remoteInputs?.isNotEmpty() == true }
    }
    
    /**
     * KakaoNotification을 기반으로 채팅방 컨텍스트 추출 (편의 메서드)
     * @param sbn StatusBarNotification 객체
     * @param kakaoNotification 파싱된 KakaoNotification 객체
     * @return 추출된 ChatContext (실패시 null)
     */
    fun extractChatContextFromKakaoNotification(
        sbn: StatusBarNotification,
        kakaoNotification: KakaoNotification
    ): ChatContext? {
        
        return when (kakaoNotification) {
            is PersonalMessage -> {
                extractAndSaveChatContext(
                    sbn = sbn,
                    chatName = kakaoNotification.sender,
                    chatType = ChatContext.ChatType.PERSONAL,
                    senderName = kakaoNotification.sender
                )
            }
            
            is GroupMessage -> {
                extractAndSaveChatContext(
                    sbn = sbn,
                    chatName = kakaoNotification.groupName,
                    chatType = ChatContext.ChatType.GROUP,
                    senderName = kakaoNotification.sender
                )
            }
            
            is ImageMessage -> {
                // ImageMessage 처리
                if (kakaoNotification.groupName != null) {
                    // 그룹 이미지 메시지
                    extractAndSaveChatContext(
                        sbn = sbn,
                        chatName = kakaoNotification.groupName,
                        chatType = ChatContext.ChatType.GROUP,
                        senderName = kakaoNotification.sender
                    )
                } else {
                    // 개인 이미지 메시지
                    extractAndSaveChatContext(
                        sbn = sbn,
                        chatName = kakaoNotification.sender,
                        chatType = ChatContext.ChatType.PERSONAL,
                        senderName = kakaoNotification.sender
                    )
                }
            }
            
            is UnreadSummary -> {
                // UnreadSummary는 실제 메시지가 아니므로 컨텍스트 추출하지 않음
                Log.d(TAG, "📊 Skipping context extraction for UnreadSummary")
                null
            }
        }
    }
    
    /**
     * 채팅방 컨텍스트 강제 새로고침
     * @param chatId 새로고침할 채팅방 ID
     * @param sbn 최신 StatusBarNotification
     * @return 새로고침된 ChatContext (실패시 null)
     */
    fun refreshChatContext(chatId: String, sbn: StatusBarNotification): ChatContext? {
        try {
            val existingContext = chatRepository.getChatContext(chatId)
            if (existingContext == null) {
                Log.w(TAG, "❌ No existing context found for refresh: $chatId")
                return null
            }
            
            val remoteInput = extractRemoteInput(sbn)
            if (remoteInput == null) {
                Log.w(TAG, "❌ Cannot refresh - no RemoteInput in notification")
                return null
            }
            
            val refreshedContext = existingContext.copy(
                remoteInputKey = remoteInput.resultKey,
                lastUpdateTime = System.currentTimeMillis()
            )
            
            chatRepository.updateChatContext(refreshedContext)
            Log.d(TAG, "🔄 Refreshed chat context: $chatId")
            
            return refreshedContext
            
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing chat context: $chatId", e)
            return null
        }
    }
    
    /**
     * 채팅방 컨텍스트 디버그 정보 출력
     * @param chatId 디버그할 채팅방 ID
     */
    fun debugChatContext(chatId: String) {
        val context = chatRepository.getChatContext(chatId)
        if (context != null) {
            Log.d(TAG, "🔍 Debug Chat Context:")
            Log.d(TAG, "  - ID: ${context.chatId}")
            Log.d(TAG, "  - Type: ${context.chatType}")
            Log.d(TAG, "  - Name: ${context.chatName}")
            Log.d(TAG, "  - Last Sender: ${context.lastSender}")
            Log.d(TAG, "  - RemoteInput Key: ${context.remoteInputKey}")
            Log.d(TAG, "  - Last Update: ${context.formattedTime}")
            Log.d(TAG, "  - Active: ${context.isActive}")
        } else {
            Log.d(TAG, "❌ No context found for: $chatId")
        }
    }
}