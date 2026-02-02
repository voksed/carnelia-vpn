package com.carnelia.vpn.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object NetworkUtils {

    suspend fun pingServer(host: String, port: Int): Long {
        return withContext(Dispatchers.IO) {
            try {
                val start = System.currentTimeMillis()
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), 2000) // 2s timeout
                socket.close()
                val end = System.currentTimeMillis()
                return@withContext (end - start)
            } catch (e: Exception) {
                return@withContext -1L // Error/Timeout
            }
        }
    }
}
