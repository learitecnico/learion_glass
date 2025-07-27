# 🧪 ACTIVE MODE TEST CHECKLIST

> **Data:** 2025-07-27  
> **Build:** app-debug.apk  
> **Status:** Ready for M400 Device Testing

## 📋 PRE-TEST SETUP

### **Environment Requirements**
- [ ] Vuzix M400 with Android 13
- [ ] WiFi connection configured
- [ ] OpenAI API key configured
- [ ] Coach SPIN Assistant ID verified
- [ ] ADB connection established

### **Initial Verification**
- [ ] App installs successfully
- [ ] Permissions granted (Audio, Camera, Network)
- [ ] Main menu displays correctly
- [ ] Voice commands responding

## 🎯 CORE FUNCTIONALITY TESTS

### **PHASE 1: Menu Navigation**
- [ ] **T1.1** - Navigate: Main → Assistants → Coach SPIN
- [ ] **T1.2** - All 6 Coach SPIN menu options visible
- [ ] **T1.3** - "5. Conexão Ativa" selectable
- [ ] **T1.4** - Back navigation works correctly

### **PHASE 2: Active Mode Activation**
- [ ] **T2.1** - Select "Conexão Ativa" from Coach SPIN menu
- [ ] **T2.2** - ActiveModeManager initializes (check logs)
- [ ] **T2.3** - Thread created successfully
- [ ] **T2.4** - Status shows "Connected ✅"
- [ ] **T2.5** - Thread ID displayed (last 8 chars)
- [ ] **T2.6** - Audio Response shows OFF by default

### **PHASE 3: Hidden Menu (Double-Tap)**
- [ ] **T3.1** - Display shows only response area
- [ ] **T3.2** - Double-tap trackpad reveals menu
- [ ] **T3.3** - Menu shows 5 active mode options
- [ ] **T3.4** - Menu auto-hides after selection

### **PHASE 4: Voice Commands in Active Mode**
- [ ] **T4.1** - "record" starts audio recording
- [ ] **T4.2** - "stop" stops recording
- [ ] **T4.3** - "photo" captures and sends photo
- [ ] **T4.4** - "new thread" creates new conversation
- [ ] **T4.5** - "toggle audio" switches audio response
- [ ] **T4.6** - Commands work with menu hidden

### **PHASE 5: Audio Recording (Manual + Voice)**
- [ ] **T5.1** - Manual: Select "1. Enviar Áudio"
- [ ] **T5.2** - Recording starts immediately
- [ ] **T5.3** - Visual feedback shows recording
- [ ] **T5.4** - Auto-stop after 60 seconds
- [ ] **T5.5** - Voice: "record" starts recording
- [ ] **T5.6** - Voice: "stop" stops before 60s
- [ ] **T5.7** - Audio sent to OpenAI Assistant
- [ ] **T5.8** - Response received and displayed

### **PHASE 6: Photo Capture**
- [ ] **T6.1** - Select "2. Enviar Foto" or say "photo"
- [ ] **T6.2** - Camera captures image
- [ ] **T6.3** - Photo sent to Assistant
- [ ] **T6.4** - Vision analysis response received
- [ ] **T6.5** - Thread context maintained

### **PHASE 7: Thread Management**
- [ ] **T7.1** - Initial thread persists between messages
- [ ] **T7.2** - Assistant remembers previous context
- [ ] **T7.3** - "3. Nova Thread" creates new conversation
- [ ] **T7.4** - New thread ID displayed
- [ ] **T7.5** - Previous context cleared
- [ ] **T7.6** - Thread survives app restart (24h)

### **PHASE 8: TTS Audio Response**
- [ ] **T8.1** - Toggle shows current state (OFF/ON)
- [ ] **T8.2** - When OFF: Text only response
- [ ] **T8.3** - Toggle to ON via menu or voice
- [ ] **T8.4** - When ON: Text appears immediately
- [ ] **T8.5** - Audio generation status shown
- [ ] **T8.6** - Audio plays through M400 speaker
- [ ] **T8.7** - Text remains after audio ends
- [ ] **T8.8** - Audio stops if exit mode

### **PHASE 9: Response Display Persistence**
- [ ] **T9.1** - Text response stays on screen
- [ ] **T9.2** - Text clears when using another tool
- [ ] **T9.3** - Text clears when exiting active mode
- [ ] **T9.4** - New response replaces old one
- [ ] **T9.5** - Long responses scrollable

### **PHASE 10: Exit and State Management**
- [ ] **T10.1** - "5. Voltar" exits active mode
- [ ] **T10.2** - Audio playback stops on exit
- [ ] **T10.3** - Returns to Coach SPIN menu
- [ ] **T10.4** - Re-entering uses same thread
- [ ] **T10.5** - State persists correctly

## 🔍 EDGE CASES TO TEST

### **Network Issues**
- [ ] **E1** - Behavior when WiFi disconnects
- [ ] **E2** - Timeout handling for API calls
- [ ] **E3** - Error messages displayed clearly

### **Audio Edge Cases**
- [ ] **E4** - Multiple rapid "record" commands
- [ ] **E5** - Recording during TTS playback
- [ ] **E6** - Very long text for TTS
- [ ] **E7** - Special characters in TTS text

### **Thread Edge Cases**
- [ ] **E8** - Thread after 24h expiration
- [ ] **E9** - Invalid thread recovery
- [ ] **E10** - Multiple rapid "new thread" commands

## 📊 PERFORMANCE METRICS

### **Response Times**
- [ ] Thread creation: < 2s
- [ ] Audio transcription: < 3s
- [ ] Assistant response: < 5s
- [ ] TTS generation: < 3s
- [ ] Photo capture & send: < 5s

### **Resource Usage**
- [ ] Memory usage stable
- [ ] No memory leaks after extended use
- [ ] Battery drain acceptable
- [ ] Cache size manageable

## 🐛 KNOWN ISSUES TO VERIFY

1. **LocalBroadcastManager deprecation warnings** - Should not affect functionality
2. **First-time API calls may be slower** - Due to cold start
3. **TTS cache may grow** - Monitor cache size

## 📝 TEST LOG TEMPLATE

```
Test ID: [Txx.x]
Date/Time: 
Tester: 
Result: [PASS/FAIL]
Notes: 
Screenshot/Video: 
```

## 🚀 POST-TEST ACTIONS

### **If All Tests Pass:**
- [ ] Mark Active Mode as production ready
- [ ] Document any performance optimizations needed
- [ ] Create user guide for active mode

### **If Issues Found:**
- [ ] Document specific failures with logs
- [ ] Identify root causes
- [ ] Create fix plan
- [ ] Re-test affected areas

---

**Ready for Testing!** The modular architecture should make debugging straightforward.