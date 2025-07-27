package com.seudominio.app_smart_companion.assistants

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import com.seudominio.app_smart_companion.camera.CameraCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * AssistantPhotoManager - Sistema modular para captura de foto → análise Vision → resposta Assistant
 * 
 * Pipeline: M400 Camera → GPT-4 Vision → OpenAI Assistant → HUD Display
 * 
 * Características:
 * - Reutilizável para qualquer OpenAI Assistant
 * - Nova thread a cada captura (conforme solicitado)
 * - Otimizado para M400 (720p, compressão 200KB)
 * - Callbacks padronizados
 */
class AssistantPhotoManager(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val assistantId: String,
    private val apiKey: String
) {
    companion object {
        private const val TAG = "AssistantPhotoManager"
        private const val VISION_API_URL = "https://api.openai.com/v1/chat/completions"
        private const val MAX_VISION_TOKENS = 300
        private const val IMAGE_DETAIL_LEVEL = "low" // Otimizado para M400
    }
    
    private var cameraCapture: CameraCapture? = null
    
    interface PhotoToAssistantCallback {
        fun onCaptureStarted()
        fun onPhotoTaken()
        fun onVisionAnalysisStarted() 
        fun onAssistantProcessingStarted()
        fun onAssistantResponse(response: String)
        fun onError(error: String)
    }
    
    /**
     * Inicia pipeline completo: Foto → Vision → Assistant
     * 
     * @param visionPrompt Prompt para análise da imagem
     * @param assistantPrompt Prompt adicional para o assistant (opcional)
     * @param callback Callback para feedback do processo
     */
    fun startPhotoToAssistant(
        visionPrompt: String,
        assistantPrompt: String = "",
        callback: PhotoToAssistantCallback
    ) {
        Log.d(TAG, "🚀 startPhotoToAssistant() CALLED - Iniciando pipeline Photo → Vision → Assistant")
        Log.d(TAG, "🚀 Vision prompt: $visionPrompt")
        Log.d(TAG, "🚀 Assistant prompt: $assistantPrompt")
        
        lifecycleScope.launch {
            try {
                // 1. Inicializar câmera
                callback.onCaptureStarted()
                initializeCamera()
                
                // 2. Capturar foto
                capturePhoto { imageBytes ->
                    lifecycleScope.launch {
                        try {
                            callback.onPhotoTaken()
                            Log.d(TAG, "📷 Foto capturada: ${imageBytes.size} bytes")
                            
                            // 3. Analisar com Vision
                            callback.onVisionAnalysisStarted()
                            val visionAnalysis = analyzeWithVision(imageBytes, visionPrompt)
                            Log.d(TAG, "👁️ Análise Vision: $visionAnalysis")
                            
                            // 4. Enviar para Assistant
                            callback.onAssistantProcessingStarted()
                            val finalPrompt = if (assistantPrompt.isNotEmpty()) {
                                "$assistantPrompt\n\nAnálise da imagem: $visionAnalysis"
                            } else {
                                visionAnalysis
                            }
                            
                            val assistantResponse = sendToAssistant(finalPrompt)
                            callback.onAssistantResponse(assistantResponse)
                            
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Erro no pipeline: ${e.message}", e)
                            callback.onError("Erro no processamento: ${e.message}")
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na inicialização: ${e.message}", e)
                callback.onError("Erro na captura: ${e.message}")
            }
        }
    }
    
    /**
     * Inicializa câmera M400
     */
    private suspend fun initializeCamera() = withContext(Dispatchers.Main) {
        Log.d(TAG, "📸 Inicializando câmera M400...")
        
        cameraCapture?.dispose()
        cameraCapture = CameraCapture(context).apply {
            initialize()
        }
        
        // Aguarda inicialização
        kotlinx.coroutines.delay(1000)
        Log.d(TAG, "✅ Câmera M400 inicializada")
    }
    
    /**
     * Captura foto usando CameraCapture otimizada
     */
    private fun capturePhoto(callback: (ByteArray) -> Unit) {
        Log.d(TAG, "📷 Capturando foto...")
        
        cameraCapture?.takePicture { imageBytes ->
            Log.d(TAG, "✅ Foto capturada e comprimida: ${imageBytes.size} bytes")
            callback(imageBytes)
        } ?: run {
            Log.e(TAG, "❌ CameraCapture não inicializada")
            throw IllegalStateException("Câmera não inicializada")
        }
    }
    
    /**
     * Analisa imagem usando OpenAI GPT-4 Vision
     */
    private suspend fun analyzeWithVision(imageBytes: ByteArray, prompt: String): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "👁️ Enviando para GPT-4 Vision...")
        
        try {
            // Base64 encode para Vision API
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            Log.d(TAG, "🔢 Imagem codificada em Base64: ${base64Image.length} chars")
            
            // Criar request JSON para Vision API
            val visionRequest = JSONObject().apply {
                put("model", "gpt-4o") // Modelo mais recente com Vision
                put("max_tokens", MAX_VISION_TOKENS)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray().apply {
                            // Text prompt
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", prompt)
                            })
                            // Image
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply {
                                    put("url", "data:image/jpeg;base64,$base64Image")
                                    put("detail", IMAGE_DETAIL_LEVEL) // "low" para M400
                                })
                            })
                        })
                    })
                })
            }
            
            // HTTP Request para Vision API
            val url = URL(VISION_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                doOutput = true
            }
            
            // Enviar request
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(visionRequest.toString())
                writer.flush()
            }
            
            // Ler response
            val responseCode = connection.responseCode
            val responseText = if (responseCode == 200) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                throw Exception("Vision API error $responseCode: $errorText")
            }
            
            // Parse response
            val responseJson = JSONObject(responseText)
            val choices = responseJson.getJSONArray("choices")
            val content = choices.getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            
            Log.d(TAG, "✅ Vision API response: $content")
            return@withContext content
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro Vision API: ${e.message}", e)
            throw Exception("Falha na análise da imagem: ${e.message}")
        }
    }
    
    /**
     * Envia análise para OpenAI Assistant (nova thread)
     * Implementação temporária simplificada
     */
    private suspend fun sendToAssistant(message: String): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "🤖 Enviando para Assistant: $assistantId")
        
        try {
            // Por enquanto, retorna uma resposta simulada para testar o pipeline
            // TODO: Implementar integração completa com OpenAI Assistant
            
            delay(2000) // Simula processamento
            
            val mockResponse = "Análise da imagem recebida. Com base no que vejo, posso fornecer as seguintes recomendações de coaching SPIN..."
            
            Log.d(TAG, "✅ Resposta simulada do Assistant: ${mockResponse.take(50)}...")
            return@withContext mockResponse
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro Assistant API: ${e.message}", e)
            throw Exception("Falha na comunicação com Assistant: ${e.message}")
        }
    }
    
    /**
     * Limpa recursos
     */
    fun dispose() {
        Log.d(TAG, "🧹 Limpando recursos AssistantPhotoManager")
        cameraCapture?.dispose()
        cameraCapture = null
    }
}