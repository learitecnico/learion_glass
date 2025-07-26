package com.seudominio.app_smart_companion.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.seudominio.app_smart_companion.MainActivity
import com.seudominio.app_smart_companion.R
import com.seudominio.app_smart_companion.audio.AudioCapture
import com.seudominio.app_smart_companion.camera.CameraCapture
import com.seudominio.app_smart_companion.webrtc.DataChannelManager
import com.seudominio.app_smart_companion.webrtc.WebRTCManager
import com.seudominio.app_smart_companion.signaling.SignalingClient
import com.seudominio.app_smart_companion.config.AppConfig
import com.seudominio.app_smart_companion.ui.HudOverlayManager
import kotlinx.coroutines.*
import org.webrtc.*
import org.json.JSONObject
import java.nio.ByteBuffer

class WebRTCService : Service() {
    
    companion object {
        private const val TAG = "SmartCompanion" // Consistent logging
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "webrtc_service_channel"
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var dataChannelManager: DataChannelManager? = null
    private var audioCapture: AudioCapture? = null
    private var cameraCapture: CameraCapture? = null
    private var signalingClient: SignalingClient? = null
    // Note: Removed HudOverlayManager - using MainActivity's HudDisplayManager via broadcasts
    private var currentPeerConnection: PeerConnection? = null
    private var isWebRTCInitialized = false
    private var makingOffer = false  // Prevent concurrent offers per WebRTC best practices
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        startForeground()
        initializeWebRTC()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        
        // Handle intent actions from MainActivity
        intent?.action?.let { action ->
            when (action) {
                "SEND_MESSAGE" -> {
                    val message = intent.getStringExtra("message")
                    if (message != null) {
                        sendMessageToCompanion(message)
                    }
                }
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        cleanup()
        serviceScope.cancel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WebRTC Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Smart Companion WebRTC Service"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun startForeground() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smart Companion")
            .setContentText("WebRTC service is running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    private fun initializeWebRTC() {
        serviceScope.launch {
            try {
                // Initialize WebRTC Manager
                if (!WebRTCManager.isInitialized()) {
                    WebRTCManager.initialize(this@WebRTCService)
                }
                
                // Note: HudOverlayManager is Compose-based and requires MainActivity's UI
                // WebRTCService will use broadcast messages to MainActivity's HudDisplayManager instead
                Log.i(TAG, "🔍 WebRTCService will use MainActivity's HudDisplayManager via broadcasts")
                
                // Setup audio capture
                setupAudioCapture()
                
                // Setup camera capture  
                setupCameraCapture()
                
                // Initialize signaling client
                setupSignalingClient()
                
                isWebRTCInitialized = true
                Log.d(TAG, "WebRTC initialized successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize WebRTC", e)
            }
        }
    }
    
    private fun setupSignalingClient() {
        signalingClient = SignalingClient(AppConfig.SIGNALING_URL, serviceScope).apply {
            setListener(object : SignalingClient.SignalingListener {
                override fun onConnected() {
                    Log.d(TAG, "Connected to signaling server")
                    notifyMainActivityConnectionStatus(true)
                    
                    // Clean up any existing connection before creating new one
                    currentPeerConnection?.let { pc ->
                        Log.i(TAG, "🧹 Cleaning up existing PeerConnection before creating new one")
                        pc.close()
                        currentPeerConnection = null
                        dataChannelManager?.dispose()
                        dataChannelManager = null
                    }
                    
                    // Create peer connection when connected
                    Log.i(TAG, "🎯 Creating new PeerConnection for companion-desktop")
                    createPeerConnection("companion-desktop")
                    
                    // Force offer creation after a short delay to ensure PeerConnection is ready
                    serviceScope.launch {
                        delay(100) // Short delay to ensure everything is set up
                        currentPeerConnection?.let { pc ->
                            Log.i(TAG, "🎯 FORCING offer creation after signaling connection")
                            try {
                                makingOffer = true
                                createOfferWithPeerConnection(pc)
                            } catch (e: Exception) {
                                Log.e(TAG, "🎯 FAILED to force offer creation", e)
                            } finally {
                                makingOffer = false
                            }
                        } ?: Log.e(TAG, "🎯 Cannot force offer - no currentPeerConnection!")
                    }
                    
                    // Auto-start audio capture when connected
                    startAudioCapture()
                    Log.d(TAG, "Audio capture started automatically")
                }
                
                override fun onDisconnected() {
                    Log.d(TAG, "Disconnected from signaling server")
                    notifyMainActivityConnectionStatus(false)
                }
                
                override fun onOfferReceived(offer: SessionDescription) {
                    Log.d(TAG, "Offer received from companion")
                    handleOffer(offer)
                }
                
                override fun onAnswerReceived(answer: SessionDescription) {
                    Log.d(TAG, "Answer received from companion")
                    handleAnswer(answer)
                }
                
                override fun onIceCandidateReceived(candidate: IceCandidate) {
                    Log.d(TAG, "ICE candidate received")
                    currentPeerConnection?.addIceCandidate(candidate)
                }
                
                override fun onError(error: String) {
                    Log.e(TAG, "Signaling error: $error")
                    // Send error status to MainActivity
                    sendStatusToMainActivity("Error: $error")
                }
                
                override fun onTextMessageReceived(messageJson: String) {
                    Log.i(TAG, "🔍 TEXT MESSAGE RECEIVED via WebSocket")
                    Log.i(TAG, "🔍 Message content: $messageJson")
                    
                    // Forward to MainActivity's HudDisplayManager immediately (primary path)
                    forwardMessageToMainActivity(messageJson)
                    
                    // Also process in service for confirmation callbacks (secondary)
                    processTextMessageForConfirmation(messageJson)
                }
            })
            
            // Connect to signaling server
            connect()
        }
    }
    
    private fun setupAudioCapture() {
        audioCapture = AudioCapture(this).apply {
            onAudioDataCaptured = { audioData ->
                // Send audio data to Companion Desktop via DataChannel for OpenAI processing
                Log.v(TAG, "Audio data captured: ${audioData.size} bytes")
                
                // Send audio via DataChannel to Companion Desktop
                sendAudioViaDataChannel(audioData)
            }
        }
    }
    
    private fun setupCameraCapture() {
        cameraCapture = CameraCapture(this).apply {
            onSnapshotCaptured = { imageData ->
                // Snapshot capturado e comprimido ≤200KB
                Log.d(TAG, "Snapshot ready: ${imageData.size} bytes")
                dataChannelManager?.sendSnapshot(imageData)
            }
            
            // Initialize camera
            initialize()
        }
    }
    
    private fun handleOffer(offer: SessionDescription) {
        currentPeerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Remote description set successfully")
                createAnswer()
            }
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to create session: $error")
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Failed to set remote description: $error")
            }
        }, offer)
    }
    
    private fun handleAnswer(answer: SessionDescription) {
        currentPeerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Remote answer set successfully")
            }
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to create session: $error")
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Failed to set remote answer: $error")
            }
        }, answer)
    }
    
    private fun createOffer() {
        Log.d(TAG, "createOffer() called - starting offer creation")
        
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        
        Log.d(TAG, "PeerConnection available: ${currentPeerConnection != null}")
        currentPeerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let { offer ->
                    currentPeerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(desc: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local offer set, sending to companion")
                            signalingClient?.sendOffer(offer)
                        }
                        override fun onCreateFailure(error: String?) {
                            Log.e(TAG, "Failed to create local desc: $error")
                        }
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "Failed to set local desc: $error")
                        }
                    }, offer)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to create offer: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }
    
    private fun createAnswer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        
        currentPeerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let { answer ->
                    currentPeerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(desc: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local answer set, sending to companion")
                            signalingClient?.sendAnswer(answer)
                        }
                        override fun onCreateFailure(error: String?) {
                            Log.e(TAG, "Failed to create local desc: $error")
                        }
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "Failed to set local desc: $error")
                        }
                    }, answer)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to create answer: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }
    
    private fun createOfferWithPeerConnection(peerConnection: PeerConnection) {
        Log.i(TAG, "🎯 createOfferWithPeerConnection() CALLED - starting offer creation")
        
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        
        Log.d(TAG, "🎯 About to call peerConnection.createOffer() with constraints")
        peerConnection.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                Log.i(TAG, "🎯 OFFER CREATED SUCCESSFULLY!")
                desc?.let { offer ->
                    Log.d(TAG, "🎯 Setting local description for offer")
                    peerConnection.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(desc: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.i(TAG, "🎯 LOCAL OFFER SET - sending to companion via SignalingClient")
                            Log.d(TAG, "🎯 SignalingClient available: ${signalingClient != null}")
                            signalingClient?.sendOffer(offer)
                            Log.i(TAG, "🎯 OFFER SENT TO COMPANION!")
                        }
                        override fun onCreateFailure(error: String?) {
                            Log.e(TAG, "🎯 FAILED to create local desc: $error")
                        }
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "🎯 FAILED to set local desc: $error")
                        }
                    }, offer)
                } ?: Log.e(TAG, "🎯 OFFER IS NULL!")
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "🎯 FAILED TO CREATE OFFER: $error")
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "🎯 SDP SET FAILURE: $error")
            }
        }, constraints)
        Log.d(TAG, "🎯 createOffer() call completed, waiting for callbacks...")
    }
    
    fun createPeerConnection(peerId: String) {
        if (!isWebRTCInitialized) {
            Log.w(TAG, "WebRTC not initialized yet")
            return
        }
        
        // Store the reference BEFORE creating the PeerConnection to avoid race condition
        // This way, any observer callbacks (like onRenegotiationNeeded) will have access to currentPeerConnection
        
        val peerConnection = WebRTCManager.createPeerConnection(peerId, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(TAG, "ICE candidate generated: ${candidate.sdp}")
                signalingClient?.sendIceCandidate(candidate)
            }
            
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
                Log.d(TAG, "ICE candidates removed")
            }
            
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(TAG, "Signaling state changed: $state")
            }
            
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE connection state changed: $state")
                
                when (state) {
                    PeerConnection.IceConnectionState.FAILED -> {
                        Log.e(TAG, "🚨 ICE CONNECTION FAILED! Possible causes:")
                        Log.e(TAG, "  - STUN servers not reachable")
                        Log.e(TAG, "  - Network/firewall blocking WebRTC")
                        Log.e(TAG, "  - Need TURN servers")
                        Log.e(TAG, "  - Localhost connectivity issues")
                        sendStatusToMainActivity("ICE connection failed")
                    }
                    PeerConnection.IceConnectionState.CONNECTED -> {
                        Log.i(TAG, "🎉 ICE CONNECTION ESTABLISHED!")
                        sendStatusToMainActivity("WebRTC connected")
                    }
                    PeerConnection.IceConnectionState.CHECKING -> {
                        Log.i(TAG, "🔍 ICE checking connectivity...")
                        sendStatusToMainActivity("Checking connection")
                    }
                    else -> {
                        Log.d(TAG, "ICE state: $state")
                    }
                }
            }
            
            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                Log.d(TAG, "ICE connection receiving change: $receiving")
            }
            
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "ICE gathering state changed: $state")
            }
            
            override fun onAddStream(stream: MediaStream?) {
                Log.d(TAG, "Stream added")
            }
            
            override fun onRemoveStream(stream: MediaStream?) {
                Log.d(TAG, "Stream removed")
            }
            
            override fun onDataChannel(dataChannel: DataChannel?) {
                Log.d(TAG, "Data channel received from remote peer")
                // For now, we'll handle incoming data channels here
                // In production, DataChannelManager should be created when WE create the peer connection
                dataChannel?.registerObserver(object : DataChannel.Observer {
                    override fun onBufferedAmountChange(previousAmount: Long) {}
                    
                    override fun onStateChange() {
                        Log.d(TAG, "Remote DataChannel state: ${dataChannel.state()}")
                    }
                    
                    override fun onMessage(buffer: DataChannel.Buffer) {
                        // Handle incoming messages
                        val data = buffer.data
                        val bytes = ByteArray(data.remaining())
                        data.get(bytes)
                        val message = String(bytes)
                        Log.d(TAG, "DataChannel message received: $message")
                        
                        try {
                            val json = JSONObject(message)
                            val messageType = json.getString("type")
                            handleDataChannelMessage(messageType, json)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse DataChannel message", e)
                        }
                    }
                })
            }
            
            override fun onRenegotiationNeeded() {
                Log.i(TAG, "🔥 RENEGOTIATION NEEDED for peer: $peerId")
                
                // Prevent concurrent offers per WebRTC Perfect Negotiation pattern
                if (makingOffer) {
                    Log.w(TAG, "🔥 Already making offer, skipping renegotiation")
                    return
                }
                
                Log.d(TAG, "🔥 currentPeerConnection available: ${currentPeerConnection != null}")
                
                // Use currentPeerConnection with null safety - official WebRTC pattern
                currentPeerConnection?.let { pc ->
                    Log.i(TAG, "🔥 Launching coroutine to create offer")
                    serviceScope.launch {
                        try {
                            makingOffer = true
                            Log.i(TAG, "🔥 makingOffer = true, calling createOfferWithPeerConnection")
                            createOfferWithPeerConnection(pc)
                        } catch (e: Exception) {
                            Log.e(TAG, "🔥 FAILED to handle renegotiation", e)
                        } finally {
                            makingOffer = false
                            Log.d(TAG, "🔥 makingOffer = false")
                        }
                    }
                } ?: Log.e(TAG, "🔥 NO CURRENT PEERCONNECTION FOR RENEGOTIATION!")
            }
            
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                Log.d(TAG, "Track added")
            }
            
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) {
                Log.d(TAG, "Connection state changed: $state")
            }
        })
        
        // Store the peer connection reference IMMEDIATELY after creation
        currentPeerConnection = peerConnection
        Log.i(TAG, "🎯 PeerConnection created and stored IMMEDIATELY for peer: $peerId")
        
        // Now create DataChannelManager - any onRenegotiationNeeded callbacks will have currentPeerConnection available
        peerConnection?.let { pc ->
            Log.i(TAG, "🎯 Creating DataChannelManager - this will trigger onRenegotiationNeeded with currentPeerConnection available")
            dataChannelManager = DataChannelManager(pc, serviceScope)
            Log.i(TAG, "🎯 DataChannelManager created for peer: $peerId")
        } ?: Log.e(TAG, "🎯 PeerConnection is NULL - cannot create DataChannelManager!")
    }
    
    private fun handleDataChannelMessage(messageType: String, json: JSONObject) {
        Log.i(TAG, "🔍 DATA CHANNEL MESSAGE RECEIVED!")
        Log.i(TAG, "🔍 Full JSON: $json")
        Log.i(TAG, "🔍 Message type: '$messageType'")
        
        when (messageType) {
            "capture_snapshot" -> {
                Log.d(TAG, "Snapshot request received")
                takePicture()
            }
            "model_text" -> {
                val text = json.optString("text", "")
                val messageId = json.optString("message_id", "")
                val requiresConfirmation = json.optBoolean("requires_confirmation", false)
                val seq = json.optInt("seq", -1)
                val ts = json.optLong("ts", 0)
                
                Log.i(TAG, "🎯 MODEL_TEXT received from Desktop!")
                Log.i(TAG, "🎯 Text: $text")
                Log.i(TAG, "🎯 Message ID: $messageId")
                Log.i(TAG, "🎯 Requires confirmation: $requiresConfirmation")
                Log.i(TAG, "🎯 Seq: $seq, Timestamp: $ts")
                
                // Forward message to MainActivity's HudDisplayManager
                Log.i(TAG, "🎯 Forwarding DataChannel message to MainActivity")
                val messageToForward = JSONObject().apply {
                    put("type", "model_text")
                    put("text", text)
                    put("message_id", messageId)
                    put("requires_confirmation", requiresConfirmation)
                    put("seq", seq)
                    put("ts", ts)
                }
                forwardMessageToMainActivity(messageToForward.toString())
                
                // VideoSDK pattern: Send confirmation back to Desktop
                if (requiresConfirmation && messageId.isNotEmpty()) {
                    sendDisplayConfirmation(messageId, "displayed")
                }
            }
            "model_audio" -> {
                Log.d(TAG, "Model audio response received")
                // TODO: Handle audio playback
            }
            else -> {
                Log.w(TAG, "Unknown message type: $messageType")
            }
        }
    }
    
    // VideoSDK pattern: Send display confirmation back to Desktop
    private fun sendDisplayConfirmation(messageId: String, status: String) {
        try {
            val confirmation = JSONObject().apply {
                put("type", "display_confirmed")
                put("message_id", messageId)
                put("status", status)
                put("timestamp", System.currentTimeMillis())
                put("device_id", "m400_${System.currentTimeMillis()}")
            }
            
            dataChannelManager?.sendMessage(confirmation.toString())
            Log.i(TAG, "🎯 DISPLAY CONFIRMATION sent to Desktop")
            Log.i(TAG, "🎯 Message ID: $messageId, Status: $status")
            
        } catch (e: Exception) {
            Log.e(TAG, "🚨 Failed to send display confirmation", e)
        }
    }
    
    fun startAudioCapture() {
        audioCapture?.startCapture(serviceScope)
    }
    
    fun stopAudioCapture() {
        audioCapture?.stopCapture()
    }
    
    fun takePicture() {
        cameraCapture?.takePicture { imageData ->
            // Snapshot capturado, será enviado via DataChannel
            dataChannelManager?.sendSnapshot(imageData)
            Log.d(TAG, "Snapshot taken and sent: ${imageData.size} bytes")
        }
    }
    
    private fun sendAudioViaDataChannel(audioData: ByteArray) {
        // Use WebSocket direct streaming (MVP approach) - working perfectly!
        sendAudioViaWebSocket(audioData)
        
        // Disable DataChannel audio to avoid duplication (WebSocket is working)
        /*
        dataChannelManager?.let { dcManager ->
            // Create audio message for Companion Desktop
            val audioMessage = JSONObject().apply {
                put("type", "audio_data")
                put("format", "pcm16")
                put("sampleRate", 16000)
                put("channels", 1)
                put("timestamp", System.currentTimeMillis())
                // Convert audio bytes to base64 for JSON transmission
                put("data", android.util.Base64.encodeToString(audioData, android.util.Base64.NO_WRAP))
            }
            
            dcManager.sendMessage(audioMessage.toString())
            Log.v(TAG, "Audio data sent via DataChannel: ${audioData.size} bytes")
        } ?: run {
            Log.v(TAG, "DataChannelManager not available, using WebSocket streaming")
        }
        */
    }
    
    private fun sendAudioViaWebSocket(audioData: ByteArray) {
        signalingClient?.let { client ->
            // Create audio message for OpenAI Realtime API via WebSocket
            val audioMessage = JSONObject().apply {
                put("type", "audio_stream")
                put("format", "pcm16")
                put("sampleRate", 16000)
                put("channels", 1)
                put("timestamp", System.currentTimeMillis())
                // Convert audio bytes to base64 for WebSocket transmission
                put("data", android.util.Base64.encodeToString(audioData, android.util.Base64.NO_WRAP))
            }
            
            client.sendMessage(audioMessage)
            Log.v(TAG, "🎵 Audio streamed via WebSocket: ${audioData.size} bytes")
        } ?: run {
            Log.w(TAG, "SignalingClient not available for WebSocket streaming")
        }
    }
    
    /**
     * Process text message for confirmation callbacks only (not for display)
     */
    private fun processTextMessageForConfirmation(messageJson: String) {
        Log.i(TAG, "🔍 ENTERING processTextMessage()")
        Log.i(TAG, "🔍 Raw message: $messageJson")
        
        try {
            val json = JSONObject(messageJson)
            val type = json.optString("type", "")
            
            Log.i(TAG, "🔍 Parsed JSON successfully")
            Log.i(TAG, "🔍 Message type: '$type'")
            Log.i(TAG, "🔍 All JSON keys: ${json.keys().asSequence().toList()}")
            
            when (type) {
                "model_text" -> {
                    val text = json.optString("text", "")
                    val messageId = json.optString("message_id", "")
                    
                    Log.i(TAG, "🔍 MODEL_TEXT detected for confirmation!")
                    Log.i(TAG, "🔍 Text content: '$text' (length: ${text.length})")
                    Log.i(TAG, "🔍 Message ID: '$messageId'")
                    
                    // Send confirmation back to companion (text display handled by MainActivity)
                    if (messageId.isNotEmpty()) {
                        Log.i(TAG, "🔍 Sending text confirmation for message: $messageId")
                        sendTextConfirmation(messageId, "displayed_via_mainactivity")
                    }
                }
                
                "status_update" -> {
                    val status = json.optString("message", "")
                    Log.i(TAG, "🔍 STATUS_UPDATE: $status")
                    sendStatusToMainActivity(status)
                }
                
                "clear_display" -> {
                    Log.i(TAG, "🔍 CLEAR_DISPLAY")
                    sendClearDisplayToMainActivity()
                }
                
                else -> {
                    Log.w(TAG, "🔍 Unknown text message type: '$type'")
                    Log.w(TAG, "🔍 Full message for debugging: $messageJson")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "🚨 EXCEPTION in processTextMessage()", e)
            Log.e(TAG, "🚨 Exception message: ${e.message}")
            Log.e(TAG, "🚨 Exception cause: ${e.cause}")
        }
        
        Log.i(TAG, "🔍 EXITING processTextMessage()")
    }
    
    /**
     * Send text confirmation back to companion
     */
    private fun sendTextConfirmation(messageId: String, status: String) {
        try {
            val confirmation = JSONObject().apply {
                put("type", "display_confirmed")
                put("message_id", messageId)
                put("status", status)
                put("timestamp", System.currentTimeMillis())
                put("device_id", "m400_service")
            }
            
            signalingClient?.sendMessage(confirmation)
            Log.d(TAG, "📱 Text confirmation sent: $status")
            
        } catch (e: Exception) {
            Log.e(TAG, "📱 Failed to send text confirmation", e)
        }
    }
    
    /**
     * Forward text message to MainActivity for HUD display
     */
    private fun forwardMessageToMainActivity(messageJson: String) {
        try {
            // Get MainActivity instance and forward message
            // Using Intent-based approach to avoid direct activity references
            val intent = Intent("com.seudominio.app_smart_companion.HUD_MESSAGE").apply {
                setPackage(packageName)
                putExtra("message", messageJson)
            }
            sendBroadcast(intent)
            
            Log.d(TAG, "📱 Message forwarded to MainActivity via broadcast")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error forwarding message to MainActivity", e)
        }
    }
    
    /**
     * Send message back to companion desktop (called by MainActivity)
     */
    fun sendMessageToCompanion(messageJson: String) {
        try {
            val json = JSONObject(messageJson)
            signalingClient?.sendMessage(json)
            
            Log.d(TAG, "📤 Message sent to companion: ${messageJson.substring(0, minOf(100, messageJson.length))}...")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message to companion", e)
        }
    }
    
    /**
     * Update connection status in MainActivity
     */
    private fun notifyMainActivityConnectionStatus(connected: Boolean) {
        val intent = Intent("com.seudominio.app_smart_companion.CONNECTION_STATUS").apply {
            setPackage(packageName)
            putExtra("connected", connected)
        }
        sendBroadcast(intent)
        
        Log.d(TAG, "📱 Connection status broadcast sent: $connected")
    }
    
    /**
     * Send status message to MainActivity
     */
    private fun sendStatusToMainActivity(status: String) {
        try {
            val statusMessage = JSONObject().apply {
                put("type", "status_update")
                put("message", status)
                put("timestamp", System.currentTimeMillis())
            }
            
            forwardMessageToMainActivity(statusMessage.toString())
            Log.d(TAG, "📱 Status sent to MainActivity: $status")
            
        } catch (e: Exception) {
            Log.e(TAG, "📱 Error sending status to MainActivity", e)
        }
    }
    
    /**
     * Send clear display command to MainActivity
     */
    private fun sendClearDisplayToMainActivity() {
        try {
            val clearMessage = JSONObject().apply {
                put("type", "clear_display")
                put("timestamp", System.currentTimeMillis())
            }
            
            forwardMessageToMainActivity(clearMessage.toString())
            Log.d(TAG, "📱 Clear display sent to MainActivity")
            
        } catch (e: Exception) {
            Log.e(TAG, "📱 Error sending clear display to MainActivity", e)
        }
    }
    
    private fun cleanup() {
        Log.d(TAG, "Cleaning up resources")
        
        audioCapture?.stopCapture()
        cameraCapture?.dispose()
        dataChannelManager?.dispose()
        signalingClient?.disconnect()
        currentPeerConnection?.close()
        // Note: HUD display cleanup handled by MainActivity
        
        WebRTCManager.dispose()
        serviceScope.cancel()
        
        Log.d(TAG, "Cleanup completed")
    }
}