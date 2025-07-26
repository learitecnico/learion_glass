# LOGGING SYSTEM - Learion Glass Debug Framework

> **Baseado nos padrões do ElatoAI** para debugging de OpenAI Realtime API
> **Objetivo:** Debug completo da pipeline M400 → OpenAI com métricas detalhadas

---

## 📋 **ARQUITETURA DO SISTEMA DE LOGS**

### **Níveis de Log (baseado ElatoAI)**
```kotlin
enum class LogLevel(val emoji: String, val color: String) {
    DEBUG("🔍", "#7f5af0"),     // Technical details
    INFO("📝", "#2cb67d"),      // General information  
    WARN("⚠️", "#f39c12"),      // Warnings
    ERROR("❌", "#e74c3c"),     // Errors
    METRIC("📊", "#3498db")     // Performance metrics
}
```

### **Categorias de Logs**
```kotlin
enum class LogCategory {
    CONNECTION,    // WebSocket OpenAI events
    AUDIO,        // Audio pipeline M400
    AGENT,        // Agent switching & responses
    UI,           // HUD & ActionMenu events  
    PERFORMANCE,  // Latency & metrics
    ERROR         // Error handling & recovery
}
```

---

## 🔧 **IMPLEMENTAÇÃO: LogManager.kt**

```kotlin
package com.seudominio.app_smart_companion.debug

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Sistema de logs estruturado baseado no ElatoAI
 * - Logs em tempo real para debugging
 * - Armazenamento local para análise posterior
 * - Métricas de performance automáticas
 */
class LogManager private constructor(
    private val context: Context,
    private val enableFileLogging: Boolean = true
) {
    companion object {
        private const val TAG = "LearionGlass"
        private const val MAX_LOG_SIZE = 10 * 1024 * 1024 // 10MB
        private const val LOG_FILE_NAME = "learion_glass_debug.log"
        
        @Volatile
        private var INSTANCE: LogManager? = null
        
        fun getInstance(context: Context): LogManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LogManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileWriterScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logFile = File(context.filesDir, LOG_FILE_NAME)
    
    data class LogEntry(
        val timestamp: String,
        val level: LogLevel,
        val category: LogCategory,
        val message: String,
        val data: Map<String, Any>? = null,
        val exception: Throwable? = null
    )
    
    /**
     * Log structured event (ElatoAI style)
     */
    fun log(
        level: LogLevel,
        category: LogCategory, 
        message: String,
        data: Map<String, Any>? = null,
        exception: Throwable? = null
    ) {
        val timestamp = dateFormatter.format(Date())
        val entry = LogEntry(timestamp, level, category, message, data, exception)
        
        // Android logcat (immediate)
        val logMessage = formatLogMessage(entry)
        when (level) {
            LogLevel.DEBUG -> Log.d(TAG, logMessage)
            LogLevel.INFO -> Log.i(TAG, logMessage)  
            LogLevel.WARN -> Log.w(TAG, logMessage)
            LogLevel.ERROR -> Log.e(TAG, logMessage, exception)
            LogLevel.METRIC -> Log.i(TAG, logMessage)
        }
        
        // File logging (async)
        if (enableFileLogging) {
            logQueue.offer(entry)
            processLogQueue()
        }
    }
    
    /**
     * OpenAI Connection Events (Critical)
     */
    fun logOpenAIEvent(eventType: String, direction: String, data: Map<String, Any>? = null) {
        val message = "[$direction] $eventType"
        log(LogLevel.INFO, LogCategory.CONNECTION, message, data)
    }
    
    /**
     * Audio Pipeline Events
     */
    fun logAudio(event: String, metrics: Map<String, Any>? = null) {
        log(LogLevel.DEBUG, LogCategory.AUDIO, event, metrics)
    }
    
    /**
     * Performance Metrics (ElatoAI style)
     */
    fun logMetric(metricName: String, value: Any, unit: String? = null) {
        val data = mutableMapOf<String, Any>("value" to value)
        unit?.let { data["unit"] = it }
        
        log(LogLevel.METRIC, LogCategory.PERFORMANCE, "📊 $metricName", data)
    }
    
    /**
     * Latency measurement (Critical for <400ms target)
     */
    fun logLatency(operation: String, startTime: Long, endTime: Long = System.currentTimeMillis()) {
        val latencyMs = endTime - startTime
        val status = if (latencyMs < 400) "✅" else "⚠️"
        
        logMetric("${operation}_latency", latencyMs, "ms")
        log(LogLevel.METRIC, LogCategory.PERFORMANCE, 
            "$status $operation: ${latencyMs}ms", 
            mapOf("operation" to operation, "latency_ms" to latencyMs)
        )
    }
    
    /**
     * Agent switching events
     */
    fun logAgentChange(fromAgent: String, toAgent: String, success: Boolean) {
        val status = if (success) "✅" else "❌"
        log(LogLevel.INFO, LogCategory.AGENT, 
            "$status Agent: $fromAgent → $toAgent",
            mapOf("from" to fromAgent, "to" to toAgent, "success" to success)
        )
    }
    
    /**
     * Error with recovery context
     */
    fun logError(message: String, exception: Throwable? = null, recoveryAction: String? = null) {
        val data = recoveryAction?.let { mapOf("recovery_action" to it) }
        log(LogLevel.ERROR, LogCategory.ERROR, message, data, exception)
    }
    
    private fun formatLogMessage(entry: LogEntry): String {
        val builder = StringBuilder()
        builder.append("${entry.level.emoji} [${entry.category}] ${entry.message}")
        
        entry.data?.let { data ->
            val dataStr = data.entries.joinToString(", ") { "${it.key}=${it.value}" }
            builder.append(" | $dataStr")
        }
        
        return builder.toString()
    }
    
    private fun processLogQueue() {
        fileWriterScope.launch {
            val entries = mutableListOf<LogEntry>()
            
            // Drain queue  
            while (logQueue.isNotEmpty()) {
                logQueue.poll()?.let { entries.add(it) }
            }
            
            if (entries.isNotEmpty()) {
                writeToFile(entries)
            }
        }
    }
    
    private suspend fun writeToFile(entries: List<LogEntry>) {
        try {
            // Rotate log if too large
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
                val backupFile = File(context.filesDir, "${LOG_FILE_NAME}.old")
                logFile.renameTo(backupFile)
            }
            
            logFile.appendText(entries.joinToString("\n") { formatFileLogEntry(it) } + "\n")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log file", e)
        }
    }
    
    private fun formatFileLogEntry(entry: LogEntry): String {
        val dataJson = entry.data?.let { data ->
            data.entries.joinToString(", ", " {", "}") { 
                "\"${it.key}\": \"${it.value}\"" 
            }
        } ?: ""
        
        val exceptionStr = entry.exception?.let { " | ERROR: ${it.message}" } ?: ""
        
        return "${entry.timestamp} ${entry.level.name} [${entry.category}] ${entry.message}$dataJson$exceptionStr"
    }
    
    /**
     * Export logs for debugging
     */
    fun exportLogs(): String? {
        return try {
            if (logFile.exists()) {
                logFile.readText()
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export logs", e)
            null
        }
    }
    
    /**
     * Clear logs
     */
    fun clearLogs() {
        fileWriterScope.launch {
            try {
                logFile.delete()
                Log.i(TAG, "🧹 Logs cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear logs", e)
            }
        }
    }
    
    fun cleanup() {
        fileWriterScope.cancel()
    }
}
```

