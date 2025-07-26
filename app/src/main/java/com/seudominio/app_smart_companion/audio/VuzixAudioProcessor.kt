package com.seudominio.app_smart_companion.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Audio Processor otimizado para Vuzix M400
 * Baseado nas especificações do Vuzix SDK e padrões ElatoAI
 */
class VuzixAudioProcessor(
    private val context: Context,
    private val onAudioData: (ByteArray) -> Unit,
    private val onError: (String) -> Unit
) {
    
    // Audio processor for quality enhancement
    private val audioProcessor = OptimizedAudioProcessor()
    companion object {
        private const val TAG = "LearionGlass"
        
        // Optimized based on research: 44.1kHz → downsample for compatibility
        private const val SAMPLE_RATE = 44100 // Guaranteed Android support, downsample to 24kHz
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_MULTIPLIER = 4
        
        // Frame size for OpenAI (baseado em ElatoAI)
        private const val FRAME_SIZE_MS = 20 // 20ms frames
        private const val BYTES_PER_SAMPLE = 2 // 16-bit = 2 bytes
        private const val FRAME_SIZE_BYTES = SAMPLE_RATE * FRAME_SIZE_MS / 1000 * BYTES_PER_SAMPLE
    }
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Inicia captura de áudio usando configurações otimizadas para Vuzix M400
     */
    fun startCapture() {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }
        
        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            
            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                onError("Invalid audio configuration for Vuzix M400")
                return
            }
            
            val bufferSize = minBufferSize * BUFFER_SIZE_MULTIPLIER
            
            // Use VOICE_RECOGNITION source (recomendado pelo Vuzix SDK para IA)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onError("Failed to initialize AudioRecord for Vuzix M400")
                return
            }
            
            audioRecord?.startRecording()
            isRecording = true
            
            // Initialize audio processor with session ID
            val sessionId = audioRecord?.audioSessionId ?: 0
            val effectsEnabled = audioProcessor.initialize(sessionId)
            
            Log.d(TAG, "🎤 Started Vuzix M400 audio capture (VOICE_RECOGNITION, 44.1kHz→24kHz, effects: $effectsEnabled)")
            Log.d(TAG, audioProcessor.getProcessingInfo())
            
            // Start audio processing loop
            scope.launch {
                processAudioLoop()
            }
            
        } catch (e: SecurityException) {
            onError("Audio permission required: ${e.message}")
        } catch (e: Exception) {
            onError("Failed to start audio capture: ${e.message}")
        }
    }
    
    /**
     * Loop de processamento de áudio (padrão ElatoAI adaptado para Android)
     */
    private suspend fun processAudioLoop() {
        val frameBuffer = ByteArray(FRAME_SIZE_BYTES)
        
        while (isRecording && audioRecord != null) {
            try {
                val bytesRead = audioRecord!!.read(frameBuffer, 0, frameBuffer.size)
                
                if (bytesRead > 0) {
                    // Process only complete frames
                    if (bytesRead == FRAME_SIZE_BYTES) {
                        // Convert to little-endian if needed (OpenAI expects little-endian PCM16)
                        val littleEndianFrame = ensureLittleEndian(frameBuffer)
                        
                        // Apply optimized audio processing (44.1kHz → 24kHz + effects)
                        val processedFrame = audioProcessor.processAudioFrame(littleEndianFrame)
                        onAudioData(processedFrame)
                    }
                } else if (bytesRead < 0) {
                    Log.e(TAG, "AudioRecord read error: $bytesRead")
                    break
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in audio processing loop", e)
                break
            }
        }
        
        Log.d(TAG, "Audio processing loop ended")
    }
    
    /**
     * Ensure audio data is in little-endian format (OpenAI requirement)
     */
    private fun ensureLittleEndian(audioData: ByteArray): ByteArray {
        return if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            audioData
        } else {
            // Convert from big-endian to little-endian
            val buffer = ByteBuffer.wrap(audioData)
            buffer.order(ByteOrder.BIG_ENDIAN)
            val littleEndianBuffer = ByteBuffer.allocate(audioData.size)
            littleEndianBuffer.order(ByteOrder.LITTLE_ENDIAN)
            
            while (buffer.hasRemaining()) {
                littleEndianBuffer.putShort(buffer.short)
            }
            
            littleEndianBuffer.array()
        }
    }
    
    /**
     * Para captura de áudio
     */
    fun stopCapture() {
        if (!isRecording) {
            return
        }
        
        isRecording = false
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            Log.d(TAG, "🛑 Stopped Vuzix M400 audio capture")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio capture", e)
        }
    }
    
    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean = isRecording
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopCapture()
        audioProcessor.release()
        scope.cancel()
    }
    
    /**
     * Get current audio configuration info
     */
    fun getAudioInfo(): String {
        return "Vuzix M400 Audio: ${SAMPLE_RATE}Hz→24kHz, Mono, PCM16, VOICE_RECOGNITION + Audio Processing"
    }
}