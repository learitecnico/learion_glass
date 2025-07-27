package com.seudominio.app_smart_companion.assistants

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * TTSManager - Text-to-Speech Manager using OpenAI Audio API
 * 
 * Gerencia conversão de texto para áudio usando OpenAI TTS API
 * com suporte a:
 * - Diferentes vozes e modelos
 * - Streaming de áudio
 * - Playback com MediaPlayer
 * - Cache local de áudios
 * 
 * Baseado em OpenAI Audio API (/v1/audio/speech)
 */
class TTSManager(
    private val context: Context,
    private val apiKey: String
) {
    companion object {
        private const val TAG = "TTSManager"
        private const val BASE_URL = "https://api.openai.com/v1"
        private const val TTS_ENDPOINT = "$BASE_URL/audio/speech"
        
        // TTS Models
        const val MODEL_TTS_1 = "tts-1"          // Otimizado para latência
        const val MODEL_TTS_1_HD = "tts-1-hd"    // Otimizado para qualidade
        
        // Text limits
        private const val MAX_TEXT_LENGTH = 4096  // OpenAI TTS limit
        
        // Available Voices
        const val VOICE_ALLOY = "alloy"      // Neutro
        const val VOICE_ECHO = "echo"        // Masculino
        const val VOICE_FABLE = "fable"      // Britânico
        const val VOICE_ONYX = "onyx"        // Profundo
        const val VOICE_NOVA = "nova"        // Feminino
        const val VOICE_SHIMMER = "shimmer"  // Suave
        
        // Audio Formats
        const val FORMAT_MP3 = "mp3"
        const val FORMAT_OPUS = "opus"
        const val FORMAT_AAC = "aac"
        const val FORMAT_FLAC = "flac"
        
        // Default settings
        private const val DEFAULT_MODEL = MODEL_TTS_1
        private const val DEFAULT_VOICE = VOICE_SHIMMER  // Coach profissional
        private const val DEFAULT_FORMAT = FORMAT_MP3
        private const val DEFAULT_SPEED = 1.0f
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentMediaPlayer: MediaPlayer? = null
    private val cacheDir = File(context.cacheDir, "tts_cache")
    
    init {
        // Create cache directory
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }
    
    /**
     * TTS configuration
     */
    data class TTSConfig(
        val model: String = DEFAULT_MODEL,
        val voice: String = DEFAULT_VOICE,
        val speed: Float = DEFAULT_SPEED,
        val responseFormat: String = DEFAULT_FORMAT
    )
    
    /**
     * TTS callback interface
     */
    interface TTSCallback {
        fun onTTSStarted()
        fun onTTSCompleted()
        fun onTTSError(error: String)
        fun onPlaybackStarted()
        fun onPlaybackCompleted()
    }
    
    /**
     * Convert text to speech and play
     */
    suspend fun textToSpeechAndPlay(
        text: String,
        config: TTSConfig = TTSConfig(),
        callback: TTSCallback? = null
    ) = withContext(Dispatchers.IO) {
        try {
            // Validate and truncate text if needed
            val processedText = if (text.length > MAX_TEXT_LENGTH) {
                Log.w(TAG, "⚠️ Text too long (${text.length} chars), truncating to $MAX_TEXT_LENGTH")
                text.take(MAX_TEXT_LENGTH - 20) + "... (texto truncado)"
            } else {
                text
            }
            
            Log.d(TAG, "🎤 Starting TTS for text: ${processedText.take(50)}...")
            callback?.onTTSStarted()
            
            // Generate audio file
            val audioFile = generateSpeech(processedText, config)
            if (audioFile != null) {
                Log.d(TAG, "✅ TTS audio generated: ${audioFile.name}")
                callback?.onTTSCompleted()
                
                // Play audio
                withContext(Dispatchers.Main) {
                    playAudio(audioFile, callback)
                }
            } else {
                throw Exception("Failed to generate audio")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ TTS error", e)
            withContext(Dispatchers.Main) {
                callback?.onTTSError(e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Generate speech audio file
     */
    private suspend fun generateSpeech(
        text: String,
        config: TTSConfig
    ): File? = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            val cacheKey = generateCacheKey(text, config)
            val cachedFile = getCachedAudio(cacheKey)
            if (cachedFile != null && cachedFile.exists()) {
                Log.d(TAG, "💾 Using cached audio: ${cachedFile.name}")
                return@withContext cachedFile
            }
            
            // Build request body
            val requestBody = JSONObject().apply {
                put("model", config.model)
                put("input", text)
                put("voice", config.voice)
                put("speed", config.speed)
                put("response_format", config.responseFormat)
            }
            
            // Build HTTP request
            val request = Request.Builder()
                .url(TTS_ENDPOINT)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            // Execute request
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val audioBytes = response.body?.bytes()
                if (audioBytes != null) {
                    // Save to cache
                    val audioFile = saveToCacheWithKey(audioBytes, cacheKey, config.responseFormat)
                    Log.d(TAG, "✅ Audio saved to: ${audioFile.absolutePath}")
                    return@withContext audioFile
                }
            } else {
                val errorBody = response.body?.string()
                Log.e(TAG, "❌ TTS API error: ${response.code} - $errorBody")
            }
            
            null
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception generating speech", e)
            null
        }
    }
    
    /**
     * Play audio file using MediaPlayer
     */
    private fun playAudio(audioFile: File, callback: TTSCallback?) {
        try {
            // Stop any current playback
            stopPlayback()
            
            // Create new MediaPlayer
            currentMediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                
                setOnPreparedListener {
                    Log.d(TAG, "▶️ Starting audio playback")
                    callback?.onPlaybackStarted()
                    start()
                }
                
                setOnCompletionListener {
                    Log.d(TAG, "✅ Audio playback completed")
                    callback?.onPlaybackCompleted()
                    release()
                    currentMediaPlayer = null
                }
                
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "❌ MediaPlayer error: what=$what, extra=$extra")
                    callback?.onTTSError("Playback error: $what")
                    release()
                    currentMediaPlayer = null
                    true
                }
                
                prepareAsync()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error playing audio", e)
            callback?.onTTSError("Playback error: ${e.message}")
        }
    }
    
    /**
     * Stop current playback
     */
    fun stopPlayback() {
        currentMediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping playback", e)
            }
        }
        currentMediaPlayer = null
    }
    
    /**
     * Check if currently playing
     */
    fun isPlaying(): Boolean {
        return try {
            currentMediaPlayer?.isPlaying == true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Generate cache key for audio
     */
    private fun generateCacheKey(text: String, config: TTSConfig): String {
        val combined = "${text}_${config.model}_${config.voice}_${config.speed}_${config.responseFormat}"
        return combined.hashCode().toString()
    }
    
    /**
     * Get cached audio file
     */
    private fun getCachedAudio(cacheKey: String): File? {
        val cachedFile = File(cacheDir, "$cacheKey.mp3")
        return if (cachedFile.exists()) cachedFile else null
    }
    
    /**
     * Save audio to cache
     */
    private fun saveToCacheWithKey(
        audioBytes: ByteArray,
        cacheKey: String,
        format: String
    ): File {
        val audioFile = File(cacheDir, "$cacheKey.$format")
        FileOutputStream(audioFile).use { fos ->
            fos.write(audioBytes)
        }
        return audioFile
    }
    
    /**
     * Clear TTS cache
     */
    fun clearCache() {
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
            Log.d(TAG, "🧹 TTS cache cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
        }
    }
    
    /**
     * Get cache size in MB
     */
    fun getCacheSizeMB(): Float {
        val totalSize = cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
        return totalSize / (1024f * 1024f)
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopPlayback()
        scope.cancel()
        Log.d(TAG, "🧹 TTSManager cleaned up")
    }
}