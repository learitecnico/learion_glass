package com.seudominio.app_smart_companion

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import kotlinx.coroutines.withContext
import org.json.JSONObject
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.seudominio.app_smart_companion.assistants.SimpleAssistantService
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
    private lateinit var sharedPreferences: SharedPreferences
    private var isSessionActive = false
    private var isListening = false  // Toggle state for Talk to AI
    private var isAssistantActive = false  // Assistant session state
    private var isAssistantConnected = false  // Assistant connection ready state
    private var isRecordingAudio = false  // Audio recording state
    
    // Menu Navigation State
    private enum class MenuState {
        MAIN,       // Main menu: Assistant, Live Agent, Settings, Exit
        ASSISTANT,  // Assistant submenu: Start Chat, Back
        LIVE_AGENT  // Live Agent submenu: Start Chat, Switch Agent, Back
    }
    
    private var currentMenuState = MenuState.MAIN
    private val menuStack = mutableListOf<MenuState>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        Log.d(TAG, "MainActivity onCreate - ActionMenuActivity initialized")
        
        // Initialize UI components
        initializeHudComponents()
        
        // Initialize shared preferences for API key storage
        sharedPreferences = getSharedPreferences("smart_companion_prefs", Context.MODE_PRIVATE)
        
        // Initialize broadcast receivers
        setupBroadcastReceivers()
        setupAssistantBroadcastReceivers()
        
        // Check permissions and API key setup
        checkPermissions()
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
        
        // Show initial status
        hudDisplayManager.showStatusMessage("Learion Glass initializing...")
        hudDisplayManager.updateConnectionStatus(false)
        
        Log.d(TAG, "HUD components initialized")
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
                "Ready to chat with $currentAgent!\n\n" +
                "🎤 TAP TRACKPAD → Open Menu\n" +
                "🗣️ Say \"Hello Vuzix, 1\" → Start Now\n\n" +
                "💡 After starting AI, just talk normally!"
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
        
        // Auto-configure API key from companion .env file if not set
        val envApiKey = loadApiKeyFromEnv()
        if (envApiKey != null) {
            // Save to SharedPreferences for future use
            saveApiKey(envApiKey)
            Log.d(TAG, "API key loaded from .env file and saved to SharedPreferences")
            return envApiKey
        }
        
        // Fallback - user needs to configure manually
        Log.w(TAG, "No API key found - user must configure via Settings")
        return null
    }
    
    /**
     * Load API key from project root .env file
     */
    private fun loadApiKeyFromEnv(): String? {
        return try {
            Log.d(TAG, "🔑 Loading API key from env configuration")
            
            // Try to read from project root .env file (works on M400 with project structure)
            val envFile = File(filesDir.parentFile?.parentFile?.parentFile, ".env")
            Log.d(TAG, "🔍 Trying to read .env from: ${envFile.absolutePath}")
            Log.d(TAG, "🔍 .env file exists: ${envFile.exists()}")
            
            if (envFile.exists()) {
                envFile.readLines().forEach { line ->
                    if (line.startsWith("OPENAI_API_KEY=") && !line.contains("YOUR_OPENAI_API_KEY_HERE")) {
                        val apiKey = line.substring("OPENAI_API_KEY=".length).trim()
                        if (apiKey.startsWith("sk-") && apiKey.length > 20) {
                            Log.d(TAG, "✅ Found valid API key in .env file")
                            return apiKey
                        }
                    }
                }
            }
            
            Log.d(TAG, "❌ No valid API key found in .env file")
            null
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Error reading .env file: ${e.message}")
            null
        }
    }
    
    /**
     * Save API key to SharedPreferences
     */
    private fun saveApiKey(apiKey: String) {
        sharedPreferences.edit().putString("openai_api_key", apiKey).apply()
        Log.d(TAG, "API key saved to SharedPreferences")
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
        
        // Cleanup HUD components
        if (::hudDisplayManager.isInitialized) {
            hudDisplayManager.cleanup()
        }
        
        // Cleanup StandaloneService if needed
        if (isSessionActive) {
            stopAISession()
        }
        Log.d(TAG, "MainActivity destroyed and cleaned up")
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
            MenuState.ASSISTANT -> R.menu.assistant_menu
            MenuState.LIVE_AGENT -> R.menu.live_agent_menu
        }
        
        menuInflater.inflate(menuResource, menu)
        
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
            R.id.action_assistant -> {
                Log.d(TAG, "🎯 Assistant menu item selected")
                navigateToMenu(MenuState.ASSISTANT)
                showVisualFeedback("🤖 Assistant Menu")
                true
            }
            R.id.action_live_agent -> {
                Log.d(TAG, "🎯 Live Agent menu item selected")
                navigateToMenu(MenuState.LIVE_AGENT)
                showVisualFeedback("👥 Live Agent Menu")
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
        
        // Push current state to stack for back navigation
        if (currentMenuState != newState) {
            menuStack.add(currentMenuState)
        }
        
        currentMenuState = newState
        
        // Trigger menu recreation (ActionMenuActivity specific)
        invalidateActionMenu()
        
        Log.d(TAG, "🎯 Navigation complete. Menu stack size: ${menuStack.size}")
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
     * Start Assistant chat session
     */
    private fun startAssistantChat() {
        Log.d(TAG, "🤖 Starting Assistant chat session")
        
        showVisualFeedback("🤖 Starting Assistant Chat...")
        
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
            // Show API key setup instructions
            val settingsText = "🔑 API Key Setup Required\n\n" +
                "1. Get OpenAI API key from:\n   platform.openai.com\n\n" +
                "2. Currently using test key\n\n" +
                "3. Full setup UI coming soon..."
            
            hudDisplayManager.showStatusMessage(settingsText, 8000L)
            Toast.makeText(this, "API key setup - see HUD", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Settings (no API key) displayed via showStatusMessage")
        } else {
            // Show current settings with better formatting
            val maskedKey = "sk-..." + currentApiKey.takeLast(4)
            
            val settingsText = "🔧 Smart Companion Settings\n\n" +
                "🔑 API Key: $maskedKey\n" +
                "🤖 Current Agent: $currentAgent\n" +
                "🎤 Audio Input: M400 Microphones\n" +
                "🔊 Sample Rate: 24kHz PCM16\n" +
                "🔌 Connection: WiFi\n\n" +
                "Tap trackpad to return to menu"
            
            hudDisplayManager.showStatusMessage(settingsText, 8000L)
            Log.d(TAG, "Settings displayed via showStatusMessage with 8s timeout")
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
}
