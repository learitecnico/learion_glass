# ⚠️ DESENVOLVIMENTO EM PROGRESSO - NÃO USAR EM PRODUÇÃO

## Status do Projeto (25/07/2025)

**Estado Atual:** Implementação de discovery automático UDP em desenvolvimento

### ⚠️ AVISOS IMPORTANTES

1. **NÃO USAR EM PRODUÇÃO** - Este código está em desenvolvimento ativo
2. **COMPANION MANUAL** - Nunca iniciar companion via terminal Claude Code (causa conflitos de processo)
3. **PERMISSÕES** - Adicionada ACCESS_WIFI_STATE mas ainda testando discovery
4. **REDE** - Sistema configurado para rede 192.168.1.x (deve ser adaptado para outras redes)

### 🔧 Implementações Recentes

#### Android App
- ✅ Permissão ACCESS_WIFI_STATE adicionada ao AndroidManifest
- ✅ Sistema de discovery UDP broadcast implementado (CompanionDiscovery.kt)
- ✅ Network Security Config permitindo cleartext para desenvolvimento
- ✅ IPs fallback atualizados para rede atual (192.168.1.7)

#### Desktop Companion  
- ✅ DiscoveryServer implementado com UDP broadcast
- ✅ OpenAI Realtime API funcionando com finishRequest pattern
- ✅ Servidor escutando em 0.0.0.0:3001 (aceita conexões de qualquer IP)
- ✅ Discovery server ativo na porta 3002 UDP

### 🐛 Problemas Conhecidos

1. Discovery automático ainda sendo testado após correção de permissões
2. Logs mostram tentativas de conexão em IPs incorretos (precisa validação)
3. M400 precisa estar conectado na mesma rede WiFi que o PC

### 📋 Próximos Passos

1. Validar funcionamento do discovery UDP após correções
2. Testar conexão automática em diferentes redes
3. Implementar fallback robusto caso discovery falhe
4. Documentar configuração de rede para diferentes ambientes

### 🚨 COMO USAR AGORA

**NUNCA execute `npm run dev` no terminal Claude Code!**

1. Abra terminal separado e execute: `cd companion-desktop && npm run dev`
2. Conecte M400 na mesma rede WiFi do PC
3. No M400: TAP TRACKPAD > "Conectar Companion"
4. Sistema deve descobrir automaticamente o companion na rede

### 📊 Estado Técnico

- **Pipeline M400↔Desktop↔OpenAI**: ✅ Funcionando
- **Audio Streaming**: ✅ Funcionando  
- **OpenAI Realtime API**: ✅ Funcionando
- **Discovery Automático**: ⚠️ Em teste após correções
- **HUD Display**: ✅ Funcionando

---
**Última atualização:** 25/07/2025 - Claude Code
**Branch:** m400_mvp
**Commit:** Em preparação - estado de discovery UDP implementado