package com.seudominio.app_smart_companion.assistants

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAI Assistants API Client para Android
 * Implementa comunicação REST com OpenAI Assistants API v2
 * Suporte a text, audio, images e files
 */
class OpenAIAssistantClient(
    private val apiKey: String,
    private val onResponse: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onStatusUpdate: (String) -> Unit
) {
    companion object {
        private const val TAG = "AssistantClient"
        private const val BASE_URL = "https://api.openai.com/v1"
        private const val DEFAULT_MODEL = "gpt-4o-mini"
        
        // Rate limiting
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Session state
    private var currentThreadId: String? = null
    private var currentAssistantId: String? = null
    
    /**
     * Create a new Assistant with specified configuration
     */
    suspend fun createAssistant(
        name: String,
        instructions: String,
        model: String = DEFAULT_MODEL,
        tools: List<String> = emptyList()
    ): String? = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("name", name)
                put("instructions", instructions)
                put("model", model)
                
                if (tools.isNotEmpty()) {
                    val toolsArray = JSONArray()
                    tools.forEach { tool ->
                        toolsArray.put(JSONObject().apply {
                            put("type", tool)
                        })
                    }
                    put("tools", toolsArray)
                }
            }
            
            val request = buildRequest("$BASE_URL/assistants")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = executeWithRetry(request)
            val responseBody = response?.body?.string()
            
            if (response?.isSuccessful == true && responseBody != null) {
                val jsonResponse = JSONObject(responseBody)
                val assistantId = jsonResponse.getString("id")
                currentAssistantId = assistantId
                Log.d(TAG, "✅ Assistant created: $assistantId")
                onStatusUpdate("Assistant '$name' created successfully")
                assistantId
            } else {
                Log.e(TAG, "❌ Failed to create assistant: ${response?.code} - $responseBody")
                onError("Failed to create assistant: ${response?.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception creating assistant", e)
            onError("Error creating assistant: ${e.message}")
            null
        }
    }
    
    /**
     * Create a new conversation thread
     */
    suspend fun createThread(): String? = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("messages", JSONArray()) // Start with empty messages
            }
            
            val request = buildRequest("$BASE_URL/threads")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = executeWithRetry(request)
            val responseBody = response?.body?.string()
            
            if (response?.isSuccessful == true && responseBody != null) {
                val jsonResponse = JSONObject(responseBody)
                val threadId = jsonResponse.getString("id")
                currentThreadId = threadId
                Log.d(TAG, "✅ Thread created: $threadId")
                onStatusUpdate("New conversation thread created")
                threadId
            } else {
                Log.e(TAG, "❌ Failed to create thread: ${response?.code} - $responseBody")
                onError("Failed to create thread: ${response?.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception creating thread", e)
            onError("Error creating thread: ${e.message}")
            null
        }
    }
    
    /**
     * Send text message to current thread
     */
    suspend fun sendTextMessage(text: String): Boolean = withContext(Dispatchers.IO) {
        val threadId = currentThreadId
        if (threadId == null) {
            onError("No active thread. Create thread first.")
            return@withContext false
        }
        
        try {
            val requestBody = JSONObject().apply {
                put("role", "user")
                put("content", text)
            }
            
            val request = buildRequest("$BASE_URL/threads/$threadId/messages")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))  
                .build()
            
            val response = executeWithRetry(request)
            val responseBody = response?.body?.string()
            
            if (response?.isSuccessful == true) {
                Log.d(TAG, "✅ Text message sent successfully")
                onStatusUpdate("Message sent, processing...")
                true
            } else {
                Log.e(TAG, "❌ Failed to send message: ${response?.code} - $responseBody")
                onError("Failed to send message: ${response?.code}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception sending text message", e)
            onError("Error sending message: ${e.message}")
            false
        }
    }
    
    /**
     * Transcribe audio file using Audio API and send as text message
     * More efficient and faster than file upload approach
     */
    suspend fun sendAudioMessageViaTranascription(audioFile: File): Boolean = withContext(Dispatchers.IO) {
        val threadId = currentThreadId
        if (threadId == null) {
            onError("No active thread. Create thread first.")
            return@withContext false
        }
        
        try {
            // Step 1: Transcribe audio using Audio API
            onStatusUpdate("Transcribing audio...")
            val transcription = transcribeAudio(audioFile)
            if (transcription == null) {
                return@withContext false
            }
            
            Log.d(TAG, "✅ Audio transcribed: ${transcription.take(100)}...")
            onStatusUpdate("Audio transcribed, sending to assistant...")
            
            // Step 2: Send transcribed text as regular message
            val messageSent = sendTextMessage("I said: \"$transcription\"")
            if (messageSent) {
                Log.d(TAG, "✅ Transcribed message sent successfully")
                onStatusUpdate("Message sent, processing...")
                return@withContext true
            } else {
                onError("Failed to send transcribed message")
                return@withContext false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception in audio transcription flow", e)
            onError("Error processing audio message: ${e.message}")
            false
        }
    }
    
    /**
     * Transcribe audio file using OpenAI Audio API
     */
    private suspend fun transcribeAudio(audioFile: File): String? = withContext(Dispatchers.IO) {
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", "whisper-1")
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/wav".toMediaType())
                )
                .build()
            
            val request = buildRequest("$BASE_URL/audio/transcriptions")
                .post(requestBody)
                .build()
            
            val response = executeWithRetry(request)
            val responseBody = response?.body?.string()
            
            if (response?.isSuccessful == true && responseBody != null) {
                val jsonResponse = JSONObject(responseBody)
                val transcription = jsonResponse.getString("text")
                
                Log.d(TAG, "✅ Audio transcribed successfully: ${transcription.take(50)}...")
                transcription
            } else {
                Log.e(TAG, "❌ Failed to transcribe audio: ${response?.code} - $responseBody")
                onError("Failed to transcribe audio: ${response?.code}")
                null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception transcribing audio", e)
            onError("Error transcribing audio: ${e.message}")
            null
        }
    }
    
    /**
     * Upload audio file and send as message (DEPRECATED - use transcription instead)
     */
    @Deprecated("Use sendAudioMessageViaTranascription for better performance and compatibility")
    suspend fun sendAudioMessage(audioFile: File): Boolean = withContext(Dispatchers.IO) {
        val threadId = currentThreadId
        if (threadId == null) {
            onError("No active thread. Create thread first.")
            return@withContext false
        }
        
        try {
            // Step 1: Upload file to OpenAI
            onStatusUpdate("Uploading audio file...")
            val fileId = uploadFile(audioFile, "assistants")
            if (fileId == null) {
                return@withContext false
            }
            
            // Step 2: Create message with file attachment
            val requestBody = JSONObject().apply {
                put("role", "user")
                put("content", "Please analyze this audio message.")
                
                val attachments = JSONArray()
                attachments.put(JSONObject().apply {
                    put("file_id", fileId)
                    put("tools", JSONArray().apply {
                        put(JSONObject().apply { put("type", "file_search") })
                    })
                })
                put("attachments", attachments)
            }
            
            val request = buildRequest("$BASE_URL/threads/$threadId/messages")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = executeWithRetry(request)
            val responseBody = response?.body?.string()
            
            if (response?.isSuccessful == true) {
                Log.d(TAG, "✅ Audio message sent successfully")
                onStatusUpdate("Audio uploaded, processing...")
                true
            } else {
                Log.e(TAG, "❌ Failed to send audio message: ${response?.code} - $responseBody")
                onError("Failed to send audio message: ${response?.code}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception sending audio message", e)
            onError("Error sending audio message: ${e.message}")
            false
        }
    }
    
    /**
     * Upload file to OpenAI Files API
     */
    private suspend fun uploadFile(file: File, purpose: String): String? = withContext(Dispatchers.IO) {
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("purpose", purpose)
                .addFormDataPart(
                    "file", 
                    file.name,
                    file.asRequestBody("audio/*".toMediaType())
                )
                .build()
            
            val request = buildRequest("$BASE_URL/files")
                .post(requestBody)
                .build()
            
            val response = executeWithRetry(request)
            val responseBody = response?.body?.string()
            
            if (response?.isSuccessful == true && responseBody != null) {
                val jsonResponse = JSONObject(responseBody)
                val fileId = jsonResponse.getString("id")
                Log.d(TAG, "✅ File uploaded: $fileId")
                fileId
            } else {
                Log.e(TAG, "❌ Failed to upload file: ${response?.code} - $responseBody")
                onError("Failed to upload file: ${response?.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception uploading file", e)
            onError("Error uploading file: ${e.message}")
            null
        }
    }
    
    /**
     * Execute run to get assistant response
     */
    suspend fun executeRun(): Boolean = withContext(Dispatchers.IO) {
        val threadId = currentThreadId
        val assistantId = currentAssistantId
        
        if (threadId == null || assistantId == null) {
            onError("Missing thread or assistant ID")
            return@withContext false
        }
        
        try {
            // Create run
            val requestBody = JSONObject().apply {
                put("assistant_id", assistantId)
            }
            
            val request = buildRequest("$BASE_URL/threads/$threadId/runs")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = executeWithRetry(request)
            val responseBody = response?.body?.string()
            
            if (response?.isSuccessful == true && responseBody != null) {
                val jsonResponse = JSONObject(responseBody)
                val runId = jsonResponse.getString("id")
                Log.d(TAG, "✅ Run created: $runId")
                
                // Poll run status
                pollRunStatus(threadId, runId)
                true
            } else {
                Log.e(TAG, "❌ Failed to create run: ${response?.code} - $responseBody")
                onError("Failed to execute run: ${response?.code}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception executing run", e)
            onError("Error executing run: ${e.message}")
            false
        }
    }
    
    /**
     * Poll run status until completion
     */
    private suspend fun pollRunStatus(threadId: String, runId: String) {
        try {
            var attempts = 0
            val maxAttempts = 30 // 30 seconds timeout
            
            while (attempts < maxAttempts) {
                delay(1000) // Wait 1 second between polls
                attempts++
                
                val request = buildRequest("$BASE_URL/threads/$threadId/runs/$runId").build()
                val response = executeWithRetry(request)
                val responseBody = response?.body?.string()
                
                if (response?.isSuccessful == true && responseBody != null) {
                    val jsonResponse = JSONObject(responseBody)
                    val status = jsonResponse.getString("status")
                    
                    Log.d(TAG, "🔄 Run status: $status (attempt $attempts)")
                    onStatusUpdate("Processing... ($status)")
                    
                    when (status) {
                        "completed" -> {
                            Log.d(TAG, "✅ Run completed successfully")
                            fetchAssistantResponse(threadId)
                            return
                        }
                        "failed", "cancelled", "expired" -> {
                            val lastError = jsonResponse.optJSONObject("last_error")
                            val errorMessage = lastError?.optString("message") ?: "Unknown error"
                            Log.e(TAG, "❌ Run failed: $errorMessage")
                            onError("Assistant run failed: $errorMessage")
                            return
                        }
                        "in_progress", "queued" -> {
                            // Continue polling
                            continue
                        }
                    }
                } else {
                    Log.w(TAG, "⚠️ Failed to check run status: ${response?.code}")
                }
            }
            
            // Timeout
            Log.w(TAG, "⏰ Run polling timeout after $maxAttempts attempts")
            onError("Assistant response timeout. Please try again.")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception polling run status", e)
            onError("Error checking run status: ${e.message}")
        }
    }
    
    /**
     * Fetch the latest assistant response from thread
     */
    private suspend fun fetchAssistantResponse(threadId: String) {
        try {
            val request = buildRequest("$BASE_URL/threads/$threadId/messages?limit=1").build()
            val response = executeWithRetry(request)
            val responseBody = response?.body?.string()
            
            if (response?.isSuccessful == true && responseBody != null) {
                val jsonResponse = JSONObject(responseBody)
                val messages = jsonResponse.getJSONArray("data")
                
                if (messages.length() > 0) {
                    val latestMessage = messages.getJSONObject(0)
                    val role = latestMessage.getString("role")
                    
                    if (role == "assistant") {
                        val content = latestMessage.getJSONArray("content")
                        if (content.length() > 0) {
                            val firstContent = content.getJSONObject(0)
                            if (firstContent.getString("type") == "text") {
                                val textObj = firstContent.getJSONObject("text")
                                val responseText = textObj.getString("value")
                                
                                Log.d(TAG, "✅ Assistant response received: ${responseText.take(100)}...")
                                onResponse(responseText)
                                onStatusUpdate("Response received")
                                return
                            }
                        }
                    }
                }
            }
            
            Log.w(TAG, "⚠️ No assistant response found")
            onError("No response received from assistant")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception fetching response", e)
            onError("Error fetching response: ${e.message}")
        }
    }
    
    /**
     * Build authenticated request
     */
    private fun buildRequest(url: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("OpenAI-Beta", "assistants=v2")
    }
    
    /**
     * Execute request with retry logic
     */
    private suspend fun executeWithRetry(request: Request): Response? {
        repeat(MAX_RETRIES) { attempt ->
            try {
                val response = client.newCall(request).execute()
                
                // Success or client error (don't retry 4xx)
                if (response.isSuccessful || response.code in 400..499) {
                    return response
                }
                
                // Server error (5xx) - retry
                if (attempt < MAX_RETRIES - 1) {
                    Log.w(TAG, "⚠️ Request failed (${response.code}), retrying in ${RETRY_DELAY_MS}ms...")
                    delay(RETRY_DELAY_MS * (attempt + 1)) // Exponential backoff
                }
                
                response.close()
            } catch (e: IOException) {
                if (attempt < MAX_RETRIES - 1) {
                    Log.w(TAG, "⚠️ Network error, retrying: ${e.message}")
                    delay(RETRY_DELAY_MS * (attempt + 1))
                } else {
                    Log.e(TAG, "❌ Final network error", e)
                    onError("Network error: ${e.message}")
                }
            }
        }
        return null
    }
    
    /**
     * Get current thread ID
     */
    fun getCurrentThreadId(): String? = currentThreadId
    
    /**
     * Get current assistant ID  
     */
    fun getCurrentAssistantId(): String? = currentAssistantId
    
    /**
     * Set assistant ID (for pre-created assistants)
     */
    fun setAssistantId(assistantId: String) {
        currentAssistantId = assistantId
        Log.d(TAG, "🤖 Assistant ID set: $assistantId")
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        scope.cancel()
        Log.d(TAG, "🧹 Assistant client cleaned up")
    }
}