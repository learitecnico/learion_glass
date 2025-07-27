# Photo-to-Assistant Pattern

## Overview
Standardized pattern for implementing camera capture → vision analysis → assistant response workflows in the Vuzix M400 smart glasses app.

## Architecture
```
M400 Camera → Vision Analysis → OpenAI Assistant → HUD Display
```

## Components

### 1. AssistantPhotoManager
Central class that handles the complete pipeline:
- **Location**: `app/src/main/java/com/seudominio/app_smart_companion/assistants/AssistantPhotoManager.kt`
- **Purpose**: Centralized, reusable photo-to-assistant logic

### 2. Dependencies
- `CameraCapture`: Handles M400 camera capture (720p, 200KB compression)
- `OpenAI Vision API`: Analyzes images with GPT-4 Vision
- `OpenAIAssistantClient`: Communicates with OpenAI Assistants API

## Usage Pattern

### Basic Implementation
```kotlin
// 1. Create AssistantPhotoManager instance
val photoManager = AssistantPhotoManager(
    context = this,
    lifecycleScope = lifecycleScope,
    assistantId = "asst_your_assistant_id_here",
    apiKey = apiKey
)

// 2. Start photo-to-assistant flow
photoManager.startPhotoToAssistant(
    visionPrompt = "Analyze this image and describe what you see",
    assistantPrompt = "Based on the image analysis, provide coaching advice",
    callback = object : AssistantPhotoManager.PhotoToAssistantCallback {
        override fun onCaptureStarted() {
            showTemporaryMessage("Preparando câmera...")
        }
        
        override fun onPhotoTaken() {
            showTemporaryMessage("Foto capturada...")
        }
        
        override fun onVisionAnalysisStarted() {
            showTemporaryMessage("Analisando imagem...")
        }
        
        override fun onAssistantProcessingStarted() {
            showTemporaryMessage("Aguardando resposta...")
        }
        
        override fun onAssistantResponse(response: String) {
            showPermanentMessage("Assistant: $response")
        }
        
        override fun onError(error: String) {
            showPermanentMessage("Erro: $error")
        }
    }
)
```

### Callback Stages
1. **onCaptureStarted()**: Called when camera initialization begins
2. **onPhotoTaken()**: Called when photo is successfully captured
3. **onVisionAnalysisStarted()**: Called when sending image to Vision API
4. **onAssistantProcessingStarted()**: Called when sending to Assistant
5. **onAssistantResponse(response)**: Called when assistant responds
6. **onError(error)**: Called if any stage fails

## Examples

### Coach SPIN Assistant
```kotlin
private fun sendPhotoToCoach() {
    val photoManager = AssistantPhotoManager(
        context = this,
        lifecycleScope = lifecycleScope,
        assistantId = "asst_hXcg5nxjUuv2EMcJoiJbMIBN",
        apiKey = getApiKey()
    )
    
    photoManager.startPhotoToAssistant(
        visionPrompt = "Analyze this image for sales opportunities and customer insights",
        assistantPrompt = "Based on this image analysis, provide SPIN selling coaching advice",
        callback = object : AssistantPhotoManager.PhotoToAssistantCallback {
            override fun onCaptureStarted() = showTemporaryMessage("Preparando câmera...")
            override fun onPhotoTaken() = showTemporaryMessage("Foto capturada...")
            override fun onVisionAnalysisStarted() = showTemporaryMessage("Analisando imagem...")
            override fun onAssistantProcessingStarted() = showTemporaryMessage("Aguardando resposta...")
            override fun onAssistantResponse(response: String) = showPermanentMessage("Coach SPIN: $response")
            override fun onError(error: String) = showPermanentMessage("Erro: $error")
        }
    )
}
```

### Product Analysis Assistant (Example)
```kotlin
private fun analyzeProduct() {
    val photoManager = AssistantPhotoManager(
        context = this,
        lifecycleScope = lifecycleScope,
        assistantId = "asst_product_analyzer_id",
        apiKey = getApiKey()
    )
    
    photoManager.startPhotoToAssistant(
        visionPrompt = "Identify this product and analyze its features, quality, and market positioning",
        assistantPrompt = "Provide a detailed product analysis and competitive insights",
        callback = object : AssistantPhotoManager.PhotoToAssistantCallback {
            override fun onCaptureStarted() = showTemporaryMessage("Iniciando análise...")
            override fun onPhotoTaken() = showTemporaryMessage("Produto capturado...")
            override fun onVisionAnalysisStarted() = showTemporaryMessage("Analisando produto...")
            override fun onAssistantProcessingStarted() = showTemporaryMessage("Gerando insights...")
            override fun onAssistantResponse(response: String) = showPermanentMessage("Análise: $response")
            override fun onError(error: String) = showPermanentMessage("Erro: $error")
        }
    )
}
```

## Benefits

1. **Reusability**: Single pattern works for any OpenAI Assistant + Vision combination
2. **Consistency**: Standardized callbacks and error handling
3. **Maintainability**: Centralized logic easier to update
4. **Scalability**: Easy to add new photo-based assistants
5. **Testing**: Isolated components easier to test
6. **M400 Optimized**: Designed for M400's camera and display constraints

## Thread Management

### New Thread per Photo (Current Implementation)
```kotlin
// Each photo creates a new conversation thread
threadId = null // Always creates new thread
```

### Future: Persistent Thread Support
```kotlin
// For conversation continuity (future enhancement)
threadId = existingThreadId // Continue existing conversation
```

### State Management
- Currently: Each photo starts fresh conversation
- Future: Track `currentThreadId` for conversation continuity
- Clean up resources with `dispose()` when done

## Error Handling

Common error scenarios:
- Camera initialization failure
- Photo capture failure
- Vision API communication failure
- Assistant API communication failure  
- Invalid API key
- Network connectivity issues
- M400 hardware constraints

All errors are handled via the `onError()` callback with descriptive messages.

## M400 Optimizations

### Camera Settings
- **Resolution**: 720p (1280x720) for balance of quality and performance
- **Compression**: Automatic compression to ≤200KB
- **Format**: JPEG with dynamic quality adjustment
- **Detail Level**: "low" for Vision API (optimized for cost and speed)

### Performance Considerations
- Background thread processing
- Automatic resource cleanup
- Memory-efficient image handling
- Network timeout handling
- Battery-conscious capture frequency

## Required Permissions

Add to AndroidManifest.xml:
```xml
<!-- Camera permissions -->
<uses-permission android:name="android.permission.CAMERA" />

<!-- Optional: Storage permissions for debugging -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

## API Costs

### Vision API Pricing (2025)
- **Low detail**: ~85 tokens per image (~$0.0004)
- **High detail**: ~85-255 tokens per image (~$0.001-$0.002)
- **Recommendation**: Use "low" detail for M400 to minimize costs

### Assistant API Pricing
- Standard Assistant API rates apply
- New thread per photo keeps conversations focused
- Automatic token management prevents overflow

## Future Extensions

This pattern can be extended for:
- **Persistent conversations**: Thread continuity across multiple photos
- **Batch processing**: Multiple photos in sequence
- **Real-time analysis**: Continuous camera feed processing
- **Multi-modal workflows**: Photo + audio combined analysis
- **Offline mode**: Local image analysis fallback
- **Custom vision models**: Integration with specialized computer vision APIs

## Lifecycle Management

```kotlin
// Initialize
val photoManager = AssistantPhotoManager(...)

// Use
photoManager.startPhotoToAssistant(...)

// Clean up when done
photoManager.dispose()
```

Always call `dispose()` when the activity is destroyed or when switching between different assistants to free camera and network resources.