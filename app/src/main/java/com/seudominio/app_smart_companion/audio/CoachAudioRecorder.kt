package com.seudominio.app_smart_companion.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Audio recorder for Coach SPIN with Vosk integration
 * Records audio at 16kHz for optimal Vosk transcription
 */
class CoachAudioRecorder(private val context: Context) {
    
    companion object {
        private const val TAG = "CoachAudioRecorder"
        
        // Audio configuration for Vosk (16kHz, Mono, PCM16)
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        // Buffer size for 20ms chunks (recommended for real-time processing)
        private const val BUFFER_SIZE_MS = 20
        private const val BUFFER_SIZE_SAMPLES = SAMPLE_RATE * BUFFER_SIZE_MS / 1000
        private const val BUFFER_SIZE_BYTES = BUFFER_SIZE_SAMPLES * 2 // 16-bit = 2 bytes per sample
        
        // Voice detection thresholds
        private const val SILENCE_THRESHOLD = 500 // Amplitude threshold for silence
        private const val SILENCE_DURATION_MS = 1500 // Stop after 1.5s of silence
        private const val MAX_RECORDING_MS = 30000 // Max 30 seconds recording
        
        // Emulator detection
        fun isEmulator(): Boolean {
            return (android.os.Build.FINGERPRINT.contains("generic")
                    || android.os.Build.FINGERPRINT.contains("unknown")
                    || android.os.Build.MODEL.contains("google_sdk")
                    || android.os.Build.MODEL.contains("Emulator")
                    || android.os.Build.MODEL.contains("Android SDK")
                    || android.os.Build.MANUFACTURER.contains("Genymotion")
                    || android.os.Build.HARDWARE == "goldfish"
                    || android.os.Build.HARDWARE == "ranchu"
                    || android.os.Build.HARDWARE == "vbox86"
                    || android.os.Build.PRODUCT.contains("sdk"))
        }
    }
    
    interface AudioRecordingCallback {
        fun onRecordingStarted()
        fun onAudioData(audioData: ByteArray)
        fun onAmplitudeUpdate(amplitude: Int)
        fun onRecordingCompleted()
        fun onRecordingError(error: String)
    }
    
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val isRecording = AtomicBoolean(false)
    
    // Recording state
    private var recordingStartTime = 0L
    private var lastVoiceTime = 0L
    private val audioBuffer = ByteArray(BUFFER_SIZE_BYTES)
    
    /**
     * Check if recording permission is granted
     */
    fun hasRecordingPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Start recording audio with automatic voice detection
     */
    fun startRecording(callback: AudioRecordingCallback) {
        if (!hasRecordingPermission()) {
            callback.onRecordingError("Recording permission not granted")
            return
        }
        
        if (isRecording.get()) {
            Log.w(TAG, "Already recording")
            return
        }
        
        // Check if running on emulator and use test mode
        if (isEmulator()) {
            Log.w(TAG, "🎮 Emulator detected - using test mode audio")
            startEmulatorTestMode(callback)
            return
        }
        
        try {
            // Initialize AudioRecord
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            
            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                callback.onRecordingError("Failed to get min buffer size")
                return
            }
            
            val bufferSize = maxOf(minBufferSize, BUFFER_SIZE_BYTES * 2)
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, // Optimized for speech
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                callback.onRecordingError("Failed to initialize AudioRecord")
                audioRecord?.release()
                audioRecord = null
                return
            }
            
            // Start recording
            audioRecord?.startRecording()
            isRecording.set(true)
            recordingStartTime = System.currentTimeMillis()
            lastVoiceTime = recordingStartTime
            
            callback.onRecordingStarted()
            Log.d(TAG, "🎤 Recording started at ${SAMPLE_RATE}Hz")
            
