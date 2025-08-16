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
                        is GroupMessage -> Log.d(TAG, "Group message - Group: ${notif.groupName}, Sender: ${notif.sender}, Message: ${notif.message}")
                        is PersonalMessage -> Log.d(TAG, "Personal message - Sender: ${notif.sender}, Message: ${notif.message}")
                        is UnreadSummary -> Log.d(TAG, "Unread summary - Info: ${notif.unreadInfo}")
                    }
                } ?: Log.d(TAG, "Unknown notification type ignored")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
    
    private fun isGroupMessage(title: String, text: String): Boolean {
        return text.contains(": ") && !title.equals(text.substringBefore(": "), ignoreCase = true)
    }
    
    private fun parseGroupMessage(title: String, text: String): Pair<String, String> {
        val sender = text.substringBefore(": ")
        val groupName = title
        return Pair(groupName, sender)
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