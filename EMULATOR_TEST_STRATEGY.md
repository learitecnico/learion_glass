# 🧪 EMULATOR TEST STRATEGY (No Simulation)

> **Objetivo:** Testar o máximo possível sem simulações ou API real
> **Data:** 2025-07-27

## 🎯 O QUE PODEMOS TESTAR NO EMULADOR

### 1. **TESTE DE INICIALIZAÇÃO E NAVEGAÇÃO**

```bash
# Instalar APK no emulador
adb install app-debug.apk

# Monitorar logs específicos
adb logcat -c  # Clear logs
adb logcat | grep -E "LearionGlass|ActiveModeManager|ThreadManager|TTSManager|VoiceCommander"
```

**O que verificar:**
- ✅ App abre sem crashes
- ✅ Menu principal carrega
- ✅ Navegação entre menus funciona
- ✅ Logs mostram inicializações corretas

### 2. **TESTE DE VOICE COMMANDS (Via ADB)**

```bash
# Testar navegação por voz
adb shell am broadcast -a VOICE_TEST --es phrase "one"

# Testar comandos do modo ativo
adb shell am broadcast -a VOICE_TEST --es phrase "record"
adb shell am broadcast -a VOICE_TEST --es phrase "stop"
adb shell am broadcast -a VOICE_TEST --es phrase "photo"
adb shell am broadcast -a VOICE_TEST --es phrase "new thread"
adb shell am broadcast -a VOICE_TEST --es phrase "toggle audio"
```

**Logs esperados:**
```
D/LearionGlass: 🧪 Voice test command received: 'record'
D/LearionGlass: 🗣️ Voice command received: record
D/LearionGlass: 🎯 Handling voice command: start_recording
D/LearionGlass: 🎤 Voice: Start recording requested
```

### 3. **TESTE DE CONFIGURAÇÃO DE API KEY**

```bash
# Configurar API key via ADB (sem fazer chamadas reais)
adb shell am broadcast -a com.seudominio.app_smart_companion.SET_API_KEY --es api_key "sk-test-key-for-validation-only"
```

**Verificar:**
- SharedPreferences salva a key
- ActiveModeManager pode ser inicializado
- Não faz chamadas reais à API

### 4. **TESTE DE ESTADOS E TRANSIÇÕES**

```bash
# Via menu do emulador:
1. Main Menu → Assistants → Coach SPIN
2. Verificar se todos os 6 itens aparecem
3. Tentar entrar em "Conexão Ativa"
```

**Logs esperados sem API:**
```
D/LearionGlass: 🚀 Activating Coach SPIN connection...
D/LearionGlass: 🔗 NEW PATTERN: Activating assistant connection for Coach SPIN
W/LearionGlass: ❌ API key not configured  # Se não configurou
# OU
D/ActiveModeManager: 📦 Creating ActiveModeManager instance  # Se configurou key fake
```

### 5. **TESTE DE PERSISTÊNCIA (SharedPreferences)**

```bash
# Verificar se preferences são salvas
adb shell run-as com.seudominio.app_smart_companion cat /data/data/com.seudominio.app_smart_companion/shared_prefs/thread_manager_prefs.xml

adb shell run-as com.seudominio.app_smart_companion cat /data/data/com.seudominio.app_smart_companion/shared_prefs/active_mode_prefs.xml
```

### 6. **TESTE DE MENU ACTION (Vuzix Simulation)**

```bash
# Simular teclas do trackpad
adb shell input keyevent KEYCODE_DPAD_CENTER  # Select
adb shell input keyevent KEYCODE_DPAD_DOWN    # Navigate down
adb shell input keyevent KEYCODE_DPAD_UP      # Navigate up
adb shell input keyevent KEYCODE_BACK         # Back
```

## 📊 VALIDAÇÕES ESTRUTURAIS A FAZER

### 1. **Verificar Ordem de Inicialização**
```kotlin
// No ActiveModeManager.initializeComponents()
// Ordem correta deve ser:
1. OpenAIAssistantClient (base)
2. ThreadManager (precisa do client)
3. Audio/Photo Managers (podem ser paralelos)
4. TTSManager (independente)
```

### 2. **Verificar Cleanup Resources**
```kotlin
// Todos os managers devem ter cleanup() em:
- ActiveModeManager.exitActiveMode()
- ActiveModeManager.cleanup()
- MainActivity.onDestroy()
```

### 3. **Verificar Error Boundaries**
```kotlin
// Cada manager deve tratar erros localmente
// Não deixar exceptions propagarem para MainActivity
```

## 🔍 CHECKLIST DE VALIDAÇÃO ESTRUTURAL

### **Integrações Críticas:**
- [ ] AssistantAudioManager usa sendTextMessage (não deprecated)
- [ ] TTSManager tem tratamento para respostas grandes
- [ ] ThreadManager valida expiração antes de usar
- [ ] ActiveModeManager para áudio ao sair
- [ ] MainActivity limpa managers em onDestroy

### **Configurações Sensíveis:**
- [ ] API Key não é logada
- [ ] Thread IDs são validados
- [ ] Audio permissions verificadas
- [ ] Network state considerado

### **Fluxo de Callbacks:**
- [ ] Todos os callbacks rodam em UI thread
- [ ] Não há callbacks órfãos
- [ ] Estados são atualizados corretamente

## 🚀 COMANDO COMPLETO DE TESTE

```bash
# 1. Limpar e instalar
adb uninstall com.seudominio.app_smart_companion
adb install app-debug.apk

# 2. Iniciar com logs limpos
adb logcat -c
adb logcat | tee emulator_test_log.txt | grep -E "LearionGlass|Manager|Voice"

# 3. Testar navegação básica
# (Use o emulador para navegar pelos menus)

# 4. Testar voice commands
adb shell am broadcast -a VOICE_TEST --es phrase "one"
sleep 2
adb shell am broadcast -a VOICE_TEST --es phrase "back"

# 5. Verificar logs
grep -E "ERROR|FATAL|Exception|null" emulator_test_log.txt
```

## ✅ SINAIS DE SUCESSO

1. **Navegação fluida** sem crashes
2. **Logs organizados** com fluxo claro
3. **Voice commands** reconhecidos e processados
4. **Estados** transitam corretamente
5. **Nenhum erro** de NullPointer ou Exception

---

**Próximo:** Executar estes testes no emulador