package com.example.kakaomiddleware

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.*

data class MessageData(
    val id: String,
    val isGroup: Boolean,
    val groupName: String?,
    val sender: String,
    val message: String,
    val timestamp: Long,
    val deviceId: String
)

data class ServerResponse(
    val id: String,
    val success: Boolean,
    val reply: String?,
    val processingTime: Int,
    val error: ErrorInfo?
)

data class ErrorInfo(
    val code: String,
    val message: String,
    val retryAfter: Int?
)

class ServerApiService {
    
    companion object {
        private const val TAG = "ServerApiService"
        private const val API_ENDPOINT = "https://kakaobot-server3.vercel.app/api/v1/process-message"
        private const val DEVICE_ID = "android_kakaomiddleware"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    suspend fun processMessage(
        messageId: String,
        isGroup: Boolean,
        groupName: String?,
        sender: String,
        message: String,
        timestamp: Long
    ): Result<ServerResponse> = withContext(Dispatchers.IO) {
        try {
            val messageData = MessageData(
                id = messageId,
                isGroup = isGroup,
                groupName = groupName,
                sender = sender,
                message = message,
                timestamp = timestamp,
                deviceId = DEVICE_ID
            )
            
            val response = sendToServer(messageData)
            Log.d(TAG, "Server processed: $messageId")
            
            Result.success(response)
            
        } catch (e: Exception) {
            Log.e(TAG, "Server request failed: $messageId - ${e.message}")
            Result.failure(e)
        }
    }
    
    private suspend fun sendToServer(messageData: MessageData): ServerResponse {
        val jsonPayload = createJsonPayload(messageData)
        
        val requestBody = jsonPayload.toString()
            .toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url(API_ENDPOINT)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", "KakaoMiddleware-Android/1.0")
            .build()
        
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
        
        if (response.isSuccessful) {
            return parseSuccessResponse(responseBody)
        } else {
            throw Exception("Server error: ${response.code} - $responseBody")
        }
    }
    
    private fun createJsonPayload(messageData: MessageData): JSONObject {
        return JSONObject().apply {
            put("id", messageData.id)
            put("isGroup", messageData.isGroup)
            put("groupName", messageData.groupName ?: JSONObject.NULL)
            put("sender", messageData.sender)
            put("message", messageData.message)
            put("timestamp", messageData.timestamp)
            put("deviceId", messageData.deviceId)
        }
    }
    
    private fun parseSuccessResponse(responseBody: String?): ServerResponse {
        try {
            val jsonResponse = JSONObject(responseBody ?: "{}")
            
            val id = jsonResponse.getString("id")
            val success = jsonResponse.getBoolean("success")
            val reply = if (jsonResponse.isNull("reply")) null else jsonResponse.getString("reply")
            val processingTime = jsonResponse.optInt("processingTime", 0)
            
            val error = if (jsonResponse.isNull("error")) {
                null
            } else {
                val errorObj = jsonResponse.getJSONObject("error")
                ErrorInfo(
                    code = errorObj.getString("code"),
                    message = errorObj.getString("message"),
                    retryAfter = errorObj.optInt("retryAfter")
                )
            }
            
            return ServerResponse(
                id = id,
                success = success,
                reply = reply,
                processingTime = processingTime,
                error = error
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server response", e)
            throw Exception("Failed to parse server response: ${e.message}")
        }
    }
    
    fun generateMessageId(): String {
        return "msg_android_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
    }
}