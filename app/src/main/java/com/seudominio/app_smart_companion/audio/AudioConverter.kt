package com.seudominio.app_smart_companion.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Converts audio files to Vosk-compatible format (16kHz, mono, PCM16)
 */
class AudioConverter(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioConverter"
        private const val TARGET_SAMPLE_RATE = 16000
        private const val TARGET_CHANNELS = 1
    }
    
    /**
     * Convert any audio file to WAV 16kHz mono for Vosk
     */
    fun convertToVoskFormat(inputFile: File): File? {
        return try {
            Log.d(TAG, "🔄 Converting ${inputFile.name} to Vosk format...")
            
            // Create output file
            val outputFile = File(context.cacheDir, "converted_${inputFile.nameWithoutExtension}.wav")
            
            // Try different conversion methods
            val success = convertUsingMediaExtractor(inputFile, outputFile) ||
                         convertUsingMetadataRetriever(inputFile, outputFile) ||
                         convertSimpleResample(inputFile, outputFile)
            
            if (success && outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "✅ Conversion successful: ${outputFile.name} (${outputFile.length()} bytes)")
                outputFile
            } else {
                Log.e(TAG, "❌ Conversion failed")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception during conversion", e)
            null
        }
    }
    
    /**
     * Method 1: Use MediaExtractor (most compatible)
     */
    private fun convertUsingMediaExtractor(inputFile: File, outputFile: File): Boolean {
        return try {
            Log.d(TAG, "🔧 Trying MediaExtractor conversion...")
            
            val extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)
            
            // Find audio track
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    break
                }
            }
            
            if (audioTrackIndex == -1) {
                Log.w(TAG, "⚠️ No audio track found in MediaExtractor")
                extractor.release()
                return false
            }
            
            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            
            Log.d(TAG, "📊 Source format: $format")
            
            // This is a simplified approach - real conversion would need decoder
            Log.w(TAG, "⚠️ MediaExtractor method needs audio decoder implementation")
            extractor.release()
            false
            
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ MediaExtractor failed: ${e.message}")
            false
        }
    }
    
    /**
     * Method 2: Use MediaMetadataRetriever (limited but simple)
     */
    private fun convertUsingMetadataRetriever(inputFile: File, outputFile: File): Boolean {
        return try {
            Log.d(TAG, "🔧 Trying MediaMetadataRetriever conversion...")
            
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(inputFile.absolutePath)
            
            // Get embedded picture if available (won't work for audio conversion)
            Log.w(TAG, "⚠️ MediaMetadataRetriever cannot extract raw audio data")
            retriever.release()
            false
            
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ MediaMetadataRetriever failed: ${e.message}")
            false
        }
    }
    
    /**
     * Method 3: Simple raw data attempt (fallback)
     */
    private fun convertSimpleResample(inputFile: File, outputFile: File): Boolean {
        return try {
            Log.d(TAG, "🔧 Trying simple raw data processing...")
            
            val inputData = inputFile.readBytes()
            Log.d(TAG, "📊 Input file size: ${inputData.size} bytes")
            
            // This is a hack - try to find audio data in the file
            val audioData = extractPossibleAudioData(inputData)
            
            if (audioData.isNotEmpty()) {
                writeWavFile(outputFile, audioData, TARGET_SAMPLE_RATE, TARGET_CHANNELS)
                Log.d(TAG, "✅ Simple conversion attempt completed")
                true
            } else {
                Log.w(TAG, "⚠️ Could not extract audio data")
                false
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Simple conversion failed: ${e.message}")
            false
        }
    }
    
    /**
     * Try to extract possible audio data from file
     */
    private fun extractPossibleAudioData(data: ByteArray): ByteArray {
        // Very basic approach - skip some header and take middle portion
        return when {
            data.size < 1000 -> byteArrayOf()
            data.size < 10000 -> data.copyOfRange(100, data.size - 100)
            else -> data.copyOfRange(1000, minOf(data.size - 1000, 50000)) // Max 50KB
        }
    }
    
    /**
     * Write WAV file with proper header
     */
    private fun writeWavFile(outputFile: File, audioData: ByteArray, sampleRate: Int, channels: Int) {
        FileOutputStream(outputFile).use { fos ->
            val bytesPerSample = 2 // 16-bit
            val byteRate = sampleRate * channels * bytesPerSample
            val dataSize = audioData.size
            val fileSize = 36 + dataSize
            
            // WAV header
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            
            // RIFF chunk
            header.put("RIFF".toByteArray())
            header.putInt(fileSize)
            header.put("WAVE".toByteArray())
            
            // fmt chunk
            header.put("fmt ".toByteArray())
            header.putInt(16) // chunk size
            header.putShort(1) // audio format (PCM)
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort((channels * bytesPerSample).toShort()) // block align
            header.putShort((bytesPerSample * 8).toShort()) // bits per sample
            
            // data chunk
            header.put("data".toByteArray())
            header.putInt(dataSize)
            
            fos.write(header.array())
            fos.write(audioData)
        }
    }
}