# Vuzix M400 UI/UX Development Guidelines

> **Purpose:** Comprehensive guide for building UI components on Vuzix M400 based on official documentation and SmartGlassManager patterns
> **Created:** 2025-07-24
> **Target:** Smart Companion MVP + Future Visual Features

---

## 1. Core Architecture Patterns

### 1.1 ActionMenuActivity Base
**CRITICAL:** All activities must extend `ActionMenuActivity`

```java
public class MainActivity extends ActionMenuActivity {
    private TextView hudTextDisplay;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize HUD text display
        hudTextDisplay = findViewById(R.id.hud_text_display);
    }
    
    @Override
    public boolean onCreateActionMenu(Menu menu) {
        // CRITICAL: Call super method (learned from troubleshooting)
        super.onCreateActionMenu(menu);
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }
}
```

### 1.2 Device Model Detection
```java
import com.vuzix.hud.actionmenu.Utils;

// Check if running on M400
if(Utils.getModelNumber() == Utils.DEVICE_MODEL_M400) {
    // M400-specific optimizations
    setupM400Display();
}
```

---

## 2. HUD Text Display Patterns

### 2.1 Dynamic Text Updates (Real-time Transcription)
```java
public class TranscriptionDisplayManager {
    private TextView hudTextView;
    private Handler uiHandler;
    
    public TranscriptionDisplayManager(TextView textView) {
        this.hudTextView = textView;
        this.uiHandler = new Handler(Looper.getMainLooper());
    }
    
    public void updateTranscriptionText(String text) {
        uiHandler.post(() -> {
            hudTextView.setText(text);
            // Auto-scroll for long texts
            if (text.length() > 100) {
                hudTextView.setMovementMethod(new ScrollingMovementMethod());
            }
        });
    }
    
    public void clearDisplay() {
        uiHandler.post(() -> hudTextView.setText(""));
    }
}
```

### 2.2 Toast Messages for Status Updates
```java
private void showHudToast(final String message) {
    runOnUiThread(() -> 
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    );
}

// Usage examples:
showHudToast("Connected to OpenAI");
showHudToast("Audio processing...");
showHudToast("Response received");
```

---

## 3. Layout Design Principles

### 3.1 HUD-Optimal TextView Configuration
```xml
<!-- res/layout/hud_text_overlay.xml -->
<TextView
    android:id="@+id/hud_text_display"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_centerInParent="true"
    android:textSize="16sp"
    android:textColor="@color/hud_primary_text"
    android:background="@color/hud_background_transparent"
    android:padding="8dp"
    android:maxLines="5"
    android:scrollbars="vertical"
    android:fadingEdge="vertical"
    android:fadingEdgeLength="20dp" />
```

### 3.2 Color Schemes for M400
```xml
<!-- res/values/hud_colors.xml -->
<resources>
    <!-- High contrast for outdoor visibility -->
    <color name="hud_primary_text">#FFFFFF</color>
    <color name="hud_secondary_text">#CCCCCC</color>
    <color name="hud_background_transparent">#80000000</color>
    <color name="hud_accent">#00FF00</color>
    <color name="hud_warning">#FFAA00</color>
    <color name="hud_error">#FF0000</color>
</resources>
```

### 3.3 Theme Configuration
```xml
<!-- res/values/themes.xml -->
<style name="SmartCompanionHudTheme" parent="@style/HudTheme">
    <item name="actionMenuColor">@color/hud_accent</item>
    <item name="android:textColor">@color/hud_primary_text</item>
    <item name="android:windowBackground">@android:color/transparent</item>
</style>
```

---

## 4. Communication Patterns (WebSocket Integration)

