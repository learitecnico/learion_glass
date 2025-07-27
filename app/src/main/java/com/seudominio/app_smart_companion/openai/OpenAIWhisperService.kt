package com.seudominio.app_smart_companion.openai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * OpenAI Whisper API service for speech-to-text transcription
 * Direct HTTP integration without WebSocket or Companion Desktop
 */
class OpenAIWhisperService(private val context: Context) {
    
    companion object {
        private const val TAG = "OpenAIWhisperService"
        private const val WHISPER_API_URL = "https://api.openai.com/v1/audio/transcriptions"
        private const val TIMEOUT_SECONDS = 30L
    }
    
    interface TranscriptionCallback {
        fun onTranscriptionSuccess(transcript: String)
        fun onTranscriptionError(error: String)
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    /**
     * Transcribe audio file using OpenAI Whisper API
     */
    suspend fun transcribeAudio(
        audioFile: File,
        apiKey: String,
        language: String = "pt", // Portuguese by default
        callback: TranscriptionCallback
    ) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🎤 Starting Whisper transcription for: ${audioFile.name}")
                
                // Validate file
                if (!audioFile.exists()) {
                    callback.onTranscriptionError("Audio file not found: ${audioFile.path}")
                    return@withContext
                }
                
                if (audioFile.length() == 0L) {
                    callback.onTranscriptionError("Audio file is empty")
                    return@withContext
                }
                
                // Check file size (25MB limit)
                val fileSizeMB = audioFile.length() / (1024 * 1024)
                if (fileSizeMB > 25) {
                    callback.onTranscriptionError("File too large: ${fileSizeMB}MB (max 25MB)")
                    return@withContext
                }
                
                Log.d(TAG, "📄 File validated: ${audioFile.length()} bytes (${fileSizeMB}MB)")
                
                // Build multipart request
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        audioFile.name,
                        audioFile.asRequestBody("audio/wav".toMediaType())
                    )
                    .addFormDataPart("model", "whisper-1")
                    .addFormDataPart("language", language)
                    .addFormDataPart("response_format", "json")
                    .build()
                
                val request = Request.Builder()
                    .url(WHISPER_API_URL)
                    .header("Authorization", "Bearer $apiKey")
                    .post(requestBody)
                    .build()
                
                Log.d(TAG, "🌐 Sending request to OpenAI Whisper API...")
                
                // Execute request
                val response = httpClient.newCall(request).execute()
                
                response.use { resp ->
                    val responseBody = resp.body?.string()
                    
                    if (resp.isSuccessful && responseBody != null) {
                        Log.d(TAG, "✅ Whisper API response received")
                        
                        // Parse JSON response
                        val jsonResponse = JSONObject(responseBody)
                        val transcript = jsonResponse.optString("text", "")
                        
                        if (transcript.isNotBlank()) {
                            Log.d(TAG, "🎯 Transcription: '$transcript'")
                            withContext(Dispatchers.Main) {
                                callback.onTranscriptionSuccess(transcript)
                            }
                        } else {
                            Log.w(TAG, "⚠️ Empty transcript received")
                            withContext(Dispatchers.Main) {
                                callback.onTranscriptionError("Empty transcript received")
                            }
                        }
                    } else {
                        val errorMsg = "HTTP ${resp.code}: ${responseBody ?: "Unknown error"}"
                        Log.e(TAG, "❌ Whisper API error: $errorMsg")
                        withContext(Dispatchers.Main) {
                            callback.onTranscriptionError(errorMsg)
                        }
                    }
                }
                
            } catch (e: Exception) {
                val errorMsg = "Transcription failed: ${e.message}"
                Log.e(TAG, "❌ Exception: $errorMsg", e)
                withContext(Dispatchers.Main) {
                    callback.onTranscriptionError(errorMsg)
                }
            }
        }
    }
    
    /**
     * Validate API key format
     */
    fun isValidApiKey(apiKey: String?): Boolean {
        return !apiKey.isNullOrBlank() && apiKey.startsWith("sk-")
    }
}