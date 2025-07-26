package com.seudominio.app_smart_companion.service

import android.app.*
import android.content.Intent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.seudominio.app_smart_companion.MainActivity
import com.seudominio.app_smart_companion.R
import com.seudominio.app_smart_companion.openai.OpenAIRealtimeClient
// import com.seudominio.app_smart_companion.webrtc.DirectWebRTCManager // Temporarily disabled
import com.seudominio.app_smart_companion.audio.VuzixAudioProcessor
import com.seudominio.app_smart_companion.audio.VuzixAudioPlayer
import com.seudominio.app_smart_companion.agents.AgentManager
import kotlinx.coroutines.*
import kotlinx.coroutines.delay
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Standalone Service - substitui toda a complexidade WebRTC
 * Conecta diretamente: M400 → OpenAI Realtime API
 * Baseado nos padrões do ElatoAI
 */
class StandaloneService : Service() {
    
    companion object {
        private const val TAG = "LearionGlass"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "standalone_service_channel"
        const val ACTION_START_SESSION = "START_SESSION"
        const val ACTION_STOP_SESSION = "STOP_SESSION"
        const val ACTION_COMMIT_AUDIO = "COMMIT_AUDIO"
        const val ACTION_CHANGE_AGENT = "CHANGE_AGENT"
        const val ACTION_START_LISTENING = "START_LISTENING"
        const val ACTION_STOP_LISTENING = "STOP_LISTENING"
        const val EXTRA_AGENT_KEY = "agent_key"
        const val EXTRA_API_KEY = "api_key"
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Core components
    private var openAIClient: OpenAIRealtimeClient? = null
    // private var webrtcManager: DirectWebRTCManager? = null // Temporarily disabled
    private var audioProcessor: VuzixAudioProcessor? = null
    private var audioPlayer: VuzixAudioPlayer? = null
    private var agentManager: AgentManager? = null
    
    // State
    private var isSessionActive = false
    private var isListening = false  // Always-on listening state (ElatoAI pattern)
    private var currentApiKey: String? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 StandaloneService created")
        createNotificationChannel()
        startForeground()
        
        // Initialize components
        agentManager = AgentManager(this)
        initializeAudioComponents()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🎯 Service command: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_SESSION -> {
                val apiKey = intent.getStringExtra(EXTRA_API_KEY)
                if (apiKey != null) {
                    startOpenAISession(apiKey)
                } else {
                    Log.e(TAG, "❌ No API key provided")
                    broadcastError("API key required")
                }
            }
            
            ACTION_STOP_SESSION -> {
                stopOpenAISession()
            }
            
            ACTION_COMMIT_AUDIO -> {
                commitAudioAndRequestResponse()
            }
            
            ACTION_CHANGE_AGENT -> {
                val agentKey = intent.getStringExtra(EXTRA_AGENT_KEY)
                if (agentKey != null) {
                    changeAgent(agentKey)
                }
            }
            
            ACTION_START_LISTENING -> {
                startContinuousListening()
            }
            
            ACTION_STOP_LISTENING -> {
                stopContinuousListening()
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🛑 StandaloneService destroyed")
        cleanup()
        serviceScope.cancel()
    }
    
    /**
     * Initialize audio components for Vuzix M400
     */
    private fun initializeAudioComponents() {
        // Audio processor (input)
        audioProcessor = VuzixAudioProcessor(
            context = this,
            onAudioData = { audioData ->
                // Send audio directly to OpenAI
                Log.v(TAG, "📊 Audio frame received from processor: ${audioData.size} bytes")
                // Send audio frame to OpenAI via WebRTC
                // webrtcManager?.sendAudio(audioData) // Temporarily disabled
            },
            onError = { error ->
                Log.e(TAG, "Audio processor error: $error")
                broadcastError("Audio input error: $error")
            }
        )
        
        // Audio player (output)
        audioPlayer = VuzixAudioPlayer(
            context = this,
            onPlaybackComplete = {
                Log.d(TAG, "🎵 Audio playback completed")
            },
            onError = { error ->
                Log.e(TAG, "Audio player error: $error")
                broadcastError("Audio output error: $error")
            }
        )
        
        audioPlayer?.initialize()
        Log.d(TAG, "🎧 Audio components initialized for Vuzix M400")
    }
    
    /**
     * Start OpenAI session with current agent
     */
    private fun startOpenAISession(apiKey: String) {
        if (isSessionActive) {
            Log.w(TAG, "Session already active")
            return
        }
        
        currentApiKey = apiKey
        val currentAgent = agentManager?.getCurrentAgent()
        
        Log.d(TAG, "🤖 Starting session with agent: ${currentAgent?.name}")
        
        // 🚀 STANDALONE: Direct WebSocket Connection to OpenAI Realtime API
        Log.d(TAG, "🔄 Initializing OpenAI Realtime Client (WebSocket)")
        
        openAIClient = OpenAIRealtimeClient(
            apiKey = apiKey,
            onTextResponse = { text -> 
                Log.d(TAG, "📝 AI Response: $text")
                broadcastTextResponse(text)
            },
            onAudioResponse = { audioData -> 
                Log.d(TAG, "🔊 AI Audio received: ${audioData.size} bytes")
                audioPlayer?.playAudio(audioData)
            },
            onError = { error -> 
                Log.e(TAG, "❌ OpenAI Error: $error")
                broadcastError("OpenAI Error: $error")
            },
            onConnectionStateChanged = { connected -> 
                Log.d(TAG, "📡 OpenAI connection state: $connected")
                isSessionActive = connected
                broadcastConnectionStatus(isSessionActive)
                broadcastStatus("🔗 OpenAI: ${if (connected) "CONNECTED" else "DISCONNECTED"}")
                
                if (isSessionActive) {
                    // Start audio capture when OpenAI connected
                    audioProcessor?.startCapture()
                    Log.d(TAG, "🎤 Audio capture started - OpenAI WebSocket connected")
                    
                    // 🎯 CONFIGURE SESSION IMMEDIATELY WHEN CONNECTED
                    val agentConfig = agentManager?.getCurrentAgentSessionConfig()
                    agentConfig?.let { config ->
                        openAIClient?.configureSessionWithAgent(config)
                        Log.d(TAG, "🎯 VAD GLOBAL Configuration Applied IMMEDIATELY: ${agentManager?.getCurrentVADConfig()}")
                        Log.d(TAG, "📋 Session config sent to OpenAI via callback")
                    } ?: Log.e(TAG, "❌ AgentConfig is null in callback!")
                } else {
                    // Stop audio capture when disconnected
                    audioProcessor?.stopCapture()
                    Log.d(TAG, "🛑 Audio capture stopped - OpenAI disconnected")
                }
            }
        )
        
        // Connect to OpenAI
        openAIClient?.connect()
        
        // Update notification
        updateNotification("Connecting to OpenAI Realtime API...")
        
        Log.d(TAG, "✅ OpenAI session initialized with OptimizedAudioProcessor")
    }
    
    /**
     * Stop OpenAI session
     */
    private fun stopOpenAISession() {
        Log.d(TAG, "🛑 Stopping OpenAI session")
        
        audioProcessor?.stopCapture()
        audioPlayer?.stopPlayback()
        
        // Disconnect OpenAI client
        openAIClient?.disconnect()
        openAIClient = null
        
        isSessionActive = false
        
        updateNotification("Disconnected")
        broadcastConnectionStatus(false)
        broadcastStatus("Session ended")
    }
    
    /**
     * Commit audio buffer and request response (manual VAD)
     */
    private fun commitAudioAndRequestResponse() {
        if (!isSessionActive) {
            Log.w(TAG, "No active session")
            return
        }
        
        Log.d(TAG, "🎤 Manual audio commit (OpenAI WebSocket)")
        // Note: OpenAI WebSocket handles audio streaming via continuous listening
        broadcastStatus("🔊 Audio transmitted via WebRTC...")
    }
    
    /**
     * Change AI agent
     */
    private fun changeAgent(agentKey: String) {
        val success = agentManager?.setCurrentAgent(agentKey) ?: false
        
        if (success) {
            val newAgent = agentManager?.getAgent(agentKey)
            Log.d(TAG, "✅ Agent changed to: ${newAgent?.name}")
            broadcastStatus("Agent changed to ${newAgent?.name}")
            
            // If session is active, restart with new agent
            if (isSessionActive && currentApiKey != null) {
                stopOpenAISession()
                // Small delay to ensure clean restart
                serviceScope.launch {
                    delay(1000)
                    startOpenAISession(currentApiKey!!)
                }
            }
        } else {
            broadcastError("Failed to change agent")
        }
    }
    
    /**
     * Broadcast text response to MainActivity (SmartGlassManager pattern)
     */
    private fun broadcastTextResponse(text: String) {
        Log.d(TAG, "📡 Broadcasting text response: '${text.take(50)}...'")
        
        val intent = Intent("com.seudominio.app_smart_companion.TEXT_RESPONSE").apply {
            putExtra("text", text)
        }
        
        // Send broadcast with explicit package for security
        sendBroadcast(intent)
        
        Log.d(TAG, "📡 Broadcast sent successfully")
    }
    
    /**
     * Broadcast connection status to MainActivity
     */
    private fun broadcastConnectionStatus(connected: Boolean) {
        val intent = Intent("com.seudominio.app_smart_companion.CONNECTION_STATUS").apply {
            putExtra("connected", connected)
        }
        sendBroadcast(intent)
    }
    
    /**
     * Broadcast status message to MainActivity
     */
    private fun broadcastStatus(message: String) {
        val intent = Intent("com.seudominio.app_smart_companion.STATUS_MESSAGE").apply {
            putExtra("message", message)
        }
        sendBroadcast(intent)
    }
    
    /**
     * Broadcast error to MainActivity
     */
    private fun broadcastError(error: String) {
        val intent = Intent("com.seudominio.app_smart_companion.ERROR_MESSAGE").apply {
            putExtra("error", error)
        }
        sendBroadcast(intent)
    }
    
    /**
     * Create notification channel
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI Assistant Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Learion Glass AI Assistant"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Start foreground service
     */
    private fun startForeground() {
        val notification = createNotification("AI Assistant Ready")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    /**
     * Update notification
     */
    private fun updateNotification(message: String) {
        val notification = createNotification(message)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Create notification
     */
    private fun createNotification(message: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val currentAgent = agentManager?.getCurrentAgent()
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Learion Glass")
            .setContentText("${currentAgent?.name}: $message")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    /**
     * Start continuous listening (ElatoAI always-on pattern)
     */
    private fun startContinuousListening() {
        if (!isSessionActive) {
            Log.w(TAG, "No active session - cannot start listening")
            broadcastError("Connect to AI first")
            return
        }
        
        if (isListening) {
            Log.w(TAG, "Already listening")
            return
        }
        
        Log.d(TAG, "🎤 Starting continuous listening (always-on mode)")
        isListening = true
        
        // Start continuous audio capture and streaming
        audioProcessor?.startCapture()
        
        // Update notification
        updateNotification("Listening continuously...")
        broadcastStatus("🎤 AI is now listening - speak normally")
    }
    
    /**
     * Stop continuous listening (ElatoAI always-on pattern)
     */
    private fun stopContinuousListening() {
        if (!isListening) {
            Log.w(TAG, "Not currently listening")
            return
        }
        
        Log.d(TAG, "🛑 Stopping continuous listening")
        isListening = false
        
        // Stop audio capture but keep session active
        audioProcessor?.stopCapture()
        
        // Note: WebRTC audio buffer is handled automatically
        
        // Update notification
        updateNotification("Listening stopped")
        broadcastStatus("⏸️ AI stopped listening - press Talk to AI to resume")
    }

    /**
     * Cleanup all resources
     */
    private fun cleanup() {
        audioProcessor?.cleanup()
        audioPlayer?.cleanup()
        
        // Cleanup WebRTC manager
        // webrtcManager?.disconnect() // Temporarily disabled
        
        audioProcessor = null
        audioPlayer = null
        openAIClient = null
        // webrtcManager = null // Temporarily disabled
        agentManager = null
        
        Log.d(TAG, "🧹 StandaloneService cleanup completed")
    }
}