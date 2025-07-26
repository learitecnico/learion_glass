# TEST PLAN - Learion Glass V1.1 (Vuzix M400)

> **Objetivo:** Validação completa do sistema standalone M400 → OpenAI Realtime API
> **Target Performance:** <400ms latency, >95% uptime, <150MB RAM, <20% battery/hora
> **Data:** 2025-07-25

---

## 🎯 **PRÉ-REQUISITOS DE TESTE**

### **Hardware Setup**
- [ ] **Vuzix M400**: Carregado (>80% battery)
- [ ] **WiFi 5GHz**: Rede estável, <50ms ping
- [ ] **ADB Connection**: USB-C conectado para logs
- [ ] **OpenAI API**: Key válida com credits

### **Software Setup**
```bash
# Verificar conectividade
adb devices
ping -c 5 8.8.8.8

# Preparar logs monitoring  
adb logcat -c  # Clear previous logs
```

### **Network Requirements**
- **WiFi 5GHz obrigatório** (2.4GHz = +100ms latency)
- **Bandwidth**: >1Mbps up/down para audio streaming
- **Stability**: <1% packet loss para OpenAI WebSocket

---

## 📋 **CHECKLIST DE EXECUÇÃO**

### **□ FASE 1: DEPLOYMENT**
**Tempo estimado:** 5 minutos

#### **T1.1 - Cleanup Sistema**
```bash
# Remover versão anterior
adb uninstall com.seudominio.app_smart_companion

# Verificar limpeza
adb shell pm list packages | grep seudominio
# Resultado esperado: (vazio)
```
**✅ Critério:** Nenhum package anterior encontrado

#### **T1.2 - Install Learion Glass**
```bash
# Deploy nova versão
adb install app/build/outputs/apk/debug/app-debug.apk

# Verificar instalação  
adb shell dumpsys package com.seudominio.app_smart_companion | grep versionName
```
**✅ Critério:** APK instala sem erro, versão correta

#### **T1.3 - Logs Setup**
```bash
# Monitor logs em background
adb logcat -s LearionGlass:* > test_logs_$(date +%Y%m%d_%H%M%S).log &
```
**✅ Critério:** Log stream ativo

---

### **□ FASE 2: INICIALIZAÇÃO**
**Tempo estimado:** 3 minutos

#### **T2.1 - App Launch**
**Ação:** Abrir "Learion Glass" no launcher M400  
**Observar:**
- [ ] App abre sem crash (5s max)
- [ ] HUD mostra "Learion Glass Ready!"
- [ ] Voice command instructions visíveis
- [ ] Current agent displayed (default: Elato)

**Logs esperados:**
```
📝 [UI] MainActivity onCreate - ActionMenuActivity initialized
🔍 [DEBUG] HUD components initialized  
📝 [UI] Learion Glass Ready! message displayed
```

#### **T2.2 - ActionMenu Test**
**Ação:** Tap trackpad para abrir menu  
**Observar:**
- [ ] Menu aparece com 6 itens numerados
- [ ] Items corretos: "1. Iniciar IA", "2. Processar Áudio", etc.
- [ ] Navigation com swipe left/right funciona
- [ ] Selection com tap funciona

**✅ Critério:** Interface Vuzix 100% funcional

#### **T2.3 - Voice Commands**
**Ação:** "Hello Vuzix" → aguardar → "1"  
**Observar:**
- [ ] Speech bubbles aparecem (se habilitado)
- [ ] Comando "1" reconhecido
- [ ] Action triggered corretamente

**✅ Critério:** Voice recognition funcionando

---

### **□ FASE 3: CONEXÃO OPENAI (CRÍTICA)**
**Tempo estimado:** 5 minutos

#### **T3.1 - Session Start**
**Ação:** Voice "1" ou menu "1. Iniciar IA"  
**Observar:**
- [ ] HUD mostra "Starting AI session..."
- [ ] Notification aparece: "Learion Glass - Elato: Connecting..."
- [ ] Processing indicator ativo
- [ ] Sem crashes ou timeouts (15s max)

**Logs esperados:**
```
🚀 [CONNECTION] Starting session with agent: Elato
📡 [CONNECTION] Connecting to OpenAI Realtime API...
✅ [CONNECTION] [client] connection_established  
🎧 [AUDIO] Audio components initialized for Vuzix M400
🎤 [AUDIO] Audio capture started
```

**⏱️ Métrica:** Connection time <10s

#### **T3.2 - Connection Stability**
**Ação:** Manter sessão ativa por 3 minutos  
**Observar:**
- [ ] HUD mostra "Connected to OpenAI"  
- [ ] Notification permanece ativa
- [ ] Sem disconnections inesperadas
- [ ] Processing indicator responsive

**Logs esperados:**
```
📊 [PERFORMANCE] connection_success_rate: 100%
📊 [PERFORMANCE] reconnection_count: 0
```

