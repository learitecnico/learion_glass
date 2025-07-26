# 🤖 Learion Glass - Smart Companion for Vuzix M400

> **Advanced AI Assistant Application for Vuzix M400 Smart Glasses**
> 
> A cutting-edge Android application that brings OpenAI's powerful AI capabilities directly to Vuzix M400 smart glasses, enabling hands-free AI interaction through voice commands, visual HUD display, and intelligent conversation management.

[![Platform](https://img.shields.io/badge/Platform-Android%2013+-green.svg)](https://developer.android.com)
[![Device](https://img.shields.io/badge/Device-Vuzix%20M400-blue.svg)](https://www.vuzix.com/products/m400-smart-glasses)
[![OpenAI](https://img.shields.io/badge/AI-OpenAI%20API-orange.svg)](https://openai.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org)

## 🎯 **Project Overview**

Learion Glass transforms the Vuzix M400 smart glasses into an intelligent AI assistant, providing seamless access to OpenAI's advanced language models through an intuitive heads-up display (HUD) interface. The application supports dual AI communication modes and features professional-grade audio processing optimized for smart glasses.

### **Key Features**
- 🤖 **Dual AI Modes**: OpenAI Assistants API (HTTP REST) + Realtime API (WebSocket)
- 🎤 **Advanced Audio Processing**: 24kHz recording with noise cancellation
- 👁️ **Professional HUD Interface**: Vuzix black/green theme with enhanced visibility
- 🗣️ **Voice Command Navigation**: Hands-free menu control with "Hello Vuzix" commands
- 🔄 **Multi-Agent System**: 7 specialized AI personalities (Elato, Sherlock, Chef, etc.)
- 📱 **Hierarchical Menu System**: Intuitive navigation with visual feedback
- 🎯 **Smart Glasses Optimization**: Beam forming, DSP processing, and gesture control

## 🏗️ **Architecture**

### **System Architecture**
```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Vuzix M400    │    │   Android App    │    │   OpenAI API    │
│                 │    │                  │    │                 │
│ • 3x Microphones│───▶│ • Audio Pipeline │───▶│ • Assistants    │
│ • HUD Display   │◀───│ • HUD Manager    │◀───│ • Realtime      │
│ • Trackpad      │───▶│ • Menu System    │    │ • Whisper       │
│ • Voice Commands│    │ • Agent Manager  │    │ • GPT-4o        │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

### **Technology Stack**
- **Platform**: Android 13+ (API 33+)
- **Language**: Kotlin 2.0.21
- **UI Framework**: Jetpack Compose + Material 3
- **Audio**: AudioRecord/AudioTrack with Android DSP effects
- **Networking**: OkHttp WebSocket + Retrofit
- **AI Integration**: OpenAI Assistants API v2 + Realtime API
- **Build System**: Gradle 8.7 with Kotlin DSL

## 🚀 **Getting Started**

### **Prerequisites**
- **Hardware**: Vuzix M400 Smart Glasses with Android 13+
- **Development**: Android Studio Koala+ with Kotlin support
- **API Access**: OpenAI API key with Assistants and Realtime API access
- **Network**: WiFi connection for API communication

### **Installation**

1. **Clone Repository**
   ```bash
   git clone https://github.com/learitecnico/learion_glass.git
   cd learion_glass
   ```

2. **Configure Build Environment**
   ```bash
   # Set JAVA_HOME (Windows)
   set JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
   
   # Build APK
   ./gradlew assembleDebug
   ```

3. **Install on M400**
   ```bash
   # Connect M400 via USB debugging
   adb devices
   
   # Install APK
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

4. **API Key Setup**
   - Get OpenAI API key from [platform.openai.com](https://platform.openai.com)
   - Configure in app settings or use hardcoded key for testing

## 📱 **Features Deep Dive**

### **🤖 Dual AI Communication Modes**

#### **Assistant Mode (HTTP REST)**
- **Technology**: OpenAI Assistants API v2
- **Features**: Persistent conversations, file handling, advanced reasoning
- **Audio Flow**: Record → Whisper Transcription → Assistant → Text Response
- **Use Cases**: Complex problem solving, document analysis, extended conversations

#### **Live Agent Mode (WebSocket)**
- **Technology**: OpenAI Realtime API
- **Features**: Real-time voice interaction, low latency, natural conversation
- **Audio Flow**: Record → VAD Detection → Streaming → Audio Response
- **Use Cases**: Quick questions, natural dialogue, immediate responses

### **🎤 Advanced Audio Processing**

#### **M400 Audio Optimization**
- **Input**: 3-microphone array with beam forming
- **Sample Rate**: 24kHz PCM16 (OpenAI optimized)
- **Effects**: Echo cancellation, noise suppression, automatic gain control
- **Processing**: 44.1kHz → 24kHz downsample with linear interpolation
- **VAD**: Server-side voice activity detection (threshold: 0.5, silence: 500ms)

### **👁️ Professional HUD Interface**

#### **Vuzix Theme Integration**
- **Colors**: Black background (#000000) with green accents (#00FF00)
- **Typography**: 22sp bold text with green shadow effects
- **Layout**: 12-line display with automatic scrolling
- **Visibility**: High contrast optimization for outdoor use
- **Navigation**: Numbered menu items with trackpad control

### **🗂️ Hierarchical Menu System**

#### **Menu Structure**
```
📱 MAIN MENU
├── 🤖 1. Assistant        → 📋 Assistant Submenu
│   ├── 💬 1. Iniciar Chat     → Start HTTP REST session
│   └── ⬅️ 2. Voltar          → Back to main menu
│
├── 👥 2. Live Agent       → 📋 Live Agent Submenu  
│   ├── 🗣️ 1. Start Chat      → Start WebSocket session
│   ├── 🔄 2. Switch Agent    → Cycle through 7 agents
│   └── ⬅️ 3. Voltar          → Back to main menu
│
├── ⚙️ 3. Configurações    → Settings display
└── 🚪 4. Sair            → Clean app exit
```

### **🎭 Multi-Agent System**

The application features 7 specialized AI personalities:

| Agent | Personality | Voice | Use Case |
|-------|-------------|-------|----------|
| **Elato** | Helpful assistant | Alloy | General assistance |
| **Sherlock** | Detective genius | Echo | Problem solving |
| **Master Chef** | Culinary expert | Sage | Cooking guidance |
| **Fitness Coach** | Health trainer | Shimmer | Exercise motivation |
| **Math Wiz** | Mathematics tutor | Nova | Academic help |
| **Batman** | Strategic thinker | Onyx | Analysis & planning |
| **Eco Champion** | Environmental guide | Alloy | Sustainability tips |

## 🔧 **Development**

### **Essential Commands**
```bash
# Working build command (tested)
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" && ./gradlew assembleDebug

# Install on connected M400
adb install -r app/build/outputs/apk/debug/app-debug.apk

# View logs during testing
adb logcat -s LearionGlass

# Clean build
./gradlew clean assembleDebug
```

## 📋 **Testing**

### **M400 Physical Testing**
See `M400_PHYSICAL_TESTING_CHECKLIST.md` for comprehensive testing protocol:
- **107 test cases** across 7 categories
- **Hardware integration** validation
- **Real-world usage** scenarios

## 🎯 **Current Status**

### **✅ Implemented Features**
- Hierarchical menu system with professional Vuzix theme
- Dual AI modes (Assistants API + Realtime API)
- Advanced audio processing with M400 optimization
- Multi-agent system with 7 specialized personalities
- Voice command navigation and HUD display

### **🔄 In Development**
- M400 physical device testing and optimization
- Performance improvements and battery optimization
- Enhanced error handling and user feedback

## 📄 **License**

This project is proprietary software developed for Leari Técnico. All rights reserved.

## 📞 **Support**

- **Issues**: [GitHub Issues](https://github.com/learitecnico/learion_glass/issues)
- **Documentation**: See `docs/` directory
- **Development**: Check `BACKLOG.md` for current status

---

**🤖 Learion Glass - Bringing AI to the Real World through Smart Glasses**

*Built with ❤️ by Leari Técnico Team* 
