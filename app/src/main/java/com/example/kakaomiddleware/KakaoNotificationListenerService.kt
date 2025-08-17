package com.example.kakaomiddleware

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

class KakaoNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "KakaoNotificationListener"
        private const val KAKAOTALK_PACKAGE = "com.kakao.talk"
        val notificationLog = mutableListOf<KakaoNotification>()
    }
    
    private lateinit var serverRequestQueue: ServerRequestQueue
    private lateinit var remoteInputHijacker: RemoteInputHijacker
    private lateinit var allowlistManager: AllowlistManager
    
    override fun onListenerConnected() {
        super.onListenerConnected()
        serverRequestQueue = ServerRequestQueue(this)
        remoteInputHijacker = RemoteInputHijacker(this)
        allowlistManager = AllowlistManager.getInstance(this)
        Log.i(TAG, "NotificationListener connected")
        
        // Debug: Print current allowlists
        if (::allowlistManager.isInitialized) {
            allowlistManager.debugPrintAllowlists()
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
                
                val timestamp = System.currentTimeMillis()
                val formattedTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
                
                val notification = when {
                    text.isNotEmpty() && isGroupConversation && subText.isNotEmpty() -> {
                        GroupMessage(
                            groupName = subText,
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
                    notificationLog.add(notif)
                    
                    when (notif) {
                        is GroupMessage -> {
                            Log.d(TAG, "Group message: ${notif.groupName} - ${notif.sender}")
                            
                            // Check allowlist before sending to server
                            if (::allowlistManager.isInitialized && allowlistManager.isGroupAllowed(notif.groupName)) {
                                Log.d(TAG, "Group '${notif.groupName}' is in allowlist - sending to server")
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
                                Log.d(TAG, "Group '${notif.groupName}' not in allowlist - skipping server request")
                            }
                        }
                        is PersonalMessage -> {
                            Log.d(TAG, "Personal message: ${notif.sender}")
                            
                            // Check allowlist before sending to server
                            if (::allowlistManager.isInitialized && allowlistManager.isPersonalAllowed(notif.sender)) {
                                Log.d(TAG, "Sender '${notif.sender}' is in allowlist - sending to server")
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
                                Log.d(TAG, "Sender '${notif.sender}' not in allowlist - skipping server request")
                            }
                        }
                        is UnreadSummary -> Log.d(TAG, "Unread summary - Info: ${notif.unreadInfo}")
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