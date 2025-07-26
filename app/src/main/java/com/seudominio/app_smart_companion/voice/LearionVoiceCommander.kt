package com.seudominio.app_smart_companion.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.widget.Toast

/**
 * LearionVoiceCommander - Hardware-agnostic voice command system for Learion Glass
 * 
 * Architecture inspired by SmartGlassManager pattern:
 * - Detects hardware capabilities (Vuzix M400 vs Emulator)
 * - Registers voice commands appropriately 
 * - Provides unified callback interface
 * - Two-phase feedback: listening indicator → command processed
 * 
 * Commands supported (English):
 * - "one" / "1" → Assistant
 * - "two" / "2" → Live Agent  
 * - "three" / "3" → Settings
 * - "four" / "4" → Exit
 * - "back" → Navigate back
 * - "help" → Show help
 */
class LearionVoiceCommander(
    private val context: Context,
    private val commandCallback: (VoiceCommand) -> Unit,
    private val hudDisplayManager: com.seudominio.app_smart_companion.ui.HudDisplayManager? = null
) {
    
    companion object {
        private const val TAG = "LearionVoice"
        
        // Voice command actions
        const val ACTION_ASSISTANT = "assistant"
        const val ACTION_LIVE_AGENT = "live_agent"
        const val ACTION_SETTINGS = "settings"
        const val ACTION_EXIT = "exit"
        const val ACTION_BACK = "back"
        const val ACTION_HELP = "help"
        
        // Wake words
        private const val WAKE_WORD_VUZIX = "hello vuzix"
        private const val WAKE_WORD_LEARION = "hello learion"
        private const val VOICE_OFF_PHRASE = "voice off"
        
        // English commands map
        val VOICE_COMMANDS = mapOf(
            // Number commands (most reliable)
            "one" to ACTION_ASSISTANT,
            "1" to ACTION_ASSISTANT,
            "two" to ACTION_LIVE_AGENT,
            "2" to ACTION_LIVE_AGENT,
            "three" to ACTION_SETTINGS,
            "3" to ACTION_SETTINGS,
            "four" to ACTION_EXIT,
            "4" to ACTION_EXIT,
            
            // Word commands
            "assistant" to ACTION_ASSISTANT,
            "live agent" to ACTION_LIVE_AGENT,
            "settings" to ACTION_SETTINGS,
            "exit" to ACTION_EXIT,
            "back" to ACTION_BACK,
            "help" to ACTION_HELP,
            "go back" to ACTION_BACK,
            "quit" to ACTION_EXIT,
            "close" to ACTION_EXIT
        )
    }
    
    // Voice command handler interface
    private var voiceHandler: VoiceCommandHandler? = null
    private var isEnabled = false
    
    /**
     * Data class representing a voice command
     */
    data class VoiceCommand(
        val action: String,
        val phrase: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Initialize voice commander - detect hardware and setup appropriate handler
     */
    fun initialize() {
        Log.d(TAG, "🎤 Initializing LearionVoiceCommander")
        
        // Detect hardware capabilities
        voiceHandler = when {
            isVuzixDevice() -> {
                Log.d(TAG, "🥽 Vuzix M400 detected - initializing VuzixSpeechClient")
                VuzixVoiceHandler(context, ::handleVoiceCommand, ::onWakeWordDetected)
            }
            else -> {
                Log.d(TAG, "📱 Non-Vuzix device - using fallback voice handler")
                FallbackVoiceHandler(context, ::handleVoiceCommand, ::onWakeWordDetected)
            }
        }
        
        // Initialize the selected handler
        voiceHandler?.initialize()
        
        // Register voice commands
        registerCommands()
        
        Log.d(TAG, "✅ Voice commander initialized with ${voiceHandler?.javaClass?.simpleName}")
    }
    
    /**
     * Check if running on Vuzix hardware
     */
    private fun isVuzixDevice(): Boolean {
        return Build.MANUFACTURER.equals("Vuzix", ignoreCase = true) ||
               Build.MODEL.contains("M400", ignoreCase = true) ||
               Build.MODEL.contains("M4000", ignoreCase = true)
    }
    
    /**
     * Register all voice commands with the handler
     */
    private fun registerCommands() {
        Log.d(TAG, "📝 Registering ${VOICE_COMMANDS.size} voice commands")
        
        voiceHandler?.let { handler ->
            // Register wake words
            handler.registerWakeWord(WAKE_WORD_VUZIX)
            handler.registerWakeWord(WAKE_WORD_LEARION)
            
            // Register voice-off phrase
            handler.registerVoiceOffPhrase(VOICE_OFF_PHRASE)
            
            // Register all commands
            VOICE_COMMANDS.forEach { (phrase, action) ->
                handler.registerCommand(phrase, action)
                Log.d(TAG, "  ✓ Registered: '$phrase' → $action")
            }
        }
    }
    
    /**
     * Handle wake word detection - show listening indicator
     */
    private fun onWakeWordDetected() {
        Log.d(TAG, "🎤 Wake word detected - showing listening indicator")
        hudDisplayManager?.showVoiceListening()
    }
    
    /**
     * Handle recognized voice command
     */
    private fun handleVoiceCommand(phrase: String) {
        Log.d(TAG, "🗣️ Voice command received: '$phrase'")
        
        // Hide listening indicator when command is processed
        hudDisplayManager?.hideVoiceIndicator()
        
        // Normalize phrase (lowercase, trim)
        val normalizedPhrase = phrase.lowercase().trim()
        
        // Look up action for phrase
        val action = VOICE_COMMANDS[normalizedPhrase]
        
        if (action != null) {
            Log.d(TAG, "✅ Command recognized: '$normalizedPhrase' → $action")
            
            // Create command object
            val command = VoiceCommand(
                action = action,
                phrase = phrase
            )
            
            // Invoke callback
            commandCallback(command)
            
            // Show subtle feedback (no longer needed since we hide indicator)
            // showFeedback("Command: $phrase")
        } else {
            Log.w(TAG, "❌ Unrecognized command: '$phrase'")
            showFeedback("Unknown command: $phrase")
        }
    }
    
    /**
     * Enable voice listening
     */
    fun enable() {
        Log.d(TAG, "🎤 Enabling voice commands")
        isEnabled = true
        voiceHandler?.enable()
    }
    
    /**
     * Disable voice listening
     */
    fun disable() {
        Log.d(TAG, "🔇 Disabling voice commands")
        isEnabled = false
        voiceHandler?.disable()
    }
    
    /**
     * Toggle voice listening state
     */
    fun toggle() {
        if (isEnabled) {
            disable()
        } else {
            enable()
        }
    }
    
    /**
     * Programmatically trigger voice listening
     */
    fun triggerListening() {
        Log.d(TAG, "🎯 Triggering voice listening")
        voiceHandler?.triggerListening()
    }
    
    /**
     * Check if voice commands are enabled
     */
    fun isEnabled(): Boolean = isEnabled
    
    /**
     * Show visual feedback for voice commands
     */
    private fun showFeedback(message: String) {
        // Use short toast for quick feedback
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        Log.d(TAG, "🧹 Cleaning up voice commander")
        disable()
        voiceHandler?.cleanup()
        voiceHandler = null
    }
}

/**
 * Interface for voice command handlers
 */
interface VoiceCommandHandler {
    fun initialize()
    fun registerCommand(phrase: String, action: String)
    fun registerWakeWord(phrase: String)
    fun registerVoiceOffPhrase(phrase: String)
    fun enable()
    fun disable()
    fun triggerListening()
    fun cleanup()
}

/**
 * Vuzix M400 voice handler using VuzixSpeechClient
 */
class VuzixVoiceHandler(
    private val context: Context,
    private val callback: (String) -> Unit,
    private val wakeWordCallback: () -> Unit
) : VoiceCommandHandler {
    
    companion object {
        private const val TAG = "VuzixVoiceHandler"
        private const val ACTION_VOICE_COMMAND = "com.vuzix.sdk.speechrecognitionservice.ACTION_VOICE_COMMAND"
        private const val PHRASE_STRING_EXTRA = "com.vuzix.sdk.speechrecognitionservice.PHRASE_STRING_EXTRA"
        private const val RECOGNIZER_ACTIVE_BOOL_EXTRA = "com.vuzix.sdk.speechrecognitionservice.RECOGNIZER_ACTIVE_BOOL_EXTRA"
    }
    
    private var speechClient: Any? = null // Will be VuzixSpeechClient
    private var voiceReceiver: BroadcastReceiver? = null
    private val registeredPhrases = mutableMapOf<String, String>()
    
    override fun initialize() {
        Log.d(TAG, "🥽 Initializing Vuzix voice handler")
        
        try {
            // Use reflection to check if VuzixSpeechClient is available
            val speechClientClass = Class.forName("com.vuzix.sdk.speechrecognitionservice.VuzixSpeechClient")
            val constructor = speechClientClass.getConstructor(Context::class.java)
            speechClient = constructor.newInstance(context)
            
            Log.d(TAG, "✅ VuzixSpeechClient created successfully")
            
            // Setup broadcast receiver for voice commands
            setupVoiceReceiver()
            
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "⚠️ VuzixSpeechClient not found - SDK not available")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing VuzixSpeechClient", e)
        }
    }
    
    private fun setupVoiceReceiver() {
        voiceReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_VOICE_COMMAND) {
                    val phrase = intent.getStringExtra(PHRASE_STRING_EXTRA)
                    val isActive = intent.getBooleanExtra(RECOGNIZER_ACTIVE_BOOL_EXTRA, false)
                    
                    Log.d(TAG, "🎤 Voice event - phrase: '$phrase', active: $isActive")
                    
                    // Check if this is wake word activation
                    if (isActive && phrase.isNullOrEmpty()) {
                        Log.d(TAG, "🎤 Wake word detected (recognizer became active)")
                        wakeWordCallback()
                    } else if (!phrase.isNullOrEmpty()) {
                        // This is an actual command
                        Log.d(TAG, "🗣️ Voice command received: '$phrase'")
                        callback(phrase)
                    }
                }
            }
        }
        
        // Register receiver
        val filter = IntentFilter(ACTION_VOICE_COMMAND)
        context.registerReceiver(voiceReceiver, filter)
        Log.d(TAG, "✅ Voice broadcast receiver registered")
    }
    
    override fun registerCommand(phrase: String, action: String) {
        speechClient?.let { client ->
            try {
                // Use reflection to call insertPhrase
                val insertMethod = client.javaClass.getMethod("insertPhrase", String::class.java)
                insertMethod.invoke(client, phrase)
                
                registeredPhrases[phrase] = action
                Log.d(TAG, "✅ Registered phrase: '$phrase'")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error registering phrase: $phrase", e)
            }
        }
    }
    
    override fun registerWakeWord(phrase: String) {
        speechClient?.let { client ->
            try {
                // Use reflection to call insertWakeWordPhrase
                val method = client.javaClass.getMethod("insertWakeWordPhrase", String::class.java)
                method.invoke(client, phrase)
                Log.d(TAG, "✅ Registered wake word: '$phrase'")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error registering wake word", e)
            }
        }
    }
    
    override fun registerVoiceOffPhrase(phrase: String) {
        speechClient?.let { client ->
            try {
                // Use reflection to call insertVoiceOffPhrase
                val method = client.javaClass.getMethod("insertVoiceOffPhrase", String::class.java)
                method.invoke(client, phrase)
                Log.d(TAG, "✅ Registered voice-off phrase: '$phrase'")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error registering voice-off phrase", e)
            }
        }
    }
    
    override fun enable() {
        try {
            // EnableRecognizer(Context, boolean)
            val enableMethod = Class.forName("com.vuzix.sdk.speechrecognitionservice.VuzixSpeechClient")
                .getMethod("EnableRecognizer", Context::class.java, Boolean::class.java)
            enableMethod.invoke(null, context, true)
            Log.d(TAG, "✅ Voice recognizer enabled")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error enabling recognizer", e)
        }
    }
    
    override fun disable() {
        try {
            // EnableRecognizer(Context, boolean)
            val enableMethod = Class.forName("com.vuzix.sdk.speechrecognitionservice.VuzixSpeechClient")
                .getMethod("EnableRecognizer", Context::class.java, Boolean::class.java)
            enableMethod.invoke(null, context, false)
            Log.d(TAG, "✅ Voice recognizer disabled")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error disabling recognizer", e)
        }
    }
    
    override fun triggerListening() {
        try {
            // TriggerVoiceAudio(Context, boolean)
            val triggerMethod = Class.forName("com.vuzix.sdk.speechrecognitionservice.VuzixSpeechClient")
                .getMethod("TriggerVoiceAudio", Context::class.java, Boolean::class.java)
            triggerMethod.invoke(null, context, true)
            Log.d(TAG, "✅ Voice listening triggered")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error triggering voice audio", e)
        }
    }
    
    override fun cleanup() {
        Log.d(TAG, "🧹 Cleaning up Vuzix voice handler")
        
        // Unregister receiver
        voiceReceiver?.let {
            context.unregisterReceiver(it)
            voiceReceiver = null
        }
        
        // Clean up speech client
        speechClient?.let { client ->
            try {
                // Call deletePhrase for all registered phrases
                val deleteMethod = client.javaClass.getMethod("deletePhrase", String::class.java)
                registeredPhrases.keys.forEach { phrase ->
                    deleteMethod.invoke(client, phrase)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up phrases", e)
            }
        }
        
        speechClient = null
        registeredPhrases.clear()
    }
}

