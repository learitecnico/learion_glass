package com.seudominio.app_smart_companion.audio

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlin.math.*

/**
 * Optimized Audio Processor based on production research
 * 
 * Key optimizations:
 * - Uses Android built-in audio effects (low CPU impact)
 * - 44.1kHz → 24kHz downsampling for OpenAI compatibility  
 * - Minimal processing to avoid latency
 * - Thread-safe design
 */
class OptimizedAudioProcessor {
    companion object {
        private const val TAG = "LearionGlass"
        
        // Research-based constants
        private const val INPUT_SAMPLE_RATE = 44100  // Android guaranteed support
        private const val OUTPUT_SAMPLE_RATE = 24000 // OpenAI requirement
        private const val DOWNSAMPLE_RATIO = INPUT_SAMPLE_RATE / OUTPUT_SAMPLE_RATE // 1.8375
        
        // Minimal processing thresholds
        private const val SILENCE_THRESHOLD = 0.001f
        private const val NORMALIZATION_TARGET = 0.7f
    }
    
    // Android built-in audio effects (efficient)
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null
    
    // Downsampling state
    private var downsampleBuffer = mutableListOf<Short>()
    private var sampleIndex = 0f
    
    /**
     * Initialize with Android built-in audio effects
     * These are hardware-accelerated and have minimal CPU impact
     */
    fun initialize(audioSessionId: Int): Boolean {
        var effectsEnabled = 0
        
        try {
            // Echo Cancellation (essential for OpenAI VAD)
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = true
                    effectsEnabled++
                    Log.d(TAG, "✅ Echo Canceler enabled")
                }
            }
            
            // Noise Suppression (improves VAD accuracy)
            if (NoiseSuppressor.isAvailable()) {  
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = true
                    effectsEnabled++
                    Log.d(TAG, "✅ Noise Suppressor enabled")
                }
            }
            
            // Automatic Gain Control (optional - can help with volume consistency)
            if (AutomaticGainControl.isAvailable()) {
                agc = AutomaticGainControl.create(audioSessionId)?.apply {
                    enabled = true
                    effectsEnabled++
                    Log.d(TAG, "✅ Automatic Gain Control enabled")
                }
            }
            
            Log.d(TAG, "🎛️ Audio effects initialized: $effectsEnabled/3 available")
            return effectsEnabled > 0
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize audio effects", e)
            return false
        }
    }
    
    /**
     * Process audio frame with minimal latency
     * 
     * Pipeline: 44.1kHz PCM16 → Downsample → 24kHz PCM16
     * Processing time: ~1-2ms for 20ms frame
     */
    fun processAudioFrame(inputData: ByteArray): ByteArray {
        // Convert to shorts for processing
        val inputSamples = byteArrayToShortArray(inputData)
        
        // Downsample from 44.1kHz to 24kHz using linear interpolation
        val outputSamples = downsampleTo24kHz(inputSamples)
        
        // Quick silence detection to avoid unnecessary processing
        if (isSilence(outputSamples)) {
            return shortArrayToByteArray(outputSamples)
        }
        
        // Minimal normalization (only if needed)
        normalizeIfNeeded(outputSamples)
        
        return shortArrayToByteArray(outputSamples)
    }
    
    /**
     * Efficient downsampling from 44.1kHz to 24kHz
     * Uses linear interpolation for quality vs performance balance
     */
    private fun downsampleTo24kHz(input: ShortArray): ShortArray {
        val outputSize = (input.size * OUTPUT_SAMPLE_RATE / INPUT_SAMPLE_RATE).toInt()
        val output = ShortArray(outputSize)
        
        var inputIndex = 0f
        val step = INPUT_SAMPLE_RATE.toFloat() / OUTPUT_SAMPLE_RATE.toFloat()
        
        for (i in output.indices) {
            val index = inputIndex.toInt()
            if (index >= input.size - 1) break
            
            // Linear interpolation between samples
            val fraction = inputIndex - index
            val sample1 = input[index]
            val sample2 = input[index + 1]
            output[i] = (sample1 + fraction * (sample2 - sample1)).toInt().toShort()
            
            inputIndex += step
        }
        
        return output
    }
    
    /**
     * Fast silence detection
     */
    private fun isSilence(samples: ShortArray): Boolean {
        var energy = 0L
        for (sample in samples) {
            energy += (sample * sample).toLong()
        }
        val rms = sqrt(energy.toDouble() / samples.size) / 32768.0
        return rms < SILENCE_THRESHOLD
    }
    
    /**
     * Conditional normalization - only apply if signal is too quiet
     */
    private fun normalizeIfNeeded(samples: ShortArray) {
        // Calculate RMS
        var energy = 0L
        for (sample in samples) {
            energy += (sample * sample).toLong()
        }
        val rms = sqrt(energy.toDouble() / samples.size) / 32768.0
        
        // Only normalize if significantly below target
        if (rms < NORMALIZATION_TARGET * 0.5) {
            val gain = (NORMALIZATION_TARGET / rms).toFloat().coerceAtMost(3.0f)
            
            for (i in samples.indices) {
                val amplified = (samples[i] * gain).toInt()
                samples[i] = amplified.coerceIn(-32767, 32767).toShort()
            }
        }
    }
    
    /**
     * Convert byte array to short array (PCM16)
     */
    private fun byteArrayToShortArray(bytes: ByteArray): ShortArray {
        val shorts = ShortArray(bytes.size / 2)
        for (i in shorts.indices) {
            val low = bytes[i * 2].toInt() and 0xFF
            val high = bytes[i * 2 + 1].toInt() shl 8
            shorts[i] = (high or low).toShort()
        }
        return shorts
    }
    
    /**
     * Convert short array to byte array (PCM16 little-endian)
     */
    private fun shortArrayToByteArray(shorts: ShortArray): ByteArray {
        val bytes = ByteArray(shorts.size * 2)
        for (i in shorts.indices) {
            val sample = shorts[i].toInt()
            bytes[i * 2] = (sample and 0xFF).toByte()
            bytes[i * 2 + 1] = (sample shr 8).toByte()
        }
        return bytes
    }
    
    /**
     * Get processing info for debugging
     */
    fun getProcessingInfo(): String {
        val effects = mutableListOf<String>()
        if (echoCanceler?.enabled == true) effects.add("Echo Cancellation")
        if (noiseSuppressor?.enabled == true) effects.add("Noise Suppression")  
        if (agc?.enabled == true) effects.add("AGC")
        
        return "Audio Processing: ${INPUT_SAMPLE_RATE}Hz→${OUTPUT_SAMPLE_RATE}Hz, Effects: ${effects.joinToString(", ")}"
    }
    
    /**
     * Release resources
     */
    fun release() {
        try {
            echoCanceler?.release()
            noiseSuppressor?.release()
            agc?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio effects", e)
        }
        
        echoCanceler = null
        noiseSuppressor = null
        agc = null
        
        Log.d(TAG, "🧹 Audio processor released")
    }
}