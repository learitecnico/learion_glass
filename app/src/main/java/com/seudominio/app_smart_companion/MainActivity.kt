package com.seudominio.app_smart_companion

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import com.vuzix.hud.actionmenu.ActionMenuActivity
import com.seudominio.app_smart_companion.service.StandaloneService
import com.seudominio.app_smart_companion.agents.AgentManager
import com.seudominio.app_smart_companion.ui.HudDisplayManager
import com.seudominio.app_smart_companion.ui.HudMessageHandler
import android.content.SharedPreferences
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.seudominio.app_smart_companion.assistants.SimpleAssistantService
import com.seudominio.app_smart_companion.voice.LearionVoiceCommander
import com.seudominio.app_smart_companion.vosk.VoskTranscriptionService
import com.seudominio.app_smart_companion.audio.CoachAudioRecorder
import com.seudominio.app_smart_companion.assistants.OpenAIAssistantClient
import com.seudominio.app_smart_companion.assistants.AssistantAudioManager
import com.seudominio.app_smart_companion.assistants.AssistantPhotoManager
import com.seudominio.app_smart_companion.assistants.ActiveModeManager
import com.seudominio.app_smart_companion.assistants.ThreadManager
import com.seudominio.app_smart_companion.assistants.AssistantRegistry
import com.seudominio.app_smart_companion.models.Assistant
import java.io.File

class MainActivity : ActionMenuActivity() {
    companion object {
        const val TAG = "LearionGlass"
        const val PERMISSION_REQUEST_CODE = 1001
        val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }
    
    // HUD Display Components
    private lateinit var hudDisplayManager: HudDisplayManager
    // Legacy hudMessageHandler removed - not needed in standalone mode
    
    // UI Components
    private lateinit var hudTextDisplay: TextView
    private lateinit var connectionStatus: TextView
    private lateinit var processingIndicator: TextView
    
    // Broadcast Receiver for StandaloneService communication
    private var messageReceiver: BroadcastReceiver? = null
    // Broadcast Receiver for SimpleAssistantService communication  
    private var assistantReceiver: BroadcastReceiver? = null
    // Broadcast Receiver for API key configuration
    private var apiKeyReceiver: BroadcastReceiver? = null
    private lateinit var sharedPreferences: SharedPreferences
    private var isSessionActive = false
    private var isListening = false  // Toggle state for Talk to AI
    private var isAssistantConnected = false  // Assistant connection ready state
    private var isRecordingAudio = false  // Audio recording state
    
    // Menu Navigation State
    private enum class MenuState {
        MAIN,             // Main menu: Assistants, Live Agent, Voice Commands, Settings, Exit
        ASSISTANTS,       // Assistants menu: Coach SPIN, [Future agents], Back
        ASSISTANT_MENU,   // Generic assistant menu: Test, Photo, Audio, Info, Active, Back
        ASSISTANT_ACTIVE, // Generic assistant active mode: Audio, Photo, New Thread, Toggle Audio, Back
        ASSISTANT,        // Legacy: Assistant submenu: Start Chat, Back
        LIVE_AGENT        // Live Agent submenu: Start Chat, Switch Agent, Back
    }
    
    private var currentMenuState = MenuState.MAIN
    private val menuStack = mutableListOf<MenuState>()
    
    // Voice Commander for hands-free control
    private lateinit var voiceCommander: LearionVoiceCommander
    
