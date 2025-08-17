package com.example.kakaomiddleware

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AllowlistManager(context: Context) {
    
    companion object {
        private const val TAG = "AllowlistManager"
        private const val PREFS_NAME = "allowlist_prefs"
        private const val KEY_PERSONAL_ALLOWLIST = "personal_allowlist"
        private const val KEY_GROUP_ALLOWLIST = "group_allowlist"
        private const val DELIMITER = "||"
    }
    
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _personalAllowlist = MutableStateFlow(loadPersonalAllowlist())
    val personalAllowlist: StateFlow<Set<String>> = _personalAllowlist.asStateFlow()
    
    private val _groupAllowlist = MutableStateFlow(loadGroupAllowlist())
    val groupAllowlist: StateFlow<Set<String>> = _groupAllowlist.asStateFlow()
    
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
        val allowed = _personalAllowlist.value.contains(sender.trim())
        Log.d(TAG, "Personal allowlist check - Sender: $sender, Allowed: $allowed")
        return allowed
    }
    
    fun isGroupAllowed(groupName: String): Boolean {
        val allowed = _groupAllowlist.value.contains(groupName.trim())
        Log.d(TAG, "Group allowlist check - Group: $groupName, Allowed: $allowed")
        return allowed
    }
}