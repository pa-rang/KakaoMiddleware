package com.example.kakaomiddleware

object ApiKeyManager {
    // TODO: Replace with your actual Gemini API key
    // Get your API key from: https://aistudio.google.com/app/apikey
    var GEMINI_API_KEY: String = "YOUR_GEMINI_API_KEY_HERE"
    
    fun setApiKey(apiKey: String) {
        GEMINI_API_KEY = apiKey
    }
    
    fun isApiKeySet(): Boolean {
        return GEMINI_API_KEY != "YOUR_GEMINI_API_KEY_HERE" && GEMINI_API_KEY.isNotBlank()
    }
}