---

## 📊 **IMPLEMENTAÇÃO: DebugMetrics.kt**

```kotlin
package com.seudominio.app_smart_companion.debug

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger

/**
 * Performance metrics collector (ElatoAI style)
 * Tracks latency, connection stability, audio quality
 */
class DebugMetrics private constructor() {
    companion object {
        @Volatile
        private var INSTANCE: DebugMetrics? = null
        
        fun getInstance(): DebugMetrics {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DebugMetrics().also { INSTANCE = it }
            }
        }
    }
    
    private val logger = LogManager.getInstance(context)
    
    // Connection metrics
    private val connectionAttempts = AtomicInteger(0)  
    private val connectionFailures = AtomicInteger(0)
    private val reconnectionCount = AtomicInteger(0)
    private var lastConnectionTime = AtomicLong(0)
    
    // Audio metrics
    private val audioChunksSent = AtomicLong(0)
    private val audioChunksReceived = AtomicLong(0)
    private val audioBytesSent = AtomicLong(0)
    private val audioBytesReceived = AtomicLong(0)
    
    // Latency tracking
    private val latencyMeasurements = mutableListOf<Long>()
    private var sessionStartTime = 0L
    
    // Agent metrics
    private val agentSwitches = AtomicInteger(0)
    private val agentSwitchFailures = AtomicInteger(0)
    
    /**
     * Connection events
     */
    fun recordConnectionAttempt() {
        connectionAttempts.incrementAndGet()
        logger.logMetric("connection_attempts", connectionAttempts.get())
    }
    
    fun recordConnectionSuccess() {
        lastConnectionTime.set(System.currentTimeMillis())
        logger.logMetric("connection_success_rate", getConnectionSuccessRate())
    }
    
    fun recordConnectionFailure(reason: String) {
        connectionFailures.incrementAndGet()
        logger.logError("Connection failed: $reason", recoveryAction = "retry_with_backoff")
    }
    
    fun recordReconnection() {
        reconnectionCount.incrementAndGet()
        logger.logMetric("reconnection_count", reconnectionCount.get())
    }
    
    /**
     * Audio pipeline metrics
     */
    fun recordAudioSent(bytes: Int) {
        audioChunksSent.incrementAndGet()
        audioBytesSent.addAndGet(bytes.toLong())
        
        logger.logAudio("Audio sent", mapOf(
            "chunk_size" to bytes,
            "total_chunks" to audioChunksSent.get(),
            "total_bytes" to audioBytesSent.get()
        ))
    }
    
    fun recordAudioReceived(bytes: Int) {
        audioChunksReceived.incrementAndGet()
        audioBytesReceived.addAndGet(bytes.toLong())
        
        logger.logAudio("Audio received", mapOf(
            "chunk_size" to bytes,
            "total_chunks" to audioChunksReceived.get(), 
            "total_bytes" to audioBytesReceived.get()
        ))
    }
    
    /**
     * Latency measurement (Critical <400ms target)
     */
    fun recordLatency(operation: String, latencyMs: Long) {
        synchronized(latencyMeasurements) {
            latencyMeasurements.add(latencyMs)
            
            // Keep only last 100 measurements
            if (latencyMeasurements.size > 100) {
                latencyMeasurements.removeAt(0)
            }
        }
        
        logger.logLatency(operation, System.currentTimeMillis() - latencyMs)
    }
    
    /**
     * Agent switching metrics
     */
    fun recordAgentSwitch(success: Boolean) {
        agentSwitches.incrementAndGet()
        if (!success) {
            agentSwitchFailures.incrementAndGet()
        }
        
        logger.logMetric("agent_switch_success_rate", getAgentSwitchSuccessRate())
    }
    
    /**
     * Session metrics
     */
    fun startSession() {
        sessionStartTime = System.currentTimeMillis()
        logger.log(LogLevel.INFO, LogCategory.PERFORMANCE, "📊 Session started")
    }
    
    fun endSession() {
        val sessionDuration = System.currentTimeMillis() - sessionStartTime
        logger.logMetric("session_duration", sessionDuration, "ms")
        
        // Log session summary
        logSessionSummary(sessionDuration)
    }
    
    /**
     * Get performance summary
     */
    fun getPerformanceSummary(): Map<String, Any> {
        val avgLatency = synchronized(latencyMeasurements) {
            if (latencyMeasurements.isNotEmpty()) {
                latencyMeasurements.average()
            } else 0.0
        }
        
        return mapOf(
            "connection_success_rate" to getConnectionSuccessRate(),
            "avg_latency_ms" to avgLatency,
            "max_latency_ms" to (latencyMeasurements.maxOrNull() ?: 0),
            "total_audio_sent_kb" to (audioBytesSent.get() / 1024),
            "total_audio_received_kb" to (audioBytesReceived.get() / 1024),
            "agent_switch_success_rate" to getAgentSwitchSuccessRate(),
            "reconnection_count" to reconnectionCount.get()
        )
    }
    
    private fun getConnectionSuccessRate(): Double {
        val attempts = connectionAttempts.get()
        val failures = connectionFailures.get()
        return if (attempts > 0) {
            ((attempts - failures).toDouble() / attempts) * 100
        } else 0.0
    }
    
    private fun getAgentSwitchSuccessRate(): Double {
        val switches = agentSwitches.get()
        val failures = agentSwitchFailures.get()
        return if (switches > 0) {
            ((switches - failures).toDouble() / switches) * 100
        } else 0.0
    }
    
    private fun logSessionSummary(durationMs: Long) {
        val summary = getPerformanceSummary()
        logger.log(LogLevel.INFO, LogCategory.PERFORMANCE, 
            "📊 Session Summary (${durationMs}ms)", summary)
    }
}
```

