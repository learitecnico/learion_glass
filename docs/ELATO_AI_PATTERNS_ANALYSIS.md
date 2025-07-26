# ElatoAI OpenAI Realtime API Patterns Analysis

## Overview
This document analyzes the patterns and configurations used in the ElatoAI repository for connecting to OpenAI Realtime API, focusing on solutions for common issues we're experiencing.

## Key Patterns Identified

### 1. Connection Setup and Configuration

#### WebSocket Connection
```typescript
const url = 'wss://api.openai.com/v1/realtime?model=gpt-4o-realtime-preview-2024-10-01';
const ws = new WebSocket(url, {
  headers: {
    'Authorization': `Bearer ${this.apiKey}`,
    'OpenAI-Beta': 'realtime=v1'
  }
});
```

#### Session Configuration (Smart Glasses Optimized)
```typescript
sessionConfig = {
  modalities: ['text', 'audio'],
  instructions: 'Concise responses for HUD display, max 50 words',
  voice: 'alloy',
  input_audio_format: 'pcm16',
  output_audio_format: 'pcm16',
  input_audio_transcription: {
    model: 'whisper-1'
  },
  turn_detection: {
    type: 'server_vad',
    threshold: 0.3,  // Lower = more sensitive (was 0.5)
    prefix_padding_ms: 300,
    silence_duration_ms: 300  // Shorter = faster trigger (was 500ms)
  },
  temperature: 0.6,  // Minimum allowed by API (was 0.3 - too low)
  tools: [...],  // HUD display tool
  tool_choice: 'auto'  // CRITICAL: preserves text_complete flow
}
```

### 2. VAD (Voice Activity Detection) Issues & Solutions

#### Problem: OpenAI not responding to audio input
**Root Cause**: VAD threshold too high or silence duration too long

**Solution**:
1. Lower VAD threshold from 0.5 to 0.3
2. Reduce silence_duration_ms from 500ms to 300ms
3. Add prefix_padding_ms: 300 for better speech capture

#### Force Reply Pattern (Community Recommendation)
```typescript
async forceReply(): Promise<void> {
  // Force OpenAI to generate response with current audio buffer
  this.createResponse();
  logger.info('🎯 FORCE REPLY triggered');
}
```

### 3. Event Handling Critical Path

#### Text Response Flow
```typescript
// CRITICAL: Handle both direct text and audio transcript events
case 'response.content_part.done':
  if (event.part?.type === 'text') {
    this.emit('text_complete', event.part.text);
  }
  break;

case 'response.audio_transcript.done':
  // Audio responses include transcript!
  if (event.transcript) {
    this.emit('text_complete', event.transcript);
  }
  break;
```

### 4. Reconnection Strategy

#### Automatic Reconnection
```typescript
private scheduleReconnect(): void {
  if (this.reconnectAttempts >= this.maxReconnectAttempts) {
    this.emit('max_reconnect_attempts');
    return;
  }

  this.reconnectAttempts++;
  const delay = this.reconnectDelay * this.reconnectAttempts;
  
  setTimeout(() => {
    if (!this.isConnected) {
      this.connect().catch(error => {
        logger.error('Reconnection failed', { error });
      });
    }
  }, delay);
}
```

### 5. Error Handling

#### Comprehensive Error Types
```typescript
switch (errorType) {
  case 'invalid_request_error':
  case 'authentication_error':
  case 'rate_limit_error':
  case 'server_error':
    // Specific handling for each error type
}
```

### 6. Tool Integration for HUD Display

#### Display Tool Definition
```typescript
tools: [{
  type: 'function',
  name: 'display_on_hud',
  description: 'Display text on smart glasses HUD - ALWAYS use this tool',
  parameters: {
    type: 'object',
    properties: {
      text: { type: 'string', description: 'Text to display (max 50 words)' },
      priority: { type: 'string', enum: ['low', 'medium', 'high'] }
    },
    required: ['text']
  }
}]
```

#### Tool Response Handling
```typescript
private handleFunctionCall(item: any): void {
  if (item.name === 'display_on_hud') {
    const { text, priority } = JSON.parse(item.arguments);
    this.emit('hud_display_request', { text, priority });
    
    // Send result back to OpenAI
    this.sendFunctionCallResult(item.call_id, {
      success: true,
      message: `Text displayed on HUD`
    });
  }
}
```

## Recommended Improvements for Our Implementation

### 1. VAD Configuration
- Update VAD threshold to 0.3
- Reduce silence_duration_ms to 300ms
- Add prefix_padding_ms: 300

### 2. Event Handling
- Add handler for `response.audio_transcript.done` event
- Ensure both text and audio transcript paths emit to HUD

### 3. Error Recovery
- Implement automatic reconnection with exponential backoff
- Add specific error type handling

### 4. Force Reply Feature
- Add endpoint/method to force OpenAI response when VAD fails
- Useful for noisy environments or when user stops mid-sentence

### 5. Session Management
- Implement session expiration detection
- Add health check endpoint for monitoring

### 6. Temperature Settings
- Use 0.6 minimum (0.3 was rejected by API)
- Adjust based on response quality needs

## Critical Insights

1. **Tool Choice**: Use 'auto' not 'required' to preserve text_complete events
2. **Audio Transcripts**: Audio responses include text transcripts - must handle both paths
3. **VAD Sensitivity**: Lower thresholds work better for smart glasses use cases
4. **Reconnection**: Essential for production reliability
5. **Force Reply**: Community-proven solution for VAD issues

## Implementation Priority

1. **High Priority**:
   - Fix VAD settings (threshold, silence duration)
   - Add audio transcript event handler
   - Implement force reply mechanism

2. **Medium Priority**:
   - Add reconnection logic
   - Improve error handling

3. **Low Priority**:
   - Session management improvements
   - Advanced tool integrations