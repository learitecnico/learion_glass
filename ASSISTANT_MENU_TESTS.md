# ASSISTANT MENU SYSTEM - TEST PLAN

> **Versão:** 2025-07-26 18:00  
> **Build:** app-debug.apk (bf5d6b9)  
> **Status:** ✅ INSTALLED ON EMULATOR

## 🧪 TEST CATEGORIES

### **FASE 1: BASIC NAVIGATION TESTS**
- [ ] **T1.1** - Main menu displays correctly
- [ ] **T1.2** - "1. Assistants" option visible and selectable
- [ ] **T1.3** - Navigate: Main → Assistants → "1. Coach SPIN"
- [ ] **T1.4** - Navigate: Coach SPIN menu shows 6 options
- [ ] **T1.5** - Back navigation: Coach SPIN → Assistants → Main

### **FASE 2: COACH SPIN MENU TESTS**
- [ ] **T2.1** - "1. Testar Conexão" - Shows connection test feedback
- [ ] **T2.2** - "2. Mandar Foto" - Shows photo capture stub
- [ ] **T2.3** - "3. Mandar Áudio" - Shows audio recording stub
- [ ] **T2.4** - "4. Informação" - Shows Coach SPIN agent info
- [ ] **T2.5** - "5. Conexão Ativa" - Enters active mode
- [ ] **T2.6** - "6. Voltar" - Returns to Assistants menu

### **FASE 3: ACTIVE MODE TESTS**
- [ ] **T3.1** - Active mode shows status with thread ID
- [ ] **T3.2** - Menu navigation: Double-tap to show hidden menu
- [ ] **T3.3** - "1. Enviar Áudio" - Quick audio in active mode
- [ ] **T3.4** - "2. Enviar Foto" - Quick photo in active mode
- [ ] **T3.5** - "3. Nova Thread" - Creates new thread ID
- [ ] **T3.6** - "4. Receber Áudio [OFF]" - Toggles to [ON]
- [ ] **T3.7** - "4. Receber Áudio [ON]" - Toggles to [OFF]
- [ ] **T3.8** - "5. Voltar" - Returns to Coach SPIN menu

### **FASE 4: STATE MANAGEMENT TESTS**
- [ ] **T4.1** - Thread ID persistence during active mode
- [ ] **T4.2** - Audio toggle state persistence
- [ ] **T4.3** - isCoachActive flag management
- [ ] **T4.4** - Menu state transitions work correctly
- [ ] **T4.5** - Visual feedback appears for all functions

### **FASE 5: VOICE COMMAND INTEGRATION TESTS**
- [ ] **T5.1** - Voice command "one" opens Assistants
- [ ] **T5.2** - Voice commands work in Coach SPIN menu
- [ ] **T5.3** - Voice commands work in Active mode
- [ ] **T5.4** - Voice command "back" navigates correctly

### **FASE 6: ERROR HANDLING TESTS**
- [ ] **T6.1** - Active mode functions validate isCoachActive
- [ ] **T6.2** - Invalid menu transitions handled gracefully
- [ ] **T6.3** - Menu recreation works after orientation change
- [ ] **T6.4** - System back button works correctly

## 📊 TEST EXECUTION LOG

### **Test Session:** 2025-07-26 18:00
**Tester:** Assistant System  
**Environment:** Android Emulator  
**APK:** app-debug.apk (bf5d6b9)

| Test ID | Description | Status | Notes |
|---------|-------------|--------|-------|
| T1.1 | Main menu display | ✅ | Menu shows 5 items correctly |
| T1.2 | Assistants option | ✅ | "1. Assistants" visible and working |
| T1.3 | Navigation to Coach SPIN | ✅ | MAIN → ASSISTANTS → COACH_SPIN works |
| T1.4 | Coach SPIN 6 options | ✅ | All 6 options displayed correctly |
| T1.5 | Back navigation | ⏳ | |
| T2.1 | Test Connection function | ✅ | Shows connection test feedback correctly |
| T2.2 | Send Photo function | ⏳ | |
| T2.3 | Send Audio function | ⏳ | |
| T2.4 | Information function | ⏳ | |
| T2.5 | Active Connection | ⏳ | |

## 🎯 SUCCESS CRITERIA

### **MINIMUM VIABLE (PASSO 6 VALIDATION):**
- ✅ All 9 functions show visual feedback
- ✅ Menu navigation works without crashes
- ✅ State management preserves data
- ✅ Voice commands integration functional

### **READY FOR PASSO 7:**
- ✅ Audio function stubs ready for Vosk integration
- ✅ Thread management working for conversation persistence
- ✅ Active mode functional for continuous coaching

## 🚨 KNOWN ISSUES TO MONITOR

1. **Menu Display Cache** - Logs vs actual display consistency
2. **Thread Persistence** - Validate thread IDs survive mode changes
3. **Voice Command Conflicts** - Ensure new menus don't break existing commands
4. **Active Mode Stability** - Double-tap gesture needs validation

## 📋 POST-TEST ACTIONS

### **If All Tests Pass:**
- ✅ Mark PASSO 6 as 100% complete
- ✅ Update todo list 
- ✅ Proceed to PASSO 7 (Vosk integration)

### **If Issues Found:**
- 🔧 Document specific issues
- 🔧 Create targeted fixes
- 🔧 Re-test failed scenarios
- 🔧 Update before PASSO 7

---
*This test plan ensures comprehensive validation before advancing to Vosk integration*