package com.carnelia.vpn.core

import android.content.Context
import com.carnelia.vpn.utils.AppLogger
import com.carnelia.vpn.utils.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object AssetsManager {

    private const val GEO_SITE = "geosite.dat"
    private const val GEO_IP = "geoip.dat"

    suspend fun installGeoAssets(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            AppLogger.log("AssetsManager: Checking geo assets...")
            
            val filesDir = context.filesDir
            val siteFile = File(filesDir, GEO_SITE)
            val ipFile = File(filesDir, GEO_IP)
            
            // If files exist and pass size check (optional), we are good.
            // For now, overwrite if we "update" or if missing.
            
            // Check if assets exist in APK
            val assetManager = context.assets
            val assetsList = assetManager.list("") ?: emptyArray()
            
            var siteInstalled = false
            var ipInstalled = false
            
            if (assetsList.contains(GEO_SITE)) {
                copyAsset(context, GEO_SITE, siteFile)
                siteInstalled = true
            } else {
                AppLogger.log("AssetsManager: $GEO_SITE not found in assets. Using default rules.")
            }
            
            if (assetsList.contains(GEO_IP)) {
                copyAsset(context, GEO_IP, ipFile)
                ipInstalled = true
            } else {
                AppLogger.log("AssetsManager: $GEO_IP not found in assets. Using default rules.")
            }
            
            PrefsManager.setGeoAssetsInstalled(context, siteInstalled && ipInstalled)
            return@withContext siteInstalled || ipInstalled // Return true if at least one installed or updated
            
        } catch (e: Exception) {
            AppLogger.error("AssetsManager: Install failed", e)
            return@withContext false
        }
    }
    
    private fun copyAsset(context: Context, assetName: String, destFile: File) {
        context.assets.open(assetName).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        AppLogger.log("AssetsManager: Installed $assetName to ${destFile.absolutePath}")
    }
    
    fun getGeoSitePath(context: Context): String? {
        val file = File(context.filesDir, GEO_SITE)
        return if (file.exists()) file.absolutePath else null
    }

    fun getGeoIpPath(context: Context): String? {
        val file = File(context.filesDir, GEO_IP)
        return if (file.exists()) file.absolutePath else null
    }
}
