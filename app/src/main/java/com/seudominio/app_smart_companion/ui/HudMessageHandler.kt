package com.seudominio.app_smart_companion.ui

import android.util.Log
import com.vuzix.hud.actionmenu.ActionMenuActivity
import org.json.JSONException
import org.json.JSONObject

/**
 * Message handler for WebSocket communication with Companion Desktop
 * Handles real-time text updates and sends confirmation callbacks
 */
class HudMessageHandler(
    private val hudDisplayManager: HudDisplayManager,
    private val context: ActionMenuActivity,
    private val onSendMessage: (JSONObject) -> Unit
) {
    companion object {
        const val TAG = "HudMessageHandler"
        
        // Message types from companion desktop
        const val MSG_TYPE_MODEL_TEXT = "model_text"
        const val MSG_TYPE_STATUS_UPDATE = "status_update"
        const val MSG_TYPE_CLEAR_DISPLAY = "clear_display"
        const val MSG_TYPE_CONNECTION_STATUS = "connection_status"
        
        // Response message types to companion desktop
        const val MSG_TYPE_DISPLAY_CONFIRMED = "display_confirmed"
        const val MSG_TYPE_STATUS_REQUEST = "status_request"
    }
    
    /**
     * Handle incoming WebSocket message from companion desktop
     */
    fun handleMessage(messageJson: String) {
        try {
            val message = JSONObject(messageJson)
            val messageType = message.getString("type")
            
            Log.d(TAG, "Received message type: $messageType")
            
            when (messageType) {
                MSG_TYPE_MODEL_TEXT -> handleModelText(message)
                MSG_TYPE_STATUS_UPDATE -> handleStatusUpdate(message)
                MSG_TYPE_CLEAR_DISPLAY -> handleClearDisplay(message)
                MSG_TYPE_CONNECTION_STATUS -> handleConnectionStatus(message)
                else -> {
                    Log.w(TAG, "Unknown message type: $messageType")
                }
            }
            
        } catch (e: JSONException) {
            Log.e(TAG, "Error parsing message JSON: $messageJson", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message", e)
        }
    }
    
    /**
     * Handle text from OpenAI model (main transcription display)
     */
    private fun handleModelText(message: JSONObject) {
        try {
            val text = message.getString("text")
            val messageId = message.optString("message_id", message.optString("messageId", ""))
            val timestamp = message.optLong("ts", message.optLong("timestamp", System.currentTimeMillis()))
            
            Log.i(TAG, "🔍 DISPLAYING MODEL TEXT on HUD!")
            Log.i(TAG, "🔍 Text: '${text.substring(0, minOf(50, text.length))}...'")
            Log.i(TAG, "🔍 Message ID: '$messageId'")
            Log.i(TAG, "🔍 Timestamp: $timestamp")
            
            // Update HUD display using real TextView
            hudDisplayManager.updateTranscriptionText(text)
            Log.i(TAG, "🔍 HudDisplayManager.updateTranscriptionText() called!")
            
            // Send confirmation back to companion
            confirmDisplayReceived(text, messageId, timestamp)
            
        } catch (e: JSONException) {
            Log.e(TAG, "🚨 Error parsing model_text message", e)
            Log.e(TAG, "🚨 Raw message: $message")
        }
    }
    
    /**
     * Handle status updates (temporary messages)
     */
    private fun handleStatusUpdate(message: JSONObject) {
        try {
            val statusText = message.optString("message", message.optString("text", ""))
            val duration = message.optLong("durationMs", 3000L)
            
            Log.i(TAG, "🔍 SHOWING STATUS UPDATE: $statusText")
            
            hudDisplayManager.showStatusMessage(statusText, duration)
            showHudToast(statusText)
            
        } catch (e: JSONException) {
            Log.e(TAG, "🚨 Error parsing status_update message", e)
            Log.e(TAG, "🚨 Raw message: $message")
        }
    }
    
    /**
     * Handle clear display command
     */
    private fun handleClearDisplay(message: JSONObject) {
        Log.d(TAG, "Clearing HUD display")
        
        hudDisplayManager.clearDisplay()
        showHudToast("Display cleared")
        
        // Send confirmation
        val confirmation = createConfirmationMessage(
            type = "clear_confirmed",
            messageId = message.optString("messageId", ""),
            data = JSONObject().put("action", "display_cleared")
        )
        onSendMessage(confirmation)
    }
    
    /**
     * Handle connection status updates
     */
    private fun handleConnectionStatus(message: JSONObject) {
        try {
            val connected = message.getBoolean("connected")
            val details = message.optString("details", "")
            
            Log.d(TAG, "Connection status update: connected=$connected, details=$details")
            
            hudDisplayManager.updateConnectionStatus(connected)
            
            if (details.isNotEmpty()) {
                showHudToast(details)
            }
            
        } catch (e: JSONException) {
            Log.e(TAG, "Error parsing connection_status message", e)
        }
    }
    
    /**
     * Send confirmation that text was displayed on HUD
     */
    private fun confirmDisplayReceived(displayedText: String, messageId: String, originalTimestamp: Long) {
        val confirmation = createConfirmationMessage(
            type = MSG_TYPE_DISPLAY_CONFIRMED,
            messageId = messageId,
            data = JSONObject().apply {
                put("text_preview", displayedText.substring(0, minOf(30, displayedText.length)) + "...")
                put("display_timestamp", System.currentTimeMillis())
                put("original_timestamp", originalTimestamp)
                put("latency_ms", System.currentTimeMillis() - originalTimestamp)
                put("char_count", displayedText.length)
            }
        )
        
        onSendMessage(confirmation)
        Log.d(TAG, "Display confirmation sent for message: $messageId")
    }
    
    /**
     * Request connection status from companion
     */
    fun requestConnectionStatus() {
        val request = createMessage(
            type = MSG_TYPE_STATUS_REQUEST,
            data = JSONObject().put("requested_at", System.currentTimeMillis())
        )
        
        onSendMessage(request)
        Log.d(TAG, "Connection status requested")
    }
    
    /**
     * Send processing indicator update
     */
    fun sendProcessingStatus(processing: Boolean, details: String = "") {
        hudDisplayManager.showProcessingIndicator(processing, if (processing) "🎤 $details" else "")
        
        val status = createMessage(
            type = "processing_status",
            data = JSONObject().apply {
                put("processing", processing)
                put("details", details)
                put("timestamp", System.currentTimeMillis())
            }
        )
        
        onSendMessage(status)
        Log.d(TAG, "Processing status sent: processing=$processing")
    }
    
    /**
     * Show HUD toast message
     */
    private fun showHudToast(message: String) {
        context.runOnUiThread {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Create confirmation message
     */
    private fun createConfirmationMessage(type: String, messageId: String, data: JSONObject): JSONObject {
        return JSONObject().apply {
            put("type", type)
            put("messageId", messageId)
            put("timestamp", System.currentTimeMillis())
            put("source", "m400_hud")
            put("data", data)
        }
    }
    
    /**
     * Create generic message
     */
    private fun createMessage(type: String, data: JSONObject): JSONObject {
        return JSONObject().apply {
            put("type", type)
            put("messageId", "m400_${System.currentTimeMillis()}_${(0..999).random()}")
            put("timestamp", System.currentTimeMillis())
            put("source", "m400_hud")
            put("data", data)
        }
    }
    
    /**
     * Get current HUD state for debugging
     */
    fun getHudState(): JSONObject {
        return JSONObject().apply {
            put("current_text", hudDisplayManager.getCurrentDisplayText())
            put("display_history_count", hudDisplayManager.getDisplayHistory().size)
            put("timestamp", System.currentTimeMillis())
        }
    }
}