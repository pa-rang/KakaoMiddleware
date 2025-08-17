Here's a concise context document for implementing API calls to your KakaoBot server using OkHttp in Android:

---

## KakaoBot Server API Integration - Android/Kotlin

### Server Details
- **Endpoint**: `https://kakaobot-server.vercel.app/api/v1/process-message`
- **Method**: POST
- **Content-Type**: application/json
- **Current Response**: `{"id":"placeholder_id","success":true,"reply":"Hello World!","processingTime":0,"error":null}`

### Dependencies (build.gradle.kts)
```kotlin
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
```

### Required Permissions (AndroidManifest.xml)
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### Complete Implementation
```kotlin
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import android.util.Log

class ApiTester {
    private val client = OkHttpClient()
    
    fun testProcessMessageApi() {
        // JSON payload matching MessageRequest schema
        val jsonPayload = JSONObject().apply {
            put("id", "msg_android_${System.currentTimeMillis()}")
            put("isGroup", false)
            put("groupName", JSONObject.NULL)
            put("sender", "AndroidTestUser")
            put("message", "Hello from Android!")
            put("timestamp", System.currentTimeMillis())
            put("deviceId", "android_test_device_123")
        }
        
        val requestBody = jsonPayload.toString()
            .toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url("https://kakaobot-server.vercel.app/api/v1/process-message")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Log.d("API_TEST", "Success: $responseBody")
                        handleSuccessResponse(responseBody)
                    } else {
                        Log.e("API_TEST", "Error: ${response.code} - $responseBody")
                        handleErrorResponse(response.code, responseBody)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("API_TEST", "Network error: ${e.message}")
                    handleNetworkError(e)
                }
            }
        }
    }
    
    private fun handleSuccessResponse(responseBody: String?) {
        try {
            val jsonResponse = JSONObject(responseBody ?: "")
            val reply = jsonResponse.getString("reply")
            val success = jsonResponse.getBoolean("success")
            Log.d("API_SUCCESS", "Reply: $reply, Success: $success")
            // Update UI here
        } catch (e: Exception) {
            Log.e("API_PARSE", "Error parsing response: ${e.message}")
        }
    }
    
    private fun handleErrorResponse(code: Int, responseBody: String?) {
        Log.e("API_ERROR", "HTTP Error $code: $responseBody")
    }
    
    private fun handleNetworkError(error: Exception) {
        Log.e("NETWORK_ERROR", "Network error: ${error.message}")
    }
}
```

### Usage in Activity
```kotlin
class MainActivity : AppCompatActivity() {
    private val apiTester = ApiTester()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Call API test
        apiTester.testProcessMessageApi()
    }
}
```

### Expected Response Schema
```kotlin
data class MessageResponse(
    val id: String,
    val success: Boolean,
    val reply: String?,
    val processingTime: Int,
    val error: ErrorInfo?
)
```

### Key Points
- Uses coroutines for async networking
- Proper error handling for network/parsing errors
- Logs all responses for debugging
- Ready for future AI integration (currently returns "Hello World!")

---

This provides everything needed to implement and test API calls to your KakaoBot server from Android using OkHttp.