# AUDIO CAPTURE TEST PLAN - PASSO 8.1

> **Objetivo:** Testar implementação real de captura de áudio com Vosk local transcription
> **Data:** 2025-07-26
> **Status:** READY FOR TESTING

## 📋 CHECKLIST DE TESTES

### **1. PRE-REQUISITOS**
- [ ] Build do app bem-sucedido sem erros
- [ ] APK instalado no emulador/M400
- [ ] Permissão de áudio concedida
- [ ] Modelo Vosk carregado (verificar logs)

### **2. TESTE BÁSICO - SEND AUDIO (Menu Normal)**
1. [ ] Abrir app
2. [ ] Navegar: Main Menu → Assistants → Coach SPIN
3. [ ] Selecionar "3. Mandar Áudio"
4. [ ] **ESPERADO:** Ver tela de feedback:
   ```
   🎤 Coach SPIN Audio
   
   🔴 Recording voice...
   🧠 Local transcription...
   ⏳ Processing with Vosk
   
   Speak now...
   ```
5. [ ] Falar claramente por 3-5 segundos
6. [ ] **ESPERADO:** Ver indicador de nível de áudio:
   ```
   🎤 Recording Audio
   
   Level: ██░
   Transcript: [palavras aparecem aqui]...
   
   Stop speaking to finish
   ```
7. [ ] Parar de falar (1.5s de silêncio)
8. [ ] **ESPERADO:** Ver transcrição completa:
   ```
   📝 Transcript Complete
   
   "[sua fala transcrita]"
   
   Sending to OpenAI Assistant...
   ```
9. [ ] **ESPERADO:** Receber resposta do Coach SPIN

### **3. TESTE ACTIVE MODE - SEND AUDIO**
1. [ ] No menu Coach SPIN, selecionar "5. Conexão Ativa"
2. [ ] **ESPERADO:** Entrar em modo ativo com thread ID
3. [ ] Double-tap para mostrar menu ativo
4. [ ] Selecionar "1. Enviar Áudio"
5. [ ] Repetir processo de gravação
6. [ ] **ESPERADO:** Áudio enviado na mesma thread

### **4. TESTE DE ERROS**
1. [ ] **Sem permissão de áudio:**
   - [ ] Negar permissão no Android
   - [ ] Tentar enviar áudio
   - [ ] **ESPERADO:** Mensagem de erro sobre permissão
   
2. [ ] **Vosk não inicializado:**
   - [ ] Forçar erro no carregamento do modelo
   - [ ] Tentar enviar áudio
   - [ ] **ESPERADO:** Mensagem de erro sobre serviço

3. [ ] **Sem fala detectada:**
   - [ ] Iniciar gravação mas não falar
   - [ ] **ESPERADO:** "No speech detected"

### **5. TESTE DE PERFORMANCE**
1. [ ] **Latência de transcrição:**
   - [ ] Medir tempo entre fim da fala e transcrição
   - [ ] **TARGET:** < 500ms
   
2. [ ] **Qualidade da transcrição:**
   - [ ] Falar frases claras em inglês
   - [ ] **TARGET:** > 80% precisão
   
3. [ ] **Detecção de silêncio:**
   - [ ] Verificar parada automática após 1.5s
   - [ ] **TARGET:** Funciona consistentemente

## 🔍 LOGS PARA VERIFICAR

### **Sucesso esperado:**
```
D/LearionGlass: 🎯 Send Audio selected
D/LearionGlass: 🎤 Sending audio to Coach SPIN...
D/VoskTranscriptionService: 🎤 Initializing Vosk transcription service...
D/VoskTranscriptionService: ✅ Vosk model loaded successfully
D/CoachAudioRecorder: 🎤 Recording started at 16000Hz
D/VoskTranscriptionService: 📝 Transcript FINAL: 'hello coach spin'
D/LearionGlass: ✅ Audio recording completed
D/LearionGlass: 🚀 Sending transcript to Coach SPIN: 'hello coach spin'
D/AssistantClient: ✅ Thread created: thread_xxx
D/AssistantClient: ✅ Text message sent successfully
D/AssistantClient: ✅ Run completed successfully
```

