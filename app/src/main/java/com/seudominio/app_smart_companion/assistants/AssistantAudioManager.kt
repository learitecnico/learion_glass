package com.seudominio.app_smart_companion.assistants

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.launch
import com.seudominio.app_smart_companion.audio.CoachAudioRecorder
import com.seudominio.app_smart_companion.openai.OpenAIWhisperService
import com.seudominio.app_smart_companion.assistants.OpenAIAssistantClient
import java.io.File

/**
 * Centralized Audio-to-Assistant Manager
 * Handles the complete pipeline: Audio Recording → Whisper Transcription → Assistant Response
 * 
 * Usage Pattern:
 * 1. Create instance with assistant ID and callbacks
 * 2. Call startAudioToAssistant() to begin the flow
 * 3. Handle responses via callbacks
 */
class AssistantAudioManager(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val assistantId: String,
    private val apiKey: String
) {
    
    companion object {
        const val TAG = "AssistantAudioManager"
    }
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private val whisperService = OpenAIWhisperService(context)
    
    // Callback interfaces for different stages
    interface AudioToAssistantCallback {
        fun onRecordingStarted()
        fun onProcessingStarted() 
        fun onAssistantResponse(response: String)
        fun onError(error: String)
    }
    
    // State management
    private var isProcessing = false
    
    /**
     * Start the complete Audio → Assistant pipeline
     * @param callback Interface to handle different stages of the process
     * @param threadId Optional thread ID for conversation continuity (null = new conversation)
     * @param language Language code for Whisper (default: "pt" for Portuguese)
     */
    fun startAudioToAssistant(
        callback: AudioToAssistantCallback,
        threadId: String? = null,
        language: String = "pt"
    ) {
        if (isProcessing) {
            Log.w(TAG, "Audio processing already in progress")
            callback.onError("Processamento já em andamento")
            return
        }
        
        isProcessing = true
        Log.d(TAG, "🎤 Starting Audio-to-Assistant pipeline for assistant: $assistantId")
        
        // Stage 1: Recording Started
        callback.onRecordingStarted()
        
        // Start audio recording
        val audioRecorder = CoachAudioRecorder(context)
        audioRecorder.startRecording(object : CoachAudioRecorder.AudioRecordingCallback {
            override fun onRecordingComplete(audioFile: File) {
                Log.d(TAG, "🎵 Audio recording completed: ${audioFile.absolutePath}")
                
                // Stage 2: Processing Started  
                mainHandler.post { callback.onProcessingStarted() }
                
                // Process audio through Whisper → Assistant
                processAudioToAssistant(audioFile, callback, threadId, language)
            }
            
            override fun onRecordingError(error: String) {
                Log.e(TAG, "❌ Audio recording error: $error")
                isProcessing = false
                mainHandler.post { callback.onError("Erro na gravação: $error") }
            }
        })
    }
    
    /**
     * Process audio file through Whisper → Assistant pipeline
     */
    private fun processAudioToAssistant(
        audioFile: File,
        callback: AudioToAssistantCallback,
        threadId: String?,
        language: String
    ) {
        lifecycleScope.launch {
            try {
                // Step 1: Transcribe audio with Whisper
                Log.d(TAG, "🌐 Transcribing audio with Whisper...")
                
                whisperService.transcribeAudio(
                    audioFile = audioFile,
                    apiKey = apiKey,
                    language = language,
                    callback = object : OpenAIWhisperService.TranscriptionCallback {
                        override fun onTranscriptionSuccess(transcript: String) {
                            Log.d(TAG, "✅ Whisper transcription: '$transcript'")
                            
                            // Step 2: Send transcript to Assistant
                            sendTranscriptToAssistant(transcript, callback, threadId)
                        }
                        
                        override fun onTranscriptionError(error: String) {
                            Log.e(TAG, "❌ Whisper transcription error: $error")
                            isProcessing = false
                            mainHandler.post { callback.onError("Erro na transcrição: $error") }
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception in audio processing", e)
                isProcessing = false
                mainHandler.post { callback.onError("Erro no processamento: ${e.message}") }
            }
        }
    }
    
    /**
     * Send transcript to OpenAI Assistant
     */
    private fun sendTranscriptToAssistant(
        transcript: String,
        callback: AudioToAssistantCallback,
        threadId: String?
    ) {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "🤖 Sending to assistant $assistantId: '$transcript'")
                
                // Create Assistant client
                val assistantClient = OpenAIAssistantClient(
                    apiKey = apiKey,
                    onResponse = { response: String ->
                        Log.d(TAG, "✅ Assistant response received: ${response.take(100)}...")
                        isProcessing = false
                        mainHandler.post { callback.onAssistantResponse(response) }
                    },
                    onStatusUpdate = { status: String ->
                        Log.d(TAG, "ℹ️ Assistant status: $status")
                        // Status updates can be ignored or logged
                    }
                )
                
                // Create thread (new or existing)
                val finalThreadId = threadId ?: run {
                    val newThreadId = assistantClient.createThread()
                    Log.d(TAG, "🆕 Created new thread: $newThreadId")
                    newThreadId
                }
                
                if (finalThreadId == null) {
                    isProcessing = false
                    mainHandler.post { callback.onError("Erro ao criar thread") }
                    return@launch
                }
                
                // Send message to assistant
                val messageAdded = assistantClient.addMessageToThread(finalThreadId, transcript, assistantId)
                if (!messageAdded) {
                    isProcessing = false
                    mainHandler.post { callback.onError("Erro ao enviar mensagem") }
                    return@launch
                }
                
                // Execute run to get response
                val runExecuted = assistantClient.executeRun()
                if (!runExecuted) {
                    isProcessing = false
                    mainHandler.post { callback.onError("Sem resposta do assistant") }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception in assistant communication", e)
                isProcessing = false
                mainHandler.post { callback.onError("Erro na comunicação: ${e.message}") }
            }
        }
    }
    
    /**
     * Check if currently processing audio
     */
    fun isProcessing(): Boolean = isProcessing
    
    /**
     * Cancel current processing (if needed)
     */
    fun cancelProcessing() {
        isProcessing = false
        Log.d(TAG, "🛑 Audio processing cancelled")
    }
}