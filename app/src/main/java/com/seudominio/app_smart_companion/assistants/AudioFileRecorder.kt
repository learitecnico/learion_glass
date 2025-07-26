package com.seudominio.app_smart_companion.assistants

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Audio File Recorder para OpenAI Assistants API
 * Captura áudio do M400 e salva como arquivo .wav para upload
 * Baseado no VuzixAudioProcessor existente
 */
class AudioFileRecorder(
    private val context: Context,
    private val onRecordingComplete: (File) -> Unit,
    private val onError: (String) -> Unit,
    private val onStatusUpdate: (String) -> Unit
) {
    companion object {
        private const val TAG = "AudioFileRecorder"
        
        // Audio settings optimized for M400 and OpenAI
        private const val SAMPLE_RATE = 24000 // OpenAI preferred
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BITS_PER_SAMPLE = 16
        private const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8
        
        // Recording settings
        private const val MAX_RECORDING_DURATION_MS = 30000 // 30 seconds max
        private const val MIN_RECORDING_DURATION_MS = 1000  // 1 second min
    }
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var recordingStartTime = 0L
    
    /**
     * Start recording audio to file
     */
    fun startRecording(): Boolean {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return false
        }
        
        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            ) * 2 // Double buffer for safety
            
            // Try different audio sources for better emulator compatibility
            val audioSource = if (isEmulator()) {
                MediaRecorder.AudioSource.MIC // Better for emulator
            } else {
                MediaRecorder.AudioSource.VOICE_RECOGNITION // Optimal for M400
            }
            
            audioRecord = AudioRecord(
                audioSource,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onError("Failed to initialize AudioRecord")
                return false
            }
            
            audioRecord?.startRecording()
            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            
            Log.d(TAG, "🎤 Started recording audio (24kHz, PCM16, Mono)")
            onStatusUpdate("🎤 Recording... (max 30s)")
            
            // Start recording in background
            scope.launch {
                recordToFile()
            }
            
            // Auto-stop after max duration
            scope.launch {
                delay(MAX_RECORDING_DURATION_MS.toLong())
                if (isRecording) {
                    Log.d(TAG, "⏰ Auto-stopping recording after 30 seconds")
                    stopRecording()
                }
            }
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting recording", e)
            onError("Failed to start recording: ${e.message}")
            return false
        }
    }
    
    /**
     * Stop recording and save file
     */
    fun stopRecording() {
        if (!isRecording) {
            Log.w(TAG, "Not currently recording")
            return
        }
        
        val recordingDuration = System.currentTimeMillis() - recordingStartTime
        
        if (recordingDuration < MIN_RECORDING_DURATION_MS) {
            Log.w(TAG, "Recording too short: ${recordingDuration}ms")
            onError("Recording too short. Please record for at least 1 second.")
            return
        }
        
        isRecording = false
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            Log.d(TAG, "🛑 Recording stopped after ${recordingDuration}ms")
            onStatusUpdate("Processing audio file...")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping recording", e)
            onError("Error stopping recording: ${e.message}")
        }
    }
    
    /**
     * Record audio data to WAV file
     */
    private suspend fun recordToFile() = withContext(Dispatchers.IO) {
        try {
            // Create temporary WAV file
            val audioFile = File(context.cacheDir, "assistant_audio_${System.currentTimeMillis()}.wav")
            val outputStream = FileOutputStream(audioFile)
            
            // Write WAV header (will update later with correct size)
            writeWavHeader(outputStream, 0)
            
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val buffer = ByteArray(bufferSize)
            var totalBytesWritten = 0
            var silentSamples = 0
            var totalSamples = 0
            
            Log.d(TAG, "📝 Writing audio data to: ${audioFile.name}")
            Log.d(TAG, "🎛️ Audio buffer size: $bufferSize bytes")
            
            while (isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                
                if (bytesRead > 0) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesWritten += bytesRead
                    
                    // Analyze audio level for debugging
                    var maxAmplitude = 0
                    for (i in 0 until bytesRead step 2) {
                        if (i + 1 < bytesRead) {
                            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF))
                            val amplitude = kotlin.math.abs(sample)
                            if (amplitude > maxAmplitude) maxAmplitude = amplitude
                            totalSamples++
                            if (amplitude < 100) silentSamples++ // Threshold for "silence"
                        }
                    }
                    
                    // Log audio level every second - with stricter control
                    if (isRecording && totalBytesWritten % (bufferSize * 10) == 0) {
                        val durationSec = (System.currentTimeMillis() - recordingStartTime) / 1000
                        val silencePercent = if (totalSamples > 0) (silentSamples * 100 / totalSamples) else 100
                        Log.d(TAG, "🎤 Audio level - Max: $maxAmplitude, Silence: $silencePercent%")
                        
                        // Only send status update every 5 seconds to reduce HUD spam
                        synchronized(this) {
                            if (isRecording && durationSec % 5 == 0L) {
                                onStatusUpdate("🎤 Recording... ${durationSec}s (max 30s)")
                            }
                        }
                    }
                } else if (bytesRead < 0) {
                    Log.e(TAG, "❌ AudioRecord read error: $bytesRead")
                    break
                }
            }
            
            // Final audio analysis
            val silencePercent = if (totalSamples > 0) (silentSamples * 100 / totalSamples) else 100
            Log.d(TAG, "📊 Final audio analysis: ${totalBytesWritten} bytes, ${silencePercent}% silence")
            
            outputStream.close()
            
            // Update WAV header with correct file size
            updateWavHeader(audioFile, totalBytesWritten)
            
            val finalDuration = (System.currentTimeMillis() - recordingStartTime) / 1000
            Log.d(TAG, "✅ Audio file created: ${audioFile.name} (${totalBytesWritten} bytes, ${finalDuration}s)")
            
            // Validate audio quality before sending
            if (silencePercent > 95) {
                Log.w(TAG, "⚠️ Audio appears to be ${silencePercent}% silence - may be emulator issue")
                
                if (isEmulator()) {
                    Log.d(TAG, "📝 Emulator detected - generating test audio for demonstration")
                    generateTestAudio(audioFile)
                    onStatusUpdate("🧪 Generated test audio for emulator")
                } else {
                    onStatusUpdate("⚠️ Audio mostly silent - check microphone")
                }
            } else {
                onStatusUpdate("Audio file ready for upload")
            }
            
            onRecordingComplete(audioFile)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error recording to file", e)
            onError("Error creating audio file: ${e.message}")
        }
    }
    
    /**
     * Write WAV file header
     */
    private fun writeWavHeader(outputStream: FileOutputStream, dataSize: Int) {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        
        // RIFF header
        header.put("RIFF".toByteArray())
        header.putInt(36 + dataSize) // File size - 8
        header.put("WAVE".toByteArray())
        
        // Format chunk
        header.put("fmt ".toByteArray())
        header.putInt(16) // Chunk size (PCM)
        header.putShort(1) // Audio format (PCM)
        header.putShort(1) // Number of channels (Mono)
        header.putInt(SAMPLE_RATE) // Sample rate
        header.putInt(SAMPLE_RATE * BYTES_PER_SAMPLE) // Byte rate
        header.putShort(BYTES_PER_SAMPLE.toShort()) // Block align
        header.putShort(BITS_PER_SAMPLE.toShort()) // Bits per sample
        
        // Data chunk
        header.put("data".toByteArray())
        header.putInt(dataSize) // Data size
        
        outputStream.write(header.array())
    }
    
    /**
     * Update WAV header with correct file size
     */
    private fun updateWavHeader(file: File, dataSize: Int) {
        try {
            val randomAccessFile = java.io.RandomAccessFile(file, "rw")
            
            // Update file size at position 4
            randomAccessFile.seek(4)
            randomAccessFile.writeInt(Integer.reverseBytes(36 + dataSize))
            
            // Update data chunk size at position 40
            randomAccessFile.seek(40)
            randomAccessFile.writeInt(Integer.reverseBytes(dataSize))
            
            randomAccessFile.close()
            
            Log.d(TAG, "✅ WAV header updated with size: $dataSize bytes")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating WAV header", e)
        }
    }
    
    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean = isRecording
    
    /**
     * Get recording duration in seconds
     */
    fun getRecordingDuration(): Int {
        return if (isRecording) {
            ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
        } else {
            0
        }
    }
    
    /**
     * Generate test audio for emulator demonstration
     */
    private fun generateTestAudio(audioFile: File) {
        try {
            val outputStream = FileOutputStream(audioFile)
            
            // Generate 3 seconds of test audio (speech-like pattern)
            val durationSamples = SAMPLE_RATE * 3 // 3 seconds
            val audioData = ByteArray(durationSamples * 2) // 16-bit PCM
            
            // Generate more recognizable test speech pattern
            for (i in 0 until durationSamples) {
                val time = i.toDouble() / SAMPLE_RATE
                
                // Create different "words" with pauses (more speech-like)
                val wordPhase = (time * 2) % 3.0 // 3 words over 3 seconds
                var sample = 0.0
                
                when {
                    wordPhase < 0.8 -> {
                        // Word 1: "Hello" - emphasized low frequency
                        val freq = 300 + Math.sin(time * 10) * 50
                        sample = Math.sin(2 * Math.PI * freq * time) * 0.8
                    }
                    wordPhase < 1.0 -> {
                        // Pause between words
                        sample = 0.0
                    }
                    wordPhase < 1.8 -> {
                        // Word 2: "Assistant" - mid frequency
                        val freq = 500 + Math.sin(time * 15) * 100
                        sample = Math.sin(2 * Math.PI * freq * time) * 0.7
                    }
                    wordPhase < 2.0 -> {
                        // Pause between words
                        sample = 0.0
                    }
                    else -> {
                        // Word 3: "Test" - higher frequency
                        val freq = 800 + Math.sin(time * 8) * 150
                        sample = Math.sin(2 * Math.PI * freq * time) * 0.6
                    }
                }
                
                // Add subtle noise to make it more speech-like
                val noise = (Math.random() - 0.5) * 0.1
                sample += noise
                
                val finalSample = (sample * 8000).toInt().coerceIn(-32767, 32767)
                
                // Convert to 16-bit little-endian
                audioData[i * 2] = (finalSample and 0xFF).toByte()
                audioData[i * 2 + 1] = ((finalSample shr 8) and 0xFF).toByte()
            }
            
            // Write WAV header
            writeWavHeader(outputStream, audioData.size)
            outputStream.write(audioData)
            outputStream.close()
            
            Log.d(TAG, "🧪 Generated test audio: 3s speech-like pattern for Whisper")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error generating test audio", e)
        }
    }
    
    /**
     * Check if running on emulator
     */
    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
                "google_sdk" == Build.PRODUCT)
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        if (isRecording) {
            stopRecording()
        }
        scope.cancel()
        Log.d(TAG, "🧹 AudioFileRecorder cleaned up")
    }
}