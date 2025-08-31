package com.example.kakaomiddleware

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ServerConfigManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ServerConfigManager"
        private const val PREF_NAME = "server_config"
        private const val KEY_CUSTOM_SERVER_URL = "custom_server_url"
        private const val KEY_USE_CUSTOM_SERVER = "use_custom_server"
        
        // 기본 서버 설정
        private const val DEFAULT_LOCAL_SERVER = "http://192.168.1.100:3000" // 사용자가 설정할 기본 로컬 서버
        private const val DEFAULT_PRODUCTION_SERVER = "https://kakao-agent-server-dun.vercel.app"
        
        @Volatile
        private var INSTANCE: ServerConfigManager? = null
        
        fun getInstance(context: Context): ServerConfigManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ServerConfigManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    // StateFlow for reactive UI updates - 직접 초기화로 단순화
    private val _customServerUrl = MutableStateFlow("")
    val customServerUrl: StateFlow<String> = _customServerUrl.asStateFlow()
    
    private val _useCustomServer = MutableStateFlow(false)
    val useCustomServer: StateFlow<Boolean> = _useCustomServer.asStateFlow()
    
    private val _currentEndpoint = MutableStateFlow("")
    val currentEndpoint: StateFlow<String> = _currentEndpoint.asStateFlow()
    
    init {
        Log.i(TAG, "ServerConfigManager initializing...")
        
        // StateFlow 값 초기화
        _customServerUrl.value = getCustomServerUrl()
        _useCustomServer.value = getUseCustomServer()
        _currentEndpoint.value = getCurrentEndpoint()
        
        Log.i(TAG, "ServerConfigManager initialized")
        Log.i(TAG, "Initial state:")
        Log.i(TAG, "  Custom URL: ${_customServerUrl.value}")
        Log.i(TAG, "  Use Custom: ${_useCustomServer.value}")
        Log.i(TAG, "  Current Endpoint: ${_currentEndpoint.value}")
        
        // SharedPreferences 변경 리스너
        sharedPreferences.registerOnSharedPreferenceChangeListener { prefs, key ->
            Log.i(TAG, "🔔 SharedPreferences listener triggered for key: $key")
            
            when (key) {
                KEY_CUSTOM_SERVER_URL -> {
                    val oldUrl = _customServerUrl.value
                    val newUrl = getCustomServerUrl()
                    val oldEndpoint = _currentEndpoint.value
                    val newEndpoint = getCurrentEndpoint()
                    
                    Log.i(TAG, "🔄 Updating StateFlow for Custom server URL:")
                    Log.i(TAG, "   Old StateFlow URL: $oldUrl")
                    Log.i(TAG, "   New SharedPref URL: $newUrl")
                    
                    _customServerUrl.value = newUrl
                    _currentEndpoint.value = newEndpoint
                    
                    Log.i(TAG, "   ✅ StateFlow URL updated to: ${_customServerUrl.value}")
                    Log.i(TAG, "   ✅ StateFlow endpoint updated to: ${_currentEndpoint.value}")
                    Log.i(TAG, "   Current Use Custom: ${getUseCustomServer()}")
                }
                KEY_USE_CUSTOM_SERVER -> {
                    val oldUseCustom = _useCustomServer.value
                    val newUseCustom = getUseCustomServer()
                    val oldEndpoint = _currentEndpoint.value
                    val newEndpoint = getCurrentEndpoint()
                    
                    Log.i(TAG, "🔄 Updating StateFlow for Use custom server:")
                    Log.i(TAG, "   Old StateFlow value: $oldUseCustom")
                    Log.i(TAG, "   New SharedPref value: $newUseCustom")
                    
                    _useCustomServer.value = newUseCustom
                    _currentEndpoint.value = newEndpoint
                    
                    Log.i(TAG, "   ✅ StateFlow useCustom updated to: ${_useCustomServer.value}")
                    Log.i(TAG, "   ✅ StateFlow endpoint updated to: ${_currentEndpoint.value}")
                }
                else -> {
                    Log.i(TAG, "🔔 SharedPreferences change for unhandled key: $key")
                }
            }
        }
    }
    
    /**
     * 커스텀 서버 URL 가져오기
     */
    fun getCustomServerUrl(): String {
        return sharedPreferences.getString(KEY_CUSTOM_SERVER_URL, DEFAULT_LOCAL_SERVER) ?: DEFAULT_LOCAL_SERVER
    }
    
    /**
     * 커스텀 서버 사용 여부 가져오기
     * Debug 빌드에서는 기본적으로 커스텀 서버 사용, Release에서는 BuildConfig 사용
     */
    fun getUseCustomServer(): Boolean {
        val defaultValue = BuildConfig.DEBUG // Debug 빌드에서는 true, Release에서는 false
        return sharedPreferences.getBoolean(KEY_USE_CUSTOM_SERVER, defaultValue)
    }
    
    /**
     * 현재 사용 중인 엔드포인트 가져오기 (API 경로 포함)
     */
    fun getCurrentEndpoint(): String {
        return if (getUseCustomServer()) {
            // 커스텀 서버 사용 시: 설정된 URL + API 경로
            val customUrl = getCustomServerUrl()
            "$customUrl/api/v1/process-message"
        } else {
            // BuildConfig 사용 시: 그대로 반환 (이미 전체 경로)
            BuildConfig.API_ENDPOINT
        }
    }
    
    /**
     * 현재 사용 중인 서버 타입 가져오기
     */
    fun getCurrentServerType(): String {
        val endpoint = getCurrentEndpoint()
        return when {
            endpoint.contains("localhost") || endpoint.contains("192.168") || endpoint.contains("10.0.2.2") -> "🏠 LOCAL SERVER"
            endpoint.contains("vercel.app") -> "☁️ PRODUCTION SERVER"
            else -> "🔧 CUSTOM SERVER"
        }
    }
    
    /**
     * 커스텀 서버 URL 설정
     */
    fun setCustomServerUrl(url: String) {
        val cleanUrl = url.trim().removeSuffix("/")
        val oldValue = getCustomServerUrl()
        
        Log.i(TAG, "🔧 setCustomServerUrl called:")
        Log.i(TAG, "   Old value: $oldValue")
        Log.i(TAG, "   New value: $cleanUrl")
        Log.i(TAG, "   Current StateFlow value: ${_customServerUrl.value}")
        
        sharedPreferences.edit()
            .putString(KEY_CUSTOM_SERVER_URL, cleanUrl)
            .apply()
        
        // 즉시 StateFlow 업데이트 (SharedPreferences 리스너 대기 안 함)
        _customServerUrl.value = cleanUrl
        _currentEndpoint.value = getCurrentEndpoint()
        
        Log.i(TAG, "   ✅ SharedPreferences and StateFlow updated immediately")
        Log.i(TAG, "   🔍 New StateFlow URL: ${_customServerUrl.value}")
        Log.i(TAG, "   🔍 New StateFlow endpoint: ${_currentEndpoint.value}")
    }
    
    /**
     * 커스텀 서버 사용 여부 설정
     */
    fun setUseCustomServer(useCustom: Boolean) {
        val currentValue = getUseCustomServer()
        Log.i(TAG, "🔧 setUseCustomServer called:")
        Log.i(TAG, "   Current value: $currentValue")
        Log.i(TAG, "   Requested value: $useCustom")
        Log.i(TAG, "   Current StateFlow value: ${_useCustomServer.value}")
        
        if (currentValue != useCustom) {
            sharedPreferences.edit()
                .putBoolean(KEY_USE_CUSTOM_SERVER, useCustom)
                .apply()
                
            // 즉시 StateFlow 업데이트 (SharedPreferences 리스너 대기 안 함)
            _useCustomServer.value = useCustom
            _currentEndpoint.value = getCurrentEndpoint()
            
            Log.i(TAG, "   ✅ SharedPreferences and StateFlow updated immediately")
            Log.i(TAG, "   🔍 New StateFlow useCustom: ${_useCustomServer.value}")
            Log.i(TAG, "   🔍 New StateFlow endpoint: ${_currentEndpoint.value}")
        } else {
            Log.i(TAG, "   ⚠️ No change needed (same value)")
        }
    }
    
    /**
     * URL 유효성 검사
     */
    fun isValidUrl(url: String): Boolean {
        return try {
            val cleanUrl = url.trim()
            cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 서버 연결 테스트용 기본 URL들
     */
    fun getPresetUrls(): List<String> {
        return listOf(
            "http://192.168.1.100:3000",   // 일반적인 공유기 대역
            "http://192.168.0.100:3000",   // 또 다른 일반적인 공유기 대역  
            "http://192.168.10.100:3000",  // 기업용 네트워크 대역
            "http://10.0.2.2:3000",        // 에뮬레이터용
            "http://localhost:3000",       // 로컬호스트
            DEFAULT_PRODUCTION_SERVER      // 프로덕션 서버
        )
    }
}