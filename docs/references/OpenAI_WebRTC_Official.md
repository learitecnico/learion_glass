# OpenAI WebRTC Official Documentation

> **CRITICAL:** This file contains the official OpenAI WebRTC documentation that MUST be consulted at every implementation step. Always verify your implementation against these official patterns.
> 
> **Last Updated:** 2025-07-25  
> **Source:** Official OpenAI Platform Documentation + Community Insights  
> **Status:** Complete Implementation Guide

## 🚨 **DOCUMENTATION FIRST RULE**

Before implementing ANY WebRTC functionality:
1. ✅ **Read this entire document**
2. ✅ **Check latest OpenAI WebRTC docs** at platform.openai.com/docs/guides/realtime-webrtc  
3. ✅ **Verify audio formats** and connection patterns
4. ✅ **Follow authentication flow** exactly as documented
5. ✅ **Implement error handling** per official guidelines

## 📋 **OFFICIAL REQUIREMENTS CHECKLIST**

### Authentication (SIMPLIFIED FOR OUR MVP)
- [x] **Direct API key usage** (our approach - simpler for MVP)
- [ ] ~~Ephemeral API keys~~ (not needed for our implementation)
- [ ] ~~Backend token generation~~ (not required for direct connection)
- [x] **Authorization Bearer** header with API key

### Audio Format
- [x] **Opus codec** (primary WebRTC codec)
- [x] **PCM16 format** for API communication
- [x] **48kHz sample rate** (wideband audio)
- [x] **Mono channel** (1 channel)
- [x] **Forward Error Correction** (FEC) enabled by default

### Connection Flow
- [x] Create RTCPeerConnection
- [x] Add local audio track and data channel
- [x] Generate SDP offer
- [x] Send offer to OpenAI REST API
- [x] Receive SDP answer
- [x] Set remote description
- [x] Monitor connection state

### Error Handling
- [x] Connection failures and ICE state monitoring
- [x] Network drops and reconnection logic
- [x] Token expiration handling
- [x] Invalid SDP offer errors
- [x] Server-side error events

---

## 🔐 **AUTHENTICATION FLOW**

### Ephemeral Token Generation

**Server-Side Implementation (Required):**
```javascript
// Generate ephemeral key on your backend
const response = await fetch("https://api.openai.com/v1/realtime/sessions", {
  method: "POST",
  headers: {
    "Authorization": `Bearer ${OPENAI_API_KEY}`,
    "Content-Type": "application/json"
  },
  body: JSON.stringify({
    model: "gpt-4o-realtime-preview-2024-12-17",
    voice: "verse"
  })
});

const session = await response.json();
// session.client_secret.value contains the ephemeral key
// session.client_secret.expires_at shows expiration time (60 seconds)
```

**Key Security Points:**
- Ephemeral keys expire in **60 seconds**
- Generated server-side using your standard OpenAI API key
- **NEVER** expose your main API key on client devices
- Client receives only the temporary ephemeral key

### Supported Models & Regions

**Available Models:**
- `gpt-4o-realtime-preview-2024-12-17` (latest, 60%+ cheaper)
- `gpt-4o-mini-realtime-preview-2024-12-17` (cost-effective)

**Azure OpenAI Regions:**
- East US 2
- Sweden Central

---

## 🎵 **AUDIO SPECIFICATIONS**

### WebRTC Audio Pipeline
```
Microphone → Opus Codec → WebRTC Transport → OpenAI API
                ↓
Speaker ← PCM16 Audio ← WebRTC Transport ← OpenAI API
```

### Technical Specifications
- **Primary Codec:** Opus (50 packets per second)
- **Sample Rate:** 48kHz (wideband audio)
- **Format:** PCM16 for API communication
- **Channels:** Mono (1 channel)
- **Features:** Forward Error Correction (FEC) enabled
- **Alternative Codecs:** PCM, G722 (negotiated but Opus preferred)

### Audio Quality Features
- **Echo Cancellation:** Required on client device
- **Noise Reduction:** Available in WebRTC implementations
- **Automatic Gain Control:** Enabled by default
- **Voice Activity Detection:** Server-side processing available

---

## 🔄 **CONNECTION ESTABLISHMENT**

### Step-by-Step WebRTC Setup

