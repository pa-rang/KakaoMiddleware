package com.example.kakaomiddleware

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class KakaoNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "KakaoNotificationListener"
        private const val KAKAOTALK_PACKAGE = "com.kakao.talk"
        val notificationLog = mutableListOf<KakaoNotification>()
        
        // 서비스 인스턴스 정적 참조 (영구 저장소에서 활성 알림 접근용)
        @Volatile
        private var instance: KakaoNotificationListenerService? = null
        
        fun getInstance(): KakaoNotificationListenerService? = instance
    }
    
    private lateinit var serverRequestQueue: ServerRequestQueue
    private lateinit var remoteInputHijacker: RemoteInputHijacker
    private lateinit var allowlistManager: AllowlistManager
    private lateinit var chatContextManager: ChatContextManager
    
    // 중복 메시지 방지를 위한 캐시 (sender+message를 키로 사용)
    private val recentMessagesCache = mutableSetOf<String>()
    private val cacheCleanupInterval = 30000L // 30초
    
    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this  // 정적 인스턴스 설정
        serverRequestQueue = ServerRequestQueue(this)
        remoteInputHijacker = RemoteInputHijacker(this)
        allowlistManager = AllowlistManager.getInstance(this)
        chatContextManager = ChatContextManager(this)
        Log.i(TAG, "NotificationListener connected with ChatContextManager")
    }
    
    
    /**
     * 중복 메시지 확인 및 캐시 업데이트
     * @param messageKey 메시지 고유 키
     * @return true if duplicate, false if new message
     */
    private fun isDuplicateMessage(messageKey: String): Boolean {
        val timestampedKey = "$messageKey:${System.currentTimeMillis() / 5000}" // 5초 단위로 그룹핑
        
        return if (recentMessagesCache.contains(timestampedKey)) {
            Log.d(TAG, "🔄 Duplicate message detected: $messageKey")
            true
        } else {
            recentMessagesCache.add(timestampedKey)
            // 캐시 크기 제한 (최대 100개)
            if (recentMessagesCache.size > 100) {
                val iterator = recentMessagesCache.iterator()
                repeat(20) { if (iterator.hasNext()) iterator.remove() }
            }
            false
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        
        sbn?.let { notification ->
            if (notification.packageName == KAKAOTALK_PACKAGE) {
                val extras = notification.notification.extras
                
                val title = extras.getString("android.title", "")
                val text = extras.getString("android.text", "")
                val subText = extras.getString("android.subText", "")
                val isGroupConversation = extras.getBoolean("android.isGroupConversation", false)
                // KakaoTalk normally puts the room name in subText, but it drops it on
                // some notifications; MessagingStyle's conversationTitle carries the same
                // name and is the fallback. Only used to *name* a group, never to decide
                // that a chat is one — a personal chat that happens to carry a title must
                // not start being treated as a group.
                val conversationTitle = extras.getString("android.conversationTitle", "")
                val resolvedGroupName = when {
                    subText.isNotEmpty() -> subText
                    conversationTitle.isNotEmpty() -> conversationTitle
                    else -> null
                }
                
                // Check for image messages in android.messages array
                var imageUri: Uri? = null
                var imageBitmap: Bitmap? = null
                val messagesArray = extras.getParcelableArray("android.messages")
                messagesArray?.let { messages ->
                    messages.forEach { message ->
                        if (message is Bundle) {
                            val uri = message.get("uri") as? Uri
                            val type = message.getString("type", "")
                            
                            if (uri != null && type.startsWith("image/")) {
                                imageUri = uri
                                try {
                                    val inputStream = contentResolver.openInputStream(uri)
                                    inputStream?.let { stream ->
                                        imageBitmap = BitmapFactory.decodeStream(stream)
                                        stream.close()
                                        Log.d(TAG, "Image loaded: ${imageBitmap?.width}x${imageBitmap?.height}")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error loading image: ${e.message}")
                                }
                            }
                        }
                    }
                }
                
                val timestamp = System.currentTimeMillis()
                val formattedTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
                
                val notification = when {
                    // Image message (detected from android.messages array)
                    imageUri != null && imageBitmap != null -> {
                        if (isGroupConversation) {
                            // A group image whose room name cannot be resolved is dropped,
                            // never downgraded to a personal image. Downgrading is what made
                            // the server see a 1:1 chat and answer a group photo that never
                            // mentioned the assistant; the text branch below has always
                            // dropped this case instead.
                            if (resolvedGroupName == null) {
                                Log.w(TAG, "Group image with no resolvable room name - dropping: $title")
                                null
                            } else {
                                ImageMessage(
                                    sender = title,
                                    imageUri = imageUri,
                                    imageBitmap = imageBitmap,
                                    groupName = resolvedGroupName,
                                    timestamp = timestamp,
                                    formattedTime = formattedTime
                                )
                            }
                        } else {
                            ImageMessage(
                                sender = title,
                                imageUri = imageUri,
                                imageBitmap = imageBitmap,
                                groupName = null,
                                timestamp = timestamp,
                                formattedTime = formattedTime
                            )
                        }
                    }
                    // Regular text messages
                    text.isNotEmpty() && isGroupConversation && resolvedGroupName != null -> {
                        GroupMessage(
                            groupName = resolvedGroupName,
                            sender = title,
                            message = text,
                            timestamp = timestamp,
                            formattedTime = formattedTime
                        )
                    }
                    text.isNotEmpty() && !isGroupConversation -> {
                        PersonalMessage(
                            sender = title,
                            message = text,
                            timestamp = timestamp,
                            formattedTime = formattedTime
                        )
                    }
                    text.isEmpty() && subText.isNotEmpty() -> {
                        UnreadSummary(
                            unreadInfo = subText,
                            timestamp = timestamp,
                            formattedTime = formattedTime
                        )
                    }
                    else -> null
                }
                
                notification?.let { notif ->
                    // 모든 메시지 타입에 대해 중복 검사 수행
                    val messageKey = when (notif) {
                        is ImageMessage -> {
                            val identifier = if (notif.groupName != null) "${notif.groupName}:${notif.sender}" else notif.sender
                            "$identifier:[Image]"
                        }
                        is GroupMessage -> "${notif.groupName}:${notif.sender}:${notif.message}"
                        is PersonalMessage -> "${notif.sender}:${notif.message}"
                        is UnreadSummary -> "summary:${notif.unreadInfo}"
                    }
                    
                    // UnreadSummary는 UI에 표시하지 않음
                    if (notif is UnreadSummary) {
                        Log.d(TAG, "Unread summary ignored for UI: ${notif.unreadInfo}")
                        return@let
                    }
                    
                    // 중복 메시지 검사
                    if (isDuplicateMessage(messageKey)) {
                        Log.d(TAG, "🔄 Skipping duplicate notification: $messageKey")
                        return@let
                    }
                    
                    // 유효한 메시지만 로그에 추가 (UnreadSummary 제외)
                    notificationLog.add(notif)
                    
                    when (notif) {
                        is ImageMessage -> {
                            if (notif.groupName != null) {
                                Log.d(TAG, "Group image: ${notif.groupName} - ${notif.sender}")
                                // Check Turbo mode or allowlist before sending to server
                                val shouldProcess = if (::allowlistManager.isInitialized) {
                                    allowlistManager.isTurboModeEnabled() || allowlistManager.isGroupAllowed(notif.groupName)
                                } else false
                                
                                if (shouldProcess) {
                                    if (allowlistManager.isTurboModeEnabled()) {
                                        Log.d(TAG, "Turbo mode enabled - sending group image to server")
                                    } else {
                                        Log.d(TAG, "Group '${notif.groupName}' is in allowlist - sending image to server")
                                    }
                                    notif.imageBitmap?.let { bitmap ->
                                        serverRequestQueue.addRequest(
                                            originalSbn = sbn,
                                            message = text, // Or a placeholder like "[Image]"
                                            sender = notif.sender,
                                            groupName = notif.groupName,
                                            isGroup = true,
                                            imageBitmap = bitmap
                                        )
                                    }
                                } else {
                                    Log.d(TAG, "Group '${notif.groupName}' not in allowlist and Turbo mode disabled - skipping")
                                }
                            } else {
                                Log.d(TAG, "Personal image: ${notif.sender}")
                                // Check Turbo mode or allowlist before sending to server
                                val shouldProcess = if (::allowlistManager.isInitialized) {
                                    allowlistManager.isTurboModeEnabled() || allowlistManager.isPersonalAllowed(notif.sender)
                                } else false
                                
                                if (shouldProcess) {
                                    if (allowlistManager.isTurboModeEnabled()) {
                                        Log.d(TAG, "Turbo mode enabled - sending personal image to server")
                                    } else {
                                        Log.d(TAG, "Sender '${notif.sender}' is in allowlist - sending image to server")
                                    }
                                    notif.imageBitmap?.let { bitmap ->
                                        serverRequestQueue.addRequest(
                                            originalSbn = sbn,
                                            message = text, // Or a placeholder like "[Image]"
                                            sender = notif.sender,
                                            groupName = null,
                                            isGroup = false,
                                            imageBitmap = bitmap
                                        )
                                    }
                                } else {
                                    Log.d(TAG, "Sender '${notif.sender}' not in allowlist and Turbo mode disabled - skipping")
                                }
                            }
                        }
                        is GroupMessage -> {
                            Log.d(TAG, "Group message: ${notif.groupName} - ${notif.sender}")
                            
                            // 채팅방 컨텍스트 추출 및 저장 (모든 그룹 메시지에 대해)
                            if (::chatContextManager.isInitialized) {
                                chatContextManager.extractChatContextFromKakaoNotification(sbn, notif)
                            }
                            
                            // 최신 StatusBarNotification 저장 (답장 기능용)
                            val groupChatId = ChatContext.generateChatId(ChatContext.ChatType.GROUP, notif.groupName)
                            NotificationStorage.storeNotification(groupChatId, sbn)
                            
                            
                            // Check Turbo mode or allowlist before sending to server
                            val shouldProcess = if (::allowlistManager.isInitialized) {
                                allowlistManager.isTurboModeEnabled() || allowlistManager.isGroupAllowed(notif.groupName)
                            } else false
                            
                            if (shouldProcess) {
                                if (allowlistManager.isTurboModeEnabled()) {
                                    Log.d(TAG, "Turbo mode enabled - sending group message to server")
                                } else {
                                    Log.d(TAG, "Group '${notif.groupName}' is in allowlist - sending to server")
                                }
                                if (::serverRequestQueue.isInitialized) {
                                    serverRequestQueue.addRequest(
                                        originalSbn = sbn,
                                        message = notif.message,
                                        sender = notif.sender,
                                        groupName = notif.groupName,
                                        isGroup = true
                                    )
                                }
                            } else {
                                Log.d(TAG, "Group '${notif.groupName}' not in allowlist and Turbo mode disabled - skipping server request")
                            }
                        }
                        is PersonalMessage -> {
                            Log.d(TAG, "Personal message: ${notif.sender}")
                            
                            // 채팅방 컨텍스트 추출 및 저장 (모든 개인 메시지에 대해)
                            if (::chatContextManager.isInitialized) {
                                chatContextManager.extractChatContextFromKakaoNotification(sbn, notif)
                            }
                            
                            // 최신 StatusBarNotification 저장 (답장 기능용)
                            val personalChatId = ChatContext.generateChatId(ChatContext.ChatType.PERSONAL, notif.sender)
                            NotificationStorage.storeNotification(personalChatId, sbn)
                            
                            
                            // Check Turbo mode or allowlist before sending to server
                            val shouldProcess = if (::allowlistManager.isInitialized) {
                                allowlistManager.isTurboModeEnabled() || allowlistManager.isPersonalAllowed(notif.sender)
                            } else false
                            
                            if (shouldProcess) {
                                if (allowlistManager.isTurboModeEnabled()) {
                                    Log.d(TAG, "Turbo mode enabled - sending personal message to server")
                                } else {
                                    Log.d(TAG, "Sender '${notif.sender}' is in allowlist - sending to server")
                                }
                                if (::serverRequestQueue.isInitialized) {
                                    serverRequestQueue.addRequest(
                                        originalSbn = sbn,
                                        message = notif.message,
                                        sender = notif.sender,
                                        groupName = null,
                                        isGroup = false
                                    )
                                }
                            } else {
                                Log.d(TAG, "Sender '${notif.sender}' not in allowlist and Turbo mode disabled - skipping server request")
                            }
                        }
                        is UnreadSummary -> {
                            // UnreadSummary는 위에서 이미 필터링됨, 이 경우는 도달하지 않음
                            Log.w(TAG, "UnreadSummary reached when block - this should not happen")
                        }
                    }
                } ?: Log.d(TAG, "Unknown notification type ignored")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
    
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null  // 정적 인스턴스 해제
        if (::serverRequestQueue.isInitialized) {
            serverRequestQueue.shutdown()
        }
        Log.i(TAG, "NotificationListener disconnected")
    }
    
}

sealed class KakaoNotification {
    abstract val timestamp: Long
    abstract val formattedTime: String
}

data class PersonalMessage(
    val sender: String,
    val message: String,
    override val timestamp: Long,
    override val formattedTime: String
) : KakaoNotification()

data class GroupMessage(
    val groupName: String,
    val sender: String,
    val message: String,
    override val timestamp: Long,
    override val formattedTime: String
) : KakaoNotification()

data class UnreadSummary(
    val unreadInfo: String,
    override val timestamp: Long,
    override val formattedTime: String
) : KakaoNotification()

data class ImageMessage(
    val sender: String,
    val imageUri: Uri,
    val imageBitmap: Bitmap?,
    val groupName: String? = null, // null for personal, groupName for group
    override val timestamp: Long,
    override val formattedTime: String
) : KakaoNotification()