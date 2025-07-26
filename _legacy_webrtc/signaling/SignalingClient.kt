package com.seudominio.app_smart_companion.signaling

import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import javax.net.ssl.SSLContext
import okhttp3.*
import okio.ByteString
import com.seudominio.app_smart_companion.config.AppConfig

class SignalingClient(
    private val serverUrl: String,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "SmartCompanion" // Consistent logging
    }
    
    interface SignalingListener {
        fun onConnected()
        fun onDisconnected()
        fun onOfferReceived(offer: SessionDescription)
        fun onAnswerReceived(answer: SessionDescription)
        fun onIceCandidateReceived(candidate: IceCandidate)
        fun onError(error: String)
        // NEW: For HUD text messages from companion desktop
        fun onTextMessageReceived(messageJson: String)
    }
    
    private var webSocket: WebSocket? = null
    private var listener: SignalingListener? = null
    private var isConnected = false
    private var reconnectAttempts = 0
    private var shouldReconnect = true // Add flag to control reconnection
    private val client = OkHttpClient.Builder()
        .build()
    
    fun setListener(listener: SignalingListener) {
        this.listener = listener
    }
    
    fun connect() {
        if (isConnected) {
            Log.w(TAG, "Already connected")
            return
        }
        
        Log.i(TAG, "🔗 Attempting WebSocket connection to: $serverUrl")
        
        try {
            val request = Request.Builder()
                .url(serverUrl)
                .build()
                
            Log.d(TAG, "📡 WebSocket request created, initiating connection...")
            
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket opened")
                    isConnected = true
                    reconnectAttempts = 0
                    
                    // Send join message immediately after connection
                    val joinMessage = JSONObject().apply {
                        put("type", "join")
                        put("room", "companion-session")
                    }
                    sendMessage(joinMessage)
                    Log.d(TAG, "Join message sent to signaling server")
                    
                    scope.launch(Dispatchers.Main) {
                        listener?.onConnected()
                    }
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "Message received: $text")
                    handleMessage(text)
                }
                
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    Log.d(TAG, "Binary message received")
                    // Handle binary messages if needed
                }
                
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closing: $code - $reason")
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closed: $code - $reason")
                    isConnected = false
                    
                    scope.launch(Dispatchers.Main) {
                        listener?.onDisconnected()
                    }
                    
                    // Auto-reconnect
                    scheduleReconnect()
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket failure", t)
                    isConnected = false
                    
                    scope.launch(Dispatchers.Main) {
                        listener?.onError(t.message ?: "Connection failed")
                    }
                    
                    scheduleReconnect()
                }
            })
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect", e)
            listener?.onError(e.message ?: "Failed to connect")
        }
    }
    
    fun disconnect() {
        shouldReconnect = false // Stop auto-reconnect
        webSocket?.close(1000, "Disconnecting")
        webSocket = null
        isConnected = false
        reconnectAttempts = AppConfig.MAX_RECONNECT_ATTEMPTS // Prevent auto-reconnect
        Log.d(TAG, "SignalingClient disconnected - auto-reconnect disabled")
    }
    
    fun sendOffer(offer: SessionDescription) {
        val message = JSONObject().apply {
            put("type", "offer")
            put("sdp", offer.description)
        }
        sendMessage(message)
    }
    
    fun sendAnswer(answer: SessionDescription) {
        val message = JSONObject().apply {
            put("type", "answer")
            put("sdp", answer.description)
        }
        sendMessage(message)
    }
    
    fun sendIceCandidate(candidate: IceCandidate) {
        val message = JSONObject().apply {
            put("type", "ice_candidate")
            put("candidate", candidate.sdp)
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
        }
        sendMessage(message)
    }
    
    fun sendMessage(message: JSONObject) {
        if (!isConnected) {
            Log.w(TAG, "Not connected, cannot send message")
            return
        }
        
        try {
            val messageText = message.toString()
            Log.d(TAG, "Sending message: $messageText")
            webSocket?.send(messageText)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message", e)
            listener?.onError("Failed to send message: ${e.message}")
        }
    }
    
    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.getString("type")
            
            scope.launch(Dispatchers.Main) {
                when (type) {
                    "offer" -> {
                        val sdp = json.getString("sdp")
                        val offer = SessionDescription(SessionDescription.Type.OFFER, sdp)
                        listener?.onOfferReceived(offer)
                    }
                    
                    "answer" -> {
                        val sdp = json.getString("sdp")
                        val answer = SessionDescription(SessionDescription.Type.ANSWER, sdp)
                        listener?.onAnswerReceived(answer)
                    }
                    
                    "ice_candidate" -> {
                        val candidateSdp = json.getString("candidate")
                        val sdpMid = json.getString("sdpMid")
                        val sdpMLineIndex = json.getInt("sdpMLineIndex")
                        
                        val candidate = IceCandidate(sdpMid, sdpMLineIndex, candidateSdp)
                        listener?.onIceCandidateReceived(candidate)
                    }
                    
                    // NEW: Handle text messages from companion (OpenAI responses)
                    "model_text", "status_update", "clear_display", "connection_status" -> {
                        Log.d(TAG, "📱 HUD message received from companion: $type")
                        listener?.onTextMessageReceived(text)
                    }
                    
                    else -> {
                        Log.w(TAG, "Unknown message type: $type")
                        // Forward unknown messages to HUD handler for potential processing
                        listener?.onTextMessageReceived(text)
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle message", e)
            listener?.onError("Failed to handle message: ${e.message}")
        }
    }
    
    private fun scheduleReconnect() {
        if (reconnectAttempts >= AppConfig.MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Max reconnect attempts reached")
            return
        }
        
        reconnectAttempts++
        Log.d(TAG, "Scheduling reconnect attempt $reconnectAttempts in ${AppConfig.RECONNECT_DELAY_MS}ms")
        
        scope.launch {
            delay(AppConfig.RECONNECT_DELAY_MS)
            if (!isConnected) {
                connect()
            }
        }
    }
    
    fun isConnected(): Boolean = isConnected
}