package com.example.kakaomiddleware

import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log

class RemoteInputHijacker(private val context: Context) {
    
    companion object {
        private const val TAG = "RemoteInputHijacker"
    }
    
    /**
     * Extracts the RemoteInput from a KakaoTalk notification
     * @param sbn The original StatusBarNotification from KakaoTalk
     * @return RemoteInput if found, null otherwise
     */
    fun extractOriginalRemoteInput(sbn: StatusBarNotification): RemoteInput? {
        try {
            val actions = sbn.notification.actions
            if (actions == null) {
                Log.d(TAG, "No actions found in notification")
                return null
            }
            
            for (action in actions) {
                val remoteInputs = action.remoteInputs
                if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                    Log.d(TAG, "Found RemoteInput with key: ${remoteInputs[0].resultKey}")
                    return remoteInputs[0]
                }
            }
            
            Log.d(TAG, "No RemoteInput found in notification actions")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting RemoteInput", e)
            return null
        }
    }
    
    /**
     * Injects an AI response back to KakaoTalk using the original notification's RemoteInput
     * @param sbn The original StatusBarNotification from KakaoTalk
     * @param aiResponse The AI-generated response text
     * @return true if injection was successful, false otherwise
     */
    fun injectResponse(sbn: StatusBarNotification, aiResponse: String): Boolean {
        try {
            val actions = sbn.notification.actions
            if (actions == null) {
                Log.e(TAG, "No actions found in notification for injection")
                return false
            }
            
            // Find the action with RemoteInput (usually the reply action)
            val replyAction = actions.find { it.remoteInputs?.isNotEmpty() == true }
            if (replyAction == null) {
                Log.e(TAG, "No reply action with RemoteInput found")
                return false
            }
            
            val remoteInput = replyAction.remoteInputs[0]
            val intent = Intent()
            val bundle = Bundle()
            
            // Inject the AI response into the RemoteInput
            bundle.putCharSequence(remoteInput.resultKey, aiResponse)
            RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
            
            Log.d(TAG, "Injecting response: '$aiResponse' with key: '${remoteInput.resultKey}'")
            
            // Send the intent back to KakaoTalk
            replyAction.actionIntent.send(context, 0, intent)
            
            Log.d(TAG, "Successfully injected AI response to KakaoTalk")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting response to KakaoTalk", e)
            return false
        }
    }
    
    /**
     * Validates if a notification has the capability for hijacking
     * @param sbn The StatusBarNotification to validate
     * @return true if hijacking is possible, false otherwise
     */
    fun canHijackNotification(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName != "com.kakao.talk") {
            return false
        }
        
        val actions = sbn.notification.actions
        if (actions == null) {
            return false
        }
        
        // Check if there's at least one action with RemoteInput
        return actions.any { it.remoteInputs?.isNotEmpty() == true }
    }
    
    /**
     * Gets detailed information about the notification's RemoteInput capabilities
     * @param sbn The StatusBarNotification to analyze
     * @return Map of debugging information
     */
    fun getHijackingDebugInfo(sbn: StatusBarNotification): Map<String, Any> {
        val debugInfo = mutableMapOf<String, Any>()
        
        debugInfo["package"] = sbn.packageName
        debugInfo["hasActions"] = sbn.notification.actions != null
        debugInfo["actionsCount"] = sbn.notification.actions?.size ?: 0
        
        val actions = sbn.notification.actions
        if (actions != null) {
            val remoteInputActions = actions.filter { it.remoteInputs?.isNotEmpty() == true }
            debugInfo["remoteInputActionsCount"] = remoteInputActions.size
            
            if (remoteInputActions.isNotEmpty()) {
                val firstRemoteInput = remoteInputActions[0].remoteInputs[0]
                debugInfo["remoteInputKey"] = firstRemoteInput.resultKey
                debugInfo["remoteInputLabel"] = firstRemoteInput.label?.toString() ?: "null"
            }
        }
        
        return debugInfo
    }
}