### **Erros comuns:**
```
E/CoachAudioRecorder: Failed to initialize AudioRecord
E/VoskTranscriptionService: ❌ Failed to load Vosk model
E/LearionGlass: ❌ Audio permission not granted
```

## 📊 MÉTRICAS DE SUCESSO

### **MÍNIMO VIÁVEL:**
- ✅ Captura de áudio funciona sem crashes
- ✅ Vosk transcreve fala básica
- ✅ Transcrição enviada para OpenAI
- ✅ Resposta exibida no HUD

### **QUALIDADE ALVO:**
- ✅ Precisão > 80% em inglês claro
- ✅ Latência < 500ms para transcrição
- ✅ Detecção automática de fim de fala
- ✅ Feedback visual durante gravação

## 🎮 TESTE NO EMULADOR - MODO ESPECIAL

### **Como funciona o modo emulador:**
1. **Detecção automática** - O app detecta que está rodando em emulador
2. **Simulação de áudio** - Gera níveis de áudio falsos por 3 segundos
3. **Transcrição de teste** - Usa frase pré-definida quando Vosk não transcreve
4. **Fluxo completo** - Testa integração com OpenAI mesmo sem áudio real

### **Para testar no emulador:**
1. **Não precisa configurar microfone** - Modo teste automático
2. **Siga o fluxo normal** - Menu → Coach SPIN → Send Audio
3. **Observe o feedback especial:**
   ```
   🎮 EMULATOR TEST MODE
   
   Using test phrase:
   "[frase aleatória de teste]"
   
   Sending to OpenAI Assistant...
   ```
4. **Frases de teste disponíveis:**
   - "I need help with a sales call with a difficult customer"
   - "The customer is asking about pricing and I'm not sure how to respond"
   - "How do I handle objections about our product features"
   - "Can you explain the SPIN methodology for this situation"
   - "What questions should I ask to understand the client's needs better"

### **Logs específicos do emulador:**
```
W/CoachAudioRecorder: 🎮 Emulator detected - using test mode audio
D/CoachAudioRecorder: 🎮 Starting emulator test mode...
D/CoachAudioRecorder: 🎮 Test phrase: 'I need help with a sales call'
D/LearionGlass: 🎮 Emulator mode - using test transcript: '[phrase]'
```

## 🐛 ISSUES CONHECIDOS

1. **Emulador Android:**
   - ✅ RESOLVIDO: Modo de teste automático implementado
   - Não tenta usar microfone real
   - Usa transcrições pré-definidas

2. **Modelo Vosk:**
   - Primeira inicialização pode demorar
   - Modelo ocupa ~50MB de memória
   - No emulador, Vosk pode não transcrever áudio simulado (normal)

3. **API Key:**
   - Necessário configurar chave OpenAI válida
   - Coach SPIN Assistant ID precisa existir
   - Use: "asst_coach_spin_sales" ou crie no dashboard OpenAI

## 📝 NOTAS DE IMPLEMENTAÇÃO

### **Arquivos modificados:**
1. `MainActivity.kt` - Integração real de áudio
2. `CoachAudioRecorder.kt` - Nova classe para captura
3. `VoskTranscriptionService.kt` - Já existente

### **Fluxo de dados:**
```
M400 Mic → AudioRecord (16kHz) → CoachAudioRecorder
    ↓
ByteArray chunks → VoskTranscriptionService
    ↓
Transcript text → OpenAIAssistantClient
    ↓
Coach SPIN response → HUD Display
```

### **Próximos passos após validação:**
1. **PASSO 8.2:** Photo Capture implementation
2. **PASSO 8.3:** TTS para respostas de áudio
3. **Otimizações:** Melhorar UI feedback, adicionar cancelamento

---

**Para executar os testes:**
1. Build: `./gradlew assembleDebug`
2. Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. Logs: `adb logcat | grep -E "LearionGlass|Vosk|Coach|Assistant"`