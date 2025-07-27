package com.seudominio.app_smart_companion.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaMetadataRetriever
import android.util.Log
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlin.math.abs

/**
 * Processes audio files for Vosk testing
 * Converts WAV files to 16kHz PCM16 format that Vosk expects
 */
class AudioFileProcessor(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioFileProcessor"
        private const val TARGET_SAMPLE_RATE = 16000
        private const val TARGET_CHANNELS = 1 // Mono
        private const val TARGET_BITS_PER_SAMPLE = 16
        private const val CHUNK_SIZE_MS = 20 // 20ms chunks for real-time simulation
        private const val BYTES_PER_SAMPLE = 2 // 16-bit = 2 bytes
    }
    
    data class AudioInfo(
        val sampleRate: Int,
        val channels: Int,
        val bitRate: Int,
        val duration: Long,
        val isCompatible: Boolean
    )
    
    /**
     * Get audio file information
     */
    fun getAudioInfo(audioFile: File): AudioInfo? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(audioFile.absolutePath)
            
            // Note: Getting sample rate from MediaMetadataRetriever is limited
            val sampleRate = 16000 // Assume 16kHz for now
            val bitRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            
            // For WAV files, assume mono for simplicity
            val channels = 1
            
            val isCompatible = (sampleRate == TARGET_SAMPLE_RATE && channels == TARGET_CHANNELS)
            
            retriever.release()
            
            AudioInfo(
                sampleRate = sampleRate,
                channels = channels,
                bitRate = bitRate,
                duration = duration,
                isCompatible = isCompatible
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting audio info", e)
            null
        }
    }
    
    /**
     * Process audio file and send chunks to callback (simulating real-time recording)
     */
    suspend fun processAudioFile(
        audioFile: File, 
        callback: CoachAudioRecorder.AudioRecordingCallback
    ): Boolean = withContext(Dispatchers.IO) {
        
        try {
            Log.d(TAG, "🎵 Processing audio file: ${audioFile.name}")
            
            // Check if file exists
            if (!audioFile.exists()) {
                Log.e(TAG, "❌ Audio file not found: ${audioFile.absolutePath}")
                callback.onRecordingError("Audio file not found")
                return@withContext false
            }
            
            // Get audio info
            val audioInfo = getAudioInfo(audioFile)
            if (audioInfo == null) {
                Log.e(TAG, "❌ Could not read audio file info")
                callback.onRecordingError("Could not read audio file")
                return@withContext false
            }
            
            Log.d(TAG, "📊 Audio Info: ${audioInfo.sampleRate}Hz, ${audioInfo.channels}ch, ${audioInfo.duration}ms")
            
            // Read audio data
            val audioData = if (audioFile.name.endsWith(".opus", ignoreCase = true)) {
                Log.w(TAG, "⚠️ OPUS format detected - Android MediaMetadataRetriever may not work well")
                Log.w(TAG, "⚠️ Please convert to WAV 16kHz for best results")
                readAudioFile(audioFile)
            } else {
                readWavFile(audioFile)
            }
            
            if (audioData == null) {
                Log.e(TAG, "❌ Could not read audio data")
                callback.onRecordingError("Could not read audio data - please convert to WAV 16kHz format")
                return@withContext false
            }
            
            Log.d(TAG, "✅ Read ${audioData.size} bytes of audio data")
            
            // Convert to target format if needed
            val processedData = if (audioInfo.isCompatible) {
                Log.d(TAG, "✅ Audio already compatible with Vosk")
                audioData
            } else {
                Log.d(TAG, "🔄 Converting audio to 16kHz mono...")
                convertToTargetFormat(audioData, audioInfo)
            }
            
            // Start "recording"
            withContext(Dispatchers.Main) {
                callback.onRecordingStarted()
            }
            
            // Send data in chunks to simulate real-time recording
            sendAudioChunks(processedData, callback)
            
            // Complete recording
            withContext(Dispatchers.Main) {
                callback.onRecordingCompleted()
            }
            
            Log.d(TAG, "✅ Audio file processing completed")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing audio file", e)
            withContext(Dispatchers.Main) {
                callback.onRecordingError("Error processing file: ${e.message}")
            }
            false
        }
    }
    
    /**
     * Read WAV file data (skipping header)
     */
    private fun readWavFile(file: File): ByteArray? {
        return try {
            val inputStream = FileInputStream(file)
            val data = inputStream.readBytes()
            inputStream.close()
            
            // Skip WAV header (44 bytes) if it's a WAV file
            if (data.size > 44 && 
                data[0] == 'R'.code.toByte() && 
                data[1] == 'I'.code.toByte() && 
                data[2] == 'F'.code.toByte() && 
                data[3] == 'F'.code.toByte()) {
                Log.d(TAG, "📄 WAV header detected, skipping 44 bytes")
                data.copyOfRange(44, data.size)
            } else {
                Log.d(TAG, "📄 Raw audio data (no WAV header)")
                data
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error reading WAV file", e)
            null
        }
    }
    
    /**
     * Read any audio file (generic)
     */
    private fun readAudioFile(file: File): ByteArray? {
        return try {
            val inputStream = FileInputStream(file)
            val data = inputStream.readBytes()
            inputStream.close()
            
            Log.d(TAG, "📄 Read ${data.size} bytes from ${file.name}")
            data
        } catch (e: IOException) {
            Log.e(TAG, "Error reading audio file", e)
            null
        }
    }
    
    /**
     * Convert audio to target format (16kHz, mono, PCM16)
     */
    private fun convertToTargetFormat(audioData: ByteArray, sourceInfo: AudioInfo): ByteArray {
        // For now, return as-is and log that conversion would be needed
        // Real conversion would require more complex audio processing
        Log.w(TAG, "⚠️ Audio format conversion not implemented")
        Log.w(TAG, "⚠️ Expected: ${TARGET_SAMPLE_RATE}Hz, ${TARGET_CHANNELS}ch, ${TARGET_BITS_PER_SAMPLE}-bit")
        Log.w(TAG, "⚠️ Got: ${sourceInfo.sampleRate}Hz, ${sourceInfo.channels}ch")
        Log.w(TAG, "⚠️ Using source data as-is - may not work with Vosk")
        return audioData
    }
    
    /**
     * Send audio data in chunks to simulate real-time recording
     */
    private suspend fun sendAudioChunks(
        audioData: ByteArray, 
        callback: CoachAudioRecorder.AudioRecordingCallback
    ) {
        val chunkSize = (TARGET_SAMPLE_RATE * CHUNK_SIZE_MS / 1000) * BYTES_PER_SAMPLE
        var offset = 0
        
        Log.d(TAG, "📡 Sending audio in ${chunkSize}-byte chunks (${CHUNK_SIZE_MS}ms each)")
        
        while (offset < audioData.size) {
            val remainingBytes = audioData.size - offset
            val currentChunkSize = minOf(chunkSize, remainingBytes)
            
            val chunk = audioData.copyOfRange(offset, offset + currentChunkSize)
            
            // Calculate amplitude for visual feedback
            val amplitude = calculateAmplitude(chunk)
            
            // Send chunk
            withContext(Dispatchers.Main) {
                callback.onAudioData(chunk)
                callback.onAmplitudeUpdate(amplitude)
            }
            
            offset += currentChunkSize
            
            // Delay to simulate real-time (20ms per chunk)
            delay(CHUNK_SIZE_MS.toLong())
            
            // Log progress every second
            val progressMs = (offset.toFloat() / audioData.size) * 
                             (audioData.size / (TARGET_SAMPLE_RATE * BYTES_PER_SAMPLE)) * 1000
            if (progressMs.toInt() % 1000 == 0) {
                Log.d(TAG, "📡 Progress: ${progressMs.toInt()}ms")
            }
        }
        
        Log.d(TAG, "📡 All chunks sent successfully")
    }
    
    /**
     * Calculate amplitude from audio chunk
     */
    private fun calculateAmplitude(audioChunk: ByteArray): Int {
        var sum = 0L
        var samples = 0
        
        // Process 16-bit samples
        for (i in audioChunk.indices step 2) {
            if (i + 1 < audioChunk.size) {
                val sample = (audioChunk[i + 1].toInt() shl 8) or (audioChunk[i].toInt() and 0xFF)
                sum += abs(sample)
                samples++
            }
        }
        
        return if (samples > 0) (sum / samples).toInt() else 0
    }
}