            // Start recording coroutine
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                recordingLoop(callback)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            callback.onRecordingError("Failed to start recording: ${e.message}")
            cleanup()
        }
    }
    
    /**
     * Stop recording manually
     */
    fun stopRecording() {
        if (!isRecording.get()) {
            Log.w(TAG, "Not recording")
            return
        }
        
        Log.d(TAG, "🛑 Stopping recording...")
        isRecording.set(false)
        
        recordingJob?.cancel()
        recordingJob = null
        
        cleanup()
    }
    
    /**
     * Main recording loop
     */
    private suspend fun recordingLoop(callback: AudioRecordingCallback) {
        try {
            while (isRecording.get()) {
                val bytesRead = audioRecord?.read(audioBuffer, 0, BUFFER_SIZE_BYTES) ?: 0
                
                if (bytesRead > 0) {
                    // Calculate amplitude for voice detection
                    val amplitude = calculateAmplitude(audioBuffer, bytesRead)
                    
                    // Send amplitude update
                    withContext(Dispatchers.Main) {
                        callback.onAmplitudeUpdate(amplitude)
                    }
                    
                    // Voice activity detection
                    if (amplitude > SILENCE_THRESHOLD) {
                        lastVoiceTime = System.currentTimeMillis()
                        
                        // Send audio data for transcription
                        val audioData = audioBuffer.copyOf(bytesRead)
                        withContext(Dispatchers.Main) {
                            callback.onAudioData(audioData)
                        }
                    }
                    
                    // Check for silence timeout
                    val currentTime = System.currentTimeMillis()
                    val silenceDuration = currentTime - lastVoiceTime
                    val totalDuration = currentTime - recordingStartTime
                    
                    if (silenceDuration > SILENCE_DURATION_MS) {
                        Log.d(TAG, "🔇 Silence detected for ${silenceDuration}ms, stopping")
                        break
                    }
                    
                    if (totalDuration > MAX_RECORDING_MS) {
                        Log.d(TAG, "⏰ Max recording time reached, stopping")
                        break
                    }
                    
                } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                    throw Exception("Invalid operation")
                } else if (bytesRead == AudioRecord.ERROR_BAD_VALUE) {
                    throw Exception("Bad value")
                }
                
                // Small delay to prevent CPU overload
                delay(10)
            }
            
            // Recording completed
            withContext(Dispatchers.Main) {
                callback.onRecordingCompleted()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Recording error", e)
            withContext(Dispatchers.Main) {
                callback.onRecordingError("Recording error: ${e.message}")
            }
        } finally {
            cleanup()
        }
    }
    
    /**
     * Calculate amplitude from audio buffer
     */
    private fun calculateAmplitude(buffer: ByteArray, length: Int): Int {
        var sum = 0L
        var samples = 0
        
        // Process 16-bit samples
        for (i in 0 until length step 2) {
            if (i + 1 < length) {
                val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
                sum += abs(sample)
                samples++
            }
        }
        
        return if (samples > 0) (sum / samples).toInt() else 0
    }
    
    /**
     * Clean up resources
     */
    private fun cleanup() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            isRecording.set(false)
            Log.d(TAG, "✅ Audio recorder cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
    
    /**
     * Test mode for emulator - uses real audio file if available, otherwise simulates
     */
    private fun startEmulatorTestMode(callback: AudioRecordingCallback) {
        Log.d(TAG, "🎮 Starting emulator test mode...")
        
        // Check for test audio files
        val testAudioFile = findTestAudioFile()
        if (testAudioFile != null) {
            Log.d(TAG, "🎵 Found test audio file: ${testAudioFile.name}")
            startAudioFileTest(testAudioFile, callback)
        } else {
            Log.d(TAG, "🎭 No test audio file found, using simulation")
            startSimulatedAudioTest(callback)
        }
    }
    
    /**
     * Look for test audio files in assets or external storage
     */
    private fun findTestAudioFile(): File? {
        // Priority order for test files
        val testFileNames = listOf(
            "teste_audio.wav",
            "test_audio.wav",
            "coach_test.wav", 
            "sales_call.wav",
            "vosk_test.wav"
        )
        
        // First, check if teste_audio.wav exists in assets
        val assetsFile = copyAssetToTempFile("teste_audio.wav")
        if (assetsFile != null) {
            Log.d(TAG, "✅ Found test audio in assets: teste_audio.wav")
            return assetsFile
        }
        
        // Fallback to test_audio.wav
        val assetsFile2 = copyAssetToTempFile("test_audio.wav")
        if (assetsFile2 != null) {
            Log.d(TAG, "✅ Found test audio in assets: test_audio.wav")
            return assetsFile2
        }
        
        // Also try .opus format (will need conversion)
        val opusFile = copyAssetToTempFile("test_audio.opus")
        if (opusFile != null) {
            Log.d(TAG, "✅ Found test audio in assets: test_audio.opus (attempting conversion)")
            
            // Try to convert to WAV format
            val converter = AudioConverter(context)
            val convertedFile = converter.convertToVoskFormat(opusFile)
            
            if (convertedFile != null) {
                Log.d(TAG, "✅ Successfully converted OPUS to WAV: ${convertedFile.name}")
                return convertedFile
            } else {
                Log.w(TAG, "⚠️ OPUS conversion failed, will try raw processing")
                return opusFile
            }
        }
        
        // Check in app's external files directory
        val externalDir = context.getExternalFilesDir(null)
        if (externalDir != null) {
            for (fileName in testFileNames) {
                val file = File(externalDir, fileName)
                if (file.exists()) {
                    Log.d(TAG, "✅ Found test audio in external files: ${file.absolutePath}")
                    return file
                }
            }
        }
        
        // Check in Downloads folder (easy to add files)
        val downloadsDir = File(context.getExternalFilesDir(null), "Downloads")
        for (fileName in testFileNames) {
            val file = File(downloadsDir, fileName)
            if (file.exists()) {
                Log.d(TAG, "✅ Found test audio in Downloads: ${file.absolutePath}")
                return file
            }
        }
        
        Log.d(TAG, "📁 No test audio files found. Checked:")
        Log.d(TAG, "   - assets/test_audio.wav")
        Log.d(TAG, "   - ${externalDir?.absolutePath}")
        Log.d(TAG, "   - ${downloadsDir.absolutePath}")
        Log.d(TAG, "   Looking for: ${testFileNames.joinToString(", ")}")
        
        return null
    }
    
    /**
     * Copy asset file to temporary file for processing
     */
    private fun copyAssetToTempFile(assetFileName: String): File? {
        return try {
            val inputStream = context.assets.open(assetFileName)
            val tempFile = File(context.cacheDir, assetFileName)
            
            tempFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close()
            
            Log.d(TAG, "📄 Copied asset to temp file: ${tempFile.absolutePath}")
            tempFile
        } catch (e: Exception) {
            Log.d(TAG, "📄 Asset $assetFileName not found or could not be copied")
            null
        }
    }
    
    /**
     * Test using real audio file
     */
    private fun startAudioFileTest(audioFile: File, callback: AudioRecordingCallback) {
        isRecording.set(true)
        
        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val processor = AudioFileProcessor(context)
                val success = processor.processAudioFile(audioFile, callback)
                
                if (!success) {
                    Log.w(TAG, "⚠️ Audio file processing failed, falling back to simulation")
                    startSimulatedAudioTest(callback)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in audio file test", e)
                withContext(Dispatchers.Main) {
                    callback.onRecordingError("Audio file error: ${e.message}")
                }
            } finally {
                isRecording.set(false)
            }
        }
    }
    
    /**
     * Fallback simulation mode
     */
    private fun startSimulatedAudioTest(callback: AudioRecordingCallback) {
        if (!isRecording.get()) {
            isRecording.set(true)
            callback.onRecordingStarted()
        }
        
        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Simulate recording for 3 seconds with fake audio levels
                val testDuration = 3000L
                val updateInterval = 100L
                var elapsed = 0L
                
                Log.d(TAG, "🎭 Running simulated audio test...")
                
                // Simulate audio level updates
                while (elapsed < testDuration && isRecording.get()) {
                    // Generate fake audio data (silence with some noise)
                    val fakeAudioData = ByteArray(BUFFER_SIZE_BYTES) { index ->
                        // Generate low-level noise
                        ((Math.random() * 10 - 5).toInt()).toByte()
                    }
                    
                    // Simulate varying amplitude
                    val fakeAmplitude = when {
                        elapsed < 500 -> 200  // Starting silence
                        elapsed < 2500 -> (500 + Math.random() * 1000).toInt()  // Speaking
                        else -> 100  // Ending silence
                    }
                    
                    withContext(Dispatchers.Main) {
                        callback.onAmplitudeUpdate(fakeAmplitude)
                        callback.onAudioData(fakeAudioData)
                    }
                    
                    delay(updateInterval)
                    elapsed += updateInterval
                }
                
                delay(500) // Small delay before completion
                
                withContext(Dispatchers.Main) {
                    callback.onRecordingCompleted()
                }
                
                Log.d(TAG, "🎭 Simulated audio test completed")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in simulated test", e)
                withContext(Dispatchers.Main) {
                    callback.onRecordingError("Simulation error: ${e.message}")
                }
            } finally {
                isRecording.set(false)
            }
        }
    }
}