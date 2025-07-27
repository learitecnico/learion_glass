package com.seudominio.app_smart_companion.vosk

import android.util.Log
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import java.util.concurrent.BlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Speech stream service for processing audio chunks through Vosk
 * Adapted from SmartGlassesManager implementation
 */
class SpeechStreamQueueServiceVosk(
    private val recognizer: Recognizer,
    private val audioQueue: BlockingQueue<ByteArray>,
    private val sampleRate: Float
) {
    companion object {
        private const val TAG = "SpeechStreamQueueServiceVosk"
    }

    private var listener: RecognitionListener? = null
    private var processingThread: Thread? = null
    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    /**
     * Start the speech stream service
     */
    fun start(listener: RecognitionListener) {
        if (isRunning.get()) {
            Log.w(TAG, "⚠️ Service already running")
            return
        }

        this.listener = listener
        isRunning.set(true)
        isPaused.set(false)

        Log.d(TAG, "🚀 Starting speech stream service")

        // Start audio processing thread
        processingThread = Thread {
            processAudioStream()
        }.apply {
            name = "VoskAudioProcessor"
            start()
        }
    }

    /**
     * Stop the speech stream service
     */
    fun stop() {
        if (!isRunning.get()) {
            Log.w(TAG, "⚠️ Service not running")
            return
        }

        Log.d(TAG, "🛑 Stopping speech stream service")
        
        isRunning.set(false)
        
        // Interrupt processing thread
        processingThread?.interrupt()
        
        try {
            processingThread?.join(1000) // Wait up to 1 second
        } catch (e: InterruptedException) {
            Log.w(TAG, "⚠️ Thread join interrupted")
        }
        
        processingThread = null
        listener = null
        
        // Clear the audio queue
        audioQueue.clear()
        
        Log.d(TAG, "✅ Speech stream service stopped")
    }

    /**
     * Pause/resume processing
     */
    fun setPause(paused: Boolean) {
        isPaused.set(paused)
        Log.d(TAG, "⏸️ Processing ${if (paused) "paused" else "resumed"}")
    }

    /**
     * Main audio processing loop
     */
    private fun processAudioStream() {
        Log.d(TAG, "🎵 Audio processing thread started")
        
        try {
            while (isRunning.get() && !Thread.currentThread().isInterrupted) {
                if (isPaused.get()) {
                    Thread.sleep(100)
                    continue
                }

                try {
                    // Take audio chunk from queue (blocking)
                    val audioChunk = audioQueue.take()
                    
                    if (audioChunk.isNotEmpty()) {
                        processAudioChunk(audioChunk)
                    }
                    
                } catch (e: InterruptedException) {
                    Log.d(TAG, "🛑 Audio processing interrupted")
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Audio processing error: ${e.message}")
            listener?.onError(e)
        }
        
        Log.d(TAG, "✅ Audio processing thread finished")
    }

    /**
     * Process individual audio chunk through Vosk
     */
    private fun processAudioChunk(audioChunk: ByteArray) {
        try {
            Log.v(TAG, "🎤 Processing audio chunk: ${audioChunk.size} bytes")
            
            // Accept audio chunk into Vosk recognizer
            val isEndOfUtterance = recognizer.acceptWaveForm(audioChunk, audioChunk.size)
            
            if (isEndOfUtterance) {
                // End of utterance detected - get final result
                val finalResult = recognizer.finalResult
                Log.d(TAG, "🎯 End of utterance - final result: $finalResult")
                listener?.onResult(finalResult)
            } else {
                // Partial result
                val partialResult = recognizer.partialResult
                if (partialResult.isNotEmpty()) {
                    Log.v(TAG, "📝 Partial result: $partialResult")
                    listener?.onPartialResult(partialResult)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing audio chunk: ${e.message}")
            listener?.onError(e)
        }
    }

    /**
     * Get current processing status
     */
    fun isRunning(): Boolean = isRunning.get()
    
    fun isPaused(): Boolean = isPaused.get()
}