**⏱️ Métrica:** >95% uptime, 0 reconnections

#### **T3.3 - Error Recovery Test**
**Ação:** Momentaneamente desconectar WiFi (10s)  
**Observar:**
- [ ] HUD mostra error message apropriada
- [ ] Auto-reconnect triggered após WiFi volta  
- [ ] Session restored automaticamente
- [ ] User feedback claro durante recovery

**Logs esperados:**
```
❌ [ERROR] Connection failed: Network unreachable | recovery_action=retry_with_backoff
🔄 [CONNECTION] Attempting reconnection...
✅ [CONNECTION] Reconnected successfully
📊 [PERFORMANCE] reconnection_count: 1
```

**⏱️ Métrica:** Recovery time <5s

**✅ Critério Fase 3:** OpenAI connection estável e resiliente

---

### **□ FASE 4: AUDIO PIPELINE (CORE)**
**Tempo estimado:** 10 minutos

#### **T4.1 - Audio Capture**
**Ação:** Falar "Hello, this is a test" próximo ao mic M400  
**Observar:**
- [ ] Processing indicator ativa durante fala
- [ ] HUD mostra "Processing your request..."
- [ ] Sem audio dropouts ou distorção

**Logs esperados:**
```
🎤 [AUDIO] Audio capture started | sample_rate=24000, format=PCM16
📊 [AUDIO] Audio sent | chunk_size=480, total_chunks=X, total_bytes=Y
📡 [CONNECTION] [client] audio_data_sent
```

**⏱️ Métrica:** Audio chunks enviados consistentemente

#### **T4.2 - VAD (Voice Activity Detection)**
**Ação:** Falar → pausa 2s → falar novamente  
**Observar:**
- [ ] VAD detecta início/fim da fala
- [ ] Silêncio trigger funciona (2s após parar)
- [ ] Processing indicator acompanha VAD states

**Logs esperados:**
```
🎯 [AUDIO] server.input_audio_buffer.speech_started
⏱️ [AUDIO] VAD threshold: 0.4, silence_duration: 1000ms  
🎯 [AUDIO] server.input_audio_buffer.speech_stopped
```

#### **T4.3 - Manual Commit Test**
**Ação:** Falar algo → NÃO aguardar VAD → usar "2. Processar Áudio"  
**Observar:**
- [ ] Manual commit funciona
- [ ] OpenAI processa audio buffered
- [ ] Resposta gerada mesmo sem auto-VAD

**Logs esperados:**
```
🎤 [AUDIO] Committing audio and requesting response
📡 [CONNECTION] [client] audio_commit_manual
🤖 [AGENT] Response generation triggered
```

**✅ Critério Fase 4:** Audio pipeline M400↔OpenAI funcionando

---

### **□ FASE 5: AI RESPONSES & HUD**
**Tempo estimado:** 8 minutos

#### **T5.1 - Text Response**
**Ação:** Perguntar "What is 2 plus 2?"  
**Observar:**
- [ ] Resposta aparece no HUD em <5s
- [ ] Texto legível e bem formatado
- [ ] Formato correto: "🤖 Elato: The answer is 4."
- [ ] Processing indicator desaparece após resposta

**Logs esperados:**
```
📝 [AGENT] AI Response: The answer is 4.
🤖 [UI] Displaying AI response: The answer is 4.
📺 [UI] HUD text updated: 🤖 Elato: The answer is 4.
📊 [PERFORMANCE] audio_to_response_latency: XXXms
```

**⏱️ Métrica crítica:** Latency <400ms (microfone → HUD)

#### **T5.2 - Audio Response**
**Ação:** Perguntar algo que gere resposta falada  
**Observar:**
- [ ] Audio resposta toca no speaker M400
- [ ] Volume adequado e inteligível
- [ ] Sem distorção, clipping ou artifacts
- [ ] Sync entre texto HUD e audio

**Logs esperados:**
```
🔊 [AUDIO] Audio received | chunk_size=XXX, total_bytes=YYY
🎵 [AUDIO] Audio playback started on Vuzix M400
```

#### **T5.3 - Long Response Handling**
**Ação:** Perguntar "Explain quantum physics in detail"  
**Observar:**
- [ ] HUD trunca text apropriadamente (500 chars)
- [ ] "..." aparece se text truncado
- [ ] Scroll funciona se disponível
- [ ] App não trava por overflow

**Logs esperados:**
```
📺 [UI] Long response truncated: XXXchars → 500chars
📊 [PERFORMANCE] response_length: XXX chars
```

**✅ Critério Fase 5:** AI responses exibidas corretamente

---

### **□ FASE 6: MULTI-AGENT SYSTEM**
**Tempo estimado:** 12 minutos

