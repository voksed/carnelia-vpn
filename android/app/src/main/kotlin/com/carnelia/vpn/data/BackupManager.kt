package com.carnelia.vpn.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.carnelia.vpn.core.VpnServerConfig
import com.carnelia.vpn.utils.AppLogger
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Base64

/**
 * BackupManager — v2.4.0
 * Export/import all saved servers as a portable Base64-wrapped JSON backup.
 * No external dependencies — works fully offline.
 */
object BackupManager {

    private val gson = Gson()
    private const val PREFIX = "CARNELIA_BACKUP_V1:"

    /**
     * Exports all servers to a Base64-encoded backup string and copies it to the clipboard.
     * Returns the backup string, or null if there is nothing to export.
     */
    fun exportToClipboard(context: Context): String? {
        val servers = ServerRepository(context).getServers()
        if (servers.isEmpty()) return null

        val json = gson.toJson(servers)
        val encoded = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
        val backupStr = "$PREFIX$encoded"

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Carnelia VPN Backup", backupStr))
        AppLogger.log("BackupManager: Exported ${servers.size} servers to clipboard")
        return backupStr
    }

    /**
     * Imports servers from a backup string obtained from the clipboard.
     * Returns the number of servers imported, or -1 on error.
     */
    fun importFromClipboard(context: Context): Int {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val rawText = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            ?: return -1
        return importFromString(context, rawText.trim())
    }

    /**
     * Imports servers from an arbitrary string (backup format or raw JSON).
     * Returns the number of servers imported, or -1 on error.
     */
    fun importFromString(context: Context, input: String): Int {
        return try {
            val jsonStr = when {
                input.startsWith(PREFIX) -> {
                    val decoded = Base64.getDecoder().decode(input.removePrefix(PREFIX))
                    String(decoded, Charsets.UTF_8)
                }
                // Fallback: try raw JSON array
                input.trimStart().startsWith("[") -> input
                else -> return -1
            }
            val type = object : TypeToken<List<VpnServerConfig>>() {}.type
            val servers: List<VpnServerConfig> = gson.fromJson(jsonStr, type) ?: return -1
            val repo = ServerRepository(context)
            servers.forEach { repo.addServer(it) }
            AppLogger.log("BackupManager: Imported ${servers.size} servers")
            servers.size
        } catch (e: Exception) {
            AppLogger.error("BackupManager: Import failed", e)
            -1
        }
    }
}
