package com.seudominio.app_smart_companion.assistants

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Date

/**
 * ThreadManager - Gerenciamento avançado de threads OpenAI Assistants
 * 
 * Responsável por:
 * - Criar e gerenciar threads persistentes
 * - Manter contexto de conversa entre interações
 * - Armazenar metadados de threads
 * - Fornecer interface simplificada para modo ativo
 * 
 * Baseado na documentação oficial OpenAI Assistants API v2
 */
class ThreadManager(
    private val context: Context,
    private val assistantClient: OpenAIAssistantClient
) {
    companion object {
        private const val TAG = "ThreadManager"
        private const val PREFS_NAME = "thread_manager_prefs"
        private const val KEY_ACTIVE_THREAD_PREFIX = "active_thread_"
        private const val KEY_THREAD_METADATA_PREFIX = "thread_metadata_"
        private const val KEY_THREAD_CREATED_AT_PREFIX = "thread_created_"
        
        // Thread expiration time (24 hours)
        private const val THREAD_EXPIRATION_MS = 24 * 60 * 60 * 1000L
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Current active thread state
    private var activeThreadId: String? = null
    private var activeAssistantId: String? = null
    
    /**
     * Thread metadata for tracking conversation context
     */
    data class ThreadMetadata(
        val threadId: String,
        val assistantId: String,
        val createdAt: Long,
        val lastUsedAt: Long,
        val messageCount: Int = 0,
        val customData: Map<String, String> = emptyMap()
    )
    
    /**
     * Initialize ThreadManager with an assistant
     */
    fun initialize(assistantId: String) {
        Log.d(TAG, "🔧 Initializing ThreadManager for assistant: $assistantId")
        activeAssistantId = assistantId
        
        // Try to restore existing thread for this assistant
        restoreActiveThread(assistantId)
    }
    
    /**
     * Create a new thread for conversation
     */
    suspend fun createNewThread(): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🆕 Creating new thread for assistant: $activeAssistantId")
            
            val assistantId = activeAssistantId ?: run {
                Log.e(TAG, "❌ No assistant ID set")
                return@withContext null
            }
            
            // Set assistant ID in client
            assistantClient.setAssistantId(assistantId)
            
            // Create thread via client
            val threadId = assistantClient.createThread()
            
            if (threadId != null) {
                // Save as active thread
                activeThreadId = threadId
                saveActiveThread(assistantId, threadId)
                
                // Create initial metadata
                val metadata = ThreadMetadata(
                    threadId = threadId,
                    assistantId = assistantId,
                    createdAt = System.currentTimeMillis(),
                    lastUsedAt = System.currentTimeMillis()
                )
                saveThreadMetadata(metadata)
                
                Log.d(TAG, "✅ New thread created and saved: $threadId")
                return@withContext threadId
            } else {
                Log.e(TAG, "❌ Failed to create thread")
                return@withContext null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception creating thread", e)
            return@withContext null
        }
    }
    
    /**
     * Get current active thread ID
     */
    fun getCurrentThreadId(): String? {
        // Validate thread is not expired
        if (activeThreadId != null && isThreadExpired(activeThreadId!!)) {
            Log.w(TAG, "⚠️ Thread expired: $activeThreadId")
            clearActiveThread()
            return null
        }
        return activeThreadId
    }
    
    /**
     * Use existing thread or create new one
     */
    suspend fun ensureActiveThread(): String? = withContext(Dispatchers.IO) {
        val currentThread = getCurrentThreadId()
        if (currentThread != null) {
            Log.d(TAG, "✅ Using existing thread: $currentThread")
            updateThreadLastUsed(currentThread)
            return@withContext currentThread
        }
        
        Log.d(TAG, "🔄 No active thread, creating new one")
        return@withContext createNewThread()
    }
    
    /**
     * Clear current thread (for new conversation)
     */
    fun clearActiveThread() {
        Log.d(TAG, "🧹 Clearing active thread: $activeThreadId")
        
        activeAssistantId?.let { assistantId ->
            prefs.edit()
                .remove("$KEY_ACTIVE_THREAD_PREFIX$assistantId")
                .apply()
        }
        
        activeThreadId = null
    }
    
    /**
     * Get thread metadata
     */
    fun getThreadMetadata(threadId: String = activeThreadId ?: ""): ThreadMetadata? {
        if (threadId.isEmpty()) return null
        
        val metadataJson = prefs.getString("$KEY_THREAD_METADATA_PREFIX$threadId", null)
        return metadataJson?.let { parseMetadata(it) }
    }
    
    /**
     * Update thread metadata (e.g., increment message count)
     */
    fun updateThreadMetadata(
        threadId: String = activeThreadId ?: "",
        updateBlock: (ThreadMetadata) -> ThreadMetadata
    ) {
        if (threadId.isEmpty()) return
        
        val current = getThreadMetadata(threadId) ?: return
        val updated = updateBlock(current)
        saveThreadMetadata(updated)
    }
    
    /**
     * Increment message count for current thread
     */
    fun incrementMessageCount() {
        updateThreadMetadata { metadata ->
            metadata.copy(
                messageCount = metadata.messageCount + 1,
                lastUsedAt = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Check if a thread is expired
     */
    private fun isThreadExpired(threadId: String): Boolean {
        val createdAt = prefs.getLong("$KEY_THREAD_CREATED_AT_PREFIX$threadId", 0)
        if (createdAt == 0L) return true
        
        val age = System.currentTimeMillis() - createdAt
        return age > THREAD_EXPIRATION_MS
    }
    
    /**
     * Save active thread for assistant
     */
    private fun saveActiveThread(assistantId: String, threadId: String) {
        prefs.edit()
            .putString("$KEY_ACTIVE_THREAD_PREFIX$assistantId", threadId)
            .putLong("$KEY_THREAD_CREATED_AT_PREFIX$threadId", System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Restore active thread for assistant
     */
    private fun restoreActiveThread(assistantId: String) {
        val savedThreadId = prefs.getString("$KEY_ACTIVE_THREAD_PREFIX$assistantId", null)
        if (savedThreadId != null && !isThreadExpired(savedThreadId)) {
            activeThreadId = savedThreadId
            Log.d(TAG, "♻️ Restored active thread: $savedThreadId")
        }
    }
    
    /**
     * Save thread metadata
     */
    private fun saveThreadMetadata(metadata: ThreadMetadata) {
        val json = JSONObject().apply {
            put("threadId", metadata.threadId)
            put("assistantId", metadata.assistantId)
            put("createdAt", metadata.createdAt)
            put("lastUsedAt", metadata.lastUsedAt)
            put("messageCount", metadata.messageCount)
            
            val customDataJson = JSONObject()
            metadata.customData.forEach { (key, value) ->
                customDataJson.put(key, value)
            }
            put("customData", customDataJson)
        }
        
        prefs.edit()
            .putString("$KEY_THREAD_METADATA_PREFIX${metadata.threadId}", json.toString())
            .apply()
    }
    
    /**
     * Parse metadata from JSON
     */
    private fun parseMetadata(json: String): ThreadMetadata? {
        return try {
            val obj = JSONObject(json)
            val customData = mutableMapOf<String, String>()
            
            val customDataObj = obj.optJSONObject("customData")
            customDataObj?.keys()?.forEach { key ->
                customData[key] = customDataObj.getString(key)
            }
            
            ThreadMetadata(
                threadId = obj.getString("threadId"),
                assistantId = obj.getString("assistantId"),
                createdAt = obj.getLong("createdAt"),
                lastUsedAt = obj.getLong("lastUsedAt"),
                messageCount = obj.optInt("messageCount", 0),
                customData = customData
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parsing metadata", e)
            null
        }
    }
    
    /**
     * Update last used timestamp
     */
    private fun updateThreadLastUsed(threadId: String) {
        updateThreadMetadata(threadId) { metadata ->
            metadata.copy(lastUsedAt = System.currentTimeMillis())
        }
    }
    
    /**
     * Get thread age in human-readable format
     */
    fun getThreadAge(threadId: String = activeThreadId ?: ""): String {
        val metadata = getThreadMetadata(threadId) ?: return "Unknown"
        
        val ageMs = System.currentTimeMillis() - metadata.createdAt
        val hours = ageMs / (1000 * 60 * 60)
        val minutes = (ageMs % (1000 * 60 * 60)) / (1000 * 60)
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }
    
    /**
     * Clean up expired threads from storage
     */
    fun cleanupExpiredThreads() {
        scope.launch {
            Log.d(TAG, "🧹 Cleaning up expired threads")
            
            val allKeys = prefs.all.keys
            var cleanedCount = 0
            
            allKeys.filter { it.startsWith(KEY_THREAD_CREATED_AT_PREFIX) }.forEach { key ->
                val threadId = key.removePrefix(KEY_THREAD_CREATED_AT_PREFIX)
                if (isThreadExpired(threadId)) {
                    // Remove all data for this thread
                    prefs.edit()
                        .remove(key)
                        .remove("$KEY_THREAD_METADATA_PREFIX$threadId")
                        .apply()
                    cleanedCount++
                }
            }
            
            Log.d(TAG, "✅ Cleaned up $cleanedCount expired threads")
        }
    }
}