/**
 * Fallback voice handler for non-Vuzix devices (emulator)
 * Can be extended to use Google Speech-to-Text or other services
 */
class FallbackVoiceHandler(
    private val context: Context,
    private val callback: (String) -> Unit,
    private val wakeWordCallback: () -> Unit
) : VoiceCommandHandler {
    
    companion object {
        private const val TAG = "FallbackVoiceHandler"
    }
    
    override fun initialize() {
        Log.d(TAG, "📱 Initializing fallback voice handler (no voice support)")
        // For emulator testing, we can simulate voice commands via ADB or UI
    }
    
    override fun registerCommand(phrase: String, action: String) {
        Log.d(TAG, "📝 Simulated registration: '$phrase' → $action")
    }
    
    override fun registerWakeWord(phrase: String) {
        Log.d(TAG, "📝 Simulated wake word: '$phrase'")
    }
    
    override fun registerVoiceOffPhrase(phrase: String) {
        Log.d(TAG, "📝 Simulated voice-off: '$phrase'")
    }
    
    override fun enable() {
        Log.d(TAG, "🎤 Voice commands enabled (simulation mode)")
        showEmulatorInstructions()
    }
    
    override fun disable() {
        Log.d(TAG, "🔇 Voice commands disabled")
    }
    
    override fun triggerListening() {
        Log.d(TAG, "🎯 Voice listening triggered (simulation)")
        // In emulator, we could show a dialog to input voice command
        simulateVoiceCommand()
    }
    
    override fun cleanup() {
        Log.d(TAG, "🧹 Cleaning up fallback handler")
    }
    
    private fun showEmulatorInstructions() {
        Toast.makeText(
            context,
            "Voice commands ready (emulator mode)\nUse ADB: adb shell am broadcast -a VOICE_TEST --es phrase \"one\"",
            Toast.LENGTH_LONG
        ).show()
    }
    
    private fun simulateVoiceCommand() {
        // For testing in emulator, we can simulate a voice command
        // In production, this could open a speech input dialog
        Log.d(TAG, "🧪 Simulating voice command for testing")
        
        // First simulate wake word detection
        wakeWordCallback()
        
        // Then simulate "one" command after 2 seconds
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            callback("one")
        }, 2000)
    }
}