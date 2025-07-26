package com.seudominio.app_smart_companion.assistants

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.*
import java.io.File

/**
 * Serviço simples para testar comunicação com OpenAI Assistant
 * Funcionalidade básica: texto apenas, um assistant fixo
 */
class SimpleAssistantService : Service() {
    companion object {
        private const val TAG = "SimpleAssistant"
        
        // Actions
        const val ACTION_START_ASSISTANT = "START_ASSISTANT"
        const val ACTION_SEND_MESSAGE = "SEND_MESSAGE"
        const val ACTION_START_AUDIO_RECORDING = "START_AUDIO_RECORDING"
        const val ACTION_STOP_AUDIO_RECORDING = "STOP_AUDIO_RECORDING"
        const val ACTION_STOP_ASSISTANT = "STOP_ASSISTANT"
        
        // Extras
        const val EXTRA_MESSAGE = "message"
        
        // Broadcasts
        const val BROADCAST_RESPONSE = "com.seudominio.app_smart_companion.ASSISTANT_RESPONSE"
        const val BROADCAST_STATUS = "com.seudominio.app_smart_companion.ASSISTANT_STATUS"
        const val BROADCAST_ERROR = "com.seudominio.app_smart_companion.ASSISTANT_ERROR"
        
        // Assistant ID fixo (do seu assistant)
        private const val ASSISTANT_ID = "asst_hXcg5nxjUuv2EMcJoiJbMIBN"
    }
    
