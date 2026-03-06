package com.carnelia.vpn.core

import android.content.Context
import com.carnelia.vpn.utils.AppLogger
import com.carnelia.vpn.utils.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

object TorManager {
    
    private var process: Process? = null
    private const val TOR_BINARY = "tor"
    private const val OBFS4_BINARY = "obfs4proxy"
    
    private val _torStatus = MutableStateFlow("Stopped")
    val torStatus: StateFlow<String> = _torStatus

    private val isRunning = AtomicBoolean(false)

    // Example bridges. In production, fetch these dynamically or use a robust bridge distribution mechanism like Moat.
    private val DEFAULT_BRIDGES = listOf(
        "obfs4 192.95.36.142:443 CDF2E852BF539B09D74E1F2570890E1E8E67DA13 cert=qUVQ0/DQpIDDfX5vX8dCqC7Yj8D3w7gE8cPp8z8X4qH1Z9w8kE1Z9w8kE1Z9w8kE1Z9w8k iat-mode=0",
        "obfs4 85.17.30.79:443 FC2D98F7E1D8F3C5E2B9F4E7A1C3D5E9F6B8D2A4 cert=vYIV7J7J7J7J7J7J7J7J7J7J7J7J7J7J7J7J7J7J7J7J7J7J7J7J7J7J7J7J7J7J iat-mode=0"
    )

    private suspend fun installBinary(context: Context, libName: String, destName: String): File? = withContext(Dispatchers.IO) {
        try {
            val nativePath = context.applicationInfo.nativeLibraryDir
            val destFile = File(context.filesDir, destName)
            val sourceFile = File(nativePath, "lib" + libName + ".so")
            
            if (sourceFile.exists()) {
                 sourceFile.copyTo(destFile, overwrite = true)
                 destFile.setExecutable(true, true)
                 return@withContext destFile
            }
            return@withContext null
        } catch (e: Exception) {
            AppLogger.error("Failed to install " + destName, e)
            null
        }
    }

    suspend fun startTor(context: Context, useBridges: Boolean = true) = withContext(Dispatchers.IO) {
        if (isRunning.get()) {
            AppLogger.log("Tor/Orbot is already active")
            return@withContext
        }

        // Check if user prefers External Orbot
        if (!PrefsManager.isUseInternalTor(context)) {
             AppLogger.log("TorManager: External Orbot mode active. Skipping internal binary.")
             _torStatus.value = "Connected (Orbot)"
             isRunning.set(true)
             return@withContext
        }

        try {
            _torStatus.value = "Installing..."
            val torBin = installBinary(context, "tor", TOR_BINARY)
            if (torBin == null || !torBin.exists()) {
                _torStatus.value = "Error: Tor binary missing"
                AppLogger.error("Tor binary not found!")
                return@withContext
            }

            // Attempt to install Obfs4proxy (check different casing)
            var obfs4Bin = installBinary(context, "Obfs4proxy", OBFS4_BINARY)
            if (obfs4Bin == null) {
                obfs4Bin = installBinary(context, "obfs4proxy", OBFS4_BINARY)
            }
            
            val configDir = File(context.filesDir, "tor_data")
            if (!configDir.exists()) configDir.mkdirs()
            
            val torrc = File(configDir, "torrc")
            val sb = StringBuilder()
            
            // Usage of settings from PrefsManager
            val socksPort = PrefsManager.getTorSocksPort(context)
            val httpPort = PrefsManager.getTorHttpPort(context)
            val enableBridges = PrefsManager.isTorUseBridges(context)
            
            sb.append("DataDirectory ").append(configDir.absolutePath).append("\n")
            sb.append("SocksPort ").append(socksPort).append("\n")
            sb.append("HTTPTunnelPort ").append(httpPort).append("\n") // Enable HTTP Proxy
            sb.append("ControlPort 9051\n")
            sb.append("CookieAuthentication 1\n")
            sb.append("KeepAliveIsolateSOCKSAuth 1\n")
            sb.append("Log notice stdout\n")
            
            if (obfs4Bin != null && obfs4Bin.exists()) {
                sb.append("ClientTransportPlugin obfs4 exec ").append(obfs4Bin.absolutePath).append("\n")
                if (enableBridges) {
                    sb.append("UseBridges 1\n")
                    // Custom bridges first
                    val customBridges = PrefsManager.getCustomTorBridges(context)
                    if (customBridges.isNotBlank()) {
                         customBridges.lines().filter { it.isNotBlank() }.forEach { 
                             sb.append("Bridge ").append(it.trim()).append("\n") 
                         }
                    } else {
                         // Default fallback
                         DEFAULT_BRIDGES.forEach { bridge ->
                             sb.append("Bridge ").append(bridge).append("\n")
                         }
                    }
                }
            } else {
                 if (enableBridges) {
                     AppLogger.error("Cannot use bridges: obfs4proxy binary not found.")
                 }
            }

            torrc.writeText(sb.toString())
            
            val pb = ProcessBuilder(
                torBin.absolutePath,
                "-f", torrc.absolutePath
            )
            pb.redirectErrorStream(true)
            
            AppLogger.log("Starting Tor process...")
            _torStatus.value = "Starting..."
            process = pb.start()
            isRunning.set(true)
            
            // Monitor output
            val stream: InputStream = process!!.inputStream
            val reader = BufferedReader(InputStreamReader(stream))
            
            Thread {
                try {
                    var line: String?
                    while (isRunning.get()) {
                        line = reader.readLine()
                        if (line == null) break
                        
                        if (line.contains("Bootstrapped")) {
                             val percentRaw = line.substringAfter("Bootstrapped ").substringBefore("%")
                             val percent = percentRaw.trim()
                             _torStatus.value = "Booting: " + percent + "%"
                             if (percent == "100") {
                                 _torStatus.value = "Connected"
                             }
                        }
                        AppLogger.log("[TOR] " + line)
                    }
                } catch (e: Exception) {
                    if (isRunning.get()) AppLogger.error("Tor log reader failed", e)
                }
            }.start()
            
        } catch (e: Exception) {
            _torStatus.value = "Error: " + e.message
            AppLogger.error("Failed to start Tor", e)
            isRunning.set(false)
        }
    }
    
    fun stopTor() {
        if (!isRunning.get()) return
        
        try {
            process?.destroy()
            process = null
        } catch (e: Exception) {
            AppLogger.error("Error stopping Tor", e)
        } finally {
            isRunning.set(false)
            _torStatus.value = "Stopped"
            AppLogger.log("Tor process stopped")
        }
    }
    
    fun isConnected(): Boolean {
        val status = _torStatus.value
        return status == "Connected" || status.contains("Orbot")
    }
}
