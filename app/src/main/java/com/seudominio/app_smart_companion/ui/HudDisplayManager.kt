package com.seudominio.app_smart_companion.ui

import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.TextView
import com.seudominio.app_smart_companion.R
import java.util.*

/**
 * HUD Display Manager for real-time transcription display on Vuzix M400
 * Based on Vuzix M400 UI Guidelines and SmartGlassManager patterns
 */
class HudDisplayManager(
    private val hudTextView: TextView,
    private val connectionStatusView: TextView,
    private val processingIndicator: TextView
) {
    companion object {
        const val TAG = "HudDisplayManager"
        const val MAX_DISPLAY_HISTORY = 10
        const val MAX_DISPLAY_CHARS = 500 // Optimal for M400 HUD readability
        const val MIN_UPDATE_INTERVAL_MS = 100L // Throttle to 10 FPS for battery
    }
    
    private val uiHandler = Handler(Looper.getMainLooper())
    private val displayHistory = LinkedList<String>()
    private var lastUpdateTime = 0L
    
    init {
        // Configure TextView for optimal HUD display
        setupHudTextView()
        Log.d(TAG, "HudDisplayManager initialized for M400")
    }
    
    private fun setupHudTextView() {
        uiHandler.post {
            hudTextView.movementMethod = ScrollingMovementMethod()
            Log.d(TAG, "HUD TextView configured with scrolling support")
        }
    }
    
    /**
     * Update transcription text with throttling for battery optimization
     */
    fun updateTranscriptionText(text: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime >= MIN_UPDATE_INTERVAL_MS) {
            updateTextImmediate(text)
            lastUpdateTime = currentTime
        }
    }
    
    /**
     * Immediate text update (SmartGlassManager pattern - bypasses throttling)
     */
    fun updateTextImmediate(text: String) {
        // SmartGlassManager pattern - always use UI thread with validation
        uiHandler.post {
            try {
                // Validate HUD display state first
                if (!validateTextDisplay(text)) {
                    Log.w(TAG, "HUD validation failed - cannot display text")
                    return@post
                }
                
                // Truncate if too long for optimal HUD display
                val displayText = if (text.length > MAX_DISPLAY_CHARS) {
                    text.substring(0, MAX_DISPLAY_CHARS) + "..."
                } else {
                    text
                }
                
                // Update HUD TextView (SmartGlassManager pattern)
                hudTextView.text = displayText
                
                // Add to history for debugging
                addToHistory(displayText)
                
                // Auto-scroll to bottom for new content (SmartGlassManager pattern)
                if (displayText.length > 100) {
                    hudTextView.post { scrollToBottom() }
                }
                
                Log.d(TAG, "✅ HUD text updated: ${displayText.substring(0, minOf(50, displayText.length))}...")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error updating HUD text", e)
            }
        }
    }
    
    /**
     * Append new text to existing display (for streaming updates)
     */
    fun appendText(newText: String) {
        uiHandler.post {
            val currentText = hudTextView.text.toString()
            val combinedText = if (currentText.isEmpty()) {
                newText
            } else {
                "$currentText\n$newText"
            }
            updateTextImmediate(combinedText)
        }
    }
    
    /**
     * Clear HUD display
     */
    fun clearDisplay() {
        uiHandler.post {
            hudTextView.text = ""
            displayHistory.clear()
            Log.d(TAG, "HUD display cleared")
        }
    }
    
    /**
     * Show temporary status message
     */
    fun showStatusMessage(message: String, durationMs: Long = 3000L) {
        uiHandler.post {
            val originalText = hudTextView.text.toString()
            hudTextView.text = "📢 $message"
            
            // Restore original text after delay
            uiHandler.postDelayed({
                if (originalText.isNotEmpty()) {
                    hudTextView.text = originalText
                } else {
                    clearDisplay()
                }
            }, durationMs)
            
            Log.d(TAG, "Status message shown: $message")
        }
    }
    
    /**
     * Update connection status indicator
     */
    fun updateConnectionStatus(connected: Boolean) {
        uiHandler.post {
            val statusText = if (connected) "🟢 Connected" else "🔴 Disconnected"
            val colorRes = if (connected) 
                R.color.vuzix_connected else R.color.vuzix_disconnected
                
            connectionStatusView.text = statusText
            connectionStatusView.setTextColor(
                connectionStatusView.context.getColor(colorRes)
            )
            
            Log.d(TAG, "Connection status updated: $statusText")
        }
    }
    
    /**
     * Show/hide processing indicator
     */
    fun showProcessingIndicator(show: Boolean, message: String = "🎤 Processing...") {
        uiHandler.post {
            if (show) {
                processingIndicator.text = message
                processingIndicator.visibility = android.view.View.VISIBLE
            } else {
                processingIndicator.visibility = android.view.View.GONE
            }
            
            Log.d(TAG, "Processing indicator: ${if (show) "shown" else "hidden"}")
        }
    }
    
    /**
     * Get current display text (for confirmation callbacks)
     */
    fun getCurrentDisplayText(): String {
        return hudTextView.text.toString()
    }
    
    /**
     * Validate text for HUD display
     */
    fun validateTextDisplay(text: String): Boolean {
        if (text.length > MAX_DISPLAY_CHARS * 2) {
            Log.w(TAG, "Text too long for optimal HUD display: ${text.length} chars")
            return false
        }
        
        if (hudTextView.visibility != android.view.View.VISIBLE) {
            Log.w(TAG, "HUD TextView not visible")
            return false
        }
        
        return true
    }
    
    private fun addToHistory(text: String) {
        if (displayHistory.size >= MAX_DISPLAY_HISTORY) {
            displayHistory.poll() // Remove oldest
        }
        displayHistory.offer(text)
    }
    
    private fun scrollToBottom() {
        try {
            // SmartGlassManager pattern - safe scrolling with null checks
            val layout = hudTextView.layout
            if (layout != null && hudTextView.lineCount > 0) {
                val scrollAmount = layout.getLineTop(hudTextView.lineCount)
                hudTextView.scrollTo(0, scrollAmount)
                Log.v(TAG, "Scrolled to bottom: $scrollAmount")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scrolling to bottom", e)
        }
    }
    
    /**
     * Get display history for debugging
     */
    fun getDisplayHistory(): List<String> {
        return displayHistory.toList()
    }
    
    /**
     * Performance monitoring
     */
    fun logUpdateLatency(startTime: Long, operation: String) {
        val latency = System.currentTimeMillis() - startTime
        Log.d(TAG, "$operation latency: ${latency}ms")
        
        if (latency > 100) {
            Log.w(TAG, "High latency detected for $operation")
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        displayHistory.clear()
        uiHandler.removeCallbacksAndMessages(null)
        Log.d(TAG, "HudDisplayManager cleaned up")
    }
}