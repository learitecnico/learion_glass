# M400 PHYSICAL TESTING CHECKLIST

> **Objetivo**: Lista completa de testes que devem ser realizados no dispositivo Vuzix M400 físico
> **Status**: 🔄 Em desenvolvimento - Lista será atualizada conforme implementação
> **Última atualização**: 2025-07-26 03:20

## 📋 **OVERVIEW**

Este documento contém todos os testes que precisam ser validados no hardware M400 real, organizados por categoria e prioridade. Cada item será marcado conforme testado no dispositivo físico.

---

## 🎯 **CATEGORIA 1: HIERARQUICAL MENU SYSTEM**

### **Menu Navigation Tests**
- [ ] **M400-001**: Main menu exibe 4 itens corretamente (Assistant, Live Agent, Configurações, Sair)
- [ ] **M400-002**: Ícones verdes aparecem corretamente nos itens do menu
- [ ] **M400-003**: Navegação trackpad funciona (cima/baixo entre itens)
- [ ] **M400-004**: Seleção trackpad (tap) abre submenus corretamente
- [ ] **M400-005**: Assistant submenu exibe "Iniciar Chat" e "Voltar"
- [ ] **M400-006**: Live Agent submenu exibe "Start Chat", "Switch Agent", "Voltar"
- [ ] **M400-007**: Botão "Voltar" retorna ao menu anterior
- [ ] **M400-008**: Navigation stack funciona (múltiplos back buttons)
- [ ] **M400-009**: Visual feedback aparece no HUD para cada ação
- [ ] **M400-010**: Exit menu executa shutdown limpo em 2 segundos

### **Voice Commands Tests**
- [ ] **M400-011**: "Hello Vuzix, 1" seleciona item 1 do menu principal
- [ ] **M400-012**: "Hello Vuzix, 2" seleciona item 2 do menu principal
- [ ] **M400-013**: "Hello Vuzix, 3" abre configurações
- [ ] **M400-014**: "Hello Vuzix, 4" executa exit do app
- [ ] **M400-015**: Voice commands funcionam nos submenus
- [ ] **M400-016**: Voice commands têm feedback visual no HUD

---

## 🤖 **CATEGORIA 2: ASSISTANT API (HTTP REST)**

### **Basic Functionality**
- [ ] **M400-017**: Assistant menu item inicia sessão HTTP corretamente
- [ ] **M400-018**: Connection status aparece no HUD ("Starting Assistant...")
- [ ] **M400-019**: Thread criada com sucesso (logs confirmam thread ID)
- [ ] **M400-020**: Status "Ready" aparece após 20-30 segundos
- [ ] **M400-021**: HUD mostra mensagens de status durante conexão

### **Text Communication**
- [ ] **M400-022**: Envio de mensagem de texto funciona
- [ ] **M400-023**: Resposta do assistant aparece no HUD
- [ ] **M400-024**: Formatação "🤖 Assistant: [response]" correta no HUD
- [ ] **M400-025**: Múltiplas mensagens em sequência funcionam
- [ ] **M400-026**: Thread persiste entre mensagens

### **Audio Communication**
- [ ] **M400-027**: Gravação de áudio inicia corretamente
- [ ] **M400-028**: Status "🎤 Starting audio recording..." aparece
- [ ] **M400-029**: Indicator de gravação visível durante capture
- [ ] **M400-030**: Stop recording funciona (tap novamente)
- [ ] **M400-031**: Transcrição aparece corretamente
- [ ] **M400-032**: Assistant responde baseado na transcrição
- [ ] **M400-033**: Qualidade de áudio é adequada (sem ruído excessivo)
- [ ] **M400-034**: Tempo limite de 30 segundos funciona
- [ ] **M400-035**: Cleanup de arquivos temporários após transcrição

---

## 🔊 **CATEGORIA 3: REALTIME API (WEBSOCKET)**