### 4.1 Message Handler for Real-time Updates
```java
public class HudMessageHandler {
    private TranscriptionDisplayManager displayManager;
    private ActionMenuActivity context;
    
    public void handleTextMessage(String messageType, String content) {
        switch (messageType) {
            case "model_text":
                displayManager.updateTranscriptionText(content);
                confirmDisplayReceived(content);
                break;
                
            case "status_update":
                showHudToast(content);
                break;
                
            case "clear_display":
                displayManager.clearDisplay();
                break;
        }
    }
    
    private void confirmDisplayReceived(String text) {
        // Send confirmation back to companion desktop
        WebSocketClient.sendMessage(createConfirmationMessage(text));
    }
    
    private JSONObject createConfirmationMessage(String displayedText) {
        JSONObject confirmation = new JSONObject();
        try {
            confirmation.put("type", "display_confirmed");
            confirmation.put("text_preview", displayedText.substring(0, 30) + "...");
            confirmation.put("timestamp", System.currentTimeMillis());
        } catch (JSONException e) {
            Log.e("HudMessageHandler", "Error creating confirmation", e);
        }
        return confirmation;
    }
}
```

### 4.2 Connection Status Indicator
```java
public void updateConnectionStatus(boolean connected) {
    int statusColor = connected ? R.color.hud_accent : R.color.hud_error;
    String statusText = connected ? "🟢 Connected" : "🔴 Disconnected";
    
    runOnUiThread(() -> {
        statusIndicator.setTextColor(getColor(statusColor));
        statusIndicator.setText(statusText);
    });
}
```

---

## 5. Performance Optimization for M400

### 5.1 Memory Management
```java
public class HudResourceManager {
    private static final int MAX_DISPLAY_HISTORY = 10;
    private Queue<String> displayHistory = new LinkedList<>();
    
    public void addToHistory(String text) {
        if (displayHistory.size() >= MAX_DISPLAY_HISTORY) {
            displayHistory.poll(); // Remove oldest
        }
        displayHistory.offer(text);
    }
    
    public void clearHistory() {
        displayHistory.clear();
        System.gc(); // Suggest garbage collection
    }
}
```

### 5.2 Battery-Conscious Updates
```java
private static final long MIN_UPDATE_INTERVAL_MS = 100; // Limit to 10 FPS
private long lastUpdateTime = 0;

public void updateDisplayThrottled(String text) {
    long currentTime = System.currentTimeMillis();
    if (currentTime - lastUpdateTime >= MIN_UPDATE_INTERVAL_MS) {
        updateTranscriptionText(text);
        lastUpdateTime = currentTime;
    }
}
```

---

## 6. Menu Integration Patterns

### 6.1 Context-Aware Action Menu
```xml
<!-- res/menu/transcription_menu.xml -->
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/action_clear_display"
        android:title="Clear Display"
        android:onClick="clearTranscriptionDisplay" />
    
    <item
        android:id="@+id/action_toggle_recording"
        android:title="Toggle Recording"
        android:onClick="toggleAudioRecording" />
    
    <item
        android:id="@+id/action_connection_status"
        android:title="Connection Status"
        android:onClick="showConnectionStatus" />
</menu>
```

### 6.2 Menu Action Handlers
```java
public void clearTranscriptionDisplay(MenuItem item) {
    displayManager.clearDisplay();
    showHudToast("Display cleared");
}

public void toggleAudioRecording(MenuItem item) {
    boolean isRecording = audioManager.toggleRecording();
    String status = isRecording ? "Recording started" : "Recording stopped";
    showHudToast(status);
    
    // Update menu item text
    item.setTitle(isRecording ? "Stop Recording" : "Start Recording");
}

public void showConnectionStatus(MenuItem item) {
    String status = webSocketClient.isConnected() ? 
        "Connected to companion" : "Disconnected";
    showHudToast(status);
}
```

---

## 7. Error Handling & User Feedback

### 7.1 Connection Error Patterns
```java
public void handleConnectionError(Exception error) {
    String errorMessage = "Connection failed: " + error.getMessage();
    
    // Show immediate feedback
    showHudToast(errorMessage);
    
    // Update persistent status
    updateConnectionStatus(false);
    
    // Log for debugging
    Log.e("SmartCompanion", "Connection error", error);
    
    // Attempt reconnection
    scheduleReconnection();
}
```

### 7.2 Audio Processing Feedback
```java
public void showAudioProcessingStatus(String status) {
    // Visual indicator
    displayManager.updateTranscriptionText("🎤 " + status);
    
    // Temporary toast
    showHudToast(status);
    
    // Auto-clear after delay
    handler.postDelayed(() -> {
        if (status.equals("Processing complete")) {
            displayManager.clearDisplay();
        }
    }, 3000);
}
```

