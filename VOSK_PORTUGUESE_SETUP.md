# VOSK PORTUGUESE MODEL SETUP

> **Objetivo:** Configurar modelo Vosk em português para transcrição local
> **Data:** 2025-07-26
> **Status:** READY FOR PORTUGUESE MODEL

## 📦 DOWNLOAD DO MODELO PORTUGUÊS

### **PASSO 1: Baixar modelo Vosk PT-BR**

**Opções de modelos disponíveis:**

1. **vosk-model-small-pt-0.3** (~40MB) - **RECOMENDADO**
   - Link: https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip
   - Tamanho: Adequado para M400
   - Qualidade: Boa para uso geral

2. **vosk-model-pt-0.3** (~1.6GB) - Completo
   - Link: https://alphacephei.com/vosk/models/vosk-model-pt-0.3.zip
   - Tamanho: Muito grande para M400
   - Qualidade: Excelente

### **PASSO 2: Preparar para Android**

```bash
# 1. Baixar modelo small (recomendado)
wget https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip

# 2. Extrair
unzip vosk-model-small-pt-0.3.zip

# 3. Renomear pasta
mv vosk-model-small-pt-0.3 model-pt-br
```

### **PASSO 3: Adicionar ao projeto Android**

```bash
# Colocar no diretório assets do projeto
cp -r model-pt-br app/src/main/assets/

# Estrutura esperada:
# app/src/main/assets/
#   └── model-pt-br/
#       ├── am/
#       ├── conf/
#       ├── graph/
#       ├── ivector/
#       └── README
```

## 🔧 CONFIGURAÇÃO NO CÓDIGO

### **Já implementado:**

```kotlin
// VoskTranscriptionService.kt
private const val LANGUAGE_MODEL = "model-pt-br"  // Português brasileiro
private const val FALLBACK_MODEL = "model-en-us"  // Fallback para inglês

// Tentativa automática: PT-BR → EN (se PT falhar)
```

### **Build.gradle.kts:**

```kotlin
// Tarefa para preparar modelo português
tasks.register("genUUID_pt") {
    val uuid = UUID.randomUUID().toString()
    val odir = file("${layout.buildDirectory.get()}/generated/assets/model-pt-br")
    val ofile = file("$odir/uuid")
    doLast {
        mkdir(odir)
        ofile.writeText(uuid)
    }
}
```

## 📋 TESTE COM MODELO PORTUGUÊS

### **Frases de teste em português:**

1. **"Olá Coach SPIN, preciso de ajuda com vendas"**
2. **"Tenho dificuldade para lidar com objeções do cliente"**
3. **"Como usar a metodologia SPIN nesta situação"**
4. **"O cliente não está interessado no nosso produto"**

### **Logs esperados:**

```
D/VoskTranscriptionService: 📦 Loading Vosk model: model-pt-br
D/VoskTranscriptionService: ✅ Vosk Portuguese model loaded successfully
D/VoskTranscriptionService: 📝 Transcript FINAL: 'olá coach spin preciso de ajuda'
```

### **Se português falhar:**

```
W/VoskTranscriptionService: ⚠️ Portuguese model not found, trying English fallback
D/VoskTranscriptionService: ✅ Vosk English fallback model loaded successfully
```

## 🎯 VANTAGENS DO MODELO PORTUGUÊS

✅ **Melhor precisão** - Para falantes nativos de português  
✅ **Contexto local** - Entende expressões brasileiras  
✅ **Coach SPIN em PT** - Coaching em português natural  
✅ **Fallback seguro** - Inglês como backup  
✅ **Privacy mantido** - Tudo local, nada para a nuvem  

## 📁 ESTRUTURA DE ARQUIVOS

```
app/src/main/assets/
├── model-pt-br/          ← Modelo português (principal)
│   ├── am/
│   ├── conf/
│   ├── graph/
│   ├── ivector/
│   └── README
└── model-en-us/          ← Modelo inglês (fallback)
    ├── am/
    ├── conf/
    ├── graph/
    ├── ivector/
    └── README
```

## 🚨 PROBLEMAS COMUNS

### **Modelo não encontrado:**
- Verificar pasta: `app/src/main/assets/model-pt-br/`
- Verificar nome exato: deve ser `model-pt-br`
- Rebuild projeto após adicionar arquivos

### **Tamanho do APK:**
- Modelo small: +40MB no APK
- Modelo full: +1.6GB (não recomendado)
- Considerar download dinâmico se necessário

### **Performance:**
- M400 tem limitações de RAM
- Modelo small é otimizado para devices limitados
- Monitor uso de memória durante testes

## 📝 PRÓXIMOS PASSOS

1. **Download modelo small PT-BR** (40MB)
2. **Adicionar aos assets** (model-pt-br folder)
3. **Build projeto** (genUUID_pt task)
4. **Teste com áudio português** (test_audio.wav)
5. **Verificar logs** - Confirmar modelo PT carregado
6. **Fallback test** - Remover PT, testar EN fallback

**Agora o Coach SPIN fala português! 🇧🇷**