### **Connection & Session**
- [ ] **M400-036**: Live Agent menu inicia WebSocket connection
- [ ] **M400-037**: Connection status green aparece quando conectado
- [ ] **M400-038**: Session configuration enviada corretamente
- [ ] **M400-039**: VAD configuration aplicada (threshold=0.5, silence=500ms)
- [ ] **M400-040**: Agent selection funciona (7 agents disponíveis)

### **Real-time Audio**
- [ ] **M400-041**: Start listening detecta início de fala
- [ ] **M400-042**: VAD detecta speech_started events
- [ ] **M400-043**: Audio streaming funciona durante fala
- [ ] **M400-044**: VAD detecta speech_stopped quando parar de falar
- [ ] **M400-045**: Transcrição aparece em tempo real
- [ ] **M400-046**: Response generation inicia automaticamente
- [ ] **M400-047**: Audio response playback no M400 speaker
- [ ] **M400-048**: Qualidade do áudio response é clara
- [ ] **M400-049**: Latência total < 500ms (fala → resposta)

### **Agent Switching**
- [ ] **M400-050**: Switch Agent menu funciona
- [ ] **M400-051**: Cycling através dos 7 agents (Elato→Sherlock→Chef→etc)
- [ ] **M400-052**: Personality change visível nas responses
- [ ] **M400-053**: Agent name atualiza no HUD display
- [ ] **M400-054**: Voice characteristics mudam conforme agent

---

## 🎨 **CATEGORIA 4: VUZIX UI/UX OPTIMIZATION**

### **HUD Display Tests**
- [ ] **M400-055**: Tema black/green aparece corretamente
- [ ] **M400-056**: Texto 22sp bold é legível no M400
- [ ] **M400-057**: Sombra verde melhora contraste
- [ ] **M400-058**: Border verde define área de conteúdo
- [ ] **M400-059**: 12 linhas de texto visíveis sem scroll
- [ ] **M400-060**: Scroll funciona para textos longos
- [ ] **M400-061**: Status indicators (verde/vermelho) são visíveis
- [ ] **M400-062**: Processing indicator aparece durante operações

### **Interaction & Feedback**
- [ ] **M400-063**: Trackpad responsivo em todos os menus
- [ ] **M400-064**: Visual feedback aparece instantaneamente
- [ ] **M400-065**: Status messages têm duração apropriada
- [ ] **M400-066**: Text updates são smooth (sem flickering)
- [ ] **M400-067**: Professional appearance mantido durante uso

---

## 🔧 **CATEGORIA 5: HARDWARE INTEGRATION**

### **Audio Hardware**
- [ ] **M400-068**: 3 microfones do M400 capturam áudio corretamente
- [ ] **M400-069**: Noise cancellation funciona em ambiente ruidoso
- [ ] **M400-070**: Beam forming direcional funciona
- [ ] **M400-071**: Speaker output tem volume adequado
- [ ] **M400-072**: Audio não tem echo ou feedback
- [ ] **M400-073**: 24kHz sample rate funciona no hardware
- [ ] **M400-074**: Audio effects (AGC, Noise Suppressor) funcionam

### **Performance & Stability**
- [ ] **M400-075**: App não trava após 10+ minutos de uso
- [ ] **M400-076**: Memory usage estável durante operação
- [ ] **M400-077**: CPU usage aceitável (não aquece)
- [ ] **M400-078**: Battery drain aceitável
- [ ] **M400-079**: Network stability em WiFi
- [ ] **M400-080**: Error recovery funciona (reconecta automaticamente)

### **Sensors & Movement**
- [ ] **M400-081**: Head movement não afeta audio capture
- [ ] **M400-082**: Walking movement não interfere com VAD
- [ ] **M400-083**: App funciona com diferentes lighting conditions
- [ ] **M400-084**: Proximity sensors não interferem com operation

---

## ⚙️ **CATEGORIA 6: CONFIGURATION & SETTINGS**

