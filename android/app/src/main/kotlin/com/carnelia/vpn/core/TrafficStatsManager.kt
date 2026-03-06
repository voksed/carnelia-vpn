package com.carnelia.vpn.core

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.Date

data class TrafficSession(
    val timestamp: Long,
    val durationSeconds: Long,
    val bytesReceived: Long,
    val bytesSent: Long
)

object TrafficStatsManager {
    private const val FILE_NAME = "traffic_history.json"
    private val gson = Gson()

    fun saveSession(context: Context, session: TrafficSession) {
        val history = getHistory(context).toMutableList()
        // Add to top
        history.add(0, session)
        // Keep last 100 sessions max
        if (history.size > 100) {
            history.removeAt(history.lastIndex)
        }
        saveList(context, history)
    }

    fun getHistory(context: Context): List<TrafficSession> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()

        return try {
            val json = file.readText()
            val type = object : TypeToken<List<TrafficSession>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearHistory(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) {
            file.delete()
        }
    }

    private fun saveList(context: Context, list: List<TrafficSession>) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            val json = gson.toJson(list)
            file.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
