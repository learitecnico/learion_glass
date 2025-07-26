#!/bin/bash
# Quick Deploy for Claude Code
echo "🚀 Quick Deploy - Smart Companion"

# Check emulator
if ! adb devices | grep -q "emulator"; then
    echo "❌ No emulator detected"
    exit 1
fi

# Uninstall + Clean + Build + Install
echo "🧹 Uninstalling old version..."
adb uninstall com.seudominio.app_smart_companion 2>/dev/null || echo "No previous version"

echo "🔨 Building fresh APK..."
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" && \
cd "C:/Users/Everton/AndroidStudioProjects/app_smart_companion" && \
./gradlew clean assembleDebug

if [ $? -ne 0 ]; then
    echo "❌ Build failed!"
    exit 1
fi

echo "📱 Installing..."
adb install app/build/outputs/apk/debug/app-debug.apk

echo "🎯 Launching..."
adb shell am start -n com.seudominio.app_smart_companion/.MainActivity

echo "✅ Deploy complete!"
adb shell pm dump com.seudominio.app_smart_companion | grep "versionName\|lastUpdateTime"