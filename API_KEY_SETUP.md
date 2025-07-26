# 🔑 API Key Configuration Guide

## 📱 For Emulator Testing

Since the emulator cannot access the project's `.env` file, you need to configure the API key manually:

### Method 1: Via ADB Command (Recommended)
```bash
# Configure API key for emulator testing
adb shell am broadcast -a com.seudominio.app_smart_companion.SET_API_KEY --es api_key "your-api-key-here"
```

### Method 2: Via Logs (Debug)
1. Open the app in emulator
2. Use `adb logcat` to send commands
3. The app will show instructions in Settings menu

### Getting Your API Key
1. Go to https://platform.openai.com/api-keys
2. Create a new API key
3. Copy the key (starts with `sk-`)

## 🥽 For M400 Physical Device

The M400 will automatically load the API key from the `.env` file in the project root:

```bash
# File: .env (project root)
OPENAI_API_KEY=sk-your-actual-api-key-here
```

## 🔍 Verification

Both methods will:
- ✅ Save API key to SharedPreferences
- ✅ Show confirmation message on HUD
- ✅ Update main screen to "Ready to chat"
- ✅ Enable AI features (Assistant & Live Agent)

## 🔄 Switching Between Keys

```bash
# Clear current API key
adb shell pm clear com.seudominio.app_smart_companion

# Set new API key
adb shell am broadcast -a com.seudominio.app_smart_companion.SET_API_KEY --es api_key "new-key-here"
```

## 📋 Troubleshooting

- **API key not working**: Check format (must start with `sk-` and be >20 chars)
- **Emulator not responding**: Clear app data and try again
- **M400 not loading**: Verify `.env` file exists in project root
- **Permissions**: Grant all required permissions via Settings