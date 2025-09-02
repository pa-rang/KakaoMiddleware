package com.example.kakaomiddleware

import android.content.Context
import android.content.SharedPreferences
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

/**
 * RemoteInput 정보 영구 저장소
 * SharedPreferences를 사용하여 앱 재시작 후에도 RemoteInput 정보 유지
 */
class PersistentRemoteInputStorage private constructor(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "remote_input_storage"
        private const val TAG = "PersistentRemoteInputStorage"
        
        @Volatile
        private var instance: PersistentRemoteInputStorage? = null
        
        fun getInstance(context: Context): PersistentRemoteInputStorage {
            return instance ?: synchronized(this) {
                instance ?: PersistentRemoteInputStorage(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val sharedPrefs: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // 메모리 캐시
    private val remoteInputCache = mutableMapOf<String, RemoteInputInfo>()
    
    init {
        Log.d(TAG, "PersistentRemoteInputStorage initialized")
        loadAllToCache()
    }
    
    /**
     * StatusBarNotification에서 RemoteInput 정보 추출하여 저장
     */
    fun storeFromStatusBarNotification(chatId: String, sbn: StatusBarNotification) {
        try {
            val remoteInputInfo = RemoteInputInfo.fromStatusBarNotification(chatId, sbn)
            
            if (remoteInputInfo != null) {
                storeRemoteInputInfo(remoteInputInfo)
                Log.d(TAG, "💾 Stored RemoteInput info for: $chatId")
            } else {
                Log.w(TAG, "⚠️ Failed to extract RemoteInput info from: $chatId")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error storing RemoteInput info for $chatId", e)
        }
    }
    
    /**
     * RemoteInput 정보 저장
     */
    private fun storeRemoteInputInfo(info: RemoteInputInfo) {
        try {
            val json = gson.toJson(info)
            sharedPrefs.edit()
                .putString(info.chatId, json)
                .apply()
            
            // 메모리 캐시 업데이트
            remoteInputCache[info.chatId] = info
            
            Log.v(TAG, "💿 RemoteInput info saved: ${info.chatId} (${info.chatType})")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving RemoteInput info for ${info.chatId}", e)
        }
    }
    
    /**
     * RemoteInput 정보 조회
     */
    fun getRemoteInputInfo(chatId: String): RemoteInputInfo? {
        // 메모리 캐시 우선 확인
        remoteInputCache[chatId]?.let {
            Log.v(TAG, "📱 Cache hit for RemoteInput: $chatId")
            return it
        }
        
        // SharedPreferences에서 조회
        return try {
            val json = sharedPrefs.getString(chatId, null)
            json?.let {
                val info = gson.fromJson(it, RemoteInputInfo::class.java)
                // 캐시에 저장
                remoteInputCache[chatId] = info
                Log.v(TAG, "💿 Loaded RemoteInput from storage: $chatId")
                info
            }
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "JSON parsing error for RemoteInput: $chatId", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error loading RemoteInput info for $chatId", e)
            null
        }
    }
    
    /**
     * 모든 활성 RemoteInput 정보 조회
     */
    fun getAllActiveRemoteInputs(): List<RemoteInputInfo> {
        val allInfos = mutableListOf<RemoteInputInfo>()
        
        try {
            sharedPrefs.all.forEach { (key, value) ->
                try {
                    val json = value as? String
                    json?.let {
                        val info = gson.fromJson(it, RemoteInputInfo::class.java)
                        if (info.isActive) {
                            allInfos.add(info)
                            // 캐시 업데이트
                            remoteInputCache[key] = info
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing RemoteInput info for key: $key", e)
                }
            }
            
            Log.d(TAG, "📋 Loaded ${allInfos.size} active RemoteInput infos")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading all RemoteInput infos", e)
        }
        
        // 최신 업데이트 순으로 정렬
        return allInfos.sortedByDescending { it.lastUpdateTime }
    }
    
    /**
     * 유효한 RemoteInput 정보만 조회 (모든 활성 정보가 유효)
     */
    fun getValidRemoteInputs(): List<RemoteInputInfo> {
        return getAllActiveRemoteInputs()  // 모든 활성 정보가 항상 유효
    }
    
    /**
     * RemoteInput 정보 업데이트
     */
    fun updateRemoteInputInfo(chatId: String, sbn: StatusBarNotification) {
        val existingInfo = getRemoteInputInfo(chatId)
        
        if (existingInfo != null) {
            // 기존 정보 업데이트
            val updatedInfo = RemoteInputInfo.fromStatusBarNotification(chatId, sbn)
            updatedInfo?.let {
                val finalInfo = it.copy(lastUpdateTime = System.currentTimeMillis())
                storeRemoteInputInfo(finalInfo)
                Log.d(TAG, "🔄 Updated RemoteInput info: $chatId")
            }
        } else {
            // 새로 저장
            storeFromStatusBarNotification(chatId, sbn)
        }
    }
    
    /**
     * RemoteInput 정보 비활성화
     */
    fun deactivateRemoteInputInfo(chatId: String) {
        getRemoteInputInfo(chatId)?.let { info ->
            val deactivatedInfo = info.copy(
                isActive = false,
                lastUpdateTime = System.currentTimeMillis()
            )
            storeRemoteInputInfo(deactivatedInfo)
            Log.d(TAG, "❌ Deactivated RemoteInput info: $chatId")
        }
    }
    
    /**
     * RemoteInput 정보 완전 삭제
     */
    fun deleteRemoteInputInfo(chatId: String) {
        try {
            sharedPrefs.edit()
                .remove(chatId)
                .apply()
            
            remoteInputCache.remove(chatId)
            Log.d(TAG, "🗑️ Deleted RemoteInput info: $chatId")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting RemoteInput info: $chatId", e)
        }
    }
    
    /**
     * 오래된 RemoteInput 정보 정리 (비활성화됨 - 무한 보존)
     */
    fun cleanupOldRemoteInputs(olderThanDays: Int = 7) {
        Log.d(TAG, "🔒 Cleanup disabled - infinite retention mode")
        Log.d(TAG, "   All RemoteInput infos will be preserved indefinitely")
        // 정리 기능을 완전히 비활성화 - 모든 데이터를 무한히 보존
    }
    
    /**
     * 모든 RemoteInput 정보를 메모리 캐시에 로딩
     */
    private fun loadAllToCache() {
        try {
            var loadedCount = 0
            sharedPrefs.all.forEach { (key, value) ->
                try {
                    val json = value as? String
                    json?.let {
                        val info = gson.fromJson(it, RemoteInputInfo::class.java)
                        if (info.isActive) {
                            remoteInputCache[key] = info
                            loadedCount++
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error loading RemoteInput to cache for key: $key", e)
                }
            }
            Log.d(TAG, "🚀 Pre-loaded $loadedCount RemoteInput infos to cache")
        } catch (e: Exception) {
            Log.e(TAG, "Error during RemoteInput cache pre-loading", e)
        }
    }
    
    /**
     * 저장소 통계 정보
     */
    fun getStorageStats(): Map<String, Int> {
        return mapOf(
            "totalRemoteInputs" to sharedPrefs.all.size,
            "activeRemoteInputs" to getAllActiveRemoteInputs().size,
            "validRemoteInputs" to getValidRemoteInputs().size,
            "cachedRemoteInputs" to remoteInputCache.size
        )
    }
    
    /**
     * 모든 저장된 RemoteInput 정보를 로그로 출력 (디버깅용)
     */
    fun logAllStoredRemoteInputs() {
        try {
            Log.d(TAG, "📋 === PersistentRemoteInputStorage Debug Info (Infinite Retention) ===")
            val stats = getStorageStats()
            Log.d(TAG, "📊 Total: ${stats["totalRemoteInputs"]}, Active: ${stats["activeRemoteInputs"]} (all valid)")
            
            val allInfos = getValidRemoteInputs()
            if (allInfos.isEmpty()) {
                Log.d(TAG, "🔍 No RemoteInput infos found in storage")
                return
            }
            
            allInfos.forEach { info ->
                Log.d(TAG, "📱 ${info.displayName}")
                Log.d(TAG, "  ├─ ChatId: ${info.chatId}")
                Log.d(TAG, "  ├─ RemoteInputKey: ${info.remoteInputKey}")
                Log.d(TAG, "  ├─ PostTime: ${info.formattedTime} (${info.ageMinutes}분 전)")
                Log.d(TAG, "  ├─ NotificationKey: ${info.notificationKey}")
                Log.d(TAG, "  ├─ LastSender: ${info.title}")
                Log.d(TAG, "  └─ Status: Always valid (infinite retention)")
            }
            
            Log.d(TAG, "📋 === End of PersistentRemoteInputStorage Debug ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error logging stored RemoteInput infos", e)
        }
    }
}