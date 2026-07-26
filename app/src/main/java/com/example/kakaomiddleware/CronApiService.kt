package com.example.kakaomiddleware

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 크론 메시지 데이터 클래스
 */
data class CronMessage(
    val chatId: String,
    val message: String,
    val messageType: String = "text"
)

/**
 * 크론 API 응답 데이터 클래스
 */
data class CronResponse(
    val success: Boolean,
    val messages: List<CronMessage>?,
    val error: String?,
    val executionTime: Long = 0L
)

/**
 * 10분 간격 크론 작업을 처리하는 API 서비스
 * 기존 ServerConfigManager 통합으로 동적 엔드포인트 지원
 */
class CronApiService(private val context: Context) {
    
    companion object {
        private const val TAG = "CronApiService"
        private const val CRON_ENDPOINT = "/api/v1/run-scheduled-message"
        private const val DEVICE_ID = "android_kakaomiddleware"
        private const val REQUEST_TIMEOUT_MS = 180_000L // 180초 (3분)

        // Fallback endpoint from BuildConfig
        private val FALLBACK_BASE_URL = BuildConfig.API_ENDPOINT.removeSuffix("/api/v1/process-message")
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val serverConfigManager = ServerConfigManager.getInstance(context)
    
    /**
     * 현재 설정된 베이스 URL 가져오기
     */
    private fun getBaseUrl(): String {
        val currentEndpoint = serverConfigManager.getCurrentEndpoint()
        return currentEndpoint.removeSuffix("/api/v1/process-message")
    }
    
    /**
     * 크론 API 엔드포인트 URL 구성
     */
    private fun getCronApiUrl(): String {
        return "${getBaseUrl()}$CRON_ENDPOINT"
    }
    
    init {
        val cronUrl = getCronApiUrl()
        val serverType = when {
            cronUrl.contains("localhost") || cronUrl.contains("192.168") || cronUrl.contains("10.0.2.2") -> "🏠 LOCAL"
            cronUrl.contains("vercel.app") -> "☁️ PRODUCTION"
            else -> "🔧 CUSTOM"
        }
        
        Log.i(TAG, "🕐 CronApiService 초기화됨")
        Log.i(TAG, "🌐 크론 엔드포인트: $cronUrl")
        Log.i(TAG, "🏷️ 서버 타입: $serverType")
        Log.i(TAG, "📱 디바이스 ID: $DEVICE_ID")
    }
    
    /**
     * 크론 작업 실행 - 메인 API 호출 메서드 (기존 호환성 유지)
     */
    suspend fun runCronJob(): Result<CronResponse> = runScheduledMessage(null)
    
    /**
     * 예정된 메시지 실행 - 시간 정보 포함
     * @param scheduledTime 예정된 시간 (예: "08:00", "14:30")
     */
    suspend fun runScheduledMessage(scheduledTime: String?): Result<CronResponse> = withContext(Dispatchers.IO) {
        return@withContext try {
            withTimeout(REQUEST_TIMEOUT_MS) {
                Log.i(TAG, "⏰ 예정된 메시지 작업 시작${scheduledTime?.let { " - 시간: $it" } ?: ""}")
                
                val request = createCronRequest(scheduledTime)
                val startTime = System.currentTimeMillis()
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                val executionTime = System.currentTimeMillis() - startTime
                
                Log.i(TAG, "📡 예정된 메시지 API 응답: ${response.code} (${executionTime}ms)")
                Log.d(TAG, "📋 응답 본문: $responseBody")
                
                if (response.isSuccessful) {
                    val cronResponse = parseCronResponse(responseBody, executionTime)
                    Log.i(TAG, if (cronResponse.messages?.isNotEmpty() == true) {
                        "✅ 예정된 메시지 작업 완료 - ${cronResponse.messages.size}개 메시지"
                    } else {
                        "✅ 예정된 메시지 작업 완료 - 전송할 메시지 없음"
                    })
                    Result.success(cronResponse)
                } else {
                    val errorMsg = "서버 오류: ${response.code} - $responseBody"
                    Log.w(TAG, "⚠️ $errorMsg")
                    Result.success(CronResponse(
                        success = false,
                        messages = null,
                        error = errorMsg,
                        executionTime = executionTime
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 크론 작업 실행 실패: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * 크론 API 요청 객체 생성
     * @param scheduledTime 예정된 시간 (선택적)
     */
    private fun createCronRequest(scheduledTime: String?): Request {
        val baseUrl = getCronApiUrl()
        
        // 시간 파라미터가 있으면 쿼리 파라미터로 추가
        val url = if (scheduledTime != null) {
            "$baseUrl?time=$scheduledTime"
        } else {
            baseUrl
        }
        
        Log.d(TAG, "📡 크론 요청 URL: $url")
        
        return Request.Builder()
            .url(url)
            .get()
            .addHeader("User-Agent", "KakaoMiddleware-Android/1.0")
            .addHeader("Accept", "application/json")
            .addHeader("X-Device-Id", DEVICE_ID)
            .addHeader("X-Scheduled-Time", scheduledTime ?: "")
            .addApiKeyHeader()
            .build()
    }
    
    /**
     * 크론 API 응답 파싱
     */
    private fun parseCronResponse(responseBody: String?, executionTime: Long): CronResponse {
        return try {
            val jsonResponse = JSONObject(responseBody ?: "{}")
            
            val success = jsonResponse.optBoolean("success", false)
            val error = if (jsonResponse.has("error")) {
                jsonResponse.getString("error")
            } else null
            
            val messages = if (jsonResponse.has("messages")) {
                parseCronMessages(jsonResponse.getJSONArray("messages"))
            } else null
            
            CronResponse(
                success = success,
                messages = messages,
                error = error,
                executionTime = executionTime
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "크론 응답 파싱 오류: ${e.message}")
            CronResponse(
                success = false,
                messages = null,
                error = "응답 파싱 실패: ${e.message}",
                executionTime = executionTime
            )
        }
    }
    
    /**
     * 크론 메시지 배열 파싱
     */
    private fun parseCronMessages(messagesArray: JSONArray): List<CronMessage> {
        val messages = mutableListOf<CronMessage>()
        
        for (i in 0 until messagesArray.length()) {
            try {
                val messageObj = messagesArray.getJSONObject(i)
                
                // 서버 응답 스키마에 맞게 필드명 수정
                // chatRoomName -> chatId, messageContent -> message로 매핑
                val cronMessage = CronMessage(
                    chatId = messageObj.getString("chatRoomName"),  // chatRoomName을 chatId로 매핑
                    message = messageObj.getString("messageContent"),  // messageContent를 message로 매핑
                    messageType = messageObj.optString("messageType", "text")
                )
                messages.add(cronMessage)
                
                Log.d(TAG, "크론 메시지 파싱됨: ${cronMessage.chatId} - '${cronMessage.message.take(50)}${if (cronMessage.message.length > 50) "..." else ""}'")
                
            } catch (e: Exception) {
                Log.w(TAG, "크론 메시지 파싱 실패 (index: $i): ${e.message}")
            }
        }
        
        return messages
    }
}