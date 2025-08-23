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
                        if (isGroupConversation && subText.isNotEmpty()) {
                            ImageMessage(
                                sender = title,
                                imageUri = imageUri,
                                imageBitmap = imageBitmap,
                                groupName = subText,
                                timestamp = timestamp,
                                formattedTime = formattedTime
                            )
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
                                    // TODO: Add image support to ServerRequestQueue
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
                                    // TODO: Add image support to ServerRequestQueue
                                } else {
                                    Log.d(TAG, "Sender '${notif.sender}' not in allowlist and Turbo mode disabled - skipping")
                                }
                            }
                        }
                        is GroupMessage -> {
                            Log.d(TAG, "Group message: ${notif.groupName} - ${notif.sender}")
                            
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
    
    private fun processImageMessage(uri: Uri, bitmap: Bitmap, messageText: String, sender: String) {
        Log.i(TAG, "🖼️ Processing image message from $sender")
        Log.i(TAG, "   URI: $uri")
        Log.i(TAG, "   Dimensions: ${bitmap.width}x${bitmap.height}")
        Log.i(TAG, "   Message: $messageText")
        
        // TODO: Add your image processing logic here
        // Examples:
        // 1. Save image to external storage
        // 2. Send image data to your server for AI analysis
        // 3. Extract text from image using OCR
        // 4. Convert to base64 for API transmission
        // 5. Resize or compress image
        
        // Example: You could modify ServerRequestQueue to handle image messages
        // if (::serverRequestQueue.isInitialized) {
        //     serverRequestQueue.addImageRequest(uri, bitmap, sender, messageText)
        // }
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