```javascript
// 1. Create RTCPeerConnection
const pc = new RTCPeerConnection({
  iceServers: [
    { urls: 'stun:stun.l.google.com:19302' }
  ]
});

// 2. Get microphone stream
const stream = await navigator.mediaDevices.getUserMedia({ 
  audio: true 
});

// 3. Add audio track to peer connection
stream.getAudioTracks().forEach(track => {
  pc.addTrack(track, stream);
});

// 4. Create data channel for events
const dataChannel = pc.createDataChannel('events');

// 5. Generate SDP offer
const offer = await pc.createOffer();
await pc.setLocalDescription(offer);

// 6. Send offer to OpenAI
const sdpResponse = await fetch(
  `https://api.openai.com/v1/realtime?model=gpt-4o-realtime-preview-2024-12-17`, 
  {
    method: "POST",
    body: offer.sdp,
    headers: {
      Authorization: `Bearer ${ephemeralKey}`,
      "Content-Type": "application/sdp"
    }
  }
);

// 7. Set remote description
const answer = {
  type: "answer",
  sdp: await sdpResponse.text()
};
await pc.setRemoteDescription(answer);

// 8. Monitor connection state
pc.addEventListener('connectionstatechange', () => {
  console.log('Connection state:', pc.connectionState);
  if (pc.connectionState === 'connected') {
    // Ready for audio streaming
  }
});
```

### Connection State Monitoring

**Critical Connection States:**
- `connecting` - ICE negotiation in progress
- `connected` - **Ready for audio streaming**
- `failed` - Connection failed, implement retry logic
- `disconnected` - Temporary network issue
- `closed` - Connection terminated

**Best Practice:** Wait for `connectionState === 'connected'` before proceeding with audio logic.

---

## ⚠️ **ERROR HANDLING PATTERNS**

### Common Error Scenarios

#### 1. Invalid SDP Offer
```
Error: "Invalid SDP offer. Please try again with a WHIP-compliant offer."
```
**Cause:** Starting with no media streams and only data channel  
**Solution:** Always add audio track before generating offer

#### 2. ICE Connection Failures
```
ICE state: checking → failed
```
**Cause:** Network restrictions, no TURN servers provided  
**Solution:** Implement fallback to WebSocket API

#### 3. Token Expiration
```
HTTP 401: Authentication failed
```
**Cause:** Ephemeral token expired (60-second limit)  
**Solution:** Regenerate ephemeral token and retry

#### 4. Server Error Events
```javascript
dataChannel.addEventListener('message', (event) => {
  const data = JSON.parse(event.data);
  if (data.type === 'error') {
    console.error('Server error:', data.error);
    // Implement error recovery
  }
});
```

### Robust Error Handling Implementation

```javascript
const connectWithRetry = async (retries = 3) => {
  try {
    // Generate fresh ephemeral token
    const ephemeralKey = await getEphemeralToken();
    
    // Attempt WebRTC connection
    const pc = await setupWebRTCConnection(ephemeralKey);
    
    return pc;
  } catch (error) {
    if (retries > 0) {
      console.log(`Connection failed, retrying... (${retries} attempts left)`);
      await new Promise(resolve => setTimeout(resolve, 1000));
      return connectWithRetry(retries - 1);
    }
    
    // Fallback to WebSocket implementation
    console.log('WebRTC failed, falling back to WebSocket');
    return setupWebSocketConnection();
  }
};
```

---

## 📱 **ANDROID/MOBILE IMPLEMENTATION**

### WebRTC Android Considerations

#### Native WebRTC Library
- Use Google's WebRTC SDK for Android
- Available via Maven: `implementation 'org.webrtc:google-webrtc:1.0.+'`
- Provides native performance for audio processing

#### Audio Pipeline Configuration
```kotlin
// Audio source configuration
val audioSource = MediaConstraints().apply {
    optional.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
    optional.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
    optional.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
}

// Peer connection configuration
val rtcConfig = RTCConfiguration(
    listOf(RTCIceServer.builder("stun:stun.l.google.com:19302").build())
)
```

#### Permissions Required
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

#### Network Security Configuration
```xml
<!-- For Android 13+ to allow WebRTC connections -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">api.openai.com</domain>
    </domain-config>
</network-security-config>
```

### Mobile-Specific Challenges

#### Battery Optimization
- Implement efficient connection management
- Use foreground services for persistent connections
- Monitor battery usage patterns

#### Network Switching
- Handle WiFi ↔ cellular transitions
- Implement ICE restart for network changes
- Graceful reconnection strategies

#### Audio Focus Management
```kotlin
// Handle audio focus for phone calls, notifications
val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
    .setOnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> pauseAudio()
            AudioManager.AUDIOFOCUS_GAIN -> resumeAudio()
        }
    }
    .build()
