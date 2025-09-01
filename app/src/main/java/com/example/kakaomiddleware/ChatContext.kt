package com.example.kakaomiddleware

import java.text.SimpleDateFormat
import java.util.*

/**
 * 채팅방 컨텍스트 정보를 저장하는 데이터 클래스
 * RemoteInput을 통한 답장 기능을 위해 필요한 모든 정보를 포함
 */
data class ChatContext(
    val chatId: String,                    // 고유 채팅방 ID
    val chatType: ChatType,               // 채팅방 타입 (개인/그룹)
    val chatName: String,                 // 채팅방 이름
    val lastSender: String,               // 마지막 발신자
    val remoteInputKey: String,           // RemoteInput의 resultKey
    val lastUpdateTime: Long,             // 마지막 업데이트 시간
    val isActive: Boolean = true          // 컨텍스트 활성화 상태
) {
    
    /**
     * 채팅방 타입 구분
     */
    enum class ChatType {
        PERSONAL,   // 1:1 개인 채팅
        GROUP       // 그룹 채팅
    }
    
    /**
     * 포맷된 시간 문자열 반환
     */
    val formattedTime: String
        get() = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            .format(Date(lastUpdateTime))
    
    /**
     * 채팅방 표시 이름 (타입 아이콘 포함)
     */
    val displayName: String
        get() = when (chatType) {
            ChatType.PERSONAL -> "👤 $chatName"
            ChatType.GROUP -> "👥 $chatName"
        }
    
    companion object {
        /**
         * 채팅방 고유 ID 생성
         * @param chatType 채팅방 타입
         * @param chatName 채팅방 이름
         * @return 고유 채팅방 ID
         */
        fun generateChatId(chatType: ChatType, chatName: String): String {
            return when (chatType) {
                ChatType.PERSONAL -> "personal_$chatName"
                ChatType.GROUP -> "group_$chatName"
            }
        }
    }
}

/**
 * 채팅방 요약 정보 (UI 표시용)
 */
data class ChatSummary(
    val chatId: String,
    val chatName: String,
    val chatType: ChatContext.ChatType,
    val lastSender: String,
    val lastUpdateTime: Long
) {
    val formattedTime: String
        get() = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            .format(Date(lastUpdateTime))
    
    val displayName: String
        get() = when (chatType) {
            ChatContext.ChatType.PERSONAL -> "👤 $chatName"
            ChatContext.ChatType.GROUP -> "👥 $chatName"
        }
}