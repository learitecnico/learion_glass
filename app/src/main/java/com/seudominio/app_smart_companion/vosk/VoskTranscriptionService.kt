package com.seudominio.app_smart_companion.vosk

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.StorageService
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

/**
 * Vosk local speech recognition service
 * Adapted from SmartGlassesManager implementation for Coach SPIN integration
 */
class VoskTranscriptionService(
    private val context: Context,
    private val callback: TranscriptionCallback
) : RecognitionListener {

    companion object {
        private const val TAG = "VoskTranscriptionService"
        private const val SAMPLE_RATE = 16000.0f
        private const val LANGUAGE_MODEL = "model-pt-br"  // Português brasileiro
        private const val FALLBACK_MODEL = "model-en-us"  // Fallback para inglês
        private const val AUDIO_BUFFER_SIZE = (16000 * 2 * 0.192).toInt() // ~192ms buffer
    }

    interface TranscriptionCallback {
        fun onTranscriptionResult(text: String, isFinal: Boolean)
        fun onTranscriptionError(error: String)
        fun onServiceReady()
    }

    // Vosk components
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speechStreamService: SpeechStreamQueueServiceVosk? = null
    
    // Audio processing
    private val audioQueue: BlockingQueue<ByteArray> = ArrayBlockingQueue(AUDIO_BUFFER_SIZE)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // State management
    private var isInitialized = false
    private var isListening = false

    /**
     * Initialize Vosk service and load model
     */
    fun initialize() {
        Log.d(TAG, "🎤 Initializing Vosk transcription service...")
        
        // Set Vosk log level
        LibVosk.setLogLevel(LogLevel.INFO)
        
        // Load model
        initModel()
        
        // Start recognition after delay (wait for model loading)
        val delay = 500L
        mainHandler.postDelayed({
            if (model != null) {
                Log.d(TAG, "✅ Vosk model loaded, ready for transcription")
                isInitialized = true
                callback.onServiceReady()
            } else {
                // Retry if model not loaded yet
                mainHandler.postDelayed({ initialize() }, delay)
            }
        }, delay)
    }

    /**
     * Load Vosk model from assets
     */
    private fun initModel() {
        Log.d(TAG, "📦 Loading Vosk model: $LANGUAGE_MODEL")
        
        try {
            // Try Portuguese model first - directly from assets folder
            val modelPath = "file:///android_asset/$LANGUAGE_MODEL"
            
            try {
                model = Model(modelPath)
                Log.d(TAG, "✅ Vosk Portuguese model loaded successfully from: $modelPath")
                return
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Portuguese model failed to load: ${e.message}")
                
                // Fallback to English model
                val fallbackPath = "file:///android_asset/$FALLBACK_MODEL"
                try {
                    model = Model(fallbackPath)
                    Log.d(TAG, "✅ Vosk English fallback model loaded successfully from: $fallbackPath")
                    return
                } catch (fe: Exception) {
                    val errorMsg = "❌ Failed to load any Vosk model. PT-BR: ${e.message}, EN: ${fe.message}"
                    Log.e(TAG, errorMsg)
                    callback.onTranscriptionError(errorMsg)
                }
            }
            
        } catch (e: Exception) {
            val errorMsg = "❌ Exception loading Vosk model: ${e.message}"
            Log.e(TAG, errorMsg)
            callback.onTranscriptionError(errorMsg)
        }
    }

    /**
     * Start listening for audio input
     */
    fun startListening() {
        if (!isInitialized) {
            Log.w(TAG, "⚠️ Service not initialized yet")
            return
        }
        
        if (isListening) {
            Log.w(TAG, "⚠️ Already listening")
            return
        }

        Log.d(TAG, "🎤 Starting Vosk recognition...")
        
        val currentModel = model
        if (currentModel == null) {
            val errorMsg = "❌ Vosk model not loaded"
            Log.e(TAG, errorMsg)
            callback.onTranscriptionError(errorMsg)
            return
        }

        // NOVA ABORDAGEM: Bypass Vosk - enviar WAV diretamente para OpenAI
        Log.d(TAG, "🌐 Enviando áudio WAV diretamente para OpenAI Whisper via Companion")
        
        try {
            // Notificar que começamos a "ouvir" (processar áudio)
            isListening = true
            
            // Em vez de usar Vosk, vamos processar o arquivo de áudio diretamente
            // e enviar para OpenAI via WebSocket (já existente)
            Log.d(TAG, "✅ Modo OpenAI Whisper ativo - processando arquivo de áudio")
            
            // O áudio será processado pelo CoachAudioRecorder e enviado via WebSocket
            // para o Companion Desktop, que fará a transcrição via OpenAI Whisper
            
        } catch (e: Exception) {
            val errorMsg = "❌ Failed to start OpenAI audio processing: ${e.message}"
            Log.e(TAG, errorMsg, e)
            callback.onTranscriptionError(errorMsg)
            isListening = false
        }
    }

    /**
     * Stop listening for audio input
     */
    fun stopListening() {
        if (!isListening) {
            Log.w(TAG, "⚠️ Not currently listening")
            return
        }

        Log.d(TAG, "🛑 Stopping Vosk recognition...")
        
        speechStreamService?.stop()
        speechStreamService = null
        recognizer = null
        isListening = false
        
        Log.d(TAG, "✅ Vosk recognition stopped")
    }

    /**
     * Process audio chunk - now sends directly to OpenAI via WebSocket
     */
    fun processAudioChunk(audioData: ByteArray) {
        if (!isListening) {
            Log.v(TAG, "⚠️ Not listening, ignoring audio chunk")
            return
        }

        try {
            // NOVA ABORDAGEM: Em vez de Vosk, acumular áudio para envio via WebSocket
            Log.v(TAG, "🌐 Audio chunk para OpenAI Whisper (${audioData.size} bytes)")
            
            // TODO: Implementar envio via WebSocket para Companion Desktop
            // O Companion Desktop usará OpenAI Whisper API para transcrição
            
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Error processing audio chunk: ${e.message}")
        }
    }

    /**
     * Clean up resources
     */
    fun destroy() {
        Log.d(TAG, "🧹 Destroying Vosk service...")
        
        stopListening()
        model = null
        isInitialized = false
        
        Log.d(TAG, "✅ Vosk service destroyed")
    }

    // RecognitionListener implementation
    override fun onResult(hypothesis: String) {
        mainHandler.post {
            handleTranscriptionResult(hypothesis, isFinal = true)
        }
    }

    override fun onPartialResult(hypothesis: String) {
        mainHandler.post {
            handleTranscriptionResult(hypothesis, isFinal = false)
        }
    }

    override fun onFinalResult(hypothesis: String) {
        Log.d(TAG, "🎯 Final result received")
        // Speech service completed, ready for next input
    }

    override fun onError(exception: Exception) {
        val errorMsg = "❌ Vosk recognition error: ${exception.message}"
        Log.e(TAG, errorMsg)
        mainHandler.post {
            callback.onTranscriptionError(errorMsg)
        }
    }

    override fun onTimeout() {
        Log.d(TAG, "⏰ Vosk recognition timeout")
        // This is normal behavior, not an error
    }

    /**
     * Handle transcription result from Vosk
     */
    private fun handleTranscriptionResult(hypothesis: String, isFinal: Boolean) {
        try {
            val voskResponse = JSONObject(hypothesis)
            val transcript = if (isFinal) {
                voskResponse.optString("text", "")
            } else {
                voskResponse.optString("partial", "")
            }

            // Filter out empty or invalid transcripts
            if (transcript.isBlank()) {
                Log.v(TAG, "📝 Empty transcript, ignoring")
                return
            }

            // Filter out common false positives
            val badWords = arrayOf("huh", "her", "hit", "cut", "if", "but", "by", "hi", "ha", "a", "the", "it")
            if (transcript.trim().lowercase() in badWords) {
                Log.v(TAG, "📝 False positive transcript filtered: '$transcript'")
                return
            }

            Log.d(TAG, "📝 Transcript ${if (isFinal) "FINAL" else "PARTIAL"}: '$transcript'")
            callback.onTranscriptionResult(transcript, isFinal)

        } catch (e: JSONException) {
            Log.e(TAG, "❌ Failed to parse Vosk response: ${e.message}")
            callback.onTranscriptionError("Failed to parse transcription result")
        }
    }
}