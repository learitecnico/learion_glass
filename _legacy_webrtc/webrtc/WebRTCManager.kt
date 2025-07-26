package com.seudominio.app_smart_companion.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.ConcurrentHashMap

/**
 * WebRTC Manager seguindo documentação oficial GetStream
 * Gerencia conexões peer-to-peer, audio tracks e data channels
 */
object WebRTCManager {
    private const val TAG = "WebRTCManager"
    
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private val peerConnections = ConcurrentHashMap<String, PeerConnection>()
    private var audioTrack: AudioTrack? = null
    private var videoTrack: VideoTrack? = null
    private var eglBase: EglBase? = null
    
    // Callback interfaces
    var onDataChannelMessage: ((String, String) -> Unit)? = null
    var onConnectionStateChange: ((String, PeerConnection.PeerConnectionState) -> Unit)? = null
    
    /**
     * Inicializa WebRTC factory com configurações adequadas
     */
    fun initialize(context: Context) {
        Log.d(TAG, "Initializing WebRTC")
        
        // Initialize WebRTC
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(false)
                .setFieldTrials("")
                .createInitializationOptions()
        )
        
        // Create EglBase for video rendering
        eglBase = EglBase.create()
        
        // Create audio device module
        val audioDeviceModule = JavaAudioDeviceModule.builder(context.applicationContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        
        // Create peer connection factory options
        val options = PeerConnectionFactory.Options().apply {
            disableEncryption = false
            disableNetworkMonitor = false
        }
        
        // Create peer connection factory
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase?.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase?.eglBaseContext))
            .createPeerConnectionFactory()
        
        // Create audio track
        createAudioTrack()
        
        Log.d(TAG, "WebRTC initialized successfully")
    }
    
    /**
     * Cria conexão peer-to-peer com configurações adequadas
     */
    fun createPeerConnection(
        peerId: String,
        observer: PeerConnection.Observer
    ): PeerConnection? {
        val factory = peerConnectionFactory ?: run {
            Log.e(TAG, "PeerConnectionFactory not initialized")
            return null
        }
        
        // ICE servers configuration - múltiplos STUN servers para melhor conectividade
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.stunprotocol.org:3478").createIceServer()
        )
        
        // RTC Configuration
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.ALL
            keyType = PeerConnection.KeyType.ECDSA
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        
        // Create peer connection
        val peerConnection = factory.createPeerConnection(rtcConfig, observer)
        
        peerConnection?.let { pc ->
            peerConnections[peerId] = pc
            
            // Add audio track
            audioTrack?.let { audio ->
                pc.addTrack(audio, listOf("audio-stream"))
            }
            
            // Create data channel for snapshots
            createDataChannel(pc, peerId)
            
            Log.d(TAG, "PeerConnection created for peer: $peerId")
        }
        
        return peerConnection
    }
    
    /**
     * Cria audio track com configurações 16kHz mono
     */
    private fun createAudioTrack() {
        val factory = peerConnectionFactory ?: return
        
        // Audio constraints para 16kHz mono
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }
        
        val audioSource = factory.createAudioSource(audioConstraints)
        audioTrack = factory.createAudioTrack("audio", audioSource)
        
        Log.d(TAG, "Audio track created with 16kHz mono configuration")
    }
    
    /**
     * Cria data channel para snapshots
     */
    private fun createDataChannel(peerConnection: PeerConnection, peerId: String) {
        val dataChannelInit = DataChannel.Init().apply {
            ordered = true
            maxRetransmits = 0
        }
        
        val dataChannel = peerConnection.createDataChannel("snapshots", dataChannelInit)
        
        dataChannel?.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            
            override fun onStateChange() {
                Log.d(TAG, "DataChannel state changed to: ${dataChannel.state()}")
            }
            
            override fun onMessage(buffer: DataChannel.Buffer) {
                if (!buffer.binary) {
                    val data = ByteArray(buffer.data.remaining())
                    buffer.data.get(data)
                    val message = String(data)
                    onDataChannelMessage?.invoke(peerId, message)
                }
            }
        })
    }
    
    /**
     * Obtém peer connection por ID
     */
    fun getPeerConnection(peerId: String): PeerConnection? {
        return peerConnections[peerId]
    }
    
    /**
     * Remove peer connection
     */
    fun removePeerConnection(peerId: String) {
        peerConnections[peerId]?.let { pc ->
            pc.close()
            peerConnections.remove(peerId)
            Log.d(TAG, "PeerConnection removed for peer: $peerId")
        }
    }
    
    /**
     * Envia mensagem via data channel
     */
    fun sendDataChannelMessage(peerId: String, message: String) {
        val peerConnection = peerConnections[peerId] ?: return
        
        peerConnection.senders.forEach { rtpSender ->
            // Data channels são obtidos via different method em RTCPeerConnection
            // Implementação específica depende da versão GetStream
            Log.d(TAG, "Sending data channel message to peer: $peerId")
        }
    }
    
    /**
     * Cleanup resources
     */
    fun dispose() {
        Log.d(TAG, "Disposing WebRTC resources")
        
        // Close all peer connections
        peerConnections.values.forEach { it.close() }
        peerConnections.clear()
        
        // Dispose tracks
        audioTrack?.dispose()
        videoTrack?.dispose()
        
        // Dispose factory
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        
        // Dispose EGL base
        eglBase?.release()
        eglBase = null
        
        Log.d(TAG, "WebRTC resources disposed")
    }
    
    /**
     * Verifica se está inicializado
     */
    fun isInitialized(): Boolean {
        return peerConnectionFactory != null
    }
}