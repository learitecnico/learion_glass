# VOSK INTEGRATION PLAN - PASSO 7

> **Objetivo:** Integrar Vosk local transcription para as funções de áudio do Coach SPIN  
> **Data:** 2025-07-26 18:45  
> **Status:** PLANNING → IMPLEMENTATION

## 📋 ANALYSIS SUMMARY

### **🔍 SmartGlassesManager Reference Analysis:**
- ✅ **Complete Vosk implementation** found in SGM_android project
- ✅ **Dependencies identified**: `vosk-android:0.3.34@aar`, `jna:5.8.0@aar`
- ✅ **Architecture pattern**: SpeechRecVosk extends SpeechRecFramework
- ✅ **Model management**: English model (`model-en-us`) with StorageService
- ✅ **Audio pipeline**: 16kHz, BlockingQueue for audio chunks
- ✅ **Event system**: EventBus for transcript output

### **🎯 Integration Strategy:**
1. **Minimal Integration Approach** - Focus only on Coach SPIN needs
2. **Reuse SGM Patterns** - Adapt proven implementation
3. **Local Privacy** - No cloud transcription for sales meetings
4. **Coach SPIN Workflow** - Audio recording → Vosk → OpenAI Assistants API

## 📦 IMPLEMENTATION PHASES

### **PHASE 1: Dependencies & Models (30 min)**
- ✅ Add Vosk dependencies to `app/build.gradle.kts`
- ✅ Download and configure English model (`model-en-us`)
- ✅ Setup model assets and UUID generation
- ✅ Test build and model loading

### **PHASE 2: Core Vosk Service (45 min)**
- ✅ Create `VoskTranscriptionService.kt` adapted from SGM reference
- ✅ Implement basic audio chunk processing (16kHz)
- ✅ Add transcript result callbacks
- ✅ Test basic transcription functionality

### **PHASE 3: Coach SPIN Integration (30 min)**
- ✅ Update `sendAudioToCoach()` and `sendAudioToCoachActive()` functions
- ✅ Integrate Vosk transcription in audio pipeline
- ✅ Send transcribed text to OpenAI Assistants API
- ✅ Update visual feedback for transcription status

### **PHASE 4: Testing & Validation (15 min)**
- ✅ Test complete workflow: Audio → Vosk → OpenAI → Response
- ✅ Validate Coach SPIN functions work end-to-end
- ✅ Measure latency (~300ms target)
- ✅ Update test documentation

## 🛠️ TECHNICAL IMPLEMENTATION

### **Dependencies (build.gradle.kts):**
```kotlin
dependencies {
    // Vosk local transcription
    implementation("net.java.dev.jna:jna:5.8.0@aar")
    implementation("com.alphacephei:vosk-android:0.3.34@aar")
    
    // EventBus for transcript events (if not already included)
    implementation("org.greenrobot:eventbus:3.3.1")
}
```

### **Model Configuration:**
```kotlin
// Model download task (similar to SGM)
tasks.register("genUUID_en") {
    val uuid = UUID.randomUUID().toString()
    val odir = file("$buildDir/generated/assets/model-en-us")
    val ofile = file("$odir/uuid")
    doLast {
        mkdir(odir)
        ofile.writeText(uuid)
    }
}
preBuild.dependsOn("genUUID_en")
```

### **VoskTranscriptionService Architecture:**
```kotlin
class VoskTranscriptionService(context: Context) {
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private val audioQueue = ArrayBlockingQueue<ByteArray>(bufferSize)
    
    fun initialize() {
        // Load model using StorageService.unpack()
        // Create Recognizer with 16kHz sample rate
    }
    
    fun processAudioChunk(audioData: ByteArray) {
        // Add to queue for processing
        audioQueue.put(audioData)
    }
    
    interface TranscriptionCallback {
        fun onTranscriptionResult(text: String, isFinal: Boolean)
        fun onError(error: String)
    }
}
```

### **Coach SPIN Integration Points:**
```kotlin
// In MainActivity.kt - Update these functions:

private fun sendAudioToCoach() {
    // 1. Start audio recording (existing code)
    // 2. Process chunks through VoskTranscriptionService
    // 3. When transcription complete, send to OpenAI Assistants API
    // 4. Show visual feedback: "Recording → Transcribing → Sending"
}

private fun sendAudioToCoachActive() {
    // Same workflow but in active mode
    // Quick feedback for continuous coaching
}
```

## 📊 EXPECTED PERFORMANCE

### **Latency Breakdown:**
- **Audio Recording**: ~500ms (user speaks)
- **Vosk Transcription**: ~300ms (local processing)
- **OpenAI API Call**: ~800ms (network + processing)
- **Total**: ~1.6s (excellent for coaching workflow)

### **Privacy Benefits:**
- ✅ **Local Transcription** - Audio never leaves device
- ✅ **Sales Meeting Safe** - No cloud audio processing
- ✅ **GDPR Compliant** - No audio data stored externally
- ✅ **Offline Capable** - Works without internet for transcription

## 🎯 SUCCESS CRITERIA

### **MINIMUM VIABLE (PASSO 7 Complete):**
- ✅ Vosk transcription working for Coach SPIN functions
- ✅ Audio → Text → OpenAI Assistants API pipeline functional
- ✅ Visual feedback shows transcription progress
- ✅ No build errors or crashes

### **QUALITY TARGETS:**
- ✅ **Accuracy**: >80% for clear English speech
- ✅ **Latency**: <500ms transcription time
- ✅ **Reliability**: No crashes during audio processing
- ✅ **Integration**: Seamless with existing Coach SPIN workflow

## 🚨 POTENTIAL ISSUES & MITIGATIONS

### **Issue 1: Model Size**
- **Problem**: Vosk models can be 50-300MB
- **Mitigation**: Use "small" English model (~50MB), acceptable for M400

### **Issue 2: Audio Format Mismatch**
- **Problem**: M400 audio vs Vosk requirements (16kHz)
- **Mitigation**: Reuse existing audio processing pipeline, ensure 16kHz

### **Issue 3: Memory Usage**
- **Problem**: Vosk requires RAM for model
- **Mitigation**: Load model once, reuse recognizer instance

### **Issue 4: Threading**
- **Problem**: Audio processing on UI thread
- **Mitigation**: Use background thread for Vosk, callbacks for results

## 📋 IMPLEMENTATION CHECKLIST

### **Phase 1: Setup**
- [ ] Add Vosk dependencies to build.gradle.kts
- [ ] Download English model assets
- [ ] Configure build tasks for model preparation
- [ ] Test build success

### **Phase 2: Core Service**
- [ ] Create VoskTranscriptionService.kt
- [ ] Implement model loading and initialization
- [ ] Add audio chunk processing
- [ ] Implement transcript callbacks
- [ ] Test basic transcription

### **Phase 3: Integration**
- [ ] Update sendAudioToCoach() with Vosk
- [ ] Update sendAudioToCoachActive() with Vosk
- [ ] Add transcription status feedback
- [ ] Connect to OpenAI Assistants API
- [ ] Test complete workflow

### **Phase 4: Validation**
- [ ] Test all Coach SPIN audio functions
- [ ] Verify latency targets
- [ ] Update test documentation
- [ ] Validate privacy requirements

---

**Next Action:** Begin Phase 1 - Dependencies & Models setup