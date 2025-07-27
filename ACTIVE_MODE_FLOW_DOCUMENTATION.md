# 📖 ACTIVE MODE COMPLETE FLOW DOCUMENTATION

> **Data:** 2025-07-27  
> **Componentes:** ActiveModeManager + ThreadManager + TTSManager  
> **Status:** Implementation Complete

## 🏗️ ARCHITECTURE OVERVIEW

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   MainActivity  │────▶│ ActiveModeManager │────▶│  ThreadManager  │
└─────────────────┘     └──────────────────┘     └─────────────────┘
         │                       │                          │
         │                       ├─────────────────┐        │
         │                       ▼                 ▼        │
         │              ┌──────────────┐  ┌──────────────┐  │
         │              │ AudioManager │  │ PhotoManager │  │
         │              └──────────────┘  └──────────────┘  │
         │                       │                          │
         │                       ▼                          ▼
         │              ┌──────────────┐         ┌──────────────────┐
         └─────────────▶│  TTSManager  │         │ OpenAI Assistant │
                        └──────────────┘         └──────────────────┘
```

## 🔄 COMPLETE FLOW SEQUENCE

### **1. ENTERING ACTIVE MODE**

```kotlin
User: Selects "5. Conexão Ativa" from Coach SPIN menu
         ↓
MainActivity.activateCoachConnection()
         ↓
MainActivity.activateAssistantConnectionNewPattern(assistantId, "Coach SPIN")
         ↓
ActiveModeManager created (if first time)
         ↓
ActiveModeManager.enterActiveMode(assistantId)
         ↓
Components initialized:
- OpenAIAssistantClient
- ThreadManager
- AssistantAudioManager  
- AssistantPhotoManager
- TTSManager
         ↓
ThreadManager.ensureActiveThread()
         ↓
Thread created/restored
         ↓
Callback: onActiveModeStarted(threadId)
         ↓
UI Updated: Shows active mode display
```

### **2. AUDIO RECORDING FLOW**

#### **Via Menu:**
```kotlin
User: Double-tap → Select "1. Enviar Áudio"
         ↓
MainActivity.sendAudioToCoachActive()
         ↓
ActiveModeManager.sendAudioInActiveMode()
         ↓
AssistantAudioManager.startAudioToAssistant()
         ↓
Audio Recording → Transcription → Send to Assistant
         ↓
Response received → handleAssistantResponse()
         ↓
Display text + Optional TTS playback
```

#### **Via Voice Command:**
```kotlin
User: Says "record" (or "gravar")
         ↓
VoiceCommander → handleVoiceCommand()
         ↓
ACTION_START_RECORDING → startAudioRecording()
         ↓
Sets 60-second timer + starts recording
         ↓
ActiveModeManager.sendAudioInActiveMode()
         ↓
User: Says "stop" OR waits 60 seconds
         ↓
stopAudioRecording() → Timer cancelled
         ↓
Same flow as menu option
```

### **3. PHOTO CAPTURE FLOW**

```kotlin
User: Says "photo" OR selects "2. Enviar Foto"  
         ↓
MainActivity.sendPhotoToCoachActive()
         ↓
ActiveModeManager.sendPhotoInActiveMode()
         ↓
AssistantPhotoManager.startPhotoToAssistant()
         ↓
Camera capture → Vision analysis → Assistant
         ↓
Response with same thread context
         ↓
Display response (text + optional audio)
```

### **4. TTS AUDIO RESPONSE FLOW**

```kotlin
Assistant response received
         ↓
ActiveModeManager.handleAssistantResponse(text)
         ↓
Display text immediately on HUD
         ↓
Check: isAudioResponseEnabled?
         ↓
If YES: playAudioResponse(text)
         ↓
TTSManager.textToSpeechAndPlay()
         ↓
OpenAI TTS API call (/v1/audio/speech)
         ↓
Audio file generated/cached
         ↓
MediaPlayer plays audio
         ↓
Callbacks update UI status
         ↓
Text remains on screen after audio ends
```

### **5. THREAD MANAGEMENT FLOW**

```kotlin
New Thread Request (voice: "new thread" OR menu)
         ↓
MainActivity.createNewThread()
         ↓
ActiveModeManager.createNewThread()
         ↓
ThreadManager.clearActiveThread()
         ↓
ThreadManager.createNewThread()
         ↓
New thread ID generated
         ↓
Metadata saved to SharedPreferences
         ↓
Callback: onThreadCreated(newThreadId)
         ↓
UI shows new thread confirmation
```

### **6. EXITING ACTIVE MODE**

```kotlin
User: Selects "5. Voltar" OR navigates back
         ↓
ActiveModeManager.exitActiveMode()
         ↓
Stop all ongoing operations:
- AudioManager.cancelProcessing()
- PhotoManager.dispose()  
- TTSManager.stopPlayback()
         ↓
Clear active state
         ↓
Callback: onActiveModeEnded()
         ↓
Return to Coach SPIN menu
         ↓
Thread persists for 24 hours
```

## 🎯 KEY FEATURES IMPLEMENTED

### **1. Modular Architecture**
- Each manager is independent and reusable
- Easy to add new assistants (just change ID)
- Clean separation of concerns

### **2. Thread Persistence**
- Conversations maintained across sessions
- 24-hour automatic expiration
- Metadata tracking (message count, age)

### **3. Voice Command Integration**
- Works with menu hidden
- Natural commands in English/Portuguese
- Visual feedback for all actions

### **4. TTS with Smart Display**
- Text appears immediately
- Audio plays asynchronously
- Text persists after audio ends
- Professional coach voice (shimmer)

### **5. Error Handling**
- Graceful degradation
- Clear error messages
- Retry logic for network issues

## 📱 STATE MANAGEMENT

### **MainActivity State:**
```kotlin
- isCoachActive: Boolean
- currentThreadId: String?
- audioResponseEnabled: Boolean
- currentMenuState: MenuState
- activeModeManager: ActiveModeManager?
```

### **ActiveModeManager State:**
```kotlin
- isActiveMode: Boolean
- currentAssistantId: String?
- currentThreadId: String?
- isAudioResponseEnabled: Boolean (persisted)
```

### **ThreadManager State:**
```kotlin
- activeThreadId: String?
- activeAssistantId: String?
- ThreadMetadata (persisted in SharedPreferences)
```

## 🔧 CONFIGURATION POINTS

### **TTS Configuration:**
```kotlin
TTSConfig(
    model = "tts-1",        // Fast model
    voice = "shimmer",      // Professional voice
    speed = 0.95f,         // Slightly slower
    format = "mp3"         // Compressed audio
)
```

### **Thread Expiration:**
```kotlin
THREAD_EXPIRATION_MS = 24 * 60 * 60 * 1000L  // 24 hours
```

### **Audio Recording:**
```kotlin
AUTO_STOP_DURATION = 60000  // 60 seconds
```

## 🚀 FUTURE ENHANCEMENTS

1. **Voice Profiles** - Different TTS voices per assistant
2. **Offline Mode** - Cache responses for offline access
3. **Multi-language** - Automatic language detection
4. **Advanced Tools** - Function calling integration
5. **Analytics** - Usage tracking and insights

---

**Status:** Complete implementation ready for device testing