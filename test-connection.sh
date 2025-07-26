#!/bin/bash
# Smart Companion Connection Test Script
# Tests Android build, Companion Desktop setup, and connection flow

set -e  # Exit on any error

echo "🔧 SMART COMPANION - TEST & DEBUG SETUP"
echo "========================================"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Test 1: Android Build
echo -e "\n📱 TESTE 1: Android Build"
echo "------------------------"
if ./gradlew assembleDebug --quiet; then
    print_status "Android build SUCCESSFUL"
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
    if [ -f "$APK_PATH" ]; then
        APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
        print_status "APK gerado: $APK_PATH ($APK_SIZE)"
    fi
else
    print_error "Android build FAILED"
    exit 1
fi

# Test 2: Companion Desktop Dependencies
echo -e "\n🖥️  TESTE 2: Companion Desktop Setup"
echo "-----------------------------------"
cd companion-desktop

if [ ! -f ".env" ]; then
    print_warning ".env file not found, creating from template"
    cp .env.example .env
    print_warning "EDIT .env file with your OpenAI API key before testing!"
fi

if npm run build --silent; then
    print_status "Companion Desktop build SUCCESSFUL"
else
    print_error "Companion Desktop build FAILED"
    exit 1
fi

# Test 3: Check required files
echo -e "\n📋 TESTE 3: Verificação de Arquivos"
echo "-----------------------------------"

REQUIRED_FILES=(
    "dist/index.js"
    "dist/signaling/SignalingServer.js"
    "dist/webrtc/WebRTCManager.js"
    "dist/openai/OpenAIBridge.js"
    ".env"
)

for file in "${REQUIRED_FILES[@]}"; do
    if [ -f "$file" ]; then
        print_status "$file exists"
    else
        print_error "$file MISSING"
        exit 1
    fi
done

# Test 4: Environment Variables Check
echo -e "\n🔑 TESTE 4: Environment Variables"
echo "---------------------------------"
source .env

if [ "$OPENAI_API_KEY" = "your_openai_api_key_here" ]; then
    print_warning "OpenAI API key não configurada - testes limitados"
    print_warning "Edite companion-desktop/.env com sua chave da OpenAI"
else
    print_status "OpenAI API key configurada"
fi

print_status "Signaling Port: $SIGNALING_PORT"
print_status "Log Level: $LOG_LEVEL"

# Test 5: Network Check
echo -e "\n🌐 TESTE 5: Network & Ports"
echo "---------------------------"

# Check if port is available
if netstat -an | grep ":$SIGNALING_PORT " > /dev/null; then
    print_warning "Port $SIGNALING_PORT já está em uso"
else
    print_status "Port $SIGNALING_PORT disponível"
fi

cd ..

echo -e "\n🎯 RESUMO DOS TESTES"
echo "==================="
print_status "Android APK: app/build/outputs/apk/debug/app-debug.apk"
print_status "Companion Desktop: companion-desktop/dist/index.js"
print_status "Configuração: companion-desktop/.env"

echo -e "\n🚀 PRÓXIMOS PASSOS:"
echo "1. Instalar APK no dispositivo: adb install app/build/outputs/apk/debug/app-debug.apk"
echo "2. Configurar OpenAI API key em companion-desktop/.env"
echo "3. Iniciar Companion: cd companion-desktop && npm run dev"
echo "4. Iniciar app no dispositivo e conectar"

echo -e "\n📊 ENDPOINTS DE DEBUG:"
echo "• Health Check: http://localhost:$SIGNALING_PORT/health"
echo "• WebSocket: ws://localhost:$SIGNALING_PORT/signaling"
echo "• Logs: companion-desktop/logs/companion.log"