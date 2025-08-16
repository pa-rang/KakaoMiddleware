package com.example.kakaomiddleware

import android.content.Context
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

data class GptRequest(
    val id: String,
    val originalSbn: StatusBarNotification,
    val originalMessage: String,
    val prompt: String,
    val sender: String,
    val groupName: String?,
    val timestamp: Long = System.currentTimeMillis()
)

class GptRequestQueue(private val context: Context) {
    
    companion object {
        private const val TAG = "GptRequestQueue"
        private const val MAX_CONCURRENT_REQUESTS = 3
        private const val GPT_TRIGGER = "@GPT_call_it"
    }
    
    private val processingQueue = ConcurrentLinkedQueue<GptRequest>()
    private val activeRequests = AtomicInteger(0)
    private val queueScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val geminiApiService = GeminiApiService()
    private val remoteInputHijacker = RemoteInputHijacker(context)
    
    /**
     * Adds a new GPT request to the processing queue
     */
    fun addRequest(
        originalSbn: StatusBarNotification,
        originalMessage: String,
        sender: String,
        groupName: String? = null
    ) {
        if (!shouldProcessMessage(originalMessage)) {
            Log.d(TAG, "Message does not contain trigger, skipping: $originalMessage")
            return
        }
        
        val prompt = extractPromptFromMessage(originalMessage)
        val requestId = generateRequestId()
        
        val request = GptRequest(
            id = requestId,
            originalSbn = originalSbn,
            originalMessage = originalMessage,
            prompt = prompt,
            sender = sender,
            groupName = groupName
        )
        
        processingQueue.offer(request)
        Log.d(TAG, "Added request to queue: $requestId, prompt: '$prompt'")
        
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
    
    private suspend fun processRequest(request: GptRequest) {
        try {
            Log.d(TAG, "Processing request ${request.id}: '${request.prompt}'")
            
            // Validate that we can still hijack this notification
            if (!remoteInputHijacker.canHijackNotification(request.originalSbn)) {
                Log.w(TAG, "Cannot hijack notification ${request.id}, skipping")
                return
            }
            
            // Generate AI response
            val result = geminiApiService.generateResponse(request.prompt)
            
            result.fold(
                onSuccess = { aiResponse ->
                    Log.d(TAG, "Generated AI response for ${request.id}: '$aiResponse'")
                    
                    // Inject response back to KakaoTalk
                    val injectionSuccess = remoteInputHijacker.injectResponse(request.originalSbn, aiResponse)
                    
                    if (injectionSuccess) {
                        Log.i(TAG, "Successfully injected AI response for request ${request.id}")
                        
                        // Add to our notification log for UI display
                        addToNotificationLog(request, aiResponse)
                    } else {
                        Log.e(TAG, "Failed to inject AI response for request ${request.id}")
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to generate AI response for request ${request.id}", error)
                    
                    // Optionally inject an error message
                    val errorMessage = "Sorry, I couldn't process your request: ${error.message}"
                    remoteInputHijacker.injectResponse(request.originalSbn, errorMessage)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing request ${request.id}", e)
        }
    }
    
    private fun shouldProcessMessage(message: String): Boolean {
        return message.contains(GPT_TRIGGER, ignoreCase = true)
    }
    
    private fun extractPromptFromMessage(message: String): String {
        val index = message.indexOf(GPT_TRIGGER, ignoreCase = true)
        return if (index != -1) {
            val afterTrigger = message.substring(index + GPT_TRIGGER.length).trim()
            if (afterTrigger.isNotBlank()) {
                afterTrigger
            } else {
                "Please provide a question or request."
            }
        } else {
            message
        }
    }
    
    private fun generateRequestId(): String {
        return "req_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
    
    private fun addToNotificationLog(request: GptRequest, aiResponse: String) {
        val timestamp = System.currentTimeMillis()
        val formattedTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
        
        val aiNotification = if (request.groupName != null) {
            GroupMessage(
                groupName = request.groupName,
                sender = "🤖 AI Assistant",
                message = aiResponse,
                timestamp = timestamp,
                formattedTime = formattedTime
            )
        } else {
            PersonalMessage(
                sender = "🤖 AI Assistant",
                message = aiResponse,
                timestamp = timestamp,
                formattedTime = formattedTime
            )
        }
        
        KakaoNotificationListenerService.notificationLog.add(aiNotification)
        Log.d(TAG, "Added AI response to notification log")
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
        Log.d(TAG, "GptRequestQueue shutdown complete")
    }
}