    private var assistantClient: OpenAIAssistantClient? = null
    private var audioRecorder: AudioFileRecorder? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isConnected = false
    private var isRecording = false
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 SimpleAssistantService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "🎯 Service command: $action")
        
        when (action) {
            ACTION_START_ASSISTANT -> {
                startAssistant()
            }
            ACTION_SEND_MESSAGE -> {
                val message = intent.getStringExtra(EXTRA_MESSAGE)
                if (message != null) {
                    sendMessage(message)
                } else {
                    broadcastError("No message provided")
                }
            }
            ACTION_START_AUDIO_RECORDING -> {
                startAudioRecording()
            }
            ACTION_STOP_AUDIO_RECORDING -> {
                stopAudioRecording()
            }
            ACTION_STOP_ASSISTANT -> {
                stopAssistant()
            }
        }
        
        return START_STICKY
    }
    
    /**
     * Inicializar assistant e criar thread
     */
    private fun startAssistant() {
        if (isConnected) {
            Log.w(TAG, "Assistant already connected")
            return
        }
        
        Log.d(TAG, "🤖 Starting assistant connection...")
        broadcastStatus("Connecting to assistant...")
        
        // Obter API key (mesmo método do MainActivity)
        val apiKey = getApiKey()
        if (apiKey == null) {
            broadcastError("API key not configured")
            return
        }
        
        // Criar cliente
        assistantClient = OpenAIAssistantClient(
            apiKey = apiKey,
            onResponse = { response ->
                Log.d(TAG, "✅ Assistant response: ${response.take(100)}...")
                broadcastResponse(response)
            },
            onError = { error ->
                Log.e(TAG, "❌ Assistant error: $error")
                broadcastError(error)
            },
            onStatusUpdate = { status ->
                Log.d(TAG, "📊 Status: $status")
                broadcastStatus(status)
            }
        )
        
        // Iniciar conexão assíncrona
        scope.launch {
            try {
                // Set assistant ID (já existe, não precisa criar)
                assistantClient?.setAssistantId(ASSISTANT_ID)
                
                // Criar thread para conversação
                val threadId = assistantClient?.createThread()
                if (threadId != null) {
                    Log.d(TAG, "✅ Thread created: $threadId")
                    isConnected = true
                    broadcastStatus("Assistant connected! Ready for messages.")
                } else {
                    broadcastError("Failed to create conversation thread")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception starting assistant", e)
                broadcastError("Error starting assistant: ${e.message}")
            }
        }
    }
    
    /**
     * Enviar mensagem de texto para o assistant
     */
    private fun sendMessage(message: String) {
        if (!isConnected || assistantClient == null) {
            broadcastError("Assistant not connected. Start assistant first.")
            return
        }
        
        Log.d(TAG, "📤 Sending message: $message")
        broadcastStatus("Sending message...")
        
        scope.launch {
            try {
                // Enviar mensagem
                val messageSent = assistantClient?.sendTextMessage(message)
                if (messageSent == true) {
                    Log.d(TAG, "✅ Message sent, executing run...")
                    
                    // Executar run para obter resposta
                    val runExecuted = assistantClient?.executeRun()
                    if (runExecuted != true) {
                        broadcastError("Failed to execute assistant run")
                    }
                } else {
                    broadcastError("Failed to send message")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception sending message", e)
                broadcastError("Error sending message: ${e.message}")
            }
        }
    }
    
    /**
     * Start audio recording
     */
    private fun startAudioRecording() {
        if (!isConnected || assistantClient == null) {
            broadcastError("Assistant not connected. Start assistant first.")
            return
        }
        
        if (isRecording) {
            broadcastError("Already recording audio")
            return
        }
        
        Log.d(TAG, "🎤 Starting audio recording...")
        broadcastStatus("Preparing audio recording...")
        
        audioRecorder = AudioFileRecorder(
            context = this,
            onRecordingComplete = { audioFile ->
                Log.d(TAG, "✅ Audio recording completed: ${audioFile.name}")
                sendAudioMessage(audioFile)
            },
            onError = { error ->
                Log.e(TAG, "❌ Audio recording error: $error")
                isRecording = false
                broadcastError("Recording error: $error")
            },
            onStatusUpdate = { status ->
                // Synchronized status update to prevent duplicates
                synchronized(this) {
                    if (isRecording) {  // Only broadcast status if still recording
                        Log.d(TAG, "📊 Recording status: $status")
                        broadcastStatus(status)
                    }
                }
            }
        )
        
        val success = audioRecorder?.startRecording() ?: false
        if (success) {
            isRecording = true
            broadcastStatus("🎤 Recording audio... (tap stop when finished)")
        } else {
            broadcastError("Failed to start audio recording")
        }
    }
    
    /**
     * Stop audio recording
     */
    private fun stopAudioRecording() {
        if (!isRecording || audioRecorder == null) {
            broadcastError("Not currently recording audio")
            return
        }
        
        Log.d(TAG, "🛑 Stopping audio recording...")
        audioRecorder?.stopRecording()
        isRecording = false
    }
    
    /**
     * Send audio message to assistant using transcription flow
     */
    private fun sendAudioMessage(audioFile: File) {
        Log.d(TAG, "📤 Sending audio message via transcription: ${audioFile.name}")
        broadcastStatus("Transcribing audio...")
        
        scope.launch {
            try {
                // Use the new transcription-based approach for better speed and quality
                val messageSent = assistantClient?.sendAudioMessageViaTranascription(audioFile)
                if (messageSent == true) {
                    Log.d(TAG, "✅ Audio transcribed and sent, executing run...")
                    
                    // Execute run to get response
                    val runExecuted = assistantClient?.executeRun()
                    if (runExecuted != true) {
                        broadcastError("Failed to execute assistant run")
                    }
                } else {
                    broadcastError("Failed to transcribe and send audio message")
                }
                
                // Cleanup temporary file
                if (audioFile.exists()) {
                    audioFile.delete()
                    Log.d(TAG, "🧹 Temporary audio file deleted")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception sending audio message via transcription", e)
                broadcastError("Error processing audio message: ${e.message}")
            }
        }
    }
    
    /**
     * Parar assistant
     */
    private fun stopAssistant() {
        Log.d(TAG, "🛑 Stopping assistant...")
        
        // Stop any ongoing recording
        if (isRecording) {
            audioRecorder?.cleanup()
            audioRecorder = null
            isRecording = false
        }
        
        assistantClient?.cleanup()
        assistantClient = null
        isConnected = false
        
        broadcastStatus("Assistant disconnected")
    }
    
    /**
     * Obter API key (mesmo método do MainActivity)
     */
    private fun getApiKey(): String? {
        val sharedPreferences = getSharedPreferences("smart_companion_prefs", MODE_PRIVATE)
        
        // Primeiro tentar SharedPreferences
        val savedKey = sharedPreferences.getString("openai_api_key", null)
        if (savedKey != null) return savedKey
        
        // Auto-configure API key from companion .env file if not set
        val envApiKey = loadApiKeyFromEnv()
        if (envApiKey != null) {
            // Save to SharedPreferences for future use
            sharedPreferences.edit().putString("openai_api_key", envApiKey).apply()
            Log.d(TAG, "API key loaded from .env file and saved to SharedPreferences")
            return envApiKey
        }
        
        // Fallback - user needs to configure manually
        Log.w(TAG, "No API key found - user must configure via Settings")
        return null
    }
    
    /**
     * Load API key from project root .env file
     */
    private fun loadApiKeyFromEnv(): String? {
        return try {
            // Try to read from project root .env file
            val envFile = File(filesDir.parentFile?.parentFile?.parentFile, ".env")
            Log.d(TAG, "🔍 Trying to read .env from: ${envFile.absolutePath}")
            Log.d(TAG, "🔍 .env file exists: ${envFile.exists()}")
            
            if (envFile.exists()) {
                envFile.readLines().forEach { line ->
                    if (line.startsWith("OPENAI_API_KEY=") && !line.contains("YOUR_OPENAI_API_KEY_HERE")) {
                        val apiKey = line.substring("OPENAI_API_KEY=".length).trim()
                        if (apiKey.startsWith("sk-") && apiKey.length > 20) {
                            Log.d(TAG, "✅ Found valid API key in .env file")
                            return apiKey
                        }
                    }
                }
            }
            Log.d(TAG, "❌ No valid API key found in .env file")
            null
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Could not read .env file: ${e.message}")
            null
        }
    }
    
    /**
     * Broadcast methods
     */
    private fun broadcastResponse(response: String) {
        val intent = Intent(BROADCAST_RESPONSE).apply {
            putExtra("response", response)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
    
    private fun broadcastStatus(status: String) {
        val intent = Intent(BROADCAST_STATUS).apply {
            putExtra("status", status)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
    
    private fun broadcastError(error: String) {
        val intent = Intent(BROADCAST_ERROR).apply {
            putExtra("error", error)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🧹 SimpleAssistantService destroyed")
        stopAssistant()
        scope.cancel()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}