package com.example.kakaomiddleware

import android.content.Context
import android.graphics.Bitmap
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

data class ServerRequest(
    val id: String,
    val originalSbn: StatusBarNotification,
    val message: String,
    val sender: String,
    val groupName: String?,
    val isGroup: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val imageBitmap: Bitmap? = null
)

class ServerRequestQueue(private val context: Context) {
    
    companion object {
        private const val TAG = "ServerRequestQueue"
        private const val MAX_CONCURRENT_REQUESTS = 100
    }
    
    private val processingQueue = ConcurrentLinkedQueue<ServerRequest>()
    private val activeRequests = AtomicInteger(0)
    private val queueScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val serverApiService = ServerApiService()
    private val remoteInputHijacker = RemoteInputHijacker(context)
    
    init {
        Log.i(TAG, "ServerRequestQueue initialized - Max concurrent: $MAX_CONCURRENT_REQUESTS")
    }
    
    /**
     * Adds a new server request to the processing queue
     * Processes ALL messages without trigger detection
     */
    fun addRequest(
        originalSbn: StatusBarNotification,
        message: String,
        sender: String,
        groupName: String? = null,
        isGroup: Boolean = false,
        imageBitmap: Bitmap? = null
    ) {
        val requestId = serverApiService.generateMessageId()
        
        val request = ServerRequest(
            id = requestId,
            originalSbn = originalSbn,
            message = message,
            sender = sender,
            groupName = groupName,
            isGroup = isGroup,
            imageBitmap = imageBitmap
        )
        
        processingQueue.offer(request)
        Log.d(TAG, "Queued message: $requestId")
        
        processNextRequestIfPossible()
    }
    
    private fun processNextRequestIfPossible() {
        if (activeRequests.get() >= MAX_CONCURRENT_REQUESTS) {
            Log.d(TAG, "Max concurrent requests reached, queuing request")
            return
        }
        
        val request = processingQueue.poll()
        if (request != null) {
            activeRequests.incrementAndGet()
            
            queueScope.launch {
                try {
                    processRequest(request)
                } finally {
                    activeRequests.decrementAndGet()
                    // Try to process next request in queue
                    processNextRequestIfPossible()
                }
            }
        }
    }
    
    private suspend fun processRequest(request: ServerRequest) {
        try {
            Log.d(TAG, "Processing: ${request.id}")
            
            // Validate that we can still hijack this notification
            if (!remoteInputHijacker.canHijackNotification(request.originalSbn)) {
                Log.w(TAG, "Cannot hijack notification ${request.id}")
                return
            }
            
            // Send message to server for processing
            val result = if (request.imageBitmap != null) {
                Log.d(TAG, "Sending image message to server. Bitmap size: ${request.imageBitmap.byteCount} bytes")
                serverApiService.processImageMessage(
                    messageId = request.id,
                    isGroup = request.isGroup,
                    groupName = request.groupName,
                    sender = request.sender,
                    message = request.message,
                    timestamp = request.timestamp,
                    imageBitmap = request.imageBitmap
                )
            } else {
                serverApiService.processMessage(
                    messageId = request.id,
                    isGroup = request.isGroup,
                    groupName = request.groupName,
                    sender = request.sender,
                    message = request.message,
                    timestamp = request.timestamp
                )
            }
            
            result.fold(
                onSuccess = { serverResponse ->
                    Log.d(TAG, "Server responded: ${request.id}, reply=${serverResponse.reply != null}")
                    
                    if (serverResponse.success && serverResponse.reply != null) {
                        // Inject response back to KakaoTalk
                        val injectionSuccess = remoteInputHijacker.injectResponse(request.originalSbn, serverResponse.reply)
                        
                        if (injectionSuccess) {
                            Log.i(TAG, "Reply injected successfully: ${request.id}")
                            addToNotificationLog(request, serverResponse.reply)
                        } else {
                            Log.e(TAG, "Reply injection failed: ${request.id}")
                        }
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Server error: ${request.id} - ${error.message}")
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing request ${request.id}", e)
        }
    }
    
    
    private fun addToNotificationLog(request: ServerRequest, serverResponse: String) {
        val timestamp = System.currentTimeMillis()
        val formattedTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
        
        val serverNotification = if (request.groupName != null) {
            GroupMessage(
                groupName = request.groupName,
                sender = "🤖 Server Bot",
                message = serverResponse,
                timestamp = timestamp,
                formattedTime = formattedTime
            )
        } else {
            PersonalMessage(
                sender = "🤖 Server Bot",
                message = serverResponse,
                timestamp = timestamp,
                formattedTime = formattedTime
            )
        }
        
        KakaoNotificationListenerService.notificationLog.add(serverNotification)
    }
    
    /**
     * Gets current queue status for debugging
     */
    fun getQueueStatus(): Map<String, Any> {
        return mapOf(
            "queueSize" to processingQueue.size,
            "activeRequests" to activeRequests.get(),
            "maxConcurrentRequests" to MAX_CONCURRENT_REQUESTS
        )
    }
    
    /**
     * Clears all pending requests (for cleanup)
     */
    fun clearQueue() {
        processingQueue.clear()
        Log.d(TAG, "Cleared all pending requests from queue")
    }
    
    /**
     * Cancels all active coroutines and clears queue
     */
    fun shutdown() {
        queueScope.cancel()
        processingQueue.clear()
        Log.i(TAG, "ServerRequestQueue shutdown")
    }
}