### **API Key Management**
- [ ] **M400-085**: Settings menu accessible via main menu
- [ ] **M400-086**: API key setup instructions aparecem se não configurado
- [ ] **M400-087**: Hardcoded key funciona para testing
- [ ] **M400-088**: Masked key display para segurança
- [ ] **M400-089**: API key persiste após restart

### **Agent & Session Management**
- [ ] **M400-090**: Agent preferences salvam corretamente
- [ ] **M400-091**: Session state persiste durante app lifecycle
- [ ] **M400-092**: Clean shutdown salva state
- [ ] **M400-093**: Restart recupera configurações anteriores

---

## 🔄 **CATEGORIA 7: ERROR HANDLING & EDGE CASES**

### **Network Issues**
- [ ] **M400-094**: WiFi disconnect → reconnect funciona
- [ ] **M400-095**: Poor signal quality não trava app
- [ ] **M400-096**: OpenAI API rate limits são handled gracefully
- [ ] **M400-097**: Timeout errors mostram mensagens úteis
- [ ] **M400-098**: Retry logic funciona automaticamente

### **Audio Edge Cases**
- [ ] **M400-099**: Muito silencioso → mensagem de erro adequada
- [ ] **M400-100**: Muito barulho → noise filtering funciona
- [ ] **M400-101**: Audio interrupt (phone call) → pause e resume
- [ ] **M400-102**: Long speech (>30s) → graceful truncation
- [ ] **M400-103**: Multiple rapid taps → não duplica actions

### **App Lifecycle**
- [ ] **M400-104**: Background → foreground resume funciona
- [ ] **M400-105**: Low memory → graceful degradation
- [ ] **M400-106**: Force stop → clean restart
- [ ] **M400-107**: Multiple apps → audio priority handling

---

## 📊 **TESTING METHODOLOGY**

### **Prioridades de Teste:**
1. **🔥 Crítico (Bloqueia MVP)**: M400-001 to M400-020, M400-036 to M400-049
2. **⚠️ Alto (Afeta UX)**: M400-055 to M400-067, M400-075 to M400-084
3. **📝 Médio (Polish)**: M400-021 to M400-035, M400-085 to M400-098
4. **🔧 Baixo (Edge Cases)**: M400-099 to M400-107

### **Test Execution Protocol:**
1. **Pre-test**: Instalar APK limpo no M400
2. **Environment**: Ambiente controlado primeiro, depois real-world
3. **Documentation**: Screenshot/video para issues encontrados
4. **Logging**: Ativar verbose logging durante testes
5. **Metrics**: Medir latência, memory usage, battery drain

### **Success Criteria:**
- **MVP Ready**: 90% dos testes críticos passando
- **Production Ready**: 95% de todos os testes passando
- **Commercial Ready**: 99% dos testes passando + polish completo

---

## 🎯 **CURRENT STATUS**

| Categoria | Total Tests | Completed | Pending | Success Rate |
|-----------|-------------|-----------|---------|--------------|
| Menu System | 16 | 0 | 16 | 0% |
| Assistant API | 19 | 0 | 19 | 0% |
| Realtime API | 15 | 0 | 15 | 0% |
| UI/UX | 13 | 0 | 13 | 0% |
| Hardware | 17 | 0 | 17 | 0% |
| Configuration | 9 | 0 | 9 | 0% |
| Error Handling | 14 | 0 | 14 | 0% |
| **TOTAL** | **103** | **0** | **103** | **0%** |

---

## 📝 **NOTES**

### **Testing Environment Requirements:**
- Vuzix M400 with Android 13
- WiFi connection (stable)
- OpenAI API key with credits
- Quiet environment for audio tests
- Various real-world scenarios

### **Known Limitations to Test:**
- Emulator audio synthesis vs real microphone
- Network latency in different conditions  
- Battery impact of continuous AI processing
- Thermal behavior under load

### **Future Test Categories (Post-MVP):**
- Camera integration tests
- Multi-user scenarios
- Long-session stability (hours)
- Performance benchmarking
- Accessibility features

---

**📱 Esta lista será atualizada conforme implementação e descoberta de novos cenários de teste.**