package com.carnelia.vpn.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.IOException

class VpnService : VpnService() {
    private var mThread: Thread? = null
    private var mInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mThread = Thread { setupVPN() }
        mThread?.start()
        return START_STICKY
    }

    private fun setupVPN() {
        try {
            val builder = Builder()
            builder.addAddress("10.8.0.6", 24)
            builder.addRoute("0.0.0.0", 0)
            builder.addDnsServer("8.8.8.8")
            builder.addDnsServer("8.8.4.4")
            builder.setSession("Carnelia VPN")
            builder.setMtu(1500)

            mInterface = builder.establish()

            if (mInterface != null) {
                // VPN tunnel established
                Thread.sleep(Long.MAX_VALUE)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            mInterface?.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mThread?.interrupt()
        mInterface?.close()
    }
}