---

## 8. Future UI Expansion Guidelines

### 8.1 Modular Component Structure
```java
// Base class for all HUD components
public abstract class HudComponent {
    protected View rootView;
    protected ActionMenuActivity context;
    
    public abstract void initialize();
    public abstract void updateData(Object data);
    public abstract void cleanup();
}

// Example: Transcription Component
public class TranscriptionHudComponent extends HudComponent {
    private TextView transcriptionText;
    
    @Override
    public void updateData(Object data) {
        if (data instanceof String) {
            transcriptionText.setText((String) data);
        }
    }
}
```

### 8.2 Component Registry
```java
public class HudComponentManager {
    private Map<String, HudComponent> components = new HashMap<>();
    
    public void registerComponent(String id, HudComponent component) {
        components.put(id, component);
        component.initialize();
    }
    
    public void updateComponent(String id, Object data) {
        HudComponent component = components.get(id);
        if (component != null) {
            component.updateData(data);
        }
    }
}
```

---

## 9. Testing & Validation Patterns

### 9.1 HUD Display Validation
```java
public class HudDisplayValidator {
    public static boolean validateTextDisplay(String text, TextView textView) {
        // Check text length
        if (text.length() > 200) {
            Log.w("HudValidator", "Text too long for optimal HUD display");
            return false;
        }
        
        // Check visibility
        if (textView.getVisibility() != View.VISIBLE) {
            Log.w("HudValidator", "TextView not visible");
            return false;
        }
        
        return true;
    }
}
```

### 9.2 Performance Monitoring
```java
public class HudPerformanceMonitor {
    private static final String TAG = "HudPerformance";
    
    public static void logUpdateLatency(long startTime, String operation) {
        long latency = System.currentTimeMillis() - startTime;
        Log.d(TAG, operation + " latency: " + latency + "ms");
        
        if (latency > 100) {
            Log.w(TAG, "High latency detected for " + operation);
        }
    }
}
```

---

## 10. Dependencies & Build Configuration

### 10.1 Essential Dependencies
```gradle
// build.gradle.kts (app level)
implementation("com.vuzix:hud-actionmenu:2.8.4")
implementation("com.vuzix:connectivity-sdk:1.3.0")

// WebSocket support
implementation("com.squareup.okhttp3:okhttp:4.12.0")

// JSON handling
implementation("org.json:json:20240303")
```

### 10.2 Manifest Requirements
```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<application
    android:theme="@style/SmartCompanionHudTheme"
    android:hardwareAccelerated="true">
    
    <activity
        android:name=".MainActivity"
        android:exported="true"
        android:screenOrientation="landscape"
        android:configChanges="orientation|screenSize|keyboardHidden">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
</application>
```

---

## 11. Best Practices Summary

### ✅ DO:
- Always extend `ActionMenuActivity`
- Call `super.onCreateActionMenu()` 
- Use high-contrast colors for outdoor visibility
- Limit text length for HUD readability (max 200 chars)
- Implement confirmation callbacks for external messages
- Use throttled updates to conserve battery
- Test on actual M400 hardware

### ❌ DON'T:
- Forget device model detection
- Use low-contrast colors
- Display excessive text without scrolling
- Block UI thread with heavy operations
- Ignore WebSocket confirmation patterns
- Skip error handling for connection issues

---

## 12. Implementation Roadmap

### Phase 1: Basic HUD Display ✅ Ready to Implement
- [ ] Extend MainActivity from ActionMenuActivity
- [ ] Implement TranscriptionDisplayManager
- [ ] Add WebSocket message handling
- [ ] Create confirmation callbacks

### Phase 2: Enhanced UI Features
- [ ] Status indicators and connection feedback
- [ ] Context-aware action menus
- [ ] Error handling and recovery

### Phase 3: Advanced Features
- [ ] Component-based architecture
- [ ] Performance monitoring
- [ ] Advanced visual effects

---

**Status:** Ready for Implementation
**Next Step:** Implement basic HUD text display in MainActivity.kt
**Validation:** Test with M400 hardware + WebSocket confirmation loop