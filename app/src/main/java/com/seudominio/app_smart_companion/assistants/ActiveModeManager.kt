package com.seudominio.app_smart_companion.assistants

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.launch

/**
 * ActiveModeManager - Gerenciador do modo de conexão ativa
 * 
 * Coordena o modo de conversação contínua com:
 * - Thread persistente para manter contexto
 * - Integração com Audio/Photo managers
 * - Toggle de resposta em áudio (TTS)
 * - Interface discreta otimizada
 * 
 * Padrão definido em ASSISTANT_MENU_PLAN.md
 */
class ActiveModeManager(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val apiKey: String
) {
    companion object {
        private const val TAG = "ActiveModeManager"
        private const val PREFS_NAME = "active_mode_prefs"
        private const val KEY_AUDIO_RESPONSE_ENABLED = "audio_response_enabled"
    }
    
    // Core components
    private lateinit var assistantClient: OpenAIAssistantClient
    private lateinit var threadManager: ThreadManager
    private lateinit var audioManager: AssistantAudioManager
    private lateinit var photoManager: AssistantPhotoManager
    private lateinit var ttsManager: TTSManager
    
    // State management
    private var isActiveMode = false
    private var currentAssistantId: String? = null
    private var currentThreadId: String? = null
    
    // Audio response toggle (TTS)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    var isAudioResponseEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUDIO_RESPONSE_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_AUDIO_RESPONSE_ENABLED, value).apply()
            Log.d(TAG, "🔊 Audio response ${if (value) "enabled" else "disabled"}")
        }
    
    // Callbacks for UI updates
    interface ActiveModeCallback {
        fun onActiveModeStarted(threadId: String)
        fun onActiveModeEnded()
        fun onThreadCreated(newThreadId: String)
        fun onAudioToggled(enabled: Boolean)
        fun onResponse(response: String)
        fun onError(error: String)
        fun onStatusUpdate(status: String)
    }
    
    private var callback: ActiveModeCallback? = null
    
    /**
     * Set UI callback
     */
    fun setCallback(callback: ActiveModeCallback) {
        this.callback = callback
    }
    
    /**
     * Enter active mode with specified assistant
     */
    suspend fun enterActiveMode(assistantId: String) {
        Log.d(TAG, "🟢 Entering active mode for assistant: $assistantId")
        
        try {
            // Initialize components
            initializeComponents(assistantId)
            
            // Ensure active thread
            currentThreadId = threadManager.ensureActiveThread()
            if (currentThreadId == null) {
                callback?.onError("Falha ao criar thread de conversa")
                return
            }
            
            // Update state
            isActiveMode = true
            currentAssistantId = assistantId
            
            // Notify UI
            callback?.onActiveModeStarted(currentThreadId!!)
            callback?.onStatusUpdate("Modo ativo iniciado - Thread: ${currentThreadId?.takeLast(8)}")
            
            Log.d(TAG, "✅ Active mode started with thread: $currentThreadId")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error entering active mode", e)
            callback?.onError("Erro ao iniciar modo ativo: ${e.message}")
        }
    }
    
    /**
     * Exit active mode
     */
    fun exitActiveMode() {
        Log.d(TAG, "🔴 Exiting active mode")
        
        isActiveMode = false
        currentThreadId = null
        
        // Clean up managers
        audioManager.cancelProcessing()
        photoManager.dispose()
        ttsManager.stopPlayback()  // Stop any ongoing TTS playback
        
        callback?.onActiveModeEnded()
        callback?.onStatusUpdate("Modo ativo encerrado")
    }
    
    /**
     * Send audio in active mode (uses persistent thread)
     */
    fun sendAudioInActiveMode() {
        if (!isActiveMode) {
            Log.w(TAG, "⚠️ Not in active mode")
            callback?.onError("Não está no modo ativo")
            return
        }
        
        Log.d(TAG, "🎤 Sending audio in active mode with thread: $currentThreadId")
        
        audioManager.startAudioToAssistant(
            callback = object : AssistantAudioManager.AudioToAssistantCallback {
                override fun onRecordingStarted() {
                    callback?.onStatusUpdate("Gravando...")
                }
                
                override fun onProcessingStarted() {
                    callback?.onStatusUpdate("Processando...")
                    threadManager.incrementMessageCount()
                }
                
                override fun onAssistantResponse(response: String) {
                    handleAssistantResponse(response)
                }
                
                override fun onError(error: String) {
                    callback?.onError(error)
                }
            },
            threadId = currentThreadId,
            language = "pt"
        )
    }
    
    /**
     * Send photo in active mode (uses persistent thread)
     */
    fun sendPhotoInActiveMode() {
        if (!isActiveMode) {
            Log.w(TAG, "⚠️ Not in active mode")
            callback?.onError("Não está no modo ativo")
            return
        }
        
        Log.d(TAG, "📷 Sending photo in active mode with thread: $currentThreadId")
        
        photoManager.startPhotoToAssistant(
            visionPrompt = "Analyze this image in the context of our ongoing conversation",
            assistantPrompt = "Continue our coaching conversation based on this image",
            callback = object : AssistantPhotoManager.PhotoToAssistantCallback {
                override fun onCaptureStarted() {
                    // Silent - no feedback in active mode
                }
                
                override fun onPhotoTaken() {
                    callback?.onStatusUpdate("Foto capturada...")
                    threadManager.incrementMessageCount()
                }
                
                override fun onVisionAnalysisStarted() {
                    callback?.onStatusUpdate("Analisando...")
                }
                
                override fun onAssistantProcessingStarted() {
                    // Silent
                }
                
                override fun onAssistantResponse(response: String) {
                    handleAssistantResponse(response)
                }
                
                override fun onError(error: String) {
                    callback?.onError(error)
                }
            }
        )
    }
    
    /**
     * Create new thread (reset conversation)
     */
    suspend fun createNewThread() {
        if (!isActiveMode) {
            Log.w(TAG, "⚠️ Not in active mode")
            callback?.onError("Não está no modo ativo")
            return
        }
        
        Log.d(TAG, "🔄 Creating new thread in active mode")
        
        threadManager.clearActiveThread()
        currentThreadId = threadManager.createNewThread()
        
        if (currentThreadId != null) {
            callback?.onThreadCreated(currentThreadId!!)
            callback?.onStatusUpdate("Nova conversa iniciada - Thread: ${currentThreadId?.takeLast(8)}")
        } else {
            callback?.onError("Falha ao criar nova thread")
        }
    }
    
    /**
     * Toggle audio response (TTS)
     */
    fun toggleAudioResponse() {
        isAudioResponseEnabled = !isAudioResponseEnabled
        callback?.onAudioToggled(isAudioResponseEnabled)
        callback?.onStatusUpdate("Resposta em áudio ${if (isAudioResponseEnabled) "ativada" else "desativada"}")
    }
    
    /**
     * Get current thread metadata
     */
    fun getCurrentThreadInfo(): String {
        if (!isActiveMode) return "Modo ativo desligado"
        
        val metadata = threadManager.getThreadMetadata()
        return if (metadata != null) {
            "Thread: ${metadata.threadId.takeLast(8)} | Msgs: ${metadata.messageCount} | Idade: ${threadManager.getThreadAge()}"
        } else {
            "Thread não disponível"
        }
    }
    
    /**
     * Initialize all components
     */
    private fun initializeComponents(assistantId: String) {
        // Initialize assistant client
        assistantClient = OpenAIAssistantClient(
            apiKey = apiKey,
            onResponse = { response ->
                handleAssistantResponse(response)
            },
            onError = { error ->
                callback?.onError(error)
            },
            onStatusUpdate = { status ->
                Log.d(TAG, "ℹ️ $status")
            }
        )
        
        // Initialize thread manager
        threadManager = ThreadManager(context, assistantClient)
        threadManager.initialize(assistantId)
        
        // Initialize audio manager (thread-aware)
        audioManager = AssistantAudioManager(
            context = context,
            lifecycleScope = lifecycleScope,
            assistantId = assistantId,
            apiKey = apiKey
        )
        
        // Initialize photo manager
        photoManager = AssistantPhotoManager(
            context = context,
            lifecycleScope = lifecycleScope,
            assistantId = assistantId,
            apiKey = apiKey
        )
        
        // Initialize TTS manager
        ttsManager = TTSManager(
            context = context,
            apiKey = apiKey
        )
    }
    
    /**
     * Handle assistant response with optional TTS
     */
    private fun handleAssistantResponse(response: String) {
        // Display response
        callback?.onResponse(response)
        
        // Play audio if enabled
        if (isAudioResponseEnabled) {
            playAudioResponse(response)
        }
    }
    
    /**
     * Play audio response using TTS
     */
    private fun playAudioResponse(text: String) {
        Log.d(TAG, "🔊 Starting TTS for response: ${text.take(50)}...")
        
        // Configure TTS for professional coach voice
        val ttsConfig = TTSManager.TTSConfig(
            model = TTSManager.MODEL_TTS_1,  // Fast response for real-time
            voice = TTSManager.VOICE_SHIMMER, // Professional, clear voice
            speed = 0.95f  // Slightly slower for clarity
        )
        
        // Launch TTS in coroutine
        lifecycleScope.launch {
            ttsManager.textToSpeechAndPlay(
                text = text,
                config = ttsConfig,
                callback = object : TTSManager.TTSCallback {
                    override fun onTTSStarted() {
                        Log.d(TAG, "🎤 TTS generation started")
                        callback?.onStatusUpdate("Gerando áudio...")
                    }
                    
                    override fun onTTSCompleted() {
                        Log.d(TAG, "✅ TTS generation completed")
                    }
                    
                    override fun onTTSError(error: String) {
                        Log.e(TAG, "❌ TTS error: $error")
                        callback?.onError("Erro no áudio: $error")
                    }
                    
                    override fun onPlaybackStarted() {
                        Log.d(TAG, "▶️ Audio playback started")
                        callback?.onStatusUpdate("🔊 Reproduzindo áudio...")
                    }
                    
                    override fun onPlaybackCompleted() {
                        Log.d(TAG, "✅ Audio playback completed")
                        callback?.onStatusUpdate("✅ Áudio concluído")
                        
                        // Text remains on screen as per requirement
                        // User can still read it after audio ends
                    }
                }
            )
        }
    }
    
    /**
     * Check if in active mode
     */
    fun isInActiveMode(): Boolean = isActiveMode
    
    /**
     * Clean up on destroy
     */
    fun cleanup() {
        if (isActiveMode) {
            exitActiveMode()
        }
        threadManager.cleanupExpiredThreads()
        ttsManager.cleanup()
    }
}