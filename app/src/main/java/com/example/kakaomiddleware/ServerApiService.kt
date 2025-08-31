package com.example.kakaomiddleware

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.*

data class MessageData(
    val id: String,
    val isGroup: Boolean,
    val groupName: String?,
    val sender: String,
    val message: String,
    val timestamp: Long,
    val deviceId: String,
    val imageBitmap: android.graphics.Bitmap? = null
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
        private val API_ENDPOINT = BuildConfig.API_ENDPOINT
        private const val DEVICE_ID = "android_kakaomiddleware"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    init {
        // 앱 시작시 현재 사용 중인 서버 엔드포인트 로그 출력
        val serverType = if (API_ENDPOINT.contains("localhost") || API_ENDPOINT.contains("192.168") || API_ENDPOINT.contains("10.0.2.2")) {
            "🏠 LOCAL SERVER"
        } else {
            "☁️ PRODUCTION SERVER"
        }
        Log.i(TAG, "🌐 SERVER ENDPOINT: $API_ENDPOINT")
        Log.i(TAG, "🏷️ SERVER TYPE: $serverType")
        Log.i(TAG, "📱 DEVICE ID: $DEVICE_ID")
    }
    
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
    
    suspend fun processImageMessage(
        messageId: String,
        isGroup: Boolean,
        groupName: String?,
        sender: String,
        message: String,
        timestamp: Long,
        imageBitmap: android.graphics.Bitmap
    ): Result<ServerResponse> = withContext(Dispatchers.IO) {
        try {
            val messageData = MessageData(
                id = messageId,
                isGroup = isGroup,
                groupName = groupName,
                sender = sender,
                message = message,
                timestamp = timestamp,
                deviceId = DEVICE_ID,
                imageBitmap = imageBitmap
            )
            
            val response = sendToServer(messageData)
            Log.d(TAG, "Server processed image message: $messageId")
            
            Result.success(response)
            
        } catch (e: Exception) {
            Log.e(TAG, "Server request for image failed: $messageId - ${e.message}")
            Result.failure(e)
        }
    }
    
    private suspend fun sendToServer(messageData: MessageData): ServerResponse {
        // 서버 요청 전 로그 출력
        Log.i(TAG, "📡 Sending request to: $API_ENDPOINT")
        Log.d(TAG, "📝 Message: ${messageData.sender} -> '${messageData.message}'")
        
        val request = if (messageData.imageBitmap != null) {
            createMultipartRequest(messageData)
        } else {
            createJsonRequest(messageData)
        }
        
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
        
        // 서버 응답 로그 출력
        Log.i(TAG, "📥 Server response: ${response.code} - ${response.message}")
        Log.d(TAG, "📋 Response body: $responseBody")
        
        if (response.isSuccessful) {
            return parseSuccessResponse(responseBody)
        } else {
            Log.e(TAG, "❌ Server error: ${response.code} - $responseBody")
            throw Exception("Server error: ${response.code} - $responseBody")
        }
    }

    private fun createJsonRequest(messageData: MessageData): Request {
        val jsonPayload = createJsonPayload(messageData)
        val requestBody = jsonPayload.toString()
            .toRequestBody("application/json".toMediaType())
        
        return Request.Builder()
            .url(API_ENDPOINT)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", "KakaoMiddleware-Android/1.0")
            .build()
    }
    
    private fun createMultipartRequest(messageData: MessageData): Request {
        val jsonPayload = createJsonPayload(messageData).toString()

        val imageByteArray = messageData.imageBitmap?.let {
            val stream = ByteArrayOutputStream()
            it.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, stream)
            stream.toByteArray()
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("json_data", jsonPayload)
            .apply {
                imageByteArray?.let {
                    addFormDataPart(
                        "image_file",
                        "image.jpg",
                        it.toRequestBody("image/jpeg".toMediaType(), 0, it.size)
                    )
                }
            }
            .build()

        return Request.Builder()
            .url(API_ENDPOINT)
            .post(requestBody)
            .addHeader("User-Agent", "KakaoMiddleware-Android/1.0")
            .build()
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