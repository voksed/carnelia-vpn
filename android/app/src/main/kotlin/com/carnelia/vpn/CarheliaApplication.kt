package com.carnelia.vpn

import android.app.Application
import com.carnelia.vpn.security.SecurityChecker
import com.carnelia.vpn.service.NetworkMonitor
import com.carnelia.vpn.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CarheliaApplication : Application() {
    
    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(this) }

    /** Угрозы, обнаруженные при старте. MainActivity показывает диалог, если список не пуст. */
    var detectedThreats: List<SecurityChecker.Threat> = emptyList()
        private set
    
    companion object {
        lateinit var instance: CarheliaApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppLogger.log("CarheliaApplication: Started")

        // Проверка целостности устройства (root, Frida, Xposed)
        CoroutineScope(Dispatchers.Default).launch {
            detectedThreats = SecurityChecker.runChecks(this@CarheliaApplication, includePackageCheck = true)
            if (detectedThreats.isNotEmpty()) {
                AppLogger.error("SecurityChecker: обнаружены угрозы: ${detectedThreats.map { it.title }}")
            }
        }
        
        networkMonitor.startMonitoring()
    }
}
