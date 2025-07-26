@echo off
echo ================================================
echo  SMART COMPANION - FRESH DEPLOY SCRIPT
echo ================================================

REM Set JAVA_HOME
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr

REM Check if emulator is running
adb devices | findstr "emulator" >nul
if errorlevel 1 (
    echo ERROR: No emulator detected. Start Vuzix M400 emulator first.
    pause
    exit /b 1
)

echo [1/6] Uninstalling old version...
adb uninstall com.seudominio.app_smart_companion
if errorlevel 1 (
    echo WARNING: App was not installed or failed to uninstall
)

echo [2/6] Cleaning build cache...
call gradlew clean

echo [3/6] Building fresh APK...
call gradlew assembleDebug
if errorlevel 1 (
    echo ERROR: Build failed!
    pause
    exit /b 1
)

echo [4/6] Installing fresh APK...
adb install app\build\outputs\apk\debug\app-debug.apk
if errorlevel 1 (
    echo ERROR: Installation failed!
    pause
    exit /b 1
)

echo [5/6] Verifying installation...
adb shell pm list packages | findstr "smart_companion" >nul
if errorlevel 1 (
    echo ERROR: App not found after installation!
    pause
    exit /b 1
)

echo [6/6] Launching app...
adb shell am start -n com.seudominio.app_smart_companion/.MainActivity

echo ================================================
echo  SUCCESS: App deployed and launched!
echo ================================================
pause