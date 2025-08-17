package com.example.kakaomiddleware

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AllowlistManager private constructor(context: Context) {
    
    companion object {
        private const val TAG = "AllowlistManager"
        private const val PREFS_NAME = "allowlist_prefs"
        private const val KEY_PERSONAL_ALLOWLIST = "personal_allowlist"
        private const val KEY_GROUP_ALLOWLIST = "group_allowlist"
        private const val DELIMITER = "||"
        
        @Volatile
        private var INSTANCE: AllowlistManager? = null
        
        fun getInstance(context: Context): AllowlistManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AllowlistManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _personalAllowlist = MutableStateFlow(loadPersonalAllowlist())
    val personalAllowlist: StateFlow<Set<String>> = _personalAllowlist.asStateFlow()
    
    private val _groupAllowlist = MutableStateFlow(loadGroupAllowlist())
    val groupAllowlist: StateFlow<Set<String>> = _groupAllowlist.asStateFlow()
    
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            KEY_PERSONAL_ALLOWLIST -> {
                val newList = loadPersonalAllowlist()
                _personalAllowlist.value = newList
                Log.d(TAG, "Personal allowlist updated from SharedPrefs: $newList")
            }
            KEY_GROUP_ALLOWLIST -> {
                val newList = loadGroupAllowlist()
                _groupAllowlist.value = newList
                Log.d(TAG, "Group allowlist updated from SharedPrefs: $newList")
            }
        }
    }
    
    init {
        Log.d(TAG, "AllowlistManager initialized")
        sharedPrefs.registerOnSharedPreferenceChangeListener(prefsListener)
        debugPrintAllowlists()
    }
    
    private fun loadPersonalAllowlist(): Set<String> {
        val saved = sharedPrefs.getString(KEY_PERSONAL_ALLOWLIST, "") ?: ""
        return if (saved.isEmpty()) emptySet() else saved.split(DELIMITER).toSet()
    }
    
    private fun loadGroupAllowlist(): Set<String> {
        val saved = sharedPrefs.getString(KEY_GROUP_ALLOWLIST, "") ?: ""
        return if (saved.isEmpty()) emptySet() else saved.split(DELIMITER).toSet()
    }
    
    fun addPersonalContact(name: String) {
        val current = _personalAllowlist.value.toMutableSet()
        current.add(name.trim())
        _personalAllowlist.value = current
        savePersonalAllowlist(current)
        Log.d(TAG, "Added personal contact: $name")
    }
    
    fun removePersonalContact(name: String) {
        val current = _personalAllowlist.value.toMutableSet()
        current.remove(name.trim())
        _personalAllowlist.value = current
        savePersonalAllowlist(current)
        Log.d(TAG, "Removed personal contact: $name")
    }
    
    fun addGroupName(name: String) {
        val current = _groupAllowlist.value.toMutableSet()
        current.add(name.trim())
        _groupAllowlist.value = current
        saveGroupAllowlist(current)
        Log.d(TAG, "Added group: $name")
    }
    
    fun removeGroupName(name: String) {
        val current = _groupAllowlist.value.toMutableSet()
        current.remove(name.trim())
        _groupAllowlist.value = current
        saveGroupAllowlist(current)
        Log.d(TAG, "Removed group: $name")
    }
    
    private fun savePersonalAllowlist(allowlist: Set<String>) {
        sharedPrefs.edit()
            .putString(KEY_PERSONAL_ALLOWLIST, allowlist.joinToString(DELIMITER))
            .apply()
    }
    
    private fun saveGroupAllowlist(allowlist: Set<String>) {
        sharedPrefs.edit()
            .putString(KEY_GROUP_ALLOWLIST, allowlist.joinToString(DELIMITER))
            .apply()
    }
    
    fun isPersonalAllowed(sender: String): Boolean {
        val currentList = _personalAllowlist.value
        val trimmedSender = sender.trim()
        val allowed = currentList.contains(trimmedSender)
        Log.d(TAG, "Personal allowlist check:")
        Log.d(TAG, "  - Sender: '$sender' (trimmed: '$trimmedSender')")
        Log.d(TAG, "  - Current allowlist: $currentList")
        Log.d(TAG, "  - Allowed: $allowed")
        return allowed
    }
    
    fun isGroupAllowed(groupName: String): Boolean {
        val currentList = _groupAllowlist.value
        val trimmedGroupName = groupName.trim()
        val allowed = currentList.contains(trimmedGroupName)
        Log.d(TAG, "Group allowlist check:")
        Log.d(TAG, "  - Group: '$groupName' (trimmed: '$trimmedGroupName')")
        Log.d(TAG, "  - Current allowlist: $currentList")
        Log.d(TAG, "  - Allowed: $allowed")
        return allowed
    }
    
    fun debugPrintAllowlists() {
        Log.d(TAG, "=== ALLOWLIST DEBUG INFO ===")
        Log.d(TAG, "Personal allowlist: ${_personalAllowlist.value}")
        Log.d(TAG, "Group allowlist: ${_groupAllowlist.value}")
        Log.d(TAG, "SharedPrefs personal: ${sharedPrefs.getString(KEY_PERSONAL_ALLOWLIST, "EMPTY")}")
        Log.d(TAG, "SharedPrefs group: ${sharedPrefs.getString(KEY_GROUP_ALLOWLIST, "EMPTY")}")
        Log.d(TAG, "=== END DEBUG INFO ===")
    }
}