package com.seudominio.app_smart_companion.config

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.net.InetAddress
import java.net.NetworkInterface
import kotlinx.coroutines.*
import com.seudominio.app_smart_companion.discovery.CompanionDiscovery

object AppConfig {
    private const val TAG = "SmartCompanion"
    
    // Companion Desktop server configuration
    const val COMPANION_PORT = 3001
    
    // Dynamic companion host discovery
    private var _companionHost: String? = null
    
    val COMPANION_HOST: String
        get() = _companionHost ?: "192.168.1.7" // Fallback IP - YOUR PC's IP
    
    val SIGNALING_URL: String
        get() = "ws://$COMPANION_HOST:$COMPANION_PORT/signaling"
    
    /**
     * Discover companion desktop IP on the same network using UDP broadcast
     */
    suspend fun discoverCompanionHost(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting companion discovery via UDP broadcast")
            
            // Use broadcast discovery
            val discovery = CompanionDiscovery(context)
            val companion = discovery.discoverCompanion()
            
            if (companion != null) {
                Log.i(TAG, "Companion discovered via broadcast: ${companion.name} at ${companion.host}:${companion.port}")
                _companionHost = companion.host
                return@withContext companion.host
            }
            
            // Fallback: Try HTTP test on fallback IP
            val fallbackIP = "192.168.1.7"
            Log.d(TAG, "Broadcast discovery failed, trying fallback IP: $fallbackIP")
            if (testCompanionConnection(fallbackIP)) {
                Log.i(TAG, "Companion found at fallback IP: $fallbackIP")
                _companionHost = fallbackIP
                return@withContext fallbackIP
            }
            
            Log.w(TAG, "Companion desktop not found via broadcast or fallback")
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering companion host", e)
            return@withContext null
        }
    }
    
    /**
     * Get the local IP address of this device
     */
    private fun getLocalIPAddress(context: Context): String? {
        try {
            // Try WiFi manager first
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipAddress = wifiInfo.ipAddress
            
            if (ipAddress != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    (ipAddress and 0xff),
                    (ipAddress shr 8 and 0xff),
                    (ipAddress shr 16 and 0xff),
                    (ipAddress shr 24 and 0xff)
                )
            }
            
            // Fallback: Network interfaces
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in interfaces) {
                for (address in networkInterface.inetAddresses) {
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        val ip = address.hostAddress
                        if (ip?.startsWith("192.168.") == true || 
                            ip?.startsWith("10.") == true || 
                            ip?.startsWith("172.") == true) {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP", e)
        }
        
        return null
    }
    
    /**
     * Test if companion desktop is running on the given IP
     */
    private suspend fun testCompanionConnection(ip: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Test HTTP connection to port 3001 (more reliable than ping)
            val url = java.net.URL("http://$ip:$COMPANION_PORT/health")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 3000 // 3 second timeout
            connection.readTimeout = 3000
            connection.requestMethod = "GET"
            
            val responseCode = connection.responseCode
            connection.disconnect()
            
            if (responseCode == 200) {
                Log.d(TAG, "Companion desktop confirmed at $ip (HTTP 200)")
                return@withContext true
            } else {
                Log.d(TAG, "IP $ip responded with HTTP $responseCode")
                return@withContext false
            }
            
        } catch (e: Exception) {
            Log.d(TAG, "IP $ip not reachable: ${e.message}")
            return@withContext false
        }
    }
    
    // WebRTC Configuration
    val ICE_SERVERS = listOf(
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302"
    )
    
    // Audio Configuration
    const val AUDIO_SAMPLE_RATE = 16000
    const val AUDIO_CHANNELS = 1 // Mono
    const val AUDIO_ENCODING_BIT_RATE = 32000
    
    // Camera Configuration
    const val MAX_IMAGE_SIZE_KB = 200
    const val JPEG_QUALITY = 80
    
    // Network timeouts
    const val CONNECTION_TIMEOUT_MS = 10000L
    const val RECONNECT_DELAY_MS = 5000L
    const val MAX_RECONNECT_ATTEMPTS = 5
}