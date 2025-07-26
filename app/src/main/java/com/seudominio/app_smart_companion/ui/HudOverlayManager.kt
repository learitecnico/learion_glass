package com.seudominio.app_smart_companion.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview

/**
 * HUD Overlay Manager para Vuzix M400
 * Gerencia exibição de informações no heads-up display
 */
class HudOverlayManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "HudOverlayManager"
    }
    
    // Estado do HUD
    var isVisible by mutableStateOf(false)
        private set
    
    var currentText by mutableStateOf("")
        private set
        
    var statusText by mutableStateOf("Aguardando...")
        private set
    
    var connectionStatus by mutableStateOf("Desconectado")
        private set
    
    /**
     * Exibe texto no HUD
     */
    fun showText(text: String) {
        currentText = text
        isVisible = true
        Log.d(TAG, "HUD text updated: $text")
    }
    
    /**
     * Atualiza status da conexão
     */
    fun updateConnectionStatus(status: String) {
        connectionStatus = status
        Log.d(TAG, "Connection status updated: $status")
    }
    
    /**
     * Atualiza status do sistema
     */
    fun updateStatus(status: String) {
        statusText = status
        Log.d(TAG, "System status updated: $status")
    }
    
    /**
     * Oculta o HUD
     */
    fun hide() {
        isVisible = false
        currentText = ""
        Log.d(TAG, "HUD hidden")
    }
    
    /**
     * Limpa o texto mas mantém HUD visível
     */
    fun clearText() {
        currentText = ""
        Log.d(TAG, "HUD text cleared")
    }
}

/**
 * Composable para o HUD Overlay
 */
@Composable
fun HudOverlay(
    manager: HudOverlayManager,
    modifier: Modifier = Modifier
) {
    if (manager.isVisible) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            // HUD Content Card
            Card(
                modifier = Modifier
                    .width(300.dp)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.8f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Status da conexão
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status:",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                        
                        Text(
                            text = manager.connectionStatus,
                            color = when (manager.connectionStatus) {
                                "Conectado" -> Color.Green
                                "Conectando..." -> Color.Yellow
                                else -> Color.Red
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Separador
                    if (manager.currentText.isNotEmpty()) {
                        Divider(color = Color.Gray.copy(alpha = 0.5f))
                    }
                    
                    // Texto principal da resposta
                    if (manager.currentText.isNotEmpty()) {
                        Text(
                            text = manager.currentText,
                            color = Color.White,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    // Status do sistema
                    if (manager.statusText.isNotEmpty() && manager.statusText != "Aguardando...") {
                        Divider(color = Color.Gray.copy(alpha = 0.5f))
                        
                        Text(
                            text = manager.statusText,
                            color = Color.Cyan,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Preview HUD para desenvolvimento
 */
@Preview
@Composable
fun HudOverlayPreview() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val manager = remember { 
        HudOverlayManager(context = context).apply {
            showText("Esta é uma resposta de exemplo do assistente AI. O texto é exibido de forma clara e legível no HUD.")
            updateConnectionStatus("Conectado")
            updateStatus("Áudio capturado • Processando...")
        }
    }
    
    HudOverlay(manager = manager)
}