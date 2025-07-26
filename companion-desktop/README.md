# Smart Companion Desktop

Desktop companion application for the Smart Companion M400 project. This application serves as a bridge between the Vuzix M400 smart glasses and OpenAI's Realtime API.

## Features

- **WebRTC Signaling Server**: Handles connection between M400 device and desktop
- **Audio Processing**: Receives audio from smart glasses and forwards to OpenAI
- **Image Processing**: Handles snapshot images from camera
- **OpenAI Integration**: Connects to OpenAI Realtime API for AI responses
- **System Prompt Management**: Dynamic prompt editing during sessions
- **Logging**: Comprehensive logging with Winston
- **Health Monitoring**: Health check endpoints for monitoring

## Prerequisites

- Node.js 18.0.0 or higher
- OpenAI API key with Realtime API access
- Network connectivity between M400 device and desktop

## Installation

```bash
# Install dependencies
npm install

# Copy environment variables
cp .env.example .env

# Edit .env with your OpenAI API key
nano .env
```

## Configuration

### Environment Variables

Create a `.env` file with the following variables:

```env
# OpenAI Configuration
OPENAI_API_KEY=your_openai_api_key_here
OPENAI_ORGANIZATION=your_org_id_here

# Server Configuration
SIGNALING_PORT=3000
HTTP_PORT=3001

# WebRTC Configuration
STUN_SERVER=stun:stun.l.google.com:19302

# Logging
LOG_LEVEL=info
LOG_FILE=./logs/companion.log

# Development
NODE_ENV=development
```

## Usage

### Development Mode

```bash
npm run dev
```

### Production Mode

```bash
# Build the project
npm run build

# Start the application
npm start
```

## API Endpoints

### Health Check
```
GET /health
```
Returns the health status of all components including OpenAI Realtime API connection.

### System Prompt Management
```
POST /prompt
Content-Type: application/json

{
  "prompt": "Your new system prompt here"
}
```

```
GET /prompt
```
Returns the current system prompt.

### Session Configuration
```
POST /session/config
Content-Type: application/json

{
  "config": {
    "modalities": ["text", "audio"],
    "temperature": 0.8,
    "voice": "alloy"
  }
}
```

```
GET /session/config
```
Returns the current session configuration.

### Voice Configuration
```
POST /session/voice
Content-Type: application/json

{
  "voice": "alloy"
}
```
Valid voices: `alloy`, `echo`, `fable`, `onyx`, `nova`, `shimmer`

### Temperature Configuration
```
POST /session/temperature
Content-Type: application/json

{
  "temperature": 0.8
}
```
Temperature range: 0.0 to 2.0

### Root Endpoint
```
GET /
```
Returns basic application information.

## WebSocket Signaling

The signaling server runs on `ws://localhost:3000/signaling` and handles:

- Client connection management
- SDP offer/answer exchange
- ICE candidate exchange
- Room management

## Architecture

```
┌─────────────────┐    WebSocket     ┌─────────────────┐
│   M400 Device   │◄────────────────►│ Signaling Server│
│                 │                  │                 │
│ - AudioCapture  │    WebRTC        │ - WebRTC Manager│
│ - Camera        │◄────────────────►│ - DataChannel   │
│ - DataChannel   │                  │                 │
└─────────────────┘                  └─────────────────┘
                                              │
                                              ▼
                                     ┌─────────────────┐
                                     │  OpenAI Bridge  │
                                     │                 │
                                     │ - Realtime API  │
                                     │ - Vision API    │
                                     │ - TTS API       │
                                     └─────────────────┘
```

## Logging

Logs are written to:
- `./logs/companion.log` - All logs
- `./logs/error.log` - Error logs only
- Console (development mode only)

Log levels: `error`, `warn`, `info`, `debug`

## Development

### Scripts

- `npm run dev` - Start in development mode with watch
- `npm run build` - Build TypeScript to JavaScript
- `npm run start` - Start production build
- `npm run lint` - Run ESLint
- `npm run lint:fix` - Fix ESLint issues
- `npm run clean` - Remove build directory

### Project Structure

```
src/
├── index.ts              # Main application entry point
├── signaling/           
│   └── SignalingServer.ts # WebSocket signaling server
├── webrtc/
│   └── WebRTCManager.ts   # WebRTC peer connection management
├── openai/
│   └── OpenAIBridge.ts    # OpenAI API integration
└── utils/
    └── Logger.ts          # Winston logger configuration
```

## Troubleshooting

### Common Issues

1. **Connection Failed**: Check that OPENAI_API_KEY is set correctly
2. **WebRTC Connection Issues**: Verify STUN server configuration
3. **Port Already in Use**: Change SIGNALING_PORT in .env file
4. **Audio Processing Issues**: Check WebRTC audio track handling

### Debug Mode

Set `LOG_LEVEL=debug` in your `.env` file for detailed logging.

## License

MIT