# OpenAI Assistants Integration Plan

> **Data:** 2025-07-25  
> **Objetivo:** Implementar nova modalidade de comunicação usando OpenAI Assistants API  
> **Status:** PLANEJAMENTO - Alternativa à Realtime API  

## 🎯 **VISÃO GERAL**

Esta é a **segunda modalidade de comunicação** no Smart Companion, implementando integração com OpenAI Assistants API como alternativa à Realtime API que apresentou problemas de integração.

### **Diferenças-Chave vs Realtime API:**
- ✅ **HTTP REST** em vez de WebSocket (mais estável)
- ✅ **Gerenciamento automático de contexto** (threads persistentes)
- ✅ **Multimodalidade nativa** (texto, áudio, imagens, arquivos)
- ✅ **Processamento assíncrono** (não bloqueia UI)
- ✅ **Tools integration** (code interpreter, file search, function calling)

---

## 📋 **ARQUITETURA ASSISTANTS API**

### **1. Componentes Principais**

#### **Assistant**
- Entidade com nome, descrição, instruções, modelo padrão
- Ferramentas disponíveis (code interpreter, file search, function calling)
- Arquivos anexados (knowledge base)

#### **Thread**
- Container persistente para mensagens de conversação
- **1 thread por usuário** (recomendação OpenAI)
- Contexto ilimitado com truncamento automático
- Metadata personalizável

#### **Messages**
- Building blocks da conversação
- Suporte a texto, imagens, arquivos de áudio
- Roles: `user`, `assistant`
- Anexos de arquivos com diferentes propósitos

#### **Runs**
- Execução assíncrona do processamento
- Status: `queued`, `in_progress`, `completed`, `failed`
- Streaming de respostas opcional

### **2. Fluxo de Comunicação**

```
[Vuzix M400] → [OpenAI Assistants API] → [Response Processing] → [HUD Display]
     ↑                    ↓                        ↓                    ↓
  Audio/Text         HTTP REST                Async Run            Real-time UI
     ↑                    ↓                        ↓                    ↓
  User Input         File Upload              Status Polling       Response Display
```

---

## 🔧 **IMPLEMENTAÇÃO TÉCNICA**

### **REST Endpoints Principais**

#### **Assistants Management**
- `POST /v1/assistants` - Criar assistant
- `GET /v1/assistants/{assistant_id}` - Obter assistant
- `POST /v1/assistants/{assistant_id}` - Modificar assistant

#### **Thread Management**
- `POST /v1/threads` - Criar thread
- `GET /v1/threads/{thread_id}` - Obter thread
- `POST /v1/threads/{thread_id}` - Modificar thread

#### **Messages**
- `POST /v1/threads/{thread_id}/messages` - Criar message
- `GET /v1/threads/{thread_id}/messages` - Listar messages

#### **Runs**
- `POST /v1/threads/{thread_id}/runs` - Criar run
- `GET /v1/threads/{thread_id}/runs/{run_id}` - Status do run
- `GET /v1/threads/{thread_id}/runs/{run_id}/stream` - Stream response

#### **Files**
- `POST /v1/files` - Upload arquivo
- `GET /v1/files/{file_id}` - Obter arquivo
- `DELETE /v1/files/{file_id}` - Deletar arquivo

### **Audio Processing Workflow**

#### **Para Áudio Input (Vuzix M400 → OpenAI):**
1. **Captura de áudio** - AudioRecord do M400
2. **Formato de arquivo** - Salvar como .wav/.mp3/.m4a
3. **Upload via Files API** - `POST /v1/files` (multipart/form-data)
4. **Criar message** - Anexar file_id ao message
5. **Executar run** - Processar com assistant
6. **Poll status** - Verificar conclusão
7. **Obter resposta** - Extract texto e/ou áudio

#### **Para Áudio Output (OpenAI → Vuzix M400):**
1. **Text-to-Speech** - Usar `/v1/audio/speech` se necessário
2. **Audio playback** - VuzixAudioPlayer existente
3. **HUD display** - Texto simultâneo no display

---

## 📱 **INTEGRAÇÃO ANDROID**

### **Dependências Necessárias**

