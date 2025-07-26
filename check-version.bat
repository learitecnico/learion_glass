@echo off
echo ================================================
echo  VERSION CHECK - Smart Companion
echo ================================================

echo Checking emulator connection...
adb devices | findstr "emulator" >nul
if errorlevel 1 (
    echo ERROR: No emulator detected
    pause
    exit /b 1
)

echo.
echo === APK BUILD INFO ===
for %%f in (app\build\outputs\apk\debug\app-debug.apk) do (
    echo APK File: %%~tf %%~zf bytes
)

echo.
echo === INSTALLED APP INFO ===
adb shell pm dump com.seudominio.app_smart_companion | findstr "versionCode\|versionName\|firstInstallTime\|lastUpdateTime"

echo.
echo === QUICK TEST ===
echo Testing app launch...
adb shell am start -n com.seudominio.app_smart_companion/.MainActivity

echo ================================================
pause