# REAL AUDIO TEST GUIDE - VOSK TRANSCRIPTION

> **Objetivo:** Usar arquivo de áudio real para testar Vosk transcription no emulador
> **Data:** 2025-07-26
> **Status:** READY FOR REAL AUDIO TESTING

## 🎵 COMO USAR ARQUIVO DE ÁUDIO REAL

### **PASSO 1: Preparar arquivo de áudio**

**Formato ideal para Vosk:**
- **Sample Rate:** 16kHz (obrigatório)
- **Channels:** Mono (1 channel)
- **Format:** WAV, PCM 16-bit
- **Duration:** 3-10 segundos (ideal para teste)
- **Content:** Fala clara em **PORTUGUÊS BRASILEIRO** 🇧🇷

**Comando para converter áudio (se necessário):**
```bash
# Using FFmpeg (se você tiver instalado)
ffmpeg -i input.wav -ar 16000 -ac 1 -acodec pcm_s16le test_audio.wav

# Using online converters:
# - https://online-audio-converter.com/
# - Set: 16000 Hz, Mono, WAV format
```

### **PASSO 2: Colocar arquivo no emulador**

**Opção A: Via ADB (recomendado)**
```bash
# Criar diretório no emulador
adb shell mkdir -p /sdcard/Android/data/com.seudominio.app_smart_companion/files

# Copiar arquivo
adb push test_audio.wav /sdcard/Android/data/com.seudominio.app_smart_companion/files/

# Verificar
adb shell ls -la /sdcard/Android/data/com.seudominio.app_smart_companion/files/
```

**Opção B: Via Android Studio Device Explorer**
1. Abrir Device Explorer (View → Tool Windows → Device Explorer)
2. Navegar para: `/sdcard/Android/data/com.seudominio.app_smart_companion/files/`
3. Upload do arquivo via interface gráfica

### **PASSO 3: Nomes de arquivo suportados**

O app procura automaticamente por estes nomes (em ordem de prioridade):
1. `test_audio.wav` ⭐ (recomendado)
2. `coach_test.wav`
3. `sales_call.wav`  
4. `vosk_test.wav`

### **PASSO 4: Executar teste**

1. **Build e install:**
   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

2. **Monitor logs:**
   ```bash
   adb logcat | grep -E "CoachAudioRecorder|AudioFileProcessor|Vosk"
   ```

3. **Executar teste:**
   - Abrir app no emulador
   - Menu → Assistants → Coach SPIN → "3. Mandar Áudio"
   - Observar logs e feedback visual

## 📊 LOGS ESPERADOS COM ARQUIVO REAL

### **Sucesso esperado:**
```
D/CoachAudioRecorder: 🎮 Emulator detected - using test mode audio
D/CoachAudioRecorder: 🎮 Starting emulator test mode...
D/CoachAudioRecorder: ✅ Found test audio in external files: /path/test_audio.wav
D/AudioFileProcessor: 🎵 Processing audio file: test_audio.wav
D/AudioFileProcessor: 📊 Audio Info: 16000Hz, 1ch, 3500ms
D/AudioFileProcessor: ✅ Audio already compatible with Vosk
D/AudioFileProcessor: 📡 Sending audio in 640-byte chunks (20ms each)
D/VoskTranscriptionService: 📝 Transcript PARTIAL: 'hello'
D/VoskTranscriptionService: 📝 Transcript FINAL: 'hello coach spin'
D/AudioFileProcessor: ✅ Audio file processing completed
D/LearionGlass: ✅ Audio recording completed
```

### **Fallback para simulação:**
```
D/CoachAudioRecorder: 📁 No test audio files found. Checked:
D/CoachAudioRecorder:    - /sdcard/Android/data/.../files/Downloads
D/CoachAudioRecorder:    - /sdcard/Android/data/.../files
D/CoachAudioRecorder: 🎭 No test audio file found, using simulation
D/CoachAudioRecorder: 🎭 Running simulated audio test...
```

## 🎤 GRAVANDO ÁUDIO DE TESTE

### **Sugestões de conteúdo para Coach SPIN (em português):**

**Frase 1 (básica):**
> "Olá Coach SPIN, preciso de ajuda com uma ligação de vendas"

**Frase 2 (completa):**
> "Tenho um cliente difícil que está perguntando sobre preços e não sei como responder usando a metodologia SPIN"

**Frase 3 (objeção):**
> "O cliente está levantando objeções sobre as características do nosso produto e preciso de coaching para lidar com esta situação"

**Frase 4 (identificação de necessidade):**
> "Estou tendo dificuldade para identificar a real necessidade do cliente durante a reunião"

### **Dicas para gravação:**

1. **Ambiente silencioso** - Evitar ruído de fundo
2. **Dicção clara** - Falar devagar e claramente
3. **Português brasileiro** - Vosk tem modelo específico para PT-BR
4. **Volume consistente** - Não muito baixo nem alto
5. **Pausa no início/fim** - 0.5s de silêncio antes e depois

## 🔧 TROUBLESHOOTING

### **Arquivo não encontrado:**
- Verificar path: `/sdcard/Android/data/com.seudominio.app_smart_companion/files/`
- Verificar permissões do arquivo
- Tentar nome exato: `test_audio.wav`

### **Vosk não transcreve:**
- Verificar sample rate: deve ser 16kHz
- Verificar se é mono (1 channel)
- Verificar qualidade do áudio
- Ver logs do VoskTranscriptionService

### **Erro de formato:**
- Converter para WAV 16kHz mono
- Verificar se arquivo não está corrompido
- Testar com arquivo de áudio mais simples

## 🎯 VANTAGENS DO TESTE COM ARQUIVO REAL

✅ **Vosk transcrição real** - Teste completo do pipeline  
✅ **Qualidade controlada** - Áudio ideal para teste  
✅ **Reproduzível** - Mesmo resultado toda vez  
✅ **Debug facilitado** - Fácil identificar problemas  
✅ **Sem dependência de microfone** - Funciona em qualquer emulador  

## 📝 PRÓXIMOS PASSOS

1. **Você grava um áudio** - 3-5 segundos, 16kHz, inglês claro
2. **Upload via ADB** - Colocar como `test_audio.wav`
3. **Executar teste** - Ver se Vosk transcreve corretamente
4. **Verificar resposta OpenAI** - Pipeline completo funcionando
5. **Ajustar se necessário** - Melhorar qualidade ou formato

**Pronto para usar arquivo real de áudio! 🎵**