---

## 🎯 **INTEGRATION POINTS**

### **StandaloneService Integration**
```kotlin
// Em StandaloneService.kt - adicionar:
private val logger = LogManager.getInstance(this)
private val metrics = DebugMetrics.getInstance()

// Connection events
logger.logOpenAIEvent("connection_established", "client", mapOf("agent" to currentAgent))
metrics.recordConnectionSuccess()

// Audio events  
logger.logAudio("audio_capture_started", mapOf("sample_rate" to 24000, "format" to "PCM16"))
metrics.recordAudioSent(audioData.size)

// Latency measurement
val startTime = System.currentTimeMillis()
// ... operation ...
logger.logLatency("audio_to_response", startTime)
```

### **MainActivity Integration**
```kotlin
// Em MainActivity.kt - adicionar:
private val logger = LogManager.getInstance(this)

// Agent switching
logger.logAgentChange(oldAgent, newAgent, success)
metrics.recordAgentSwitch(success)

// UI events
logger.log(LogLevel.INFO, LogCategory.UI, "Voice command executed", mapOf("command" to "1"))
```

---

## 📱 **LOG EXPORT & DEBUG**

### **ADB Commands**
```bash
# Export logs from device
adb exec-out run-as com.seudominio.app_smart_companion cat files/learion_glass_debug.log > learion_debug.log

# Monitor real-time
adb logcat -s LearionGlass:*

# Performance specific  
adb logcat -s LearionGlass:* | grep "📊\|⚡\|📈"

# Error tracking
adb logcat -s LearionGlass:* | grep "❌\|⚠️"
```