```

---

## 🔍 **TROUBLESHOOTING GUIDE**

### Connection Issues

#### Symptom: ICE Never Connects
**Diagnosis:**
- Check STUN server configuration
- Verify network allows UDP traffic
- Test with different networks

**Solution:**
```javascript
// Enhanced ICE configuration
const rtcConfig = {
  iceServers: [
    { urls: 'stun:stun.l.google.com:19302' },
    { urls: 'stun:stun1.l.google.com:19302' }
  ],
  iceCandidatePoolSize: 10
};
```

#### Symptom: No Audio Response
**Diagnosis:**
- Connection established but no audio returned
- Events received but audio stream silent

**Solution:**
- Check audio track configuration
- Verify microphone permissions
- Monitor data channel events for server responses

#### Symptom: Poor Audio Quality
**Diagnosis:**
- Choppy, distorted, or delayed audio

**Solution:**
- Verify 48kHz sample rate
- Check network bandwidth
- Enable FEC and error correction
- Implement proper buffering

### Performance Optimization

#### Latency Reduction
- Use WebRTC instead of WebSocket (lower latency)
- Minimize audio buffer sizes
- Optimize network routing

#### Bandwidth Management
- Monitor connection quality
- Implement adaptive bitrate
- Use Opus codec efficiently

#### Resource Management
- Clean up peer connections properly
- Release media streams when done
- Monitor memory usage

---

## 📊 **PRICING & USAGE**

### Current Pricing (Dec 2024)
- **Input Audio:** $40/1M tokens (60% reduction)
- **Output Audio:** $80/1M tokens
- **Text Input/Output:** Standard GPT-4o rates

### Usage Optimization
- Use gpt-4o-mini for cost-effective interactions
- Implement session management to reduce setup overhead
- Monitor token usage for cost control

---

## 🔗 **OFFICIAL RESOURCES**

### Primary Documentation
- **WebRTC Guide:** https://platform.openai.com/docs/guides/realtime-webrtc
- **Realtime API:** https://platform.openai.com/docs/guides/realtime
- **Model Capabilities:** https://platform.openai.com/docs/guides/realtime-model-capabilities

### Community Resources
- **WebRTC Hacks Guide:** https://webrtchacks.com/the-unofficial-guide-to-openai-realtime-webrtc-api/
- **OpenAI Developer Community:** https://community.openai.com/c/api/
- **Technical Deep Dive:** https://webrtc.ventures/2025/01/openai-introduces-a-webrtc-endpoint-for-its-realtime-api/

### Example Implementations
- **React Native Example:** https://github.com/thorwebdev/expo-webrtc-openai-realtime
- **Browser Implementation:** Available in OpenAI console examples
- **Embedded SDK:** OpenAI Rudolph toy demo on GitHub

---

## ✅ **IMPLEMENTATION VALIDATION**

### Pre-Implementation Checklist
- [ ] Backend endpoint for ephemeral token generation
- [ ] WebRTC library integrated (browser or native)
- [ ] Audio permissions configured
- [ ] Network security policies updated
- [ ] Error handling strategies defined

### Testing Checklist
- [ ] Token generation and expiration handling
- [ ] SDP offer/answer exchange
- [ ] Audio input/output pipeline
- [ ] Connection state monitoring
- [ ] Error scenarios and recovery
- [ ] Network condition changes

### Performance Benchmarks
- [ ] Connection establishment time < 2 seconds
- [ ] Audio latency < 500ms end-to-end
- [ ] Stable connection for 5+ minutes
- [ ] Graceful handling of network switches
- [ ] Proper resource cleanup

---

**⚠️ CRITICAL SUCCESS FACTORS:**

1. **Always generate ephemeral tokens server-side**
2. **Wait for 'connected' state before streaming audio**
3. **Implement robust error handling and fallbacks**
4. **Monitor connection quality continuously**
5. **Test across different network conditions**
6. **Follow official audio format specifications exactly**

**Next Steps:**
1. Set up backend ephemeral token endpoint
2. Implement WebRTC connection logic
3. Configure audio pipeline with proper formats
4. Add comprehensive error handling
5. Test thoroughly across scenarios