#### **T6.1 - Agent Switch**
**Ação:** Voice "3" ou menu "3. Trocar Agente"  
**Observar:**
- [ ] HUD mostra "Agent changed to: Sherlock Holmes"
- [ ] Notification title atualiza para novo agent
- [ ] Session restart automático (transparente)
- [ ] Nova personality ativa imediatamente

**Logs esperados:**
```
✅ [AGENT] Agent: Elato → Sherlock Holmes | success=true
🛑 [CONNECTION] Stopping current session...  
🚀 [CONNECTION] Starting session with agent: Sherlock
📊 [PERFORMANCE] agent_switch_success_rate: 100%
```

**⏱️ Métrica:** Agent switch time <3s

#### **T6.2 - Agent Cycle Test**
**Ação:** Trocar agente 4x: Elato→Sherlock→Chef→Fitness→Math  
**Observar:**
- [ ] Cada troca funciona sem erro
- [ ] Session restart rápido e transparente
- [ ] Agent state persiste entre switches
- [ ] UI feedback claro para cada mudança

**Logs esperados:**
```
📊 [PERFORMANCE] agent_switch_count: 4
📊 [PERFORMANCE] agent_switch_failure_rate: 0%
```

#### **T6.3 - Personality Validation**
**Ação:** Perguntar "Who are you?" para 3 agentes diferentes  
**Testar:**
- [ ] **Elato**: Responde como growth mentor/motivational
- [ ] **Sherlock**: Responde como detective/analytical  
- [ ] **Chef**: Responde como culinary expert
- [ ] Respostas condizem com prompts definidos
- [ ] Voice específica aplicada (se audio habilitado)

**✅ Critério Fase 6:** 7 agentes com personalities distintas

---

### **□ FASE 7: PERFORMANCE & LATÊNCIA**
**Tempo estimado:** 15 minutos

#### **T7.1 - Latency Measurement**
**Método:** 10 medições consecutivas  
**Ação:** Falar "Test number X" → cronometrar até resposta HUD  

**Métricas target:**
- [ ] **Média**: <400ms  
- [ ] **Máximo**: <600ms
- [ ] **95th percentile**: <500ms
- [ ] **Sem outliers**: >1000ms

**Comando logs:**
```bash
adb logcat -s LearionGlass:* | grep "📊.*latency" | tail -10
```

**Logs esperados:**
```
📊 [PERFORMANCE] ✅ audio_to_response_latency: 320ms
📊 [PERFORMANCE] ✅ audio_to_response_latency: 380ms  
📊 [PERFORMANCE] ✅ audio_to_response_latency: 295ms
```

#### **T7.2 - Memory Usage**
**Ação:** Sessão ativa 15 minutos + 20 interactions  
**Monitoramento:**
```bash
# Executar durante teste
while true; do
  adb shell dumpsys meminfo com.seudominio.app_smart_companion | grep "TOTAL PSS"
  sleep 30
done
```

**Métricas target:**
- [ ] **Average**: <150MB RAM
- [ ] **Peak**: <200MB RAM  
- [ ] **Stability**: Sem memory leaks
- [ ] **GC**: Activity normal (<10% CPU)

#### **T7.3 - Battery Impact**
**Ação:** Sessão 30 minutos contínua  
**Monitoramento:**
```bash
# Battery level antes/depois  
adb shell dumpsys battery | grep level
```

**Métricas target:**
- [ ] **Drain rate**: <20%/hora
- [ ] **Thermal**: M400 não overheating
- [ ] **CPU usage**: <15% average
- [ ] **Stability**: Sem throttling

**✅ Critério Fase 7:** Performance targets atingidos

---

### **□ FASE 8: STRESS & RELIABILITY**
**Tempo estimado:** 20 minutos

#### **T8.1 - High Frequency Interactions**
**Ação:** 30 requests em 5 minutos (1 a cada 10s)  
**Observar:**
- [ ] App não trava ou fica lento
- [ ] Memory usage estável  
- [ ] OpenAI connection mantida
- [ ] Response quality consistente
- [ ] Latency não degrada

**Logs monitorar:**
```
📊 [PERFORMANCE] Session Summary | avg_latency_ms, connection_success_rate
```

#### **T8.2 - Network Stress Test**
**Ação:** Durante sessão ativa, simular:
1. WiFi disconnect/reconnect (3x)
2. Switch para 2.4GHz temporariamente  
3. Network congestion (download grande)

**Observar:**
- [ ] Recovery automático de disconnections
- [ ] Graceful degradation em 2.4GHz
- [ ] Performance mantida com network load
- [ ] User feedback apropriado

#### **T8.3 - App Lifecycle Test**
**Ação:** Durante sessão ativa:
1. Home button → background 2min → voltar
2. Recent apps → task switch → voltar
3. Notification panel → voltar

