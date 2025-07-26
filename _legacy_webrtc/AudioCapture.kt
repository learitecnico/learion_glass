package com.seudominio.app_smart_companion.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

/**
 * AudioCapture integrado com WebRTC
 * Captura áudio 16kHz mono com buffer de 20ms conforme especificação
 */
class AudioCapture(
    private val context: Context
) {
    companion object {
        private const val TAG = "AudioCapture"
        private const val SAMPLE_RATE = 16000 // 16kHz
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_MS = 20 // 20ms buffer
    }
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    
    // Callback para enviar dados de áudio processados
    var onAudioDataCaptured: ((ByteArray) -> Unit)? = null
    
    private val bufferSize: Int by lazy {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        
        // Calculate buffer size for 20ms of audio
        val samplesIn20Ms = (SAMPLE_RATE * BUFFER_SIZE_MS) / 1000
        val bytesIn20Ms = samplesIn20Ms * 2 // 16-bit = 2 bytes per sample
        
        maxOf(minBufferSize, bytesIn20Ms)
    }
    
    fun startCapture(scope: CoroutineScope): Boolean {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return false
        }
        
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            ).apply {
                if (state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord failed to initialize")
                    return false
                }
            }
            
            isRecording = true
            audioRecord?.startRecording()
            
            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(bufferSize)
                
                while (isRecording) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    
                    if (bytesRead > 0) {
                        // Process audio data here
                        // In a real implementation, you would feed this to the WebRTC audio track
                        processAudioData(buffer, bytesRead)
                    }
                }
            }
            
            Log.d(TAG, "Audio capture started")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio capture", e)
            stopCapture()
            return false
        }
    }
    
    fun stopCapture() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        
        audioRecord?.apply {
            try {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping audio record", e)
            }
        }
        audioRecord = null
        
        Log.d(TAG, "Audio capture stopped")
    }
    
    private fun processAudioData(buffer: ByteArray, bytesRead: Int) {
        // Processa dados de áudio 16kHz mono PCM16
        if (bytesRead > 0) {
            // Cria array com o tamanho exato dos dados lidos
            val audioData = ByteArray(bytesRead)
            System.arraycopy(buffer, 0, audioData, 0, bytesRead)
            
            // Envia dados processados via callback
            onAudioDataCaptured?.invoke(audioData)
            
            Log.v(TAG, "Processed $bytesRead bytes of 16kHz mono audio")
        }
    }
    
    fun isRecording(): Boolean = isRecording
}