```kotlin
// HTTP Client para REST API
implementation "com.squareup.okhttp3:okhttp:4.12.0"
implementation "com.squareup.okhttp3:logging-interceptor:4.12.0"

// JSON Serialization
implementation "org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0"

// Multipart upload para arquivos
implementation "com.squareup.okhttp3:okhttp:4.12.0"

// Coroutines para async processing
implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"
```

### **Estrutura de Classes**

#### **OpenAIAssistantClient.kt**
- Cliente HTTP para Assistants API
- Authentication via Bearer token
- CRUD operations para assistants, threads, messages, runs

#### **AssistantManager.kt**
- Gerenciamento de multiple assistants
- Pre-configured assistants (similar ao AgentManager atual)
- Thread lifecycle management

#### **AudioFileManager.kt**
- Upload de arquivos de áudio
- Formato conversion (raw PCM → .wav)
- File cleanup e management

#### **AssistantService.kt**
- Foreground service para operações assíncronas
- Integration com existing StandaloneService
- Broadcast updates para UI

### **Menu Integration**

#### **Nova aba "7. Assistants"**
```xml
<!-- main_menu.xml -->
<item android:id="@+id/action_assistants"
      android:title="7. Assistants" />
```

#### **Sub-menu Assistants**
1. **"1. Start Assistant"** - Iniciar nova conversa
2. **"2. Send Audio"** - Gravar e enviar áudio
3. **"3. Send Photo"** - Capturar e enviar imagem
4. **"4. Switch Assistant"** - Trocar assistant ativo
5. **"5. Conversation History"** - Ver histórico thread
6. **"6. Stop Assistant"** - Finalizar sessão

---

## 🤖 **ASSISTANTS PRÉ-CONFIGURADOS**

### **Assistant Profiles**

#### **1. Elato Assistant**
```json
{
  "name": "Elato Growth Assistant",
  "instructions": "You are Elato, a growth and productivity mentor...",
  "model": "gpt-4o-mini",
  "tools": ["file_search", "code_interpreter"],
  "voice": "shimmer"
}
```

#### **2. Technical Assistant**
```json
{
  "name": "Technical Support",
  "instructions": "You are a technical support specialist...",
  "model": "gpt-4o",
  "tools": ["code_interpreter", "function_calling"],
  "voice": "echo"
}
```

#### **3. Visual Assistant**
```json
{
  "name": "Visual Analysis",
  "instructions": "You specialize in image and visual content analysis...",
  "model": "gpt-4o",
  "tools": ["file_search"],
  "voice": "sage"
}
```

### **Tools Capabilities**

#### **Code Interpreter**
- Execute Python code
- Data analysis e visualizations
- Mathematical computations
- File processing

#### **File Search**
- Vector search em arquivos uploaded
- Knowledge base querying
- Document analysis

#### **Function Calling**
- Integration com sistemas externos
- API calls para databases
- Real-time data fetching

---

## 🔄 **FLUXO DE USUÁRIO VUZIX M400**

### **Scenario 1: Conversa por Áudio**
1. **User**: TAP trackpad → Menu → "7. Assistants"
2. **System**: Mostrar sub-menu assistants
3. **User**: Voice command "1" → "Start Assistant"
4. **System**: Criar thread, exibir "🤖 Assistant ready"
5. **User**: Voice command "2" → "Send Audio"
6. **System**: Gravar áudio (AudioRecord)
7. **System**: Upload áudio → Criar message → Execute run
8. **System**: Poll run status → Display response no HUD
9. **User**: Continue conversation ou "6" para stop

### **Scenario 2: Conversa com Imagem**
1. **User**: Menu → "7. Assistants" → "3. Send Photo"
2. **System**: Ativar camera capture (existing CameraCapture)
3. **System**: Upload imagem → Criar message com attachment
4. **System**: Execute run → Display analysis no HUD

### **Scenario 3: Trocar Assistant**
1. **User**: Menu → "7. Assistants" → "4. Switch Assistant"
2. **System**: Cycle entre assistants pré-configurados
3. **System**: Update thread com novo assistant
4. **System**: Notificar "🤖 Switched to [Assistant Name]"

---

## ⚙️ **CONFIGURAÇÃO E SETUP**

