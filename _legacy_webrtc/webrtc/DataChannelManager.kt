package com.seudominio.app_smart_companion.webrtc

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.PeerConnection
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class DataChannelManager(
    private val peerConnection: PeerConnection,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "DataChannelManager"
        private const val DATA_CHANNEL_LABEL = "app_data_channel"
        private const val MAX_IMAGE_SIZE_KB = 200
        private const val CHUNK_SIZE = 16384 // 16KB chunks for large data
    }
    
    private var dataChannel: DataChannel? = null
    private val messageQueue = mutableListOf<String>()
    private var isChannelOpen = false
    
    init {
        createDataChannel()
    }
    
    private fun createDataChannel() {
        Log.i(TAG, "🎯 CREATING DATACHANNEL - this should trigger onRenegotiationNeeded")
        
        val init = DataChannel.Init().apply {
            ordered = true
            negotiated = false
            id = -1
        }
        
        Log.d(TAG, "🎯 About to call peerConnection.createDataChannel()")
        dataChannel = peerConnection.createDataChannel(DATA_CHANNEL_LABEL, init)
        
        Log.i(TAG, "🎯 DATACHANNEL CREATED: ${dataChannel != null}")
        Log.d(TAG, "🎯 DataChannel state: ${dataChannel?.state()}")
        dataChannel?.registerObserver(object : DataChannel.Observer {
            override fun onStateChange() {
                Log.i(TAG, "🎯 DATACHANNEL STATE CHANGED: ${dataChannel?.state()}")
                isChannelOpen = dataChannel?.state() == DataChannel.State.OPEN
                
                if (dataChannel?.state() == DataChannel.State.OPEN) {
                    Log.i(TAG, "🎯 DATACHANNEL OPEN! Ready to send audio data")
                }
                
                if (isChannelOpen) {
                    flushMessageQueue()
                }
            }
            
            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = buffer.data
                val bytes = ByteArray(data.remaining())
                data.get(bytes)
                
                scope.launch(Dispatchers.Main) {
                    handleIncomingMessage(String(bytes))
                }
            }
            
            override fun onBufferedAmountChange(previousAmount: Long) {
                Log.d(TAG, "Buffered amount changed: $previousAmount -> ${dataChannel?.bufferedAmount()}")
            }
        })
    }
    
    fun sendSnapshot(bitmap: Bitmap, quality: Int = 80) {
        scope.launch(Dispatchers.IO) {
            try {
                // Compress bitmap to JPEG
                val outputStream = ByteArrayOutputStream()
                var currentQuality = quality
                var imageBytes: ByteArray
                
                // Reduce quality until image is under 200KB
                do {
                    outputStream.reset()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, outputStream)
                    imageBytes = outputStream.toByteArray()
                    currentQuality -= 10
                } while (imageBytes.size > MAX_IMAGE_SIZE_KB * 1024 && currentQuality > 10)
                
                // Convert to base64
                val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                
                // Create JSON message
                val message = JSONObject().apply {
                    put("type", "snapshot")
                    put("id", "snap-${System.currentTimeMillis()}")
                    put("mime", "image/jpeg")
                    put("data_base64", base64Image)
                    put("ts", System.currentTimeMillis())
                }
                
                sendMessage(message.toString())
                Log.d(TAG, "Snapshot sent: ${imageBytes.size} bytes")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send snapshot", e)
            }
        }
    }
    
    fun sendMessage(message: String) {
        if (isChannelOpen) {
            try {
                val buffer = ByteBuffer.wrap(message.toByteArray())
                val dataBuffer = DataChannel.Buffer(buffer, false)
                dataChannel?.send(dataBuffer)
                Log.v(TAG, "🎯 Message sent via DataChannel (${message.length} chars)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
                messageQueue.add(message)
            }
        } else {
            messageQueue.add(message)
            Log.w(TAG, "🎯 DataChannel NOT OPEN (state: ${dataChannel?.state()}), queuing message")
        }
    }
    
    /**
     * Envia snapshot já comprimido ≤200KB
     */
    fun sendSnapshot(imageBytes: ByteArray) {
        scope.launch(Dispatchers.IO) {
            try {
                val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                
                val message = JSONObject().apply {
                    put("type", "snapshot")
                    put("id", "snap-${System.currentTimeMillis()}")
                    put("mime", "image/jpeg")
                    put("data_base64", base64Image)
                    put("size", imageBytes.size)
                    put("ts", System.currentTimeMillis())
                }
                
                sendMessage(message.toString())
                Log.d(TAG, "Compressed snapshot sent: ${imageBytes.size} bytes")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send compressed snapshot", e)
            }
        }
    }

    fun sendCommand(command: String, params: JSONObject = JSONObject()) {
        val message = JSONObject().apply {
            put("type", "command")
            put("command", command)
            put("params", params)
            put("ts", System.currentTimeMillis())
        }
        
        sendMessage(message.toString())
    }
    
    private fun flushMessageQueue() {
        if (messageQueue.isNotEmpty()) {
            Log.d(TAG, "Flushing ${messageQueue.size} queued messages")
            val messages = messageQueue.toList()
            messageQueue.clear()
            
            messages.forEach { sendMessage(it) }
        }
    }
    
    private fun handleIncomingMessage(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.getString("type")
            
            when (type) {
                "model_text" -> {
                    val text = json.getString("text")
                    Log.d(TAG, "Received model text: $text")
                    // Handle text display on HUD
                }
                "capture_snapshot" -> {
                    val quality = json.optInt("quality", 80)
                    Log.d(TAG, "Snapshot capture requested with quality: $quality")
                    // Trigger camera capture
                }
                else -> {
                    Log.w(TAG, "Unknown message type: $type")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse incoming message", e)
        }
    }
    
    fun dispose() {
        dataChannel?.dispose()
        dataChannel = null
    }
}