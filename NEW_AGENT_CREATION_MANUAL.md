# 📘 MANUAL COMPLETO: CRIAÇÃO DE NOVOS AGENTES ASSISTENTES

> **Última atualização:** 2025-07-27
> **Tempo estimado:** 15-30 minutos por agente
> **Dificuldade:** ⭐⭐☆☆☆ (Fácil-Médio)

## 🎯 VISÃO GERAL

Este manual detalha o processo **passo a passo** para adicionar novos assistentes OpenAI ao app Smart Companion. A arquitetura modular permite adicionar novos agentes **sem modificar a lógica central**.

### ✅ O que você vai precisar:
1. **Assistant ID** da OpenAI (ex: `asst_XXXXXXXXXXXX`)
2. **Nome do Assistente** (ex: "Coach SPIN", "Legal Advisor", etc.)
3. **Descrição breve** do que o assistente faz
4. **Android Studio** configurado com o projeto

### 🏗️ Arquitetura Modular

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│ AssistantRegistry│────▶│ Assistant Model  │────▶│ Menus XML       │
└─────────────────┘     └──────────────────┘     └─────────────────┘
         │                        │                         │
         ▼                        ▼                         ▼
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│ MainActivity    │────▶│ ActiveModeManager│────▶│ API Managers    │
└─────────────────┘     └──────────────────┘     └─────────────────┘
```

---

## 📋 PASSO A PASSO COMPLETO

### 🔧 PASSO 1: Criar os Arquivos de Menu XML

**Local:** `app/src/main/res/menu/`

#### 1.1 Menu Principal do Assistente
Crie: `your_assistant_menu.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">
    
    <!-- Your Assistant Menu Options -->
    
    <!-- 1. Testar Conexão -->
    <item
        android:id="@+id/action_test_connection"
        android:title="1. Testar Conexão"
        android:icon="@drawable/ic_connection_green_24dp"
        android:orderInCategory="1"
        app:showAsAction="never" />
    
    <!-- 2. Mandar Foto -->
    <item
        android:id="@+id/action_send_photo"
        android:title="2. Mandar Foto"
        android:icon="@drawable/ic_camera_green_24dp"
        android:orderInCategory="2"
        app:showAsAction="never" />
    
    <!-- 3. Mandar Áudio -->
    <item
        android:id="@+id/action_send_audio"
        android:title="3. Mandar Áudio"
        android:icon="@drawable/ic_mic_green_24dp"
        android:orderInCategory="3"
        app:showAsAction="never" />
    
    <!-- 4. Informação -->
    <item
        android:id="@+id/action_agent_info"
        android:title="4. Informação"
        android:icon="@drawable/ic_info_green_24dp"
        android:orderInCategory="4"
        app:showAsAction="never" />
    
    <!-- 5. Conexão Ativa -->
    <item
        android:id="@+id/action_active_connection"
        android:title="5. Conexão Ativa"
        android:icon="@drawable/ic_active_green_24dp"
        android:orderInCategory="5"
        app:showAsAction="never" />
    
    <!-- 6. Voltar -->
    <item
        android:id="@+id/action_back"
        android:title="6. Voltar"
        android:icon="@drawable/ic_back_green_24dp"
        android:orderInCategory="6"
        app:showAsAction="never" />
    
</menu>
```

#### 1.2 Menu do Modo Ativo
Crie: `your_assistant_active_menu.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">
    
    <!-- Your Assistant Active Mode Menu (Hidden by default) -->
    
    <!-- 1. Enviar Áudio -->
    <item
        android:id="@+id/action_send_audio_active"
        android:title="1. Enviar Áudio"
        android:icon="@drawable/ic_mic_green_24dp"
        android:orderInCategory="1"
        app:showAsAction="never" />
    
    <!-- 2. Enviar Foto -->
    <item
        android:id="@+id/action_send_photo_active"
        android:title="2. Enviar Foto"
        android:icon="@drawable/ic_camera_green_24dp"
        android:orderInCategory="2"
        app:showAsAction="never" />
    
    <!-- 3. Nova Thread -->
    <item
        android:id="@+id/action_new_thread"
        android:title="3. Nova Thread"
        android:icon="@drawable/ic_refresh_green_24dp"
        android:orderInCategory="3"
        app:showAsAction="never" />
    
    <!-- 4. Receber Áudio -->
    <item
        android:id="@+id/action_toggle_audio_response"
        android:title="4. Receber Áudio [OFF]"
        android:icon="@drawable/ic_speaker_green_24dp"
        android:orderInCategory="4"
        app:showAsAction="never" />
    
    <!-- 5. Voltar -->
    <item
        android:id="@+id/action_back_from_active"
        android:title="5. Voltar"
        android:icon="@drawable/ic_back_green_24dp"
        android:orderInCategory="5"
        app:showAsAction="never" />
    