### **Environment Variables**
```kotlin
// No shared preferences ou hardcoded para teste
const val OPENAI_API_KEY = "YOUR_OPENAI_API_KEY_HERE"
const val ASSISTANTS_BASE_URL = "https://api.openai.com/v1"
const val DEFAULT_MODEL = "gpt-4o-mini"
```

### **Authentication**
```kotlin
// HTTP Headers
"Authorization: Bearer $OPENAI_API_KEY"
"Content-Type: application/json"
"OpenAI-Beta: assistants=v2"  // Para features beta
```

### **File Upload Configuration**
```kotlin
// Supported formats
val SUPPORTED_AUDIO_FORMATS = listOf("wav", "mp3", "m4a", "webm")
val SUPPORTED_IMAGE_FORMATS = listOf("jpg", "jpeg", "png", "gif", "webp")
val MAX_FILE_SIZE = 25 * 1024 * 1024  // 25MB limit OpenAI
```

---

## 🚀 **VANTAGENS DA IMPLEMENTAÇÃO**

### **vs Realtime API**
- ✅ **Mais estável** - HTTP REST vs WebSocket
- ✅ **Melhor error handling** - Status codes claros
- ✅ **Multimodalidade** - Audio + images + files nativamente
- ✅ **Persistência** - Threads mantêm contexto
- ✅ **Tools integration** - Code interpreter, file search
- ✅ **Async processing** - Não bloqueia UI thread

### **vs Chat Completions**
- ✅ **Gerenciamento automático de contexto** - Sem limite de tokens manual
- ✅ **File processing nativo** - Upload direto, sem chunking
- ✅ **Memory** - Threads persistentes entre sessões
- ✅ **Tools** - Capabilities avançadas built-in

---

## 📊 **MÉTRICAS E MONITORING**

### **Performance Targets**
- **File upload time**: <5s para arquivos de áudio M400
- **Response latency**: <10s para text responses
- **Image analysis**: <15s para visual content
- **Thread persistence**: Unlimited messages per thread

### **Error Handling**
- Network errors: Retry logic com exponential backoff
- File upload failures: Fallback para text-only messages
- Rate limiting: Queue management com user feedback
- API errors: Descriptive error messages no HUD

---

## 🔧 **IMPLEMENTAÇÃO FASEADA**

### **Fase 1: Core Integration (Semana 1)**
- [ ] OpenAIAssistantClient basic implementation
- [ ] Thread e Message management
- [ ] Text-only communication
- [ ] Menu integration básica

### **Fase 2: Audio Support (Semana 2)**
- [ ] Audio file upload functionality
- [ ] Integration com existing AudioRecord
- [ ] Audio response playback
- [ ] Error handling e retries

### **Fase 3: Multimodal (Semana 3)**
- [ ] Image upload e analysis
- [ ] File attachments support
- [ ] Tools integration (code interpreter)
- [ ] Advanced assistant configurations

### **Fase 4: Polish & Optimization (Semana 4)**
- [ ] Performance optimization
- [ ] Battery usage analysis
- [ ] User experience refinements
- [ ] Comprehensive testing

---

## 🎯 **SUCCESS CRITERIA**

### **Technical**
- [ ] Successful audio upload e transcription
- [ ] Text responses displayed no HUD
- [ ] Thread persistence entre sessions
- [ ] Multiple assistants switching
- [ ] Error handling robusto

### **User Experience**
- [ ] Intuitive menu navigation
- [ ] Clear feedback durante processing
- [ ] Responsive UI (non-blocking)
- [ ] Consistent com existing app patterns

### **Performance**
- [ ] <10s response time para text
- [ ] <15s response time para audio analysis
- [ ] Stable operation 30+ minutes
- [ ] Memory usage <200MB

---

## 📝 **PRÓXIMOS PASSOS**

1. **Aprovar arquitetura** - Review e feedback do plano
2. **Setup development environment** - Dependencies e keys
3. **Implement core classes** - OpenAIAssistantClient, AssistantManager
4. **Add menu integration** - Nova aba "Assistants"
5. **Test basic text communication** - Create thread, send message, get response
6. **Iterate baseado em testes** - Refinements e optimizations

**Este documento será atualizado conforme implementação avançar.**

---

**Status:** ✅ DOCUMENTAÇÃO COMPLETA - PRONTO PARA IMPLEMENTAÇÃO