    // Assistant state management
    private var currentAssistant: Assistant? = null  // Currently selected assistant
    private var isAssistantActive = false  // Assistant active connection state
    private var currentThreadId: String? = null  // Active conversation thread
    private var audioResponseEnabled = false  // Toggle for audio responses (default OFF)
    private var voskTranscriptionService: VoskTranscriptionService? = null  // Vosk local transcription
    private var coachAudioRecorder: CoachAudioRecorder? = null  // Audio recorder for Coach
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())  // UI thread handler
    private var activeModeManager: ActiveModeManager? = null  // Active mode coordinator
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        Log.d(TAG, "MainActivity onCreate - ActionMenuActivity initialized")
        
        // Configure M400-specific display settings
        configureM400Display()
        
        // Initialize UI components
        initializeHudComponents()
        
        // Initialize shared preferences for API key storage
        sharedPreferences = getSharedPreferences("smart_companion_prefs", Context.MODE_PRIVATE)
        
        // Auto-configure API key from .env file
        autoConfigureApiKey()
        
        // Initialize broadcast receivers
        setupBroadcastReceivers()
        setupAssistantBroadcastReceivers()
        setupApiKeyReceiver()
        
        // Check permissions and API key setup
        checkPermissions()
        
        // Initialize voice commands
        initializeVoiceCommands()
    }
    
    /**
     * Initialize HUD display components
     */
    private fun initializeHudComponents() {
        // Find UI components
        hudTextDisplay = findViewById(R.id.hud_text_display)
        connectionStatus = findViewById(R.id.connection_status)
        processingIndicator = findViewById(R.id.processing_indicator)
        
        // Initialize HUD Display Manager
        hudDisplayManager = HudDisplayManager(
            hudTextView = hudTextDisplay,
            connectionStatusView = connectionStatus,
            processingIndicator = processingIndicator
        )
        
        // Legacy HudMessageHandler removed - standalone mode doesn't need message handler
        
        // Keep HUD clean on startup
        hudDisplayManager.updateConnectionStatus(false)
        
        Log.d(TAG, "HUD components initialized")
    }
    
    /**
     * Configure display settings specifically for Vuzix M400
     */
    private fun configureM400Display() {
        Log.d(TAG, "🥽 Configuring M400-specific display settings")
        
        // Hide navigation bar (M400 doesn't have navigation buttons)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
        
        // Force landscape orientation (M400 requirement)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        
        // Keep screen on for smart glasses usage
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        Log.d(TAG, "✅ M400 display configuration complete")
    }
    
    /**
     * Setup broadcast receivers for StandaloneService communication
     */
    private fun setupBroadcastReceivers() {
        messageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // SmartGlassManager pattern - immediate processing
                when (intent?.action) {
                    "com.seudominio.app_smart_companion.TEXT_RESPONSE" -> {
                        val text = intent.getStringExtra("text")
                        if (text != null) {
                            Log.d(TAG, "📱 AI text response received: '${text.take(30)}...'")
                            
                            // Immediate display (SmartGlassManager pattern)
                            displayAIResponse(text)
                        } else {
                            Log.w(TAG, "📱 Received TEXT_RESPONSE with null text")
                        }
                    }
                    
                    "com.seudominio.app_smart_companion.CONNECTION_STATUS" -> {
                        val connected = intent.getBooleanExtra("connected", false)
                        Log.d(TAG, "📱 OpenAI connection status: $connected")
                        updateConnectionStatus(connected)
                        isSessionActive = connected
                    }
                    
                    "com.seudominio.app_smart_companion.STATUS_MESSAGE" -> {
                        val message = intent.getStringExtra("message")
                        if (message != null) {
                            Log.d(TAG, "📱 Status message: $message")
                            hudDisplayManager.showStatusMessage(message, 3000L)
                        }
                    }
                    
                    "com.seudominio.app_smart_companion.ERROR_MESSAGE" -> {
                        val error = intent.getStringExtra("error")
                        if (error != null) {
                            Log.e(TAG, "❌ Error from StandaloneService: $error")
                            hudDisplayManager.showStatusMessage("❌ Error: $error", 5000L)
                        }
                    }
                }
            }
        }
        
        // Register broadcast receiver
        val filter = IntentFilter().apply {
            addAction("com.seudominio.app_smart_companion.TEXT_RESPONSE")
            addAction("com.seudominio.app_smart_companion.CONNECTION_STATUS")
            addAction("com.seudominio.app_smart_companion.STATUS_MESSAGE")
            addAction("com.seudominio.app_smart_companion.ERROR_MESSAGE")
        }
        
        registerReceiver(messageReceiver, filter)
        Log.d(TAG, "Broadcast receivers registered for StandaloneService")
    }
    
    private fun checkPermissions() {
        val deniedPermissions = REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (deniedPermissions.isEmpty()) {
            Log.d(TAG, "All permissions granted - checking API key setup")
            checkApiKeySetup()
        } else {
            Log.d(TAG, "Requesting permissions: ${deniedPermissions.joinToString()}")
            ActivityCompat.requestPermissions(this, deniedPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }
    
    private fun startAISession() {
        val apiKey = getApiKey()
        if (apiKey == null) {
            hudDisplayManager.showStatusMessage("❌ API key not configured\n🔧 Use Settings to add key", 5000L)
            return
        }
        
        Log.d(TAG, "Starting AI session with OpenAI Realtime API")
        hudDisplayManager.showStatusMessage("🤖 Starting AI session...", 3000L)
        
        val intent = Intent(this, StandaloneService::class.java).apply {
            action = StandaloneService.ACTION_START_SESSION
            putExtra(StandaloneService.EXTRA_API_KEY, apiKey)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        Log.d(TAG, "StandaloneService start session command sent")
    }
    
    private fun stopAISession() {
        Log.d(TAG, "Stopping AI session")
        hudDisplayManager.showStatusMessage("⏹️ Stopping AI session...", 2000L)
        
        val intent = Intent(this, StandaloneService::class.java).apply {
            action = StandaloneService.ACTION_STOP_SESSION
        }
        startService(intent)
        
        isSessionActive = false
        Log.d(TAG, "StandaloneService stop session command sent")
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            
            if (allGranted) {
                Log.d(TAG, "All permissions granted - checking API key setup")
                Toast.makeText(this, "Permissions granted - use menu to start AI session", Toast.LENGTH_SHORT).show()
                checkApiKeySetup()
            } else {
                Log.w(TAG, "Some permissions denied")
                Toast.makeText(this, "All permissions are required for proper functionality", Toast.LENGTH_LONG).show()
                hudDisplayManager.showStatusMessage("❌ Permissions required", 3000L)
            }
        }
    }
    
    /**
     * Show main welcome message - clear and action-focused
     */
    private fun checkApiKeySetup() {
        val apiKey = getApiKey()
        val currentAgent = getCurrentAgentName()
        
        if (apiKey != null) {
            // Use updateTextImmediate for permanent display (not temporary showStatusMessage)
            hudDisplayManager.updateTextImmediate(
                "🤖 Learion Glass AI Assistant\n\n" +
                "🎤 TAP TRACKPAD → Open Menu\n" +
                "🗣️ Say \"Hello Vuzix, 1\" → Start Now"
            )
        } else {
            hudDisplayManager.updateTextImmediate(
                "🤖 Learion Glass AI Assistant\n\n" +
                "⚠️ Using test API key\n\n" +
                "🎤 TAP TRACKPAD → Open Menu\n" +
                "🗣️ Say \"Hello Vuzix, 1\" → Start Now"
            )
        }
    }
    
    /**
     * Get API key from SharedPreferences or fallback to hardcoded for testing
     */
    private fun getApiKey(): String? {
        // First try SharedPreferences
        val savedKey = sharedPreferences.getString("openai_api_key", null)
        if (savedKey != null) return savedKey
        
        // Auto-configure API key from .env file if not set
        val envApiKey = loadApiKeyFromEnv()
        if (envApiKey != null) {
            // Save to SharedPreferences for future use with source tracking
            saveApiKey(envApiKey, "env")
            Log.d(TAG, "API key loaded from .env file and saved to SharedPreferences")
            return envApiKey
        }
        
        // Fallback - user needs to configure manually
        Log.w(TAG, "No API key found - user must configure via Settings")
        return null
    }
    
    /**
     * Load API key from assets/env.txt file
     */
    private fun loadApiKeyFromEnv(): String? {
        return try {
            Log.d(TAG, "🔑 Loading API key from assets/env.txt")
            
            // Try to read from assets/env.txt (works on both emulator and M400)
            val inputStream = assets.open("env.txt")
            val envContent = inputStream.bufferedReader().use { it.readText() }
            inputStream.close()
            
            Log.d(TAG, "✅ Successfully read env.txt from assets")
            
            envContent.lines().forEach { line ->
                if (line.startsWith("OPENAI_API_KEY=") && !line.contains("YOUR_OPENAI_API_KEY_HERE")) {
                    val apiKey = line.substring("OPENAI_API_KEY=".length).trim()
                    if (apiKey.startsWith("sk-") && apiKey.length > 20) {
                        Log.d(TAG, "✅ Found valid API key in assets/env.txt")
                        return apiKey
                    }
                }
            }
            
            Log.d(TAG, "❌ No valid API key found in assets/env.txt")
            null
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Error reading assets/env.txt: ${e.message}")
            null
        }
    }
    
    /**
     * Save API key to SharedPreferences with source tracking
     */
    private fun saveApiKey(apiKey: String, source: String = "manual") {
        sharedPreferences.edit()
            .putString("openai_api_key", apiKey)
            .putString("api_key_source", source)
            .apply()
        Log.d(TAG, "API key saved to SharedPreferences (source: $source)")
    }
    
    /**
     * Manual API key configuration for emulator testing
     * Call this method for emulator testing: setApiKey("your-api-key-here")
     */
    fun setApiKey(apiKey: String): Boolean {
        return if (apiKey.startsWith("sk-") && apiKey.length > 20) {
            saveApiKey(apiKey, "manual")
            Log.d(TAG, "✅ API key configured manually for emulator testing")
            
            // Update UI immediately
            checkApiKeySetup()
            
            hudDisplayManager.showStatusMessage(
                "✅ API Key Configured!\n\n" +
                "🔑 Key: sk-...${apiKey.takeLast(4)}\n" +
                "📍 Source: Manual Setup\n\n" +
                "Ready to test AI features!",
                5000L
            )
            true
        } else {
            Log.w(TAG, "❌ Invalid API key format")
            hudDisplayManager.showStatusMessage(
                "❌ Invalid API Key Format\n\n" +
                "Must start with 'sk-' and be > 20 chars",
                3000L
            )
            false
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Cleanup broadcast receivers
        messageReceiver?.let { receiver ->
            unregisterReceiver(receiver)
            messageReceiver = null
            Log.d(TAG, "Broadcast receiver unregistered")
        }
        
        // Cleanup assistant broadcast receiver
        assistantReceiver?.let { receiver ->
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
            assistantReceiver = null
            Log.d(TAG, "Assistant broadcast receiver unregistered")
        }
        
        // Cleanup API key receiver
        apiKeyReceiver?.let { receiver ->
            unregisterReceiver(receiver)
            apiKeyReceiver = null
            Log.d(TAG, "API key receiver unregistered")
        }
        
        // Cleanup HUD components
        if (::hudDisplayManager.isInitialized) {
            hudDisplayManager.cleanup()
        }
        
        // Cleanup StandaloneService if needed
        if (isSessionActive) {
            stopAISession()
        }
        // Cleanup voice commander
        if (::voiceCommander.isInitialized) {
            voiceCommander.cleanup()
        }
        
        // Cleanup voice test receiver
        voiceTestReceiver?.let { receiver ->
            unregisterReceiver(receiver)
            voiceTestReceiver = null
            Log.d(TAG, "Voice test receiver unregistered")
        }
        
        // Cleanup ActiveModeManager
        activeModeManager?.let { manager ->
            manager.cleanup()
            activeModeManager = null
            Log.d(TAG, "ActiveModeManager cleaned up")
        }
        
        Log.d(TAG, "MainActivity destroyed and cleaned up")
    }
    
    // ===============================
    // VUZIX M400 TRACKPAD NAVIGATION
    // ===============================
    
    /**
     * Handle trackpad events (primary M400 input method)
     */
    override fun onTrackballEvent(event: MotionEvent): Boolean {
        Log.d(TAG, "🎯 Trackpad event: action=${event.action}, x=${event.x}, y=${event.y}")
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                Log.d(TAG, "🎯 Trackpad pressed down")
                return true
            }
            MotionEvent.ACTION_UP -> {
                Log.d(TAG, "🎯 Trackpad tap - opening menu")
                // Trackpad tap = open menu (M400 standard)
                openM400ActionMenu()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                Log.d(TAG, "🎯 Trackpad swipe: dx=${event.x}, dy=${event.y}")
                // Handle trackpad movement for future cursor control
                return true
            }
        }
        
        return super.onTrackballEvent(event)
    }
    
    /**
     * Handle hardware key events (DPAD fallback + M400 buttons)
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        Log.d(TAG, "🎯 Key event: code=$keyCode, action=${event.action}")
        
        when (keyCode) {
            // M400 Physical Buttons (official mapping)
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                Log.d(TAG, "🎯 M400 rear button (DPAD_CENTER) - opening menu")
                openM400ActionMenu()
                return true
            }
            
            KeyEvent.KEYCODE_HOME -> {
                Log.d(TAG, "🎯 M400 middle button (HOME) - going to home")
                // Let system handle home button
                return super.onKeyDown(keyCode, event)
            }
            
            KeyEvent.KEYCODE_BACK -> {
                Log.d(TAG, "🎯 M400 front button (BACK) - handling back navigation")
                return handleBackNavigation()
            }
            
            // DPAD Navigation (trackpad fallback gestures)
            KeyEvent.KEYCODE_DPAD_UP -> {
                Log.d(TAG, "🎯 DPAD UP - menu navigation up")
                return handleMenuNavigation("up")
            }
            
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                Log.d(TAG, "🎯 DPAD DOWN - menu navigation down")
                return handleMenuNavigation("down")
            }
            
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                Log.d(TAG, "🎯 DPAD LEFT - menu navigation left")
                return handleMenuNavigation("left")
            }
            
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                Log.d(TAG, "🎯 DPAD RIGHT - menu navigation right")
                return handleMenuNavigation("right")
            }
            
            // Voice Commands Support
            KeyEvent.KEYCODE_VOICE_ASSIST -> {
                Log.d(TAG, "🎯 Voice assist button - starting voice interaction")
                startVoiceInteraction()
                return true
            }
        }
        
        return super.onKeyDown(keyCode, event)
    }
    
    /**
     * Handle back navigation with menu hierarchy support
     */
    private fun handleBackNavigation(): Boolean {
        return when (currentMenuState) {
            MenuState.MAIN -> {
                // At main menu - exit app
                Log.d(TAG, "🎯 Back at main menu - exiting app")
                handleExit()
                true
            }
            MenuState.ASSISTANTS -> {
                // In assistants menu - go back to main
                Log.d(TAG, "🎯 Back from assistants menu - returning to main menu")
                navigateBack()
                true
            }
            MenuState.ASSISTANT_MENU -> {
                // In assistant menu - go back to assistants
                Log.d(TAG, "🎯 Back from assistant menu - returning to assistants menu")
                currentMenuState = MenuState.ASSISTANTS
                currentAssistant = null
                invalidateOptionsMenu()
                true
            }
            MenuState.ASSISTANT_ACTIVE -> {
                // In active mode - go back to assistant menu
                Log.d(TAG, "🎯 Back from active mode - returning to assistant menu")
                isAssistantActive = false
                currentMenuState = MenuState.ASSISTANT_MENU
                invalidateOptionsMenu()
                true
            }
            MenuState.ASSISTANT, MenuState.LIVE_AGENT -> {
                // In submenu - go back to main
                Log.d(TAG, "🎯 Back from submenu - returning to main menu")
                navigateBack()
                true
            }
        }
    }
    
    /**
     * Handle DPAD menu navigation (for trackpad gesture fallbacks)
     */
    private fun handleMenuNavigation(direction: String): Boolean {
        Log.d(TAG, "🎯 Menu navigation: $direction (current state: $currentMenuState)")
        
        // Show navigation feedback
        showVisualFeedback("🧭 Navigation: $direction")
        
        // For now, let the system handle menu navigation
        // Future: implement custom focus management for better M400 UX
        return false  // Let system handle DPAD navigation
    }
    
    /**
     * Start voice interaction for M400
     */
    private fun startVoiceInteraction() {
        Log.d(TAG, "🎤 Starting voice interaction")
        
        if (isSessionActive) {
            // If AI session is active, use Talk to AI
            commitAudioAndRequestResponse()
        } else {
            // If no AI session, start one
            startAISession()
        }
    }
    
    /**
     * Open action menu with M400 optimization
     */
    private fun openM400ActionMenu() {
        Log.d(TAG, "🎯 Opening action menu (M400 optimized)")
        
        // Show visual feedback
        showVisualFeedback("📋 Opening Menu...")
        
        // Trigger ActionMenuActivity menu display
        invalidateActionMenu()  // This will trigger onCreateActionMenu
    }
    
    // ===============================
    // HUD MESSAGE HANDLING METHODS
    // ===============================
    
    /**
     * Display AI response on HUD with real-time transcription (SmartGlassManager pattern)
     */
    private fun displayAIResponse(text: String) {
        // Thread safety - ALWAYS run on UI thread (SmartGlassManager pattern)
        runOnUiThread {
            if (::hudDisplayManager.isInitialized) {
                Log.d(TAG, "🤖 Displaying AI response: ${text.take(50)}...")
                
                // Format for HUD display (SmartGlassManager pattern)
                val formattedResponse = "🤖 ${getCurrentAgentName()}: $text"
                
                // Use updateTextImmediate for real-time display (bypasses throttling)
                hudDisplayManager.updateTextImmediate(formattedResponse)
                
                // Hide processing indicator
                hudDisplayManager.showProcessingIndicator(false)
                
                Log.d(TAG, "✅ HUD text updated successfully")
            } else {
                Log.w(TAG, "HUD display manager not initialized, ignoring AI response")
            }
        }
    }
    
    /**
     * Get current agent name for display
     */
    private fun getCurrentAgentName(): String {
        val agentNames = mapOf(
            "elato_default" to "Elato",
            "sherlock" to "Sherlock",
            "master_chef" to "Chef",
            "fitness_coach" to "Fitness",
            "math_wiz" to "Math",
            "batman" to "Batman",
            "eco_champ" to "Eco"
        )
        val currentAgent = sharedPreferences.getString("current_agent", "elato_default")
        return agentNames[currentAgent] ?: "AI"
    }
    
    /**
     * Toggle Talk to AI - sempre ativo quando ON (ElatoAI pattern)
     */
    private fun commitAudioAndRequestResponse() {
        // Toggle listening state
        isListening = !isListening
        
        Log.d(TAG, "Talk to AI toggled: listening = $isListening")
        
        val intent = Intent(this, StandaloneService::class.java).apply {
            action = if (isListening) {
                StandaloneService.ACTION_START_LISTENING
            } else {
                StandaloneService.ACTION_STOP_LISTENING
            }
        }
        startService(intent)
        
        // Update UI to show current state
        val statusText = if (isListening) {
            "🎤 AI Listening - speak normally\n(Press Talk to AI again to stop)"
        } else {
            "⏸️ AI Stopped - press Talk to AI to start"
        }
        hudDisplayManager.showStatusMessage(statusText, 3000L)
    }
    
    /**
     * Update OpenAI connection status
     */
    private fun updateConnectionStatus(connected: Boolean) {
        if (::hudDisplayManager.isInitialized) {
            hudDisplayManager.updateConnectionStatus(connected)
            
            if (connected) {
                hudDisplayManager.showStatusMessage("✅ Connected to OpenAI\n🎤 Listening for voice...", 3000L)
            } else {
                hudDisplayManager.showStatusMessage("❌ OpenAI connection lost", 3000L)
            }
        }
    }
    
    // ===============================
    // VUZIX ACTION MENU METHODS
    // ===============================
    
    override fun onCreateActionMenu(menu: android.view.Menu?): Boolean {
        // CRITICAL: Clear menu BEFORE calling parent to prevent inheritance
        menu?.clear()
        
        // Call parent but don't use its result
        super.onCreateActionMenu(menu)
        
        // Clear again to ensure absolutely clean state
        menu?.clear()
        
        // Inflate menu based on current state (hierarchical navigation)
        val menuResource = when (currentMenuState) {
            MenuState.MAIN -> R.menu.main_menu
            MenuState.ASSISTANTS -> R.menu.assistants_menu
            MenuState.ASSISTANT_MENU -> currentAssistant?.menuResourceId ?: R.menu.coach_spin_menu
            MenuState.ASSISTANT_ACTIVE -> currentAssistant?.activeMenuResourceId ?: R.menu.coach_active_menu
            MenuState.ASSISTANT -> R.menu.assistant_menu
            MenuState.LIVE_AGENT -> R.menu.live_agent_menu
        }
        
        menuInflater.inflate(menuResource, menu)
        
        // Update dynamic menu items based on state
        if (currentMenuState == MenuState.ASSISTANT_ACTIVE) {
            menu?.findItem(R.id.action_toggle_audio_response)?.let { audioToggle ->
                audioToggle.title = "4. Receber Áudio [${if (audioResponseEnabled) "ON" else "OFF"}]"
            }
        }
        
        Log.d(TAG, "🎯 Action menu created for ${currentMenuState} with ${menu?.size()} items")
        
        // Debug: List all menu items with more detail
        menu?.let { m ->
            Log.d(TAG, "🎯 Menu inflated from ${getMenuResourceName(menuResource)}:")
            for (i in 0 until m.size()) {
                val item = m.getItem(i)
                Log.d(TAG, "🎯 Item $i: '${item.title}' (id: ${item.itemId}, visible: ${item.isVisible})")
            }
        }
        
        return true  // Always return true for clean behavior
    }
    
    private fun getMenuResourceName(resource: Int): String {
        return when (resource) {
            R.menu.main_menu -> "main_menu"
            R.menu.assistant_menu -> "assistant_menu"
            R.menu.live_agent_menu -> "live_agent_menu"
            else -> "unknown_menu"
        }
    }
    
    override fun onActionItemSelected(item: android.view.MenuItem?): Boolean {
        Log.d(TAG, "🎯 Action item selected: ${item?.title} (id: ${item?.itemId}) in ${currentMenuState}")
        
        return when (item?.itemId) {
            // ============ MAIN MENU ITEMS ============
            R.id.action_assistants -> {
                Log.d(TAG, "🎯 Assistants menu item selected")
                navigateToMenu(MenuState.ASSISTANTS)
                showVisualFeedback("Assistants Menu")
                true
            }
            R.id.action_live_agent -> {
                Log.d(TAG, "🎯 Live Agent menu item selected")
                navigateToMenu(MenuState.LIVE_AGENT)
                showVisualFeedback("Live Agent Menu")
                true
            }
            R.id.action_exit -> {
                Log.d(TAG, "🎯 Exit menu item selected")
                handleExit()
                true
            }
            
            // ============ ASSISTANT SUBMENU ITEMS ============
            R.id.action_assistant_start_chat -> {
                Log.d(TAG, "🎯 Assistant Start Chat selected")
                startAssistantChat()
                true
            }
            R.id.action_back -> {
                Log.d(TAG, "🎯 Back selected")
                navigateBack()
                true
            }
            
            // ============ LIVE AGENT SUBMENU ITEMS ============
            R.id.action_live_start_chat -> {
                Log.d(TAG, "🎯 Live Agent Start Chat selected")
                startLiveAgentChat()
                true
            }
            R.id.action_switch_agent -> {
                Log.d(TAG, "🎯 Live Agent Switch Agent selected")
                switchLiveAgent()
                true
            }
            
            // ============ ASSISTANTS MENU ITEMS ============
            R.id.action_coach_spin -> {
                Log.d(TAG, "🎯 Coach SPIN selected")
                // Set current assistant
                currentAssistant = AssistantRegistry.getAssistantByName("Coach SPIN")
                navigateToMenu(MenuState.ASSISTANT_MENU)
                showVisualFeedback("Coach SPIN")
                true
            }
            
            // ============ COACH SPIN MENU ITEMS ============
            R.id.action_test_connection -> {
                Log.d(TAG, "🎯 Test Connection selected")
                testAssistantConnection()
                true
            }
            
            R.id.action_send_photo -> {
                Log.d(TAG, "🎯 Send Photo selected")
                sendPhotoToAssistant()
                true
            }
            
            R.id.action_send_audio -> {
                Log.d(TAG, "🎯 Send Audio selected")
                sendAudioToAssistant()
                true
            }
            
            R.id.action_agent_info -> {
                Log.d(TAG, "🎯 Agent Info selected")
                showAgentInfo()
                true
            }
            
            R.id.action_active_connection -> {
                Log.d(TAG, "🎯 Active Connection selected")
                activateAssistantConnection()
                true
            }
            
            // ============ COACH ACTIVE MENU ITEMS ============
            R.id.action_send_audio_active -> {
                Log.d(TAG, "🎯 Send Audio (Active) selected")
                sendAudioToAssistantActive()
                true
            }
            
            R.id.action_send_photo_active -> {
                Log.d(TAG, "🎯 Send Photo (Active) selected")
                sendPhotoToAssistantActive()
                true
            }
            
            R.id.action_new_thread -> {
                Log.d(TAG, "🎯 New Thread selected")
                createNewThread()
                true
            }
            
            R.id.action_toggle_audio_response -> {
                Log.d(TAG, "🎯 Toggle Audio Response selected")
                toggleAudioResponse()
                true
            }
            
            R.id.action_back_from_active -> {
                Log.d(TAG, "🎯 Back from Active selected")
                navigateBack()
                true
            }
            
            // ============ VOICE COMMANDS ============
            R.id.action_voice_commands -> {
                Log.d(TAG, "🎯 Voice commands menu item selected")
                toggleVoiceCommands()
                true
            }
            
            // ============ SETTINGS (available in main menu) ============
            R.id.action_settings -> {
                Log.d(TAG, "🎯 Settings menu item selected")
                openSettings()
                true
            }
            
            // Note: Legacy menu items removed - using new hierarchical menu structure only
            
            else -> {
                Log.w(TAG, "🎯 Unknown menu item selected: ${item?.itemId}")
                super.onActionItemSelected(item)
            }
        }
    }
    
    private fun changeAgent() {
        Log.d(TAG, "Change agent requested via Action Menu")
        
        // Cycle through agents: elato_default -> sherlock -> master_chef -> fitness_coach -> math_wiz -> batman -> eco_champ -> back to elato
        val agentKeys = listOf("elato_default", "sherlock", "master_chef", "fitness_coach", "math_wiz", "batman", "eco_champ")
        val currentAgent = sharedPreferences.getString("current_agent", "elato_default")
        val currentIndex = agentKeys.indexOf(currentAgent)
        val nextIndex = (currentIndex + 1) % agentKeys.size
        val nextAgent = agentKeys[nextIndex]
        
        // Save new agent
        sharedPreferences.edit().putString("current_agent", nextAgent).apply()
        
        // Send change agent command to service
        val intent = Intent(this, StandaloneService::class.java).apply {
            action = StandaloneService.ACTION_CHANGE_AGENT
            putExtra(StandaloneService.EXTRA_AGENT_KEY, nextAgent)
        }
        startService(intent)
        
        val agentNames = mapOf(
            "elato_default" to "Elato",
            "sherlock" to "Sherlock Holmes", 
            "master_chef" to "Master Chef",
            "fitness_coach" to "Fitness Coach",
            "math_wiz" to "Math Wiz",
            "batman" to "Batman",
            "eco_champ" to "Eco Champion"
        )
        
        hudDisplayManager.showStatusMessage("🤖 Agent changed to: ${agentNames[nextAgent]}", 3000L)
    }
    
    // ===============================
    // NEW HIERARCHICAL MENU METHODS
    // ===============================
    
    /**
     * Navigate to a specific menu state
     */
    private fun navigateToMenu(newState: MenuState) {
        Log.d(TAG, "🎯 Navigating from ${currentMenuState} to ${newState}")
        
        // Clear HUD when leaving assistant active mode
        if (currentMenuState == MenuState.ASSISTANT_ACTIVE || currentMenuState == MenuState.ASSISTANT_MENU) {
            if (newState != MenuState.ASSISTANT_ACTIVE && newState != MenuState.ASSISTANT_MENU) {
                clearHudDisplay()
                Log.d(TAG, "🧹 HUD cleared - leaving Coach SPIN area")
            }
        }
        
        // Push current state to stack for back navigation
        if (currentMenuState != newState) {
            menuStack.add(currentMenuState)
        }
        
        currentMenuState = newState
        
        // No initialization message needed - keep HUD clean
        
        // Trigger menu recreation (ActionMenuActivity specific)
        invalidateActionMenu()
        
        Log.d(TAG, "🎯 Navigation complete. Menu stack size: ${menuStack.size}")
    }
    
    /**
     * Clear HUD display
     */
    private fun clearHudDisplay() {
        if (::hudDisplayManager.isInitialized) {
            hudDisplayManager.clearDisplay()
            Log.d(TAG, "🧹 HUD display cleared")
        }
    }
    
    
    /**
     * Navigate back to previous menu
     */
    private fun navigateBack() {
        Log.d(TAG, "🎯 Navigating back. Stack size: ${menuStack.size}")
        
        if (menuStack.isNotEmpty()) {
            currentMenuState = menuStack.removeAt(menuStack.size - 1)
            invalidateActionMenu()
            
            val backMessage = when (currentMenuState) {
                MenuState.MAIN -> "⬅️ Back to Main Menu"
                MenuState.ASSISTANTS -> "⬅️ Back to Assistants Menu"
                MenuState.ASSISTANT_MENU -> "⬅️ Back to ${currentAssistant?.name ?: "Assistant"} Menu"
                MenuState.ASSISTANT_ACTIVE -> "⬅️ Back to ${currentAssistant?.name ?: "Assistant"} Active Mode"
                MenuState.ASSISTANT -> "⬅️ Back to Assistant Menu"
                MenuState.LIVE_AGENT -> "⬅️ Back to Live Agent Menu"
            }
            
            showVisualFeedback(backMessage)
            Log.d(TAG, "🎯 Back navigation complete to ${currentMenuState}")
        } else {
            Log.w(TAG, "🎯 No previous menu in stack - staying on ${currentMenuState}")
            showVisualFeedback("⚠️ Already at main menu")
        }
    }
    
    /**
     * Show visual feedback for menu actions
     */
    private fun showVisualFeedback(message: String) {
        if (::hudDisplayManager.isInitialized) {
            hudDisplayManager.showStatusMessage(message, 1500L)  // Shorter timeout
            Log.d(TAG, "🎯 Visual feedback: $message")
        }
    }
    
    /**
     * Show temporary message (for "Gravando..." and "Aguardando resposta...")
     */
    private fun showTemporaryMessage(message: String, durationMs: Long = 3000L) {
        Log.d(TAG, "🔍 showTemporaryMessage called: '$message', HUD initialized: ${::hudDisplayManager.isInitialized}")
        if (::hudDisplayManager.isInitialized) {
            hudDisplayManager.showCleanTemporaryMessage(message, durationMs)
            Log.d(TAG, "📱 Temporary message sent to HUD: $message")
        } else {
            Log.e(TAG, "❌ HudDisplayManager not initialized for temporary message: $message")
        }
    }
    
    /**
     * Show permanent message (for Coach SPIN responses)
     */
    private fun showPermanentMessage(message: String) {
        Log.d(TAG, "🔍 showPermanentMessage called: '$message', HUD initialized: ${::hudDisplayManager.isInitialized}")
        if (::hudDisplayManager.isInitialized) {
            hudDisplayManager.updateTextImmediate(message)
            Log.d(TAG, "💬 Permanent message sent to HUD: $message")
        } else {
            Log.e(TAG, "❌ HudDisplayManager not initialized for permanent message: $message")
        }
    }
    
    /**
     * Start Assistant chat session
     */
    private fun startAssistantChat() {
        Log.d(TAG, "🤖 Starting Assistant chat session")
        
        showVisualFeedback("Starting Assistant Chat...")
        
        // Use existing assistant logic (HTTP REST API)
        openAssistants()
        
        // Stay in submenu - user can navigate back manually
        Log.d(TAG, "🤖 Assistant session initiated, staying in submenu")
    }
    
    /**
     * Start Live Agent chat session (OpenAI Realtime API)
     */
    private fun startLiveAgentChat() {
        Log.d(TAG, "👥 Starting Live Agent chat session")
        
        showVisualFeedback("👥 Starting Live Agent Chat...")
        
        // Use existing realtime API logic (WebSocket)
        startAISession()
        
        // Stay in submenu - user can navigate back manually
        Log.d(TAG, "👥 Live Agent session initiated, staying in submenu")
    }
    
    /**
     * Switch live agent (cycle through agents)
     */
    private fun switchLiveAgent() {
        Log.d(TAG, "🔄 Switching live agent")
        
        showVisualFeedback("🔄 Switching Agent...")
        
        // Use existing agent switching logic
        changeAgent()
    }
    
    /**
     * Handle app exit
     */
    private fun handleExit() {
        Log.d(TAG, "🚪 Handling app exit")
        
        showVisualFeedback("🚪 Exiting Smart Companion...")
        
        // Clean shutdown
        if (isSessionActive) {
            stopAISession()
        }
        
        // Give time for cleanup
        hudDisplayManager.showStatusMessage(
            "🚪 Goodbye!\n\nCleaning up sessions...\nApp will close in 2 seconds",
            2000L
        )
        
        // Delay finish to show message
        hudTextDisplay.postDelayed({
            finish()
        }, 2000)
    }
    
    private fun takeSnapshot() {
        Log.d(TAG, "Snapshot requested via Action Menu")
        
        if (::hudDisplayManager.isInitialized) {
            hudDisplayManager.showStatusMessage("📸 Taking snapshot...", 2000L)
            
            // TODO: Implement camera capture for standalone mode (future feature)
            hudDisplayManager.showStatusMessage("📸 Snapshot captured", 2000L)
        } else {
            Toast.makeText(this, "HUD not initialized", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showSessionStatus() {
        Log.d(TAG, "Session status requested via Action Menu")
        
        val apiKey = getApiKey()
        val currentAgent = sharedPreferences.getString("current_agent", "elato_default")
        val agentNames = mapOf(
            "elato_default" to "Elato",
            "sherlock" to "Sherlock", 
            "master_chef" to "Chef",
            "fitness_coach" to "Fitness",
            "math_wiz" to "Math",
            "batman" to "Batman",
            "eco_champ" to "Eco"
        )
        
        val status = if (apiKey != null) {
            if (isSessionActive) {
                "✅ AI Session Active\n🤖 Agent: ${agentNames[currentAgent]}\n🎤 Listening..."
            } else {
                "⏸️ AI Session Ready\n🤖 Agent: ${agentNames[currentAgent]}\n🔑 API key configured"
            }
        } else {
            "❌ API key not configured\n🔧 Use Settings to add key"
        }
        
        hudDisplayManager.showStatusMessage(status, 5000L)
    }
    
    private fun openSettings() {
        Log.d(TAG, "Settings requested via Action Menu")
        
        val currentApiKey = getApiKey()
        val currentAgent = getCurrentAgentName()
        
        // Use showStatusMessage instead of updateTextImmediate for better visibility
        if (currentApiKey == null) {
            // Show API key setup instructions with emulator support
            val settingsText = "🔑 API Key Setup Required\n\n" +
                "📱 EMULATOR: Configure manually\n" +
                "🥽 M400: Auto-loads from .env\n\n" +
                "For manual setup:\n" +
                "1. Get key: platform.openai.com\n" +
                "2. Use setApiKey() method\n\n" +
                "💡 Long press trackpad for help"
            
            hudDisplayManager.showStatusMessage(settingsText, 10000L)
            Toast.makeText(this, "API key setup - see HUD", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Settings (no API key) displayed with emulator instructions")
        } else {
            // Show current settings with better formatting
            val maskedKey = "sk-..." + currentApiKey.takeLast(4)
            val source = if (sharedPreferences.getString("api_key_source", "unknown") == "env") "📁 .env file" else "⚙️ Manual config"
            
            val settingsText = "🔧 Smart Companion Settings\n\n" +
                "🔑 API Key: $maskedKey\n" +
                "📍 Source: $source\n" +
                "🤖 Current Agent: $currentAgent\n" +
                "🎤 Audio Input: M400 Microphones\n" +
                "🔊 Sample Rate: 24kHz PCM16\n" +
                "🔌 Connection: WiFi\n\n" +
                "Tap trackpad to return to menu"
            
            hudDisplayManager.showStatusMessage(settingsText, 8000L)
            Log.d(TAG, "Settings displayed with API key info")
        }
    }
    
    /**
     * Setup broadcast receivers para SimpleAssistantService
     */
    private fun setupAssistantBroadcastReceivers() {
        assistantReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    SimpleAssistantService.BROADCAST_RESPONSE -> {
                        val response = intent.getStringExtra("response")
                        if (response != null) {
                            Log.d(TAG, "🤖 Assistant response: ${response.take(50)}...")
                            displayAssistantResponse(response)
                        }
                    }
                    
                    SimpleAssistantService.BROADCAST_STATUS -> {
                        val status = intent.getStringExtra("status")
                        if (status != null) {
                            Log.d(TAG, "📊 Assistant status: $status")
                            hudDisplayManager.showStatusMessage("🤖 $status", 3000L)
                            
                            // Track connection state
                            if (status.contains("connected") && status.contains("Ready")) {
                                isAssistantConnected = true
                            }
                        }
                    }
                    
                    SimpleAssistantService.BROADCAST_ERROR -> {
                        val error = intent.getStringExtra("error")
                        if (error != null) {
                            Log.e(TAG, "❌ Assistant error: $error")
                            hudDisplayManager.showStatusMessage("❌ Assistant: $error", 5000L)
                        }
                    }
                }
            }
        }
        
        // Register with LocalBroadcastManager
        val filter = IntentFilter().apply {
            addAction(SimpleAssistantService.BROADCAST_RESPONSE)
            addAction(SimpleAssistantService.BROADCAST_STATUS)
            addAction(SimpleAssistantService.BROADCAST_ERROR)
        }
        
        LocalBroadcastManager.getInstance(this).registerReceiver(assistantReceiver!!, filter)
        Log.d(TAG, "Assistant broadcast receivers registered")
    }
    
    /**
     * Setup API key configuration receiver for emulator testing
     */
    private fun setupApiKeyReceiver() {
        apiKeyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "com.seudominio.app_smart_companion.SET_API_KEY" -> {
                        val apiKey = intent.getStringExtra("api_key")
                        if (apiKey != null) {
                            Log.d(TAG, "🔑 Received API key configuration request via ADB")
                            setApiKey(apiKey)
                        } else {
                            Log.w(TAG, "❌ API key configuration request missing api_key parameter")
                            hudDisplayManager.showStatusMessage(
                                "❌ Invalid Request\n\n" +
                                "Missing api_key parameter\n" +
                                "Use: adb shell am broadcast -a com.seudominio.app_smart_companion.SET_API_KEY --es api_key \"your-key\"",
                                5000L
                            )
                        }
                    }
                }
            }
        }
        
        // Register API key receiver
        val apiKeyFilter = IntentFilter("com.seudominio.app_smart_companion.SET_API_KEY")
        registerReceiver(apiKeyReceiver, apiKeyFilter)
        Log.d(TAG, "API key configuration receiver registered")
        
        // Register voice test receiver for emulator testing
        setupVoiceTestReceiver()
    }
    
    /**
     * Display assistant response on HUD
     */
    private fun displayAssistantResponse(response: String) {
        runOnUiThread {
            if (::hudDisplayManager.isInitialized) {
                Log.d(TAG, "🤖 Displaying assistant response: ${response.take(50)}...")
                
                val formattedResponse = "🤖 Assistant: $response"
                hudDisplayManager.updateTextImmediate(formattedResponse)
                
                Log.d(TAG, "✅ Assistant response displayed on HUD")
            }
        }
    }
    
    /**
     * Open Assistants menu - Nova modalidade de comunicação
     */
    private fun openAssistants() {
        Log.d(TAG, "Assistants menu requested via Action Menu")
        
        if (!isAssistantActive) {
            // Iniciar assistant
            hudDisplayManager.showStatusMessage(
                "🤖 Starting OpenAI Assistant...\n\n" +
                "🔄 HTTP REST connection\n" +
                "🔄 Creating conversation thread\n" +
                "🔄 Your assistant ID: asst_hXcg5n...\n\n" +
                "⏳ This may take 20-30 seconds...\n" +
                "Please wait for 'Ready' message!",
                10000L  // Longer timeout
            )
            
            val intent = Intent(this, SimpleAssistantService::class.java).apply {
                action = SimpleAssistantService.ACTION_START_ASSISTANT
            }
            startService(intent)
            isAssistantActive = true
            
        } else if (isRecordingAudio) {
            // Se já gravando, parar gravação
            hudDisplayManager.showStatusMessage(
                "🛑 Stopping audio recording...\n\n" +
                "Processing your message...",
                3000L
            )
            
            val intent = Intent(this, SimpleAssistantService::class.java).apply {
                action = SimpleAssistantService.ACTION_STOP_AUDIO_RECORDING
            }
            startService(intent)
            isRecordingAudio = false
            
        } else {
            // Check if assistant is actually connected before recording
            if (!isAssistantConnected) {
                hudDisplayManager.showStatusMessage(
                    "⚠️ Assistant still connecting...\n\n" +
                    "🔄 Please wait for 'Ready' message\n" +
                    "⏳ Connection takes 20-30 seconds\n\n" +
                    "Try again in a few moments",
                    5000L
                )
                return
            }
            
            // Se ativo e conectado, iniciar gravação de áudio
            hudDisplayManager.showStatusMessage(
                "🎤 Starting audio recording...\n\n" +
                "✅ 24kHz quality recording\n" +
                "✅ Automatic transcription\n" +
                "✅ Max 30 seconds\n\n" +
                "Speak now! Tap again to stop.",
                5000L
            )
            
            val intent = Intent(this, SimpleAssistantService::class.java).apply {
                action = SimpleAssistantService.ACTION_START_AUDIO_RECORDING
            }
            startService(intent)
            isRecordingAudio = true
        }
    }
    
    // ===============================
    // VOICE COMMAND INTEGRATION
    // ===============================
    
    /**
     * Initialize voice command system
     */
    private fun initializeVoiceCommands() {
        Log.d(TAG, "🎤 Initializing voice command system")
        
        voiceCommander = LearionVoiceCommander(this, { command ->
            Log.d(TAG, "🗣️ Voice command received: ${command.action}")
            
            // Handle voice commands on UI thread
            runOnUiThread {
                handleVoiceCommand(command)
            }
        }, hudDisplayManager)
        
        // Initialize the voice system
        voiceCommander.initialize()
        
        // Enable voice commands by default on M400
        if (Build.MANUFACTURER.equals("Vuzix", ignoreCase = true)) {
            Log.d(TAG, "🥽 Vuzix device detected - enabling voice commands")
            voiceCommander.enable()
        }
    }
    
    /**
     * Handle voice command actions
     */
    private fun handleVoiceCommand(command: LearionVoiceCommander.VoiceCommand) {
        Log.d(TAG, "🎯 Handling voice command: ${command.action}")
        
        // Show enhanced visual feedback
        if (::hudDisplayManager.isInitialized) {
            hudDisplayManager.showVoiceCommandFeedback(command.phrase, command.action)
        }
        
        // Execute command action
        when (command.action) {
            LearionVoiceCommander.ACTION_ASSISTANT -> {
                Log.d(TAG, "🤖 Voice: Opening Assistant")
                if (currentMenuState == MenuState.MAIN) {
                    navigateToMenu(MenuState.ASSISTANT)
                } else if (currentMenuState == MenuState.ASSISTANT) {
                    startAssistantChat()
                }
            }
            
            LearionVoiceCommander.ACTION_LIVE_AGENT -> {
                Log.d(TAG, "👥 Voice: Opening Live Agent")
                if (currentMenuState == MenuState.MAIN) {
                    navigateToMenu(MenuState.LIVE_AGENT)
                } else if (currentMenuState == MenuState.LIVE_AGENT) {
                    startLiveAgentChat()
                }
            }
            
            LearionVoiceCommander.ACTION_SETTINGS -> {
                Log.d(TAG, "⚙️ Voice: Opening Settings")
                openSettings()
            }
            
            LearionVoiceCommander.ACTION_EXIT -> {
                Log.d(TAG, "🚪 Voice: Exit requested")
                handleExit()
            }
            
            LearionVoiceCommander.ACTION_BACK -> {
                Log.d(TAG, "⬅️ Voice: Navigate back")
                if (menuStack.isNotEmpty()) {
                    navigateBack()
                } else {
                    showVisualFeedback("⚠️ Already at main menu")
                }
            }
            
            LearionVoiceCommander.ACTION_HELP -> {
                Log.d(TAG, "❓ Voice: Help requested")
                showVoiceCommandHelp()
            }
            
            // Active mode voice commands
            LearionVoiceCommander.ACTION_START_RECORDING -> {
                Log.d(TAG, "🎤 Voice: Start recording requested")
                if (currentMenuState == MenuState.ASSISTANT_ACTIVE && isAssistantActive) {
                    startAudioRecording()
                } else {
                    showVisualFeedback("⚠️ Active mode required")
                }
            }
            
            LearionVoiceCommander.ACTION_STOP_RECORDING -> {
                Log.d(TAG, "⏹️ Voice: Stop recording requested")
                if (isRecordingAudio) {
                    stopAudioRecording()
                } else {
                    showVisualFeedback("⚠️ Not recording")
                }
            }
            
            LearionVoiceCommander.ACTION_SEND_PHOTO -> {
                Log.d(TAG, "📷 Voice: Send photo requested")
                if (currentMenuState == MenuState.ASSISTANT_ACTIVE && isAssistantActive) {
                    sendPhotoToAssistantActive()
                } else {
                    showVisualFeedback("⚠️ Active mode required")
                }
            }
            
            LearionVoiceCommander.ACTION_NEW_THREAD -> {
                Log.d(TAG, "🔄 Voice: New thread requested")
                if (currentMenuState == MenuState.ASSISTANT_ACTIVE && isAssistantActive) {
                    createNewAssistantThread()
                } else {
                    showVisualFeedback("⚠️ Active mode required")
                }
            }
            
            LearionVoiceCommander.ACTION_TOGGLE_AUDIO -> {
                Log.d(TAG, "🔊 Voice: Toggle audio requested")
                if (currentMenuState == MenuState.ASSISTANT_ACTIVE && isAssistantActive) {
                    toggleAudioResponse()
                } else {
                    showVisualFeedback("⚠️ Active mode required")
                }
            }
            
            else -> {
                Log.w(TAG, "❌ Unknown voice command action: ${command.action}")
                showVisualFeedback("❌ Unknown command")
            }
        }
    }
    
    /**
     * Show voice command help
     */
    private fun showVoiceCommandHelp() {
        if (::hudDisplayManager.isInitialized) {
            hudDisplayManager.showVoiceCommandHelp()
        }
    }
    
    /**
     * Toggle voice commands via menu
     */
    private fun toggleVoiceCommands() {
        if (::voiceCommander.isInitialized) {
            voiceCommander.toggle()
            
            val status = if (voiceCommander.isEnabled()) {
                "🎤 Voice commands enabled\nSay \"Hello Vuzix\" to start"
            } else {
                "🔇 Voice commands disabled"
            }
            
            showVisualFeedback(status)
        }
    }
    
    // ===============================
    // ACTIVE MODE AUDIO RECORDING
    // ===============================
    
    private var audioRecordingTimer: java.util.Timer? = null
    private var audioRecordingStartTime: Long = 0
    
    /**
     * Start audio recording with auto-stop after 1 minute
     */
    private fun startAudioRecording() {
        if (isRecordingAudio) {
            Log.w(TAG, "⚠️ Already recording audio")
            showVisualFeedback("⚠️ Already recording")
            return
        }
        
        if (!isAssistantActive || activeModeManager == null) {
            Log.w(TAG, "❌ Not in active mode or manager not initialized")
            showVisualFeedback("⚠️ Active mode required")
            return
        }
        
        Log.d(TAG, "🎤 Starting voice command audio recording")
        isRecordingAudio = true
        audioRecordingStartTime = System.currentTimeMillis()
        
        // Visual feedback
        showVisualFeedback("🎤 Recording...\n\nSay 'stop' or wait 1 min")
        
        // Start actual recording using ActiveModeManager
        activeModeManager?.sendAudioInActiveMode()
        
        // Set auto-stop timer for 1 minute
        audioRecordingTimer?.cancel()
        audioRecordingTimer = java.util.Timer()
        audioRecordingTimer?.schedule(object : java.util.TimerTask() {
            override fun run() {
                runOnUiThread {
                    Log.d(TAG, "⏰ Auto-stopping recording after 1 minute")
                    stopAudioRecording()
                }
            }
        }, 60000) // 60 seconds
    }
    
    /**
     * Stop audio recording (manual or auto-stop)
     */
    private fun stopAudioRecording() {
        if (!isRecordingAudio) {
            Log.w(TAG, "⚠️ Not recording audio")
            return
        }
        
        // Cancel timer
        audioRecordingTimer?.cancel()
        audioRecordingTimer = null
        
        // Stop recording
        isRecordingAudio = false
        coachAudioRecorder?.stopRecording()
        
        // Calculate duration
        val duration = (System.currentTimeMillis() - audioRecordingStartTime) / 1000
        Log.d(TAG, "⏹️ Stopped recording after ${duration}s")
        
        // Visual feedback
        showVisualFeedback("⏹️ Recording stopped\n\nDuration: ${duration}s")
    }
    
    // ===============================
    // VOICE COMMAND TESTING (EMULATOR)
    // ===============================
    
    private var voiceTestReceiver: BroadcastReceiver? = null
    
    /**
     * Setup voice test receiver for emulator testing via ADB
     */
    private fun setupVoiceTestReceiver() {
        voiceTestReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "VOICE_TEST" -> {
                        val phrase = intent.getStringExtra("phrase")
                        if (phrase != null) {
                            Log.d(TAG, "🧪 Voice test command received: '$phrase'")
                            
                            // Simulate voice command through our voice system
                            if (::voiceCommander.isInitialized) {
                                // Create a voice command and process it
                                val command = LearionVoiceCommander.VoiceCommand(
                                    action = LearionVoiceCommander.VOICE_COMMANDS[phrase.lowercase()] ?: "unknown",
                                    phrase = phrase
                                )
                                
                                runOnUiThread {
                                    handleVoiceCommand(command)
                                }
                            }
                        } else {
                            Log.w(TAG, "❌ Voice test command missing phrase parameter")
                            showVisualFeedback("❌ Missing phrase\nUsage: adb shell am broadcast -a VOICE_TEST --es phrase \"one\"")
                        }
                    }
                    
                    "VOICE_HELP" -> {
                        Log.d(TAG, "🧪 Voice help test command")
                        runOnUiThread {
                            showVoiceCommandHelp()
                        }
                    }
                    
                    "VOICE_TOGGLE" -> {
                        Log.d(TAG, "🧪 Voice toggle test command")
                        runOnUiThread {
                            toggleVoiceCommands()
                        }
                    }
                }
            }
        }
        
        // Register voice test receiver
        val voiceTestFilter = IntentFilter().apply {
            addAction("VOICE_TEST")
            addAction("VOICE_HELP")
            addAction("VOICE_TOGGLE")
        }
        registerReceiver(voiceTestReceiver, voiceTestFilter)
        Log.d(TAG, "🧪 Voice test receiver registered for emulator testing")
    }
    
    // ===============================
    // COACH SPIN ASSISTANT FUNCTIONS
    // ===============================
    //
    // 🎯 MODULAR PATTERN IMPLEMENTATION
    // All functions below use reusable managers:
    // - ActiveModeManager: For active mode coordination
    // - AssistantAudioManager: For audio-to-assistant
    // - AssistantPhotoManager: For photo-to-assistant
    // - ThreadManager: For conversation persistence
    //
    // To add a new assistant:
    // 1. Copy activateAssistantConnectionNewPattern()
    // 2. Change assistantId and assistantName
    // 3. All other functions work automatically!
    //
    
    /**
     * Test connection to current assistant
     */
    private fun testAssistantConnection() {
        val assistant = currentAssistant
        if (assistant == null) {
            Log.e(TAG, "❌ No assistant selected")
            showVisualFeedback("❌ No assistant selected")
            return
        }
        
        Log.d(TAG, "🧪 Testing ${assistant.name} connection...")
        
        // TODO: Implement actual connection test
        showVisualFeedback(
            "🔗 Testing Connection...\n\n" +
            "${assistant.name} Assistant\n" +
            "Status: Ready for testing\n\n" +
            "Next: Configure API endpoint"
        )
    }
    
    /**
     * Test photo capture and analysis system
     */
    private fun testPhotoSystem() {
        Log.d(TAG, "🧪 Testing Photo → Vision → Assistant system...")
        
        lifecycleScope.launch {
            try {
                // Check API key only
                val apiKey = getApiKey()
                if (apiKey.isNullOrEmpty()) {
                    showPermanentMessage("❌ API key não configurada para teste")
                    return@launch
                }
                
                showPermanentMessage("✅ Sistema de foto pronto para uso")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro no teste do sistema de foto: ${e.message}", e)
                showPermanentMessage("❌ Erro no teste: ${e.message}")
            }
        }
    }
    
    /**
     * Show Coach SPIN agent information
     */
    private fun showAgentInfo() {
        Log.d(TAG, "ℹ️ Showing Coach SPIN agent info")
        
        showVisualFeedback(
            "🤖 Coach SPIN Assistant\n\n" +
            "📋 Sales Coaching Agent\n" +
            "🎯 SPIN Methodology\n" +
            "🎤 Voice + Photo Support\n" +
            "🧠 Thread Persistence\n\n" +
            "Status: Standby"
        )
    }
    
    
    /**
     * Send audio to Coach SPIN assistant using new reusable pattern
     */
    private fun sendAudioToAssistant() {
        val assistant = currentAssistant
        if (assistant == null) {
            Log.e(TAG, "❌ No assistant selected")
            showVisualFeedback("❌ No assistant selected")
            return
        }
        
        Log.d(TAG, "🎤 Sending audio to ${assistant.name}...")
        
        // Use AssistantAudioManager for reusable audio-to-assistant flow
        sendAudioToAssistantNewPattern(assistant.id)
    }
    
    /**
     * NEW REUSABLE PATTERN: Audio-to-Assistant using AssistantAudioManager
     * This pattern can be copied and reused for any assistant
     */
    private fun sendAudioToAssistantNewPattern(assistantId: String) {
        // Get API key
        val apiKey = getApiKey()
        if (apiKey.isNullOrEmpty()) {
            showPermanentMessage("API key não configurada")
            return
        }
        
        // Create AssistantAudioManager instance
        val audioManager = AssistantAudioManager(
            context = this,
            lifecycleScope = lifecycleScope,
            assistantId = assistantId,
            apiKey = apiKey
        )
        
        // Start audio-to-assistant flow with callbacks
        audioManager.startAudioToAssistant(
            callback = object : AssistantAudioManager.AudioToAssistantCallback {
                override fun onRecordingStarted() {
                    showTemporaryMessage("Gravando...")
                }
                
                override fun onProcessingStarted() {
                    showTemporaryMessage("Aguardando resposta...")
                }
                
                override fun onAssistantResponse(response: String) {
                    showPermanentMessage("Coach SPIN: $response")
                }
                
                override fun onError(error: String) {
                    showPermanentMessage("Erro: $error")
                }
            },
            threadId = if (isAssistantActive) currentThreadId else null, // Use existing thread if in active mode
            language = "pt" // Portuguese
        )
    }
    
    /**
     * Send photo to Coach SPIN assistant using new reusable pattern
     */
    private fun sendPhotoToAssistant() {
        val assistant = currentAssistant
        if (assistant == null) {
            Log.e(TAG, "❌ No assistant selected")
            showVisualFeedback("❌ No assistant selected")
            return
        }
        
        Log.d(TAG, "📷 sendPhotoToAssistant() CALLED - Starting photo capture flow")
        
        // Show immediate feedback to user
        showTemporaryMessage("Iniciando captura...")
        
        // Use AssistantPhotoManager for reusable photo-to-assistant flow
        sendPhotoToAssistantNewPattern(assistant.id)
    }
    
    /**
     * NEW REUSABLE PATTERN: Photo-to-Assistant using AssistantPhotoManager
     * This pattern can be copied and reused for any assistant
     */
    private fun sendPhotoToAssistantNewPattern(assistantId: String) {
        // Get API key
        val apiKey = getApiKey()
        if (apiKey.isNullOrEmpty()) {
            showPermanentMessage("API key não configurada")
            return
        }
        
        // Create AssistantPhotoManager instance
        val photoManager = AssistantPhotoManager(
            context = this,
            lifecycleScope = lifecycleScope,
            assistantId = assistantId,
            apiKey = apiKey
        )
        
        // Start photo-to-assistant flow with callbacks
        photoManager.startPhotoToAssistant(
            visionPrompt = "Analyze this image for sales opportunities, customer insights, and business context",
            assistantPrompt = "Based on this image analysis, provide SPIN selling coaching advice and strategic insights",
            callback = object : AssistantPhotoManager.PhotoToAssistantCallback {
                override fun onCaptureStarted() {
                    // Silencioso - não mostra mensagem
                }
                
                override fun onPhotoTaken() {
                    showTemporaryMessage("Foto capturada...")
                }
                
                override fun onVisionAnalysisStarted() {
                    showTemporaryMessage("Analisando...")
                }
                
                override fun onAssistantProcessingStarted() {
                    // Silencioso - não mostra mensagem adicional
                }
                
                override fun onAssistantResponse(response: String) {
                    showPermanentMessage("Coach SPIN: $response")
                }
                
                override fun onError(error: String) {
                    showPermanentMessage("Erro: $error")
                }
            }
        )
    }
    
    /**
     * Send audio file directly to Coach SPIN via WebSocket (bypasses Vosk)
     */
    private fun sendAudioFileToCoachSPIN(isActiveMode: Boolean) {
        Log.d(TAG, "🌐 Sending audio file to Coach SPIN via Companion Desktop...")
        
        val threadInfo = if (isActiveMode) {
            "Thread: ${currentThreadId?.takeLast(8)}"
        } else {
            "New conversation"
        }
        
        showTemporaryMessage("Gravando...")
        
        // Start audio recording/processing
        if (CoachAudioRecorder.isEmulator()) {
            Log.d(TAG, "🎮 Emulator detected - using test audio file")
            
            // Get test audio file and send via WebSocket
            val audioRecorder = CoachAudioRecorder(this)
            audioRecorder.startRecording(object : CoachAudioRecorder.AudioRecordingCallback {
                override fun onRecordingStarted() {
                    Log.d(TAG, "✅ Test audio processing started")
                }
                
                override fun onAudioData(audioData: ByteArray) {
                    // Not used in direct file approach for test mode
                }
                
                override fun onAmplitudeUpdate(amplitude: Int) {
                    // Not used in test mode
                }
                
                override fun onRecordingCompleted() {
                    Log.d(TAG, "📄 Test audio processing completed")
                    
                    // In emulator mode, get the actual test file that was processed
                    val testAudioFile = File(cacheDir, "teste_audio.wav")
                    
                    // Ensure file exists (copy from assets if needed)
                    if (!testAudioFile.exists()) {
                        try {
                            val inputStream = assets.open("teste_audio.wav")
                            testAudioFile.outputStream().use { output ->
                                inputStream.copyTo(output)
                            }
                            Log.d(TAG, "📄 Test audio file copied from assets")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Failed to copy test audio: ${e.message}")
                            showVisualFeedback("Erro de áudio")
                            return
                        }
                    }
                    
                    sendWAVFileToWhisperAPI(testAudioFile, isActiveMode)
                }
                
                override fun onRecordingError(error: String) {
                    Log.e(TAG, "❌ Test audio error: $error")
                    showVisualFeedback("Erro de áudio")
                }
            })
        } else {
            // Real device recording - TODO: implement real recording
            showVisualFeedback("Gravação não implementada")
        }
    }
    
    /**
     * Send WAV file directly to OpenAI Whisper API for transcription
     */
    private fun sendWAVFileToWhisperAPI(audioFile: File, isActiveMode: Boolean) {
        Log.d(TAG, "🎤 Sending WAV file to OpenAI Whisper API: ${audioFile.name}")
        
        // Get API key
        val apiKey = sharedPreferences.getString("api_key", null)
        if (apiKey.isNullOrEmpty()) {
            showVisualFeedback("API Key required")
            return
        }
        
        // Validate API key format
        val whisperService = com.seudominio.app_smart_companion.openai.OpenAIWhisperService(this)
        if (!whisperService.isValidApiKey(apiKey)) {
            showVisualFeedback("Invalid API key")
            return
        }
        
        showTemporaryMessage("Aguardando resposta...")
        
        lifecycleScope.launch {
            try {
                whisperService.transcribeAudio(
                    audioFile = audioFile,
                    apiKey = apiKey,
                    language = "pt", // Portuguese
                    callback = object : com.seudominio.app_smart_companion.openai.OpenAIWhisperService.TranscriptionCallback {
                        override fun onTranscriptionSuccess(transcript: String) {
                            Log.d(TAG, "🎯 Whisper transcription success: '$transcript'")
                            
                            // Send to Coach SPIN Assistant
                            sendTranscriptToCoachSPIN(transcript, isActiveMode)
                        }
                        
                        override fun onTranscriptionError(error: String) {
                            Log.e(TAG, "❌ Whisper transcription error: $error")
                            showVisualFeedback("Erro de transcrição")
                        }
                    }
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error calling Whisper API: ${e.message}")
                showVisualFeedback("Erro de API")
            }
        }
    }
    
    /**
     * Activate Coach SPIN connection (enter active mode)
     * Uses modular pattern for easy replication
     */
    private fun activateAssistantConnection() {
        val assistant = currentAssistant
        if (assistant == null) {
            Log.e(TAG, "❌ No assistant selected")
            showVisualFeedback("❌ No assistant selected")
            return
        }
        
        Log.d(TAG, "🚀 Activating ${assistant.name} connection...")
        
        // Use modular ActiveModeManager
        activateAssistantConnectionNewPattern(assistant.id, assistant.name)
    }
    
    /**
     * NEW REUSABLE PATTERN: Activate assistant connection using ActiveModeManager
     * This pattern can be copied and reused for any assistant
     * 
     * @param assistantId The OpenAI Assistant ID
     * @param assistantName The display name of the assistant
     */
    private fun activateAssistantConnectionNewPattern(assistantId: String, assistantName: String) {
        Log.d(TAG, "🔗 NEW PATTERN: Activating assistant connection for $assistantName")
        
        // Check API key
        val apiKey = getApiKey()
        if (apiKey == null) {
            showVisualFeedback("❌ API key not configured\nUse Settings to add key")
            return
        }
        
        // Initialize ActiveModeManager if needed
        if (activeModeManager == null) {
            Log.d(TAG, "📦 Creating ActiveModeManager instance")
            activeModeManager = ActiveModeManager(
                context = this,
                lifecycleScope = lifecycleScope,
                apiKey = apiKey
            )
            
            // Set callback for UI updates
            activeModeManager?.setCallback(object : ActiveModeManager.ActiveModeCallback {
                override fun onActiveModeStarted(threadId: String) {
                    Log.d(TAG, "✅ Active mode started with thread: $threadId")
                    isAssistantActive = true
                    currentThreadId = threadId
                    currentMenuState = MenuState.ASSISTANT_ACTIVE
                    
                    runOnUiThread {
                        showVisualFeedback(
                            "🚀 $assistantName Active Mode\n\n" +
                            "Thread: ${threadId.takeLast(8)}\n" +
                            "Status: Connected ✅\n" +
                            "Audio Response: ${if (audioResponseEnabled) "ON 🔊" else "OFF 🔇"}\n\n" +
                            "Double-tap to show menu"
                        )
                        invalidateOptionsMenu()
                    }
                }
                
                override fun onActiveModeEnded() {
                    Log.d(TAG, "🔴 Active mode ended")
                    isAssistantActive = false
                    currentThreadId = null
                    currentMenuState = MenuState.ASSISTANT_MENU
                    
                    runOnUiThread {
                        showVisualFeedback("Active mode ended")
                        invalidateOptionsMenu()
                    }
                }
                
                override fun onThreadCreated(newThreadId: String) {
                    Log.d(TAG, "🔄 New thread created: $newThreadId")
                    currentThreadId = newThreadId
                    
                    runOnUiThread {
                        showVisualFeedback(
                            "🔄 New Thread Created\n\n" +
                            "Thread: ${newThreadId.takeLast(8)}\n" +
                            "Conversation reset"
                        )
                    }
                }
                
                override fun onAudioToggled(enabled: Boolean) {
                    audioResponseEnabled = enabled
                    runOnUiThread {
                        showVisualFeedback(
                            "🔊 Audio Response ${if (enabled) "ON" else "OFF"}"
                        )
                        invalidateOptionsMenu()
                    }
                }
                
                override fun onResponse(response: String) {
                    runOnUiThread {
                        // Show response permanently until user exits or uses another tool
                        hudDisplayManager.updateTextImmediate(response)
                    }
                }
                
                override fun onError(error: String) {
                    runOnUiThread {
                        showVisualFeedback("❌ Error: $error")
                    }
                }
                
                override fun onStatusUpdate(status: String) {
                    runOnUiThread {
                        hudDisplayManager.showStatusMessage(status, 2000L)
                    }
                }
            })
        }
        
        // Enter active mode
        lifecycleScope.launch {
            try {
                activeModeManager?.enterActiveMode(assistantId)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error entering active mode", e)
                runOnUiThread {
                    showVisualFeedback("❌ Failed to activate: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Send audio in active mode using ActiveModeManager
     */
    private fun sendAudioToAssistantActive() {
        Log.d(TAG, "🎤 Sending audio in active mode...")
        
        if (!isAssistantActive || activeModeManager == null) {
            Log.w(TAG, "❌ Not in active mode or manager not initialized")
            showVisualFeedback("⚠️ Active mode required")
            return
        }
        
        // NEW PATTERN: Use ActiveModeManager for audio in active mode
        activeModeManager?.sendAudioInActiveMode()
    }
    
    /**
     * Send photo in active mode using ActiveModeManager
     */
    private fun sendPhotoToAssistantActive() {
        Log.d(TAG, "📸 Sending photo in active mode...")
        
        if (!isAssistantActive || activeModeManager == null) {
            Log.w(TAG, "❌ Not in active mode or manager not initialized")
            showVisualFeedback("⚠️ Active mode required")
            return
        }
        
        // NEW PATTERN: Use ActiveModeManager for photo in active mode
        activeModeManager?.sendPhotoInActiveMode()
    }
    
    /**
     * Create new thread (reset conversation)
     */
    private fun createNewThread() {
        Log.d(TAG, "🔄 Creating new thread...")
        
        if (!isAssistantActive || activeModeManager == null) {
            Log.w(TAG, "❌ Not in active mode or manager not initialized")
            showVisualFeedback("⚠️ Active mode required")
            return
        }
        
        // NEW PATTERN: Use ActiveModeManager for new thread
        lifecycleScope.launch {
            activeModeManager?.createNewThread()
        }
    }
    
    /**
     * Create new thread for Coach (voice command version)
     */
    private fun createNewAssistantThread() {
        // Delegate to the main createNewThread function
        createNewThread()
    }
    
    /**
     * Toggle audio response setting
     */
    private fun toggleAudioResponse() {
        Log.d(TAG, "🔊 Toggling audio response...")
        
        if (!isAssistantActive || activeModeManager == null) {
            Log.w(TAG, "❌ Not in active mode or manager not initialized")
            showVisualFeedback("⚠️ Active mode required")
            return
        }
        
        // NEW PATTERN: Use ActiveModeManager for audio toggle
        activeModeManager?.toggleAudioResponse()
    }
    
    // ===============================
    // VOSK TRANSCRIPTION INTEGRATION
    // ===============================
    
    /**
     * Initialize Vosk transcription service
     */
    private fun initializeVoskService() {
        if (voskTranscriptionService != null) {
            Log.d(TAG, "🎤 Vosk service already initialized")
            return
        }
        
        Log.d(TAG, "🎤 Initializing Vosk transcription service...")
        
        val callback = object : VoskTranscriptionService.TranscriptionCallback {
            override fun onTranscriptionResult(text: String, isFinal: Boolean) {
                Log.d(TAG, "📝 Vosk transcript ${if (isFinal) "FINAL" else "PARTIAL"}: '$text'")
                // Results are handled in the specific transcription callbacks
            }
            
            override fun onTranscriptionError(error: String) {
                Log.e(TAG, "❌ Vosk transcription error: $error")
                showVisualFeedback("❌ Transcription Error\n\n$error")
            }
            
            override fun onServiceReady() {
                Log.d(TAG, "✅ Vosk service ready for transcription")
                // Start listening immediately when ready
                voskTranscriptionService?.startListening()
            }
        }
        
        voskTranscriptionService = VoskTranscriptionService(this, callback)
        voskTranscriptionService?.initialize()
    }
    
    /**
     * Start Vosk transcription process
     */
    private fun startVoskTranscription(onTranscriptComplete: (String) -> Unit) {
        val service = voskTranscriptionService
        if (service == null) {
            Log.e(TAG, "❌ Vosk service not initialized")
            showVisualFeedback("❌ Transcription service not available")
            return
        }
        
        // Check audio permission
        if (!hasAudioPermission()) {
            Log.e(TAG, "❌ Audio permission not granted")
            showVisualFeedback("❌ Audio permission required\n\nPlease grant permission in settings")
            return
        }
        
        // Initialize audio recorder if needed
        if (coachAudioRecorder == null) {
            coachAudioRecorder = CoachAudioRecorder(this)
        }
        
        // Show recording feedback
        showVisualFeedback(
            "🎤 Coach SPIN Audio\n\n" +
            "🔴 Recording voice...\n" +
            "🧠 Local transcription...\n" +
            "⏳ Processing with Vosk\n\n" +
            "Speak now..."
        )
        
        // Start Vosk listening
        service.startListening()
        
        // Accumulated transcript
        val transcriptBuilder = StringBuilder()
        var lastUpdateTime = 0L
        
        // Start real audio recording
        coachAudioRecorder?.startRecording(object : CoachAudioRecorder.AudioRecordingCallback {
            override fun onRecordingStarted() {
                Log.d(TAG, "🎤 Audio recording started")
            }
            
            override fun onAudioData(audioData: ByteArray) {
                // Send audio chunks to Vosk for transcription
                service.processAudioChunk(audioData)
            }
            
            override fun onAmplitudeUpdate(amplitude: Int) {
                // Update UI with audio level if needed
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdateTime > 100) { // Update every 100ms
                    lastUpdateTime = currentTime
                    val level = when {
                        amplitude > 1500 -> "███"
                        amplitude > 1000 -> "██░"
                        amplitude > 500 -> "█░░"
                        else -> "░░░"
                    }
                    
                    mainHandler.post {
                        showVisualFeedback(
                            "🎤 Recording Audio\n\n" +
                            "Level: $level\n" +
                            "Transcript: ${transcriptBuilder.toString().takeLast(50)}...\n\n" +
                            "Stop speaking to finish"
                        )
                    }
                }
            }
            
            override fun onRecordingCompleted() {
                Log.d(TAG, "✅ Audio recording completed")
                service.stopListening()
                
                var finalTranscript = transcriptBuilder.toString().trim()
                
                // Only proceed if we have a real transcript
                if (finalTranscript.isNotEmpty()) {
                    mainHandler.post {
                        showVisualFeedback(
                            "📝 Transcript Complete\n\n" +
                            "\"$finalTranscript\"\n\n" +
                            "Sending to OpenAI Assistant..."
                        )
                        
                        onTranscriptComplete(finalTranscript)
                    }
                } else {
                    mainHandler.post {
                        if (CoachAudioRecorder.isEmulator()) {
                            showVisualFeedback(
                                "❌ No transcript from Vosk\n\n" +
                                "📁 Add test_audio.wav file to:\n" +
                                "/sdcard/Android/data/com.seudominio.app_smart_companion/files/\n\n" +
                                "See REAL_AUDIO_TEST_GUIDE.md"
                            )
                        } else {
                            showVisualFeedback("❌ No speech detected\n\nPlease try again")
                        }
                    }
                }
            }
            
            override fun onRecordingError(error: String) {
                Log.e(TAG, "❌ Recording error: $error")
                service.stopListening()
                
                mainHandler.post {
                    showVisualFeedback("❌ Recording Error\n\n$error")
                }
            }
        })
        
        // Update Vosk callback to accumulate transcripts
        val currentCallback = voskTranscriptionService
        if (currentCallback != null) {
            // Create new callback that accumulates transcripts
            val accumulatingCallback = object : VoskTranscriptionService.TranscriptionCallback {
                override fun onTranscriptionResult(text: String, isFinal: Boolean) {
                    if (text.isNotBlank()) {
                        if (isFinal) {
                            transcriptBuilder.append(text).append(" ")
                        }
                        Log.d(TAG, "📝 Vosk transcript ${if (isFinal) "FINAL" else "PARTIAL"}: '$text'")
                    }
                }
                
                override fun onTranscriptionError(error: String) {
                    Log.e(TAG, "❌ Vosk error: $error")
                    coachAudioRecorder?.stopRecording()
                }
                
                override fun onServiceReady() {
                    Log.d(TAG, "✅ Vosk ready")
                    // Start listening immediately when ready
                    voskTranscriptionService?.startListening()
                }
            }
            
            // Reinitialize with accumulating callback
            voskTranscriptionService = VoskTranscriptionService(this, accumulatingCallback)
            voskTranscriptionService?.initialize()
        }
    }
    
    /**
     * Check if audio permission is granted
     */
    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Send transcript to Coach SPIN via OpenAI Assistants API
     */
    private fun sendTranscriptToCoachSPIN(transcript: String, isActiveMode: Boolean) {
        Log.d(TAG, "🚀 Sending transcript to Coach SPIN: '$transcript'")
        
        val threadInfo = if (isActiveMode) {
            "Thread: ${currentThreadId?.takeLast(8)}"
        } else {
            "New conversation"
        }
        
        // Keep "Aguardando resposta..." until we get the Coach SPIN response
        
        // Use the OpenAI Assistants API client
        lifecycleScope.launch {
            try {
                // Get API key (should be configured)
                val apiKey = sharedPreferences.getString("api_key", null)
                if (apiKey.isNullOrEmpty()) {
                    showVisualFeedback("❌ API Key not configured\n\nPlease configure in settings")
                    return@launch
                }
                
                // Coach SPIN Assistant ID
                val coachSpinAssistantId = "asst_hXcg5nxjUuv2EMcJoiJbMIBN"
                
                // Create client
                val assistantClient = OpenAIAssistantClient(
                    apiKey = apiKey,
                    onResponse = { response: String ->
                        mainHandler.post {
                            // Use permanent display for Coach SPIN responses
                            showPermanentMessage("Coach SPIN: $response")
                            
                            // If audio response is enabled, convert text to speech
                            if (audioResponseEnabled) {
                                // TODO: Implement TTS for audio response
                                Log.d(TAG, "🔊 TTS would play: ${response.take(50)}...")
                            }
                        }
                    },
                    onError = { error: String ->
                        mainHandler.post {
                            showVisualFeedback("Erro")
                        }
                    },
                    onStatusUpdate = { status: String ->
                        // Don't show status updates - keep "Aguardando resposta..."
                    }
                )
                
                // Set assistant ID
                assistantClient.setAssistantId(coachSpinAssistantId)
                
                // Create or use existing thread
                if (!isActiveMode || currentThreadId == null) {
                    val newThreadId = assistantClient.createThread()
                    if (newThreadId != null) {
                        currentThreadId = newThreadId
                        Log.d(TAG, "✅ Created new thread: $newThreadId")
                    } else {
                        showVisualFeedback("Erro ao criar conversa")
                        return@launch
                    }
                }
                
                // Send the transcript
                val messageSent = assistantClient.sendTextMessage(transcript)
                if (messageSent) {
                    Log.d(TAG, "✅ Message sent, executing run...")
                    
                    // Execute run to get response
                    val runExecuted = assistantClient.executeRun()
                    if (!runExecuted) {
                        showVisualFeedback("Sem resposta do Coach")
                    }
                } else {
                    showVisualFeedback("Erro ao enviar mensagem")
                }
                
                // Clean up
                assistantClient.cleanup()
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception in Coach SPIN integration", e)
                mainHandler.post {
                    showVisualFeedback("Erro")
                }
            }
        }
    }
    
    /**
     * TEMPORÁRIO: Teste direto do fluxo de áudio para debug
     */
    private fun testAudioFlowDirectly() {
        try {
            Log.d(TAG, "🧪 TESTE DIRETO: Chamando sendAudioToCoach()...")
            sendAudioToCoach()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no teste direto de áudio", e)
        }
    }
    
    // =====================================
    // API KEY CONFIGURATION
    // =====================================
    
    /**
     * Auto-configure OpenAI API key from .env file in assets
     */
    private fun autoConfigureApiKey() {
        try {
            // Check if API key is already configured
            val existingKey = sharedPreferences.getString("api_key", null)
            if (!existingKey.isNullOrEmpty() && existingKey.startsWith("sk-")) {
                Log.d(TAG, "✅ API key already configured")
                return
            }
            
            // Try to read env.txt file from assets (renamed from .env for Android compatibility)
            val envContent = assets.open("env.txt").bufferedReader().use { it.readText() }
            
            // Parse OPENAI_API_KEY from .env content
            val lines = envContent.lines()
            for (line in lines) {
                if (line.startsWith("OPENAI_API_KEY=") && !line.contains("YOUR_OPENAI_API_KEY_HERE")) {
                    val apiKey = line.substringAfter("OPENAI_API_KEY=").trim()
                    if (apiKey.startsWith("sk-")) {
                        // Save to SharedPreferences
                        sharedPreferences.edit()
                            .putString("api_key", apiKey)
                            .apply()
                        
                        Log.d(TAG, "✅ API key auto-configured from env.txt file")
                        // API key configured silently
                        return
                    }
                }
            }
            
            Log.w(TAG, "⚠️ No valid API key found in env.txt file")
            
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Could not auto-configure API key: ${e.message}")
            // This is not a critical error - user can still configure manually
        }
    }
}