</menu>
```

---

### 📝 PASSO 2: Registrar o Assistente

**Arquivo:** `app/src/main/java/com/seudominio/app_smart_companion/assistants/AssistantRegistry.kt`

Adicione seu novo assistente na lista `AVAILABLE_ASSISTANTS`:

```kotlin
val AVAILABLE_ASSISTANTS = listOf(
    Assistant(
        id = "asst_XQhJXBsG0JNgsBNzKqe7dQGa",
        name = "Coach SPIN",
        description = "Personal development coach focused on SPIN methodology",
        menuResourceId = R.menu.coach_spin_menu,
        activeMenuResourceId = R.menu.coach_active_menu
    ),
    // ADICIONE SEU NOVO ASSISTENTE AQUI:
    Assistant(
        id = "asst_YOUR_ASSISTANT_ID_HERE",     // ← Coloque o ID do OpenAI
        name = "Your Assistant Name",             // ← Nome de exibição
        description = "What your assistant does", // ← Descrição breve
        menuResourceId = R.menu.your_assistant_menu,        // ← Menu XML criado
        activeMenuResourceId = R.menu.your_assistant_active_menu // ← Menu ativo XML
    )
)
```

---

### 🎨 PASSO 3: Adicionar ao Menu de Assistentes

**Arquivo:** `app/src/main/res/menu/assistants_menu.xml`

```xml
<!-- Antes do item "Voltar", adicione: -->

<!-- 2. Your Assistant -->
<item
    android:id="@+id/action_your_assistant"  <!-- ID único -->
    android:title="2. Your Assistant Name"    <!-- Nome exibido -->
    android:icon="@drawable/ic_assistant_green_24dp"
    android:orderInCategory="2"               <!-- Ajuste a ordem -->
    app:showAsAction="never" />

<!-- Lembre de ajustar o orderInCategory dos outros itens -->
```

---

### 🔌 PASSO 4: Conectar no MainActivity

**Arquivo:** `app/src/main/java/com/seudominio/app_smart_companion/MainActivity.kt`

No método `onOptionsItemSelected`, adicione o case para seu assistente:

```kotlin
// ============ ASSISTANTS MENU ITEMS ============
R.id.action_coach_spin -> {
    Log.d(TAG, "🎯 Coach SPIN selected")
    currentAssistant = AssistantRegistry.getAssistantByName("Coach SPIN")
    navigateToMenu(MenuState.ASSISTANT_MENU)
    showVisualFeedback("Coach SPIN")
    true
}

// ADICIONE SEU NOVO ASSISTENTE AQUI:
R.id.action_your_assistant -> {
    Log.d(TAG, "🎯 Your Assistant selected")
    currentAssistant = AssistantRegistry.getAssistantByName("Your Assistant Name")
    navigateToMenu(MenuState.ASSISTANT_MENU)
    showVisualFeedback("Your Assistant Name")
    true
}
```

---

## ✨ FUNCIONALIDADES AUTOMÁTICAS

Após seguir os passos acima, seu assistente terá **automaticamente**:

### 🎤 Funções de Áudio
- **Gravação de voz** com Vosk
- **Transcrição** via Whisper
- **Envio para assistente** com thread persistente
- **Comandos de voz** (dizer "record", "stop", etc.)

### 📸 Funções de Foto
- **Captura via M400**
- **Análise via GPT-4 Vision**
- **Envio para assistente** com contexto

### 🔗 Modo Ativo (Conexão Persistente)
- **Thread contínua** (24h de duração)
- **Menu oculto** (double-tap para mostrar)
- **TTS opcional** para respostas em áudio
- **Comandos hands-free**

### 💾 Gerenciamento de Estado
- **Persistência de thread** entre sessões
- **Histórico de mensagens**
- **Contadores automáticos**

---

## 🧪 TESTANDO SEU NOVO ASSISTENTE

### 1. Compilar e Instalar
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. Navegação Manual
1. Abra o app
2. Navegue: **Main Menu → Assistants → Your Assistant**
3. Teste cada função do menu

### 3. Teste com Comandos de Voz
```bash
# Simular comando de voz para selecionar assistente
adb shell am broadcast -a VOICE_TEST --es phrase "two"

