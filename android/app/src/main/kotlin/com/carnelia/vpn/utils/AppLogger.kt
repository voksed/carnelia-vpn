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
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    fun log(message: String) {
        android.util.Log.i("CarneliaDebug", message)
        addEntry(LogLevel.INFO, message)
    }

    fun debug(message: String) {
        android.util.Log.d("CarneliaDebug", message)
        addEntry(LogLevel.DEBUG, message)
    }

    fun error(message: String, throwable: Throwable? = null) {
        android.util.Log.e("CarneliaDebug", message, throwable)
        addEntry(LogLevel.ERROR, message)
    }

    private fun addEntry(level: LogLevel, message: String) {
        val entry = LogEntry(System.currentTimeMillis(), level, message)
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            if (_logs.size >= 300) _logs.removeAt(0)
            _logs.add(entry)
        } else {
            mainHandler.post {
                if (_logs.size >= 300) _logs.removeAt(0)
                _logs.add(entry)
            }
        }
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
