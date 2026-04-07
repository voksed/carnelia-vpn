package com.carnelia.vpn

import android.app.Application
import com.carnelia.vpn.service.NetworkMonitor
import com.carnelia.vpn.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CarheliaApplication : Application() {
    
    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(this) }
    
    companion object {
        lateinit var instance: CarheliaApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppLogger.log("CarheliaApplication: Started")
        
        networkMonitor.startMonitoring()
    }
}
