package com.example.kakaomiddleware

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ClaimedOutboundMessage(
    val id: String,
    val attemptCount: Int,
    val chatRoomName: String,
    val messageContent: String
)

class DeviceApiException(
    val statusCode: Int?,
    message: String,
    val responseBody: String? = null
) : IOException(message) {
    val isRetryable: Boolean
        get() = statusCode?.let { it == 408 || it == 429 || it >= 500 } ?: true
}

/**
 * Network boundary used by the FCM/WorkManager delivery path.
 *
 * FCM only wakes the app. Messages are always claimed from the server so a
 * collapsed, delayed, or deleted push cannot become a lost KakaoTalk message.
 */
class DeviceApiService(context: Context) {
    companion object {
        private const val TAG = "DeviceApiService"
        private const val REGISTRATION_PATH = "/api/v1/device/push-registration"
        private const val CLAIM_PATH = "/api/v1/device/outbound-messages/claim"
        private const val ACK_PATH = "/api/v1/outbound-messages/ack"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val serverConfigManager = ServerConfigManager.getInstance(context.applicationContext)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun registerPush(firebaseInstallationId: String) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("deviceId", DeviceIdentity.DEVICE_ID)
            .put("firebaseInstallationId", firebaseInstallationId)
            .put("applicationId", BuildConfig.APPLICATION_ID)

        executeJson(
            Request.Builder()
                .url(url(REGISTRATION_PATH))
                .put(body.toString().toRequestBody(JSON))
                .deviceHeaders()
                .build(),
            "push registration"
        )
        Unit
    }

    suspend fun claimOutbound(): List<ClaimedOutboundMessage> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("deviceId", DeviceIdentity.DEVICE_ID)
        val responseBody = executeJson(
            Request.Builder()
                .url(url(CLAIM_PATH))
                .post(body.toString().toRequestBody(JSON))
                .deviceHeaders()
                .build(),
            "outbound claim"
        )

        try {
            val messages = JSONObject(responseBody).getJSONArray("messages")
            buildList(messages.length()) {
                for (index in 0 until messages.length()) {
                    val message = messages.getJSONObject(index)
                    add(
                        ClaimedOutboundMessage(
                            id = message.getString("id"),
                            attemptCount = message.getInt("attemptCount"),
                            chatRoomName = message.getString("chatRoomName"),
                            messageContent = message.getString("messageContent")
                        )
                    )
                }
            }
        } catch (error: Exception) {
            throw DeviceApiException(
                statusCode = 422,
                message = "Invalid outbound claim response: ${error.message}",
                responseBody = responseBody
            )
        }
    }

    suspend fun acknowledge(
        message: ClaimedOutboundMessage,
        ok: Boolean,
        error: String? = null
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("id", message.id)
            .put("attemptCount", message.attemptCount)
            .put("ok", ok)
        error?.trim()?.takeIf(String::isNotEmpty)?.let {
            body.put("error", it.take(1000))
        }

        executeJson(
            Request.Builder()
                .url(url(ACK_PATH))
                .post(body.toString().toRequestBody(JSON))
                .deviceHeaders()
                .build(),
            "outbound acknowledgement"
        )
        Unit
    }

    private fun url(path: String): String =
        serverConfigManager.getCurrentEndpoint().removeSuffix("/api/v1/process-message") + path

    private fun Request.Builder.deviceHeaders(): Request.Builder = this
        .addHeader("User-Agent", "KakaoMiddleware-Android/${BuildConfig.VERSION_NAME}")
        .addHeader("Accept", "application/json")
        .addHeader("X-Device-Id", DeviceIdentity.DEVICE_ID)
        .addApiKeyHeader()

    private fun executeJson(request: Request, operation: String): String {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.w(TAG, "$operation failed: HTTP ${response.code}")
                throw DeviceApiException(
                    statusCode = response.code,
                    message = "$operation failed with HTTP ${response.code}",
                    responseBody = body
                )
            }
            return body
        }
    }
}