**Observar:**
- [ ] Foreground service mantém session
- [ ] State preservado após background
- [ ] Audio pipeline reativa imediatamente
- [ ] Reconnection se necessário

**✅ Critério Fase 8:** Sistema robusto sob stress

---

### **□ FASE 9: USER EXPERIENCE COMPLETA**
**Tempo estimado:** 20 minutos

#### **T9.1 - Complete User Journey**
**Cenário:** Usuário típico 15 minutos  

**Script:**
1. **Launch** app → Ver welcome message
2. **Start session**: Voice "Hello Vuzix, 1"  
3. **Interact**: 3 perguntas ao Elato
4. **Switch agent**: Voice "3" → Sherlock  
5. **Analyze**: 2 perguntas analíticas  
6. **Switch again**: Voice "3" → Chef
7. **Recipe**: Pedir uma receita simples
8. **Status check**: Voice "4" → Ver status
9. **End session**: Voice "6" → Parar

**Observar:**
- [ ] Journey completo sem crashes
- [ ] Todas interactions funcionam perfeitamente
- [ ] Performance mantida durante toda sessão
- [ ] UX fluida e intuitiva no M400
- [ ] Voice commands 100% recognition

**Logs verificar:**
```
📊 [PERFORMANCE] Session Summary (900000ms) | connection_success_rate=100%, avg_latency_ms=XXX
```

#### **T9.2 - Edge Cases**
**Testar:**
- [ ] **Mumbling/unclear speech**: Como app reage?
- [ ] **Background noise**: Recognition degrada?
- [ ] **Very long questions**: App handle bem?
- [ ] **Rapid fire questions**: Queue handling OK?
- [ ] **Empty/silence input**: Graceful handling?

#### **T9.3 - Accessibility & Usability**  
**Testar:**
- [ ] **Low battery M400**: App continua funcionando?
- [ ] **Poor WiFi signal**: Degradation graceful?
- [ ] **Bright sunlight**: HUD ainda legível?
- [ ] **Walking/movement**: Audio quality mantida?

**✅ Critério Fase 9:** UX excelente em condições reais

---

## 📊 **CRITÉRIOS DE SUCESSO GLOBAL**

### **🎯 Performance Targets (OBRIGATÓRIO)**
- [ ] **Latency média**: <400ms (microfone → HUD)
- [ ] **Connection uptime**: >95%  
- [ ] **Memory usage**: <150MB average
- [ ] **Battery drain**: <20%/hora
- [ ] **Agent switch time**: <3s

### **🎯 Functional Requirements (OBRIGATÓRIO)**
- [ ] **Voice commands**: 100% recognition (commands 1-6)
- [ ] **Agent system**: 7 agents funcionando perfeitamente
- [ ] **Audio pipeline**: M400 mic → OpenAI → M400 speaker  
- [ ] **HUD display**: Real-time transcription clara
- [ ] **Error recovery**: Robusta e transparente

### **🎯 User Experience (OBRIGATÓRIO)**
- [ ] **Zero-config setup**: Install APK → funciona imediatamente
- [ ] **Vuzix standards**: ActionMenu + voice commands padrão  
- [ ] **Clear feedback**: Status messages claros
- [ ] **Reliability**: <1% error rate em uso normal

---

## 📝 **DOCUMENTAÇÃO RESULTADOS**

### **Success Metrics Template**
```
LEARION GLASS V1.1 - TEST RESULTS
Date: ___________
Tester: ___________
M400 Serial: ___________
WiFi Network: ___________

PERFORMANCE:
✅/❌ Latency: ___ms avg (target: <400ms)
✅/❌ Uptime: ___%  (target: >95%)  
✅/❌ Memory: ___MB avg (target: <150MB)
✅/❌ Battery: ___%/h (target: <20%/h)

FUNCTIONALITY:
✅/❌ Voice commands (6/6 working)
✅/❌ Agent switching (7/7 agents)  
✅/❌ Audio pipeline (bidirectional)
✅/❌ HUD display (real-time)
✅/❌ Error recovery (robust)

USER EXPERIENCE:
✅/❌ Setup (zero-config)
✅/❌ Navigation (Vuzix standard)
✅/❌ Feedback (clear status)  
✅/❌ Reliability (<1% errors)

OVERALL: ✅ PASS / ❌ FAIL
Notes: ________________________
```

### **Debug Data Export**
```bash
# Após completar testes
adb exec-out run-as com.seudominio.app_smart_companion cat files/learion_glass_debug.log > learion_test_results_$(date +%Y%m%d).log

# System info
adb shell getprop ro.build.version.release > device_info.txt
adb shell dumpsys wifi >> device_info.txt
adb shell dumpsys battery >> device_info.txt
```

---

**🚀 READY TO EXECUTE!**  
Sistema de testes completo para validação total do Learion Glass V1.1 no Vuzix M400.