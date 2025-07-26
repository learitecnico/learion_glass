package com.seudominio.app_smart_companion.audio

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlin.math.*

/**
 * Audio processor para melhorar qualidade antes de enviar para OpenAI
 * Implementa processamento similar ao que WebRTC fazia automaticamente
 */
class AudioProcessor {
    companion object {
        private const val TAG = "LearionGlass"
        
        // Audio processing parameters
        private const val TARGET_RMS = 0.1f  // Target RMS level for normalization
        private const val SILENCE_THRESHOLD = 0.01f  // Below this is considered silence
        private const val GAIN_ATTACK = 0.95f  // AGC attack rate
        private const val GAIN_RELEASE = 0.995f  // AGC release rate
    }
    
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null
    
    private var currentGain = 1.0f
    
    /**
     * Initialize audio effects for the given audio session
     */
    fun initializeEffects(audioSessionId: Int) {
        try {
            // Echo cancellation
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)
                echoCanceler?.enabled = true
                Log.d(TAG, "✅ Echo canceler enabled")
            }
            
            // Noise suppression
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)
                noiseSuppressor?.enabled = true
                Log.d(TAG, "✅ Noise suppressor enabled")
            }
            
            // Automatic Gain Control
            if (AutomaticGainControl.isAvailable()) {
                agc = AutomaticGainControl.create(audioSessionId)
                agc?.enabled = true
                Log.d(TAG, "✅ Automatic gain control enabled")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio effects", e)
        }
    }
    
    /**
     * Process audio frame to improve quality
     * Similar to WebRTC audio processing pipeline
     */
    fun processAudioFrame(audioData: ByteArray): ByteArray {
        // Convert byte array to float array for processing
        val samples = byteArrayToFloatArray(audioData)
        
        // Apply processing chain
        var processed = samples
        processed = removeSDCSilence(processed)
        processed = applyHighPassFilter(processed)
        processed = normalizeAudio(processed)
        processed = applyDynamicRangeCompression(processed)
        
        // Convert back to byte array
        return floatArrayToByteArray(processed)
    }
    
    /**
     * Remove DC offset and very low frequency components
     */
    private fun removeSDCSilence(samples: FloatArray): FloatArray {
        val result = FloatArray(samples.size)
        var dcOffset = 0f
        
        // Calculate DC offset
        samples.forEach { dcOffset += it }
        dcOffset /= samples.size
        
        // Remove DC offset
        for (i in samples.indices) {
            result[i] = samples[i] - dcOffset
        }
        
        return result
    }
    
    /**
     * Apply high-pass filter to remove low frequency noise
     * Cutoff around 80Hz (human voice fundamentals start ~85Hz)
     */
    private fun applyHighPassFilter(samples: FloatArray): FloatArray {
        val result = FloatArray(samples.size)
        var previousSample = 0f
        var previousResult = 0f
        
        // Simple first-order high-pass filter
        val alpha = 0.95f  // Adjust for cutoff frequency
        
        for (i in samples.indices) {
            result[i] = alpha * (previousResult + samples[i] - previousSample)
            previousSample = samples[i]
            previousResult = result[i]
        }
        
        return result
    }
    
    /**
     * Normalize audio to consistent level
     */
    private fun normalizeAudio(samples: FloatArray): FloatArray {
        // Calculate RMS
        var sumSquares = 0.0
        samples.forEach { sumSquares += it * it }
        val rms = sqrt(sumSquares / samples.size).toFloat()
        
        // Skip if silence
        if (rms < SILENCE_THRESHOLD) {
            return samples
        }
        
        // Calculate gain needed
        val targetGain = TARGET_RMS / rms
        
        // Apply smoothed gain (AGC)
        val result = FloatArray(samples.size)
        for (i in samples.indices) {
            // Smooth gain changes
            currentGain = if (targetGain > currentGain) {
                currentGain * GAIN_ATTACK + targetGain * (1 - GAIN_ATTACK)
            } else {
                currentGain * GAIN_RELEASE + targetGain * (1 - GAIN_RELEASE)
            }
            
            // Apply gain with limiting
            result[i] = (samples[i] * currentGain).coerceIn(-1f, 1f)
        }
        
        return result
    }
    
    /**
     * Apply dynamic range compression for consistent volume
     */
    private fun applyDynamicRangeCompression(samples: FloatArray): FloatArray {
        val threshold = 0.7f
        val ratio = 4f  // 4:1 compression
        val makeupGain = 1.2f
        
        val result = FloatArray(samples.size)
        
        for (i in samples.indices) {
            val sample = samples[i]
            val absSample = abs(sample)
            
            if (absSample > threshold) {
                // Compress signals above threshold
                val excess = absSample - threshold
                val compressedExcess = excess / ratio
                val compressedSample = threshold + compressedExcess
                result[i] = sample.sign * compressedSample * makeupGain
            } else {
                result[i] = sample * makeupGain
            }
            
            // Final limiting
            result[i] = result[i].coerceIn(-1f, 1f)
        }
        
        return result
    }
    
    /**
     * Convert byte array (PCM16) to float array for processing
     */
    private fun byteArrayToFloatArray(bytes: ByteArray): FloatArray {
        val shorts = ShortArray(bytes.size / 2)
        val floats = FloatArray(shorts.size)
        
        // Convert bytes to shorts
        for (i in shorts.indices) {
            val low = bytes[i * 2].toInt() and 0xFF
            val high = bytes[i * 2 + 1].toInt() shl 8
            shorts[i] = (high or low).toShort()
        }
        
        // Convert shorts to normalized floats
        for (i in shorts.indices) {
            floats[i] = shorts[i] / 32768f
        }
        
        return floats
    }
    
    /**
     * Convert float array back to byte array (PCM16)
     */
    private fun floatArrayToByteArray(floats: FloatArray): ByteArray {
        val bytes = ByteArray(floats.size * 2)
        
        for (i in floats.indices) {
            // Convert float to short
            val sample = (floats[i] * 32767f).toInt().toShort()
            
            // Convert short to bytes (little-endian)
            bytes[i * 2] = (sample.toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = (sample.toInt() shr 8).toByte()
        }
        
        return bytes
    }
    
    /**
     * Release audio effects
     */
    fun release() {
        echoCanceler?.release()
        noiseSuppressor?.release()
        agc?.release()
        
        echoCanceler = null
        noiseSuppressor = null
        agc = null
    }
}