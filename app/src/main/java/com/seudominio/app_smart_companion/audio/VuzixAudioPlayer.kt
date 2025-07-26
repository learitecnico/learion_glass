package com.seudominio.app_smart_companion.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*

/**
 * Audio Player otimizado para Vuzix M400
 * Reproduz respostas de áudio do OpenAI Realtime API
 */
class VuzixAudioPlayer(
    private val context: Context,
    private val onPlaybackComplete: () -> Unit = {},
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "LearionGlass"
        
        // Vuzix M400 optimized settings (matching OpenAI output)
        private const val SAMPLE_RATE = 48000 // OpenAI WebRTC requirement // OpenAI Realtime API default
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        // Buffer settings
        private const val BUFFER_SIZE_MULTIPLIER = 2
    }
    
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Initialize audio player for Vuzix M400
     */
    fun initialize() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            
            if (minBufferSize == AudioTrack.ERROR || minBufferSize == AudioTrack.ERROR_BAD_VALUE) {
                onError("Invalid audio configuration for Vuzix M400 playback")
                return
            }
            
            val bufferSize = minBufferSize * BUFFER_SIZE_MULTIPLIER
            
            // Audio attributes optimized for Vuzix M400
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            
            val audioFormat = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AUDIO_FORMAT)
                .setChannelMask(CHANNEL_CONFIG)
                .build()
            
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            
            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                onError("Failed to initialize AudioTrack for Vuzix M400")
                return
            }
            
            Log.d(TAG, "🔊 Vuzix M400 audio player initialized (24kHz, Mono, PCM16)")
            
        } catch (e: Exception) {
            onError("Failed to initialize audio player: ${e.message}")
        }
    }
    
    /**
     * Play audio data from OpenAI (PCM16 format)
     */
    fun playAudio(audioData: ByteArray) {
        if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            Log.w(TAG, "AudioTrack not initialized")
            return
        }
        
        if (isPlaying) {
            Log.d(TAG, "Already playing audio, queuing new data")
        }
        
        scope.launch {
            try {
                if (!isPlaying) {
                    audioTrack?.play()
                    isPlaying = true
                    Log.d(TAG, "🎵 Started audio playback on Vuzix M400")
                }
                
                // Write audio data to track
                val bytesWritten = audioTrack?.write(audioData, 0, audioData.size) ?: 0
                
                if (bytesWritten < 0) {
                    Log.e(TAG, "Error writing audio data: $bytesWritten")
                    onError("Audio playback error")
                } else {
                    Log.d(TAG, "🎵 Audio chunk played: ${bytesWritten} bytes")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error playing audio", e)
                onError("Audio playback failed: ${e.message}")
            }
        }
    }
    
    /**
     * Stop audio playback
     */
    fun stopPlayback() {
        if (!isPlaying) {
            return
        }
        
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            isPlaying = false
            
            Log.d(TAG, "🛑 Stopped audio playback")
            onPlaybackComplete()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio playback", e)
        }
    }
    
    /**
     * Set volume (0.0 to 1.0)
     */
    fun setVolume(volume: Float) {
        try {
            val clampedVolume = volume.coerceIn(0.0f, 1.0f)
            audioTrack?.setVolume(clampedVolume)
            Log.d(TAG, "🔊 Volume set to: $clampedVolume")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume", e)
        }
    }
    
    /**
     * Check if currently playing
     */
    fun isPlaying(): Boolean = isPlaying
    
    /**
     * Get current audio info
     */
    fun getAudioInfo(): String {
        return "Vuzix M400 Audio Player: ${SAMPLE_RATE}Hz, Mono, PCM16"
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopPlayback()
        
        try {
            audioTrack?.release()
            audioTrack = null
            scope.cancel()
            
            Log.d(TAG, "🧹 Audio player cleanup completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during audio player cleanup", e)
        }
    }
}