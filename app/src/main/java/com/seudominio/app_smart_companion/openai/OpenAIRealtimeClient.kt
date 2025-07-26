package com.seudominio.app_smart_companion.openai

import android.util.Base64
import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*

/**
 * OpenAI Realtime API Client para Android
 * Baseado na documentação oficial e padrões do ElatoAI
 * Compatível com Vuzix M400 SDK
 */
class OpenAIRealtimeClient(
    private val apiKey: String,
    private val onTextResponse: (String) -> Unit,
    private val onAudioResponse: (ByteArray) -> Unit,
    private val onError: (String) -> Unit,
    private val onConnectionStateChanged: (Boolean) -> Unit
) {
    companion object {
        private const val TAG = "LearionGlass"
        private const val OPENAI_REALTIME_URL = "wss://api.openai.com/v1/realtime"
        private const val MODEL = "gpt-4o-realtime-preview-2024-10-01"
    }
    
    private var webSocket: WebSocket? = null
    private var isConnected = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Acumular transcript deltas (padrão OpenAI Realtime API)
    private var currentTranscript = StringBuilder()
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "✅ Connected to OpenAI Realtime API")
            isConnected = true
            onConnectionStateChanged(true)
            
            // Send session configuration (OpenAI Realtime API standard)
            scope.launch {
                configureSession()
            }
        }
        
        override fun onMessage(webSocket: WebSocket, text: String) {
            scope.launch {
                handleServerEvent(text)
            }
        }
        
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "❌ Connection closed: $code - $reason")
            isConnected = false
            onConnectionStateChanged(false)
        }
        
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "❌ Connection failed", t)
            isConnected = false
            onConnectionStateChanged(false)
            onError("Connection failed: ${t.message}")
        }
    }
    
    /**
     * Conecta ao OpenAI Realtime API usando padrão da documentação oficial
     */
    fun connect() {
        if (isConnected) {
            Log.w(TAG, "Already connected")
            return
        }
        
        Log.d(TAG, "🔗 Connecting to OpenAI Realtime API...")
        
        val request = Request.Builder()
            .url("$OPENAI_REALTIME_URL?model=$MODEL")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("OpenAI-Beta", "realtime=v1")
            .build()
        
        webSocket = client.newWebSocket(request, listener)
    }
    
    /**
     * Configura sessão usando AgentManager (padrão ElatoAI)
     */
    fun configureSessionWithAgent(agentConfig: JSONObject) {
        val sessionConfig = JSONObject().apply {
            put("event_id", generateEventId())
            put("type", "session.update")
            put("session", agentConfig)
        }
        
        sendEvent(sessionConfig.toString())
        Log.d(TAG, "📋 Session configured with agent: ${agentConfig.optString("instructions", "").take(50)}...")
    }
    
    /**
     * ❌ FUNÇÃO REMOVIDA - VAD É CONTROLADO EXCLUSIVAMENTE PELO AgentManager
     * A configuração de sessão agora SEMPRE vem do agente selecionado
     */
    @Deprecated("VAD configuration is now handled exclusively by AgentManager")
    private fun configureSession() {
        Log.w(TAG, "⚠️ configureSession() deprecated - using AgentManager configuration instead")
        // Esta função não deve mais ser chamada - AgentManager controla VAD
    }
    
    /**
     * Envia áudio para OpenAI (formato padrão ElatoAI)
     * @param audioData PCM16 audio data do Vuzix M400
     */
    fun sendAudio(audioData: ByteArray) {
        if (!isConnected) {
            Log.w(TAG, "Not connected, cannot send audio")
            return
        }
        
        try {
            // ANÁLISE DETALHADA: Verificar se áudio parece voz humana real
            val samples = audioData.map { (it.toInt() and 0xFF) }
            val maxAmplitude = samples.maxOrNull() ?: 0
            val avgAmplitude = samples.average().toInt()
            val variance = samples.map { (it - avgAmplitude) * (it - avgAmplitude) }.average()
            val dynamicRange = maxAmplitude - (samples.minOrNull() ?: 0)
            
            // Log apenas frames com características de voz
            if (maxAmplitude > 30 || avgAmplitude > 10) {
                Log.d(TAG, "🎵 Audio Analysis - Max: $maxAmplitude, Avg: $avgAmplitude, Variance: %.1f, Range: $dynamicRange".format(variance))
                
                // Detector de voz real vs ruído
                if (variance > 100 && dynamicRange > 50) {
                    Log.d(TAG, "✅ REAL SPEECH detected (high variance + dynamic range)")
                } else {
                    Log.w(TAG, "⚠️ NOISE/STATIC detected (low variance: %.1f, range: $dynamicRange)".format(variance))
                }
            }
            
            // Converte para Base64 (padrão OpenAI)
            val base64Audio = Base64.encodeToString(audioData, Base64.NO_WRAP)
            
            val audioEvent = JSONObject().apply {
                put("event_id", generateEventId())
                put("type", "input_audio_buffer.append")
                put("audio", base64Audio)
            }
            
            sendEvent(audioEvent.toString())
            Log.v(TAG, "📤 Audio frame sent: ${audioData.size} bytes")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio", e)
            onError("Failed to send audio: ${e.message}")
        }
    }
    
    /**
     * Commit audio buffer e solicita resposta (padrão ElatoAI)
     */
    fun commitAudioAndCreateResponse() {
        if (!isConnected) return
        
        // Commit audio buffer
        val commitEvent = JSONObject().apply {
            put("event_id", generateEventId())
            put("type", "input_audio_buffer.commit")
        }
        sendEvent(commitEvent.toString())
        
        // Create response
        val responseEvent = JSONObject().apply {
            put("event_id", generateEventId())
            put("type", "response.create")
        }
        sendEvent(responseEvent.toString())
        
        Log.d(TAG, "🎤 Audio committed, requesting response")
    }
    
    /**
     * Clear audio buffer (padrão ElatoAI após response.done)
     */
    fun clearAudioBuffer() {
        if (!isConnected) return
        
        val clearEvent = JSONObject().apply {
            put("event_id", generateEventId())
            put("type", "input_audio_buffer.clear")
        }
        sendEvent(clearEvent.toString())
        Log.d(TAG, "🧹 Audio buffer cleared")
    }
    
    /**
     * Handle server events (baseado em ElatoAI)
     */
    private fun handleServerEvent(eventText: String) {
        try {
            val event = JSONObject(eventText)
            val eventType = event.getString("type")
            
            when (eventType) {
                "session.created" -> {
                    Log.d(TAG, "✅ Session created")
                }
                
                "session.updated" -> {
                    Log.d(TAG, "✅ Session updated successfully")
                    // Verificar configuração VAD aplicada (crítico para debug)
                    val session = event.optJSONObject("session")
                    val turnDetection = session?.optJSONObject("turn_detection")
                    val vadType = turnDetection?.optString("type", "none")
                    val threshold = turnDetection?.optDouble("threshold", -1.0)
                    val silenceDuration = turnDetection?.optInt("silence_duration_ms", -1)
                    
                    Log.d(TAG, "🔍 VAD Configuration: type='$vadType', threshold=$threshold, silence=${silenceDuration}ms")
                    
                    if (vadType == "server_vad" && threshold == 0.5 && silenceDuration == 500) {
                        Log.d(TAG, "✅ VAD OFICIAL OPENAI configurado pelo AgentManager!")
                    } else {
                        Log.w(TAG, "⚠️ VAD configuração não oficial! Usando: $vadType (threshold=$threshold, silence=${silenceDuration}ms)")
                    }
                }
                
                
                "response.created" -> {
                    Log.d(TAG, "🤖 Response creation started")
                    // Reset transcript accumulator for new response
                    currentTranscript.clear()
                }
                
                "response.audio.delta" -> {
                    // Process audio response chunk
                    val delta = event.optString("delta", "")
                    if (delta.isNotEmpty()) {
                        try {
                            val audioData = Base64.decode(delta, Base64.DEFAULT)
                            onAudioResponse(audioData)
                            Log.v(TAG, "🎵 Audio chunk received: ${audioData.size} bytes")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error decoding audio delta", e)
                        }
                    }
                }
                
                "response.audio_transcript.delta" -> {
                    // Process transcript delta (partial text) - Acumular para final
                    val delta = event.optString("delta", "")
                    if (delta.isNotEmpty()) {
                        currentTranscript.append(delta)
                        Log.d(TAG, "📝 Transcript delta: '$delta' (accumulated: '${currentTranscript}')")
                        // Enviar texto acumulado para efeito "typing" em tempo real
                        onTextResponse(currentTranscript.toString())
                    }
                }
                
                "response.audio_transcript.done" -> {
                    Log.d(TAG, "🔍 TRANSCRIPT_DONE evento recebido!")
                    
                    // Usar transcript oficial se disponível, senão usar acumulado
                    if (event.has("transcript")) {
                        val transcript = event.getString("transcript")
                        Log.d(TAG, "📝 AI Response (official): $transcript")
                        onTextResponse(transcript)
                    } else if (currentTranscript.isNotEmpty()) {
                        val accumulated = currentTranscript.toString()
                        Log.d(TAG, "📝 AI Response (accumulated): $accumulated")
                        onTextResponse(accumulated)
                    } else {
                        Log.w(TAG, "⚠️ No transcript available in done event or accumulated!")
                        onTextResponse("[ERROR] No transcript received")
                    }
                }
                
                "conversation.item.input_audio_transcription.completed" -> {
                    // User speech transcription
                    if (event.has("transcript")) {
                        val transcript = event.getString("transcript")
                        Log.d(TAG, "👤 User said: $transcript")
                    }
                }
                
                "input_audio_buffer.speech_started" -> {
                    Log.d(TAG, "🎤 VAD EVENT: User started speaking (server detected voice)")
                    val timestamp = event.optLong("event_id", 0)
                    Log.d(TAG, "🎤 Speech started at: $timestamp")
                }
                
                "input_audio_buffer.speech_stopped" -> {
                    Log.d(TAG, "🛑 VAD EVENT: User stopped speaking (server detected silence)")
                    val timestamp = event.optLong("event_id", 0)
                    val duration = event.optInt("duration_ms", -1)
                    Log.d(TAG, "🛑 Speech stopped at: $timestamp, duration: ${duration}ms")
                    // NO auto-commit here - OpenAI server-side VAD handles this automatically
                    // The server will send input_audio_buffer.committed when ready
                }
                
                "input_audio_buffer.committed" -> {
                    Log.d(TAG, "✅ Audio buffer committed successfully")
                }
                
                "response.done" -> {
                    Log.d(TAG, "✅ Response completed")
                    // Note: Server-side VAD auto-clears buffer, no manual clear needed
                }
                
                "error" -> {
                    val error = event.optJSONObject("error")
                    val message = error?.optString("message") ?: "Unknown error"
                    Log.e(TAG, "❌ OpenAI Error: $message")
                    onError("OpenAI Error: $message")
                }
                
                "conversation.item.input_audio_transcription.failed" -> {
                    val item = event.optJSONObject("item")
                    val error = event.optJSONObject("error")
                    val errorMessage = error?.optString("message") ?: item?.optString("error") ?: "Unknown transcription error"
                    val errorCode = error?.optString("code") ?: "unknown"
                    Log.e(TAG, "❌ Transcription failed: $errorMessage (code: $errorCode)")
                    // Log full event for debugging
                    Log.d(TAG, "Full transcription error: $eventText")
                }
                
                else -> {
                    Log.d(TAG, "📨 Unhandled event: $eventType")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling server event", e)
        }
    }
    
    /**
     * Send event to OpenAI
     */
    private fun sendEvent(eventJson: String) {
        webSocket?.send(eventJson)
    }
    
    /**
     * Generate unique event ID (padrão OpenAI)
     */
    private fun generateEventId(): String {
        return "evt_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
    
    /**
     * Disconnect from OpenAI
     */
    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        isConnected = false
        scope.cancel()
    }
    
    /**
     * Check if connected
     */
    fun isConnected(): Boolean = isConnected
}