package com.example.kakaomiddleware

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GeminiApiService {
    
    companion object {
        private const val TAG = "GeminiApiService"
        private const val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
    }
    
    suspend fun generateResponse(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!ApiKeyManager.isApiKeySet()) {
                return@withContext Result.failure(Exception("Gemini API key not set. Please configure your API key."))
            }
            
            Log.d(TAG, "Generating response for prompt: $prompt")
            
            val url = URL("$GEMINI_API_URL?key=${ApiKeyManager.GEMINI_API_KEY}")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 30000 // 30 seconds
                readTimeout = 60000    // 60 seconds
            }
            
            val requestBody = createRequestBody(prompt)
            Log.d(TAG, "Request body: $requestBody")
            
            connection.outputStream.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(requestBody)
                    writer.flush()
                }
            }
            
            val responseCode = connection.responseCode
            Log.d(TAG, "Response code: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        reader.readText()
                    }
                }
                
                Log.d(TAG, "Response: $response")
                val generatedText = parseResponse(response)
                Result.success(generatedText)
            } else {
                val errorResponse = connection.errorStream?.use { errorStream ->
                    BufferedReader(InputStreamReader(errorStream)).use { reader ->
                        reader.readText()
                    }
                } ?: "Unknown error"
                
                Log.e(TAG, "Error response: $errorResponse")
                Result.failure(Exception("API call failed with code $responseCode: $errorResponse"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception during API call", e)
            Result.failure(e)
        }
    }
    
    private fun createRequestBody(prompt: String): String {
        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("topK", 40)
                put("topP", 0.95)
                put("maxOutputTokens", 1024)
            })
            put("safetySettings", JSONArray().apply {
                // Add safety settings to prevent harmful content
                put(JSONObject().apply {
                    put("category", "HARM_CATEGORY_HARASSMENT")
                    put("threshold", "BLOCK_MEDIUM_AND_ABOVE")
                })
                put(JSONObject().apply {
                    put("category", "HARM_CATEGORY_HATE_SPEECH")
                    put("threshold", "BLOCK_MEDIUM_AND_ABOVE")
                })
            })
        }
        
        return requestJson.toString()
    }
    
    private fun parseResponse(response: String): String {
        return try {
            val jsonResponse = JSONObject(response)
            val candidates = jsonResponse.getJSONArray("candidates")
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val firstPart = parts.getJSONObject(0)
            
            firstPart.getString("text").trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response", e)
            "Sorry, I couldn't generate a response. Please try again."
        }
    }
}