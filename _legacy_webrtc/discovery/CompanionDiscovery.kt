package com.seudominio.app_smart_companion.discovery

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

data class CompanionInfo(
    val host: String,
    val port: Int,
    val name: String,
    val timestamp: Long
)

class CompanionDiscovery(private val context: Context) {
    companion object {
        private const val TAG = "SmartCompanion"
        private const val DISCOVERY_PORT = 3002
        private const val DISCOVERY_MESSAGE = "SMART_COMPANION_DISCOVER"
        private const val DISCOVERY_TIMEOUT = 5000 // 5 seconds
        private const val LISTEN_TIMEOUT = 10000 // 10 seconds total
    }
    
    private var discoverySocket: DatagramSocket? = null
    private var isDiscovering = false
    
    /**
     * Discover companion desktop on the network
     * Returns the first companion found or null if none found
     */
    suspend fun discoverCompanion(): CompanionInfo? = withContext(Dispatchers.IO) {
        if (isDiscovering) {
            Log.w(TAG, "Discovery already in progress")
            return@withContext null
        }
        
        isDiscovering = true
        var companion: CompanionInfo? = null
        
        try {
            // Create socket for discovery
            discoverySocket = DatagramSocket(null).apply {
                reuseAddress = true
                soTimeout = 500 // Short timeout for checking cancellation
                bind(InetSocketAddress(DISCOVERY_PORT))
            }
            
            Log.d(TAG, "Discovery socket bound to port $DISCOVERY_PORT")
            
            // Send discovery broadcast
            sendDiscoveryBroadcast()
            
            // Listen for responses
            companion = listenForCompanion()
            
        } catch (e: Exception) {
            Log.e(TAG, "Discovery failed", e)
        } finally {
            discoverySocket?.close()
            discoverySocket = null
            isDiscovering = false
        }
        
        return@withContext companion
    }
    
    /**
     * Send discovery broadcast to find companions
     */
    private fun sendDiscoveryBroadcast() {
        try {
            val message = DISCOVERY_MESSAGE.toByteArray()
            val broadcastAddress = getBroadcastAddress()
            
            if (broadcastAddress != null) {
                val packet = DatagramPacket(
                    message,
                    message.size,
                    broadcastAddress,
                    DISCOVERY_PORT
                )
                
                discoverySocket?.send(packet)
                Log.d(TAG, "Discovery broadcast sent to ${broadcastAddress.hostAddress}")
            } else {
                Log.w(TAG, "Could not determine broadcast address")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send discovery broadcast", e)
        }
    }
    
    /**
     * Listen for companion responses
     */
    private fun listenForCompanion(): CompanionInfo? {
        val buffer = ByteArray(1024)
        val packet = DatagramPacket(buffer, buffer.size)
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < LISTEN_TIMEOUT) {
            try {
                discoverySocket?.receive(packet)
                
                val response = String(packet.data, 0, packet.length)
                Log.d(TAG, "Received discovery response: $response")
                
                try {
                    val json = JSONObject(response)
                    
                    // Check if it's a companion response or announcement
                    val type = json.optString("type")
                    if (type == "SMART_COMPANION_RESPONSE" || type == "SMART_COMPANION_ANNOUNCE") {
                        val companion = CompanionInfo(
                            host = json.getString("host"),
                            port = json.getInt("port"),
                            name = json.getString("name"),
                            timestamp = json.getLong("timestamp")
                        )
                        
                        Log.i(TAG, "Companion discovered: ${companion.name} at ${companion.host}:${companion.port}")
                        return companion
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse discovery response", e)
                }
                
            } catch (e: SocketTimeoutException) {
                // Timeout is expected, continue listening
            } catch (e: Exception) {
                Log.e(TAG, "Error receiving discovery response", e)
            }
        }
        
        Log.w(TAG, "No companion found after ${LISTEN_TIMEOUT}ms")
        return null
    }
    
    /**
     * Get broadcast address for current WiFi network
     */
    private fun getBroadcastAddress(): InetAddress? {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcpInfo = wifiManager.dhcpInfo
            
            if (dhcpInfo != null) {
                val broadcast = (dhcpInfo.ipAddress and dhcpInfo.netmask) or dhcpInfo.netmask.inv()
                val quads = ByteArray(4)
                for (i in 0..3) {
                    quads[i] = (broadcast shr (i * 8) and 0xFF).toByte()
                }
                
                return InetAddress.getByAddress(quads)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get broadcast address", e)
        }
        
        // Fallback to common broadcast addresses
        return try {
            InetAddress.getByName("255.255.255.255")
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Stop discovery if in progress
     */
    fun stopDiscovery() {
        isDiscovering = false
        discoverySocket?.close()
    }
}