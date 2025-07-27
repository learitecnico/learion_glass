# Audio-to-Assistant Pattern

## Overview
Standardized pattern for implementing audio recording → transcription → assistant response workflows in the Vuzix M400 smart glasses app.

## Architecture
```
Audio Recording → Whisper Transcription → OpenAI Assistant → HUD Display
```

## Components

### 1. AssistantAudioManager
Central class that handles the complete pipeline:
- **Location**: `app/src/main/java/com/seudominio/app_smart_companion/assistants/AssistantAudioManager.kt`
- **Purpose**: Centralized, reusable audio-to-assistant logic

### 2. Dependencies
- `CoachAudioRecorder`: Handles M400 audio recording
- `OpenAIWhisperService`: Transcribes audio to text
- `OpenAIAssistantClient`: Communicates with OpenAI Assistants API

## Usage Pattern

### Basic Implementation
```kotlin
// 1. Create AssistantAudioManager instance
val audioManager = AssistantAudioManager(
    context = this,
    lifecycleScope = lifecycleScope,
    assistantId = "asst_your_assistant_id_here",
    apiKey = apiKey
)

// 2. Start audio-to-assistant flow
audioManager.startAudioToAssistant(
    callback = object : AssistantAudioManager.AudioToAssistantCallback {
        override fun onRecordingStarted() {
            showTemporaryMessage("Gravando...")
        }
        
        override fun onProcessingStarted() {
            showTemporaryMessage("Aguardando resposta...")
        }
        
        override fun onAssistantResponse(response: String) {
            showPermanentMessage("Assistant: $response")
        }
        
        override fun onError(error: String) {
            showPermanentMessage("Erro: $error")
        }
    },
    threadId = existingThreadId, // null for new conversation
    language = "pt" // Language code for Whisper
)
```

### Callback Stages
1. **onRecordingStarted()**: Called when audio recording begins
2. **onProcessingStarted()**: Called when transcription/processing starts
3. **onAssistantResponse(response)**: Called when assistant responds
4. **onError(error)**: Called if any stage fails

## Examples

### Coach SPIN Assistant
```kotlin
private fun sendAudioToCoach() {
    val audioManager = AssistantAudioManager(
        context = this,
        lifecycleScope = lifecycleScope,
        assistantId = "asst_hXcg5nxjUuv2EMcJoiJbMIBN",
        apiKey = getApiKey()
    )
    
    audioManager.startAudioToAssistant(
        callback = object : AssistantAudioManager.AudioToAssistantCallback {
            override fun onRecordingStarted() = showTemporaryMessage("Gravando...")
            override fun onProcessingStarted() = showTemporaryMessage("Aguardando resposta...")
            override fun onAssistantResponse(response: String) = showPermanentMessage("Coach SPIN: $response")
            override fun onError(error: String) = showPermanentMessage("Erro: $error")
        },
        threadId = if (isCoachActive) currentThreadId else null,
        language = "pt"
    )
}
```

### Legal Advisor Assistant (Example)
```kotlin
private fun sendAudioToLegalAdvisor() {
    val audioManager = AssistantAudioManager(
        context = this,
        lifecycleScope = lifecycleScope,
        assistantId = "asst_legal_advisor_id",
        apiKey = getApiKey()
    )
    
    audioManager.startAudioToAssistant(
        callback = object : AssistantAudioManager.AudioToAssistantCallback {
            override fun onRecordingStarted() = showTemporaryMessage("Gravando consulta...")
            override fun onProcessingStarted() = showTemporaryMessage("Analisando...")
            override fun onAssistantResponse(response: String) = showPermanentMessage("Advogado: $response")
            override fun onError(error: String) = showPermanentMessage("Erro: $error")
        },
        threadId = legalThreadId,
        language = "pt"
    )
}
```

## Benefits

1. **Reusability**: Single pattern works for any OpenAI Assistant
2. **Consistency**: Standardized callbacks and error handling
3. **Maintainability**: Centralized logic easier to update
4. **Scalability**: Easy to add new assistants
5. **Testing**: Isolated components easier to test

## Thread Management

### New Conversation
```kotlin
threadId = null // Creates new thread automatically
```

### Existing Conversation
```kotlin
threadId = existingThreadId // Continues existing conversation
```

### State Management
- Track `currentThreadId` for conversation continuity
- Store in activity/service for persistence
- Clear when starting new conversations

## Error Handling

Common error scenarios:
- Audio recording failure
- Whisper transcription failure  
- Assistant API communication failure
- Invalid API key
- Network connectivity issues

All errors are handled via the `onError()` callback with descriptive messages.

## Future Extensions

This pattern can be extended for:
- Photo-to-Assistant workflows
- Text-to-Assistant workflows
- Multi-modal assistant interactions
- Voice response (TTS) integration