# Em modo ativo, testar comandos
adb shell am broadcast -a VOICE_TEST --es phrase "record"
adb shell am broadcast -a VOICE_TEST --es phrase "photo"
```

### 4. Verificar Logs
```bash
adb logcat | grep -E "Your Assistant|AssistantRegistry|ActiveMode"
```

---

## 🎯 EXEMPLO COMPLETO: Legal Advisor Assistant

### 1. Criar Menus XML
- `legal_advisor_menu.xml`
- `legal_advisor_active_menu.xml`

### 2. Registrar no AssistantRegistry
```kotlin
Assistant(
    id = "asst_LegalAdvisor123456",
    name = "Legal Advisor",
    description = "AI assistant specialized in legal consultation",
    menuResourceId = R.menu.legal_advisor_menu,
    activeMenuResourceId = R.menu.legal_advisor_active_menu
)
```

### 3. Adicionar ao assistants_menu.xml
```xml
<item
    android:id="@+id/action_legal_advisor"
    android:title="2. Legal Advisor"
    android:icon="@drawable/ic_assistant_green_24dp"
    android:orderInCategory="2"
    app:showAsAction="never" />
```

### 4. Conectar no MainActivity
```kotlin
R.id.action_legal_advisor -> {
    Log.d(TAG, "⚖️ Legal Advisor selected")
    currentAssistant = AssistantRegistry.getAssistantByName("Legal Advisor")
    navigateToMenu(MenuState.ASSISTANT_MENU)
    showVisualFeedback("Legal Advisor")
    true
}
```

---

## 🚨 TROUBLESHOOTING

### ❌ Problema: "No assistant selected"
**Solução:** Verifique se `currentAssistant` está sendo definido corretamente no `onOptionsItemSelected`

### ❌ Problema: Menu não aparece
**Solução:** 
1. Verifique se o XML está em `res/menu/`
2. Confirme que o ID no `AssistantRegistry` corresponde ao nome do arquivo
3. Rode `./gradlew clean` e recompile

### ❌ Problema: Assistant ID inválido
**Solução:** Confirme o ID no OpenAI Playground: https://platform.openai.com/assistants

### ❌ Problema: Comandos de voz não funcionam
**Solução:** O nome do assistente nos comandos deve corresponder exatamente ao registrado

---

## 📚 REFERÊNCIAS TÉCNICAS

### Arquivos Principais
- **Models:** `app/.../models/Assistant.kt`
- **Registry:** `app/.../assistants/AssistantRegistry.kt`
- **Managers:** `app/.../assistants/ActiveModeManager.kt`
- **MainActivity:** `app/.../MainActivity.kt`

### Padrões de Design
- **Registry Pattern:** Centralização de assistentes
- **Dependency Injection:** Managers reutilizáveis
- **State Management:** currentAssistant como fonte única

### Fluxo de Dados
```
User Selection → currentAssistant → AssistantRegistry
      ↓                ↓                    ↓
MenuState.ASSISTANT_MENU → getMenuResource → Display
      ↓                         ↓
ActiveModeManager ← assistant.id → API Calls
```

---

## 🎉 CONCLUSÃO

Com este manual, você pode adicionar novos assistentes em **menos de 30 minutos**. A arquitetura modular garante que:

- ✅ **Zero duplicação** de código
- ✅ **Todas as funcionalidades** disponíveis automaticamente
- ✅ **Fácil manutenção** e atualizações
- ✅ **Escalabilidade** para dezenas de assistentes

**Próximos passos:** Configure seus assistentes no OpenAI Platform e comece a adicionar!

---

> **Criado por:** Claude + Everton
> **Projeto:** Smart Companion for Vuzix M400