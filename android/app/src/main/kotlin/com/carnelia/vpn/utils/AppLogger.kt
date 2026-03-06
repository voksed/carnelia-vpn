package com.carnelia.vpn.utils

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel { INFO, ERROR, DEBUG }

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val message: String
)

object AppLogger {
    private val _logs = mutableStateListOf<LogEntry>()
    val logs: List<LogEntry> get() = _logs

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun log(message: String) {
        addEntry(LogLevel.INFO, message)
        android.util.Log.i("CarneliaDebug", message)
        System.out.println("CarneliaDebug: " + message)
    }

    fun debug(message: String) {
        addEntry(LogLevel.DEBUG, message)
        android.util.Log.d("CarneliaDebug", message)
        System.out.println("CarneliaDebug: " + message)
    }

    fun error(message: String, throwable: Throwable? = null) {
        addEntry(LogLevel.ERROR, message)
        android.util.Log.e("CarneliaDebug", message, throwable)
        System.err.println("CarneliaDebug: " + message)
        throwable?.printStackTrace()
    }



    private fun addEntry(level: LogLevel, message: String) {
        if (_logs.size > 1000) {
            _logs.removeAt(0)
        }
        _logs.add(LogEntry(System.currentTimeMillis(), level, message))
    }
    
    fun getFormattedTime(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }
    
    fun clear() {
        _logs.clear()
    }

    fun getLogsAsString(): String {
        return _logs.joinToString("\n") { entry ->
            "${getFormattedTime(entry.timestamp)} [${entry.level}] ${entry.message}"
        }
    }
}