### **In-App Debug Menu**
```kotlin
// Em MainActivity.kt - settings menu
private fun openSettings() {
    val logger = LogManager.getInstance(this)
    val summary = DebugMetrics.getInstance().getPerformanceSummary()
    
    val debugInfo = """
    📊 Performance Summary:
    • Avg Latency: ${summary["avg_latency_ms"]}ms
    • Connection Rate: ${summary["connection_success_rate"]}%
    • Audio Sent: ${summary["total_audio_sent_kb"]}KB
    • Reconnections: ${summary["reconnection_count"]}
    """.trimIndent()
    
    hudDisplayManager.showStatusMessage(debugInfo, 10000L)
}
```

---

## 🎯 **USO DURANTE TESTES**

### **Pre-Test Setup**
```kotlin
// Clear previous logs
LogManager.getInstance(context).clearLogs()
DebugMetrics.getInstance().startSession()
```

### **During Tests**
- Todos os eventos importantes são logados automaticamente
- Latency measurements em tempo real
- Connection stability tracking
- Audio pipeline monitoring

### **Post-Test Analysis**
```kotlin
// Get performance summary
val summary = DebugMetrics.getInstance().getPerformanceSummary()
val logs = LogManager.getInstance(context).exportLogs()

// Export via ADB
adb exec-out run-as com.seudominio.app_smart_companion cat files/learion_glass_debug.log
```

---

**Este sistema de logs nos dará visibilidade completa para debugging durante os testes no M400!**