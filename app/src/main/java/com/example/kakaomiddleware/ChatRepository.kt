package com.example.kakaomiddleware

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

/**
 * 채팅방 컨텍스트 저장소
 * SharedPreferences를 이용한 로컬 저장소로 채팅방 정보를 지속적으로 보관
 */
class ChatRepository private constructor(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "chat_contexts"
        private const val TAG = "ChatRepository"
        
        @Volatile
        private var instance: ChatRepository? = null
        
        /**
         * 싱글톤 인스턴스 반환
         */
        fun getInstance(context: Context): ChatRepository {
            return instance ?: synchronized(this) {
                instance ?: ChatRepository(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val sharedPrefs: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // 메모리 캐시로 빠른 접근 제공
    private val chatContextsCache = mutableMapOf<String, ChatContext>()
    
    // 이전 컨텍스트 개수 저장 (변경 감지용)
    private var previousContextCount = 0
    
    init {
        Log.d(TAG, "ChatRepository initialized")
        // 앱 시작 시 캐시 pre-loading
        loadAllContextsToCache()
    }
    
    /**
     * 채팅방 컨텍스트 저장
     * @param chatContext 저장할 채팅방 컨텍스트
     */
    fun saveChatContext(chatContext: ChatContext) {
        try {
            val json = gson.toJson(chatContext)
            sharedPrefs.edit()
                .putString(chatContext.chatId, json)
                .apply()
            
            // 메모리 캐시 업데이트
            chatContextsCache[chatContext.chatId] = chatContext
            
            Log.d(TAG, "💾 Chat context saved: ${chatContext.chatId} (${chatContext.chatType})")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving chat context for ${chatContext.chatId}", e)
        }
    }
    
    /**
     * 기존 컨텍스트 업데이트 (최신 RemoteInput 정보로 갱신)
     * @param chatContext 업데이트할 채팅방 컨텍스트
     */
    fun updateChatContext(chatContext: ChatContext) {
        val updatedContext = chatContext.copy(
            lastUpdateTime = System.currentTimeMillis()
        )
        saveChatContext(updatedContext)
    }
    
    /**
     * 채팅방 컨텍스트 조회
     * @param chatId 채팅방 ID
     * @return 채팅방 컨텍스트 (없으면 null)
     */
    fun getChatContext(chatId: String): ChatContext? {
        // 메모리 캐시 우선 확인
        chatContextsCache[chatId]?.let { 
            Log.d(TAG, "📱 Cache hit for chatId: $chatId")
            return it 
        }
        
        // SharedPreferences에서 조회
        return try {
            val json = sharedPrefs.getString(chatId, null)
            json?.let {
                val context = gson.fromJson(it, ChatContext::class.java)
                // 캐시에 저장
                chatContextsCache[chatId] = context
                Log.d(TAG, "💿 Loaded from storage: $chatId")
                context
            }
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "JSON parsing error for chatId: $chatId", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error loading chat context for $chatId", e)
            null
        }
    }
    
    /**
     * 모든 활성 채팅방 컨텍스트 조회
     * @return 활성 상태의 채팅방 컨텍스트 목록 (최신순)
     */
    fun getAllActiveChatContexts(): List<ChatContext> {
        val allContexts = mutableListOf<ChatContext>()
        
        try {
            sharedPrefs.all.forEach { (key, value) ->
                try {
                    val json = value as? String
                    json?.let {
                        val context = gson.fromJson(it, ChatContext::class.java)
                        if (context.isActive) {
                            allContexts.add(context)
                            // 캐시 업데이트
                            chatContextsCache[key] = context
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing chat context for key: $key", e)
                }
            }
            
            // 변경사항이 있을 때만 로그 출력
            if (allContexts.size != previousContextCount) {
                Log.v(TAG, "📋 Chat contexts updated: ${previousContextCount} → ${allContexts.size} active contexts")
                previousContextCount = allContexts.size
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading all chat contexts", e)
        }
        
        // 최신 업데이트 순으로 정렬
        return allContexts.sortedByDescending { it.lastUpdateTime }
    }
    
    /**
     * 그룹 채팅방만 조회
     */
    fun getGroupChatContexts(): List<ChatContext> {
        return getAllActiveChatContexts().filter { 
            it.chatType == ChatContext.ChatType.GROUP 
        }
    }
    
    /**
     * 개인 채팅방만 조회
     */
    fun getPersonalChatContexts(): List<ChatContext> {
        return getAllActiveChatContexts().filter { 
            it.chatType == ChatContext.ChatType.PERSONAL 
        }
    }
    
    /**
     * 채팅방 컨텍스트 비활성화 (삭제하지 않고 보관)
     * @param chatId 비활성화할 채팅방 ID
     */
    fun deactivateChatContext(chatId: String) {
        getChatContext(chatId)?.let { context ->
            val deactivatedContext = context.copy(
                isActive = false,
                lastUpdateTime = System.currentTimeMillis()
            )
            saveChatContext(deactivatedContext)
            Log.d(TAG, "❌ Chat context deactivated: $chatId")
        }
    }
    
    /**
     * 채팅방 컨텍스트 완전 삭제
     * @param chatId 삭제할 채팅방 ID
     */
    fun deleteChatContext(chatId: String) {
        try {
            sharedPrefs.edit()
                .remove(chatId)
                .apply()
            
            chatContextsCache.remove(chatId)
            Log.d(TAG, "🗑️ Chat context deleted: $chatId")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting chat context: $chatId", e)
        }
    }
    
    /**
     * 전체 저장소 정리 (비활성화된 컨텍스트 삭제)
     * @param olderThanDays 지정된 일수보다 오래된 비활성 컨텍스트 삭제
     */
    fun cleanupOldContexts(olderThanDays: Int = 30) {
        val cutoffTime = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L)
        var deletedCount = 0
        
        try {
            val allEntries = sharedPrefs.all.toMap() // 복사본 생성
            
            allEntries.forEach { (key, value) ->
                try {
                    val json = value as? String
                    json?.let {
                        val context = gson.fromJson(it, ChatContext::class.java)
                        if (!context.isActive && context.lastUpdateTime < cutoffTime) {
                            deleteChatContext(key)
                            deletedCount++
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error during cleanup for key: $key", e)
                }
            }
            
            Log.d(TAG, "🧹 Cleanup completed: $deletedCount old contexts deleted")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
    
    /**
     * 모든 컨텍스트를 메모리 캐시에 로딩 (앱 시작 시 성능 최적화)
     */
    private fun loadAllContextsToCache() {
        try {
            var loadedCount = 0
            sharedPrefs.all.forEach { (key, value) ->
                try {
                    val json = value as? String
                    json?.let {
                        val context = gson.fromJson(it, ChatContext::class.java)
                        if (context.isActive) {
                            chatContextsCache[key] = context
                            loadedCount++
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error loading context to cache for key: $key", e)
                }
            }
            Log.d(TAG, "🚀 Pre-loaded $loadedCount contexts to cache")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cache pre-loading", e)
        }
    }
    
    /**
     * 저장소 통계 정보 반환 (디버깅용)
     */
    fun getStorageStats(): Map<String, Int> {
        return mapOf(
            "totalContexts" to sharedPrefs.all.size,
            "activeContexts" to getAllActiveChatContexts().size,
            "groupContexts" to getGroupChatContexts().size,
            "personalContexts" to getPersonalChatContexts().size,
            "cachedContexts" to chatContextsCache.size
        )
    }
}