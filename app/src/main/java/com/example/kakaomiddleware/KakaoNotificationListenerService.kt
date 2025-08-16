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
    
    private lateinit var gptRequestQueue: GptRequestQueue
    private lateinit var remoteInputHijacker: RemoteInputHijacker
    
    override fun onListenerConnected() {
        super.onListenerConnected()
        gptRequestQueue = GptRequestQueue(this)
        remoteInputHijacker = RemoteInputHijacker(this)
        Log.d(TAG, "NotificationListener connected and hijacking system initialized")
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
                            Log.d(TAG, "Group message - Group: ${notif.groupName}, Sender: ${notif.sender}, Message: ${notif.message}")
                            
                            // Check for GPT trigger and process if needed
                            if (::gptRequestQueue.isInitialized && notif.message.contains("@GPT_call_it", ignoreCase = true)) {
                                Log.d(TAG, "GPT trigger detected in group message, adding to queue")
                                gptRequestQueue.addRequest(
                                    originalSbn = sbn,
                                    originalMessage = notif.message,
                                    sender = notif.sender,
                                    groupName = notif.groupName
                                )
                            }
                        }
                        is PersonalMessage -> {
                            Log.d(TAG, "Personal message - Sender: ${notif.sender}, Message: ${notif.message}")
                            
                            // Check for GPT trigger and process if needed
                            if (::gptRequestQueue.isInitialized && notif.message.contains("@GPT_call_it", ignoreCase = true)) {
                                Log.d(TAG, "GPT trigger detected in personal message, adding to queue")
                                gptRequestQueue.addRequest(
                                    originalSbn = sbn,
                                    originalMessage = notif.message,
                                    sender = notif.sender,
                                    groupName = null
                                )
                            }
                        }
                        is UnreadSummary -> Log.d(TAG, "Unread summary - Info: ${notif.unreadInfo}")
                    }
                    
                    // Debug: Log hijacking capabilities for this notification
                    if (::remoteInputHijacker.isInitialized && (notif is GroupMessage || notif is PersonalMessage)) {
                        val debugInfo = remoteInputHijacker.getHijackingDebugInfo(sbn)
                        Log.d(TAG, "Hijacking debug info: $debugInfo")
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
        if (::gptRequestQueue.isInitialized) {
            gptRequestQueue.shutdown()
        }
        Log.d(TAG, "NotificationListener disconnected and cleaned up")
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