package com.carnelia.vpn.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.carnelia.vpn.utils.AppLogger
import com.carnelia.vpn.utils.PrefsManager
import java.util.Calendar

/**
 * VpnScheduleManager — v2.4.0
 * Schedules daily connect and disconnect alarms using AlarmManager.
 * No external services required — uses built-in Android AlarmManager.
 */
object VpnScheduleManager {

    private const val ACTION_CONNECT = "com.carnelia.vpn.SCHEDULE_CONNECT"
    private const val ACTION_DISCONNECT = "com.carnelia.vpn.SCHEDULE_DISCONNECT"
    private const val REQ_CONNECT = 5001
    private const val REQ_DISCONNECT = 5002

    /**
     * Reads schedule prefs and registers (or cancels) daily alarms accordingly.
     * Call this whenever the schedule settings change.
     */
    fun updateSchedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAll(context, am)

        if (!PrefsManager.isScheduleEnabled(context)) {
            AppLogger.log("VpnSchedule: Schedule disabled — alarms cancelled")
            return
        }

        val cH = PrefsManager.getScheduleConnectHour(context)
        val cM = PrefsManager.getScheduleConnectMin(context)
        val dH = PrefsManager.getScheduleDisconnectHour(context)
        val dM = PrefsManager.getScheduleDisconnectMin(context)

        scheduleDaily(context, am, cH, cM, ACTION_CONNECT, REQ_CONNECT)
        scheduleDaily(context, am, dH, dM, ACTION_DISCONNECT, REQ_DISCONNECT)
        AppLogger.log("VpnSchedule: Set connect=$cH:${cM.toString().padStart(2,'0')} disconnect=$dH:${dM.toString().padStart(2,'0')}")
    }

    private fun scheduleDaily(
        context: Context, am: AlarmManager,
        hour: Int, minute: Int, action: String, reqCode: Int
    ) {
        val intent = Intent(context, ScheduleReceiver::class.java).apply { this.action = action }
        val flags = if (Build.VERSION.SDK_INT >= 23)
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getBroadcast(context, reqCode, intent, flags)

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1)
        }

        try {
            am.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
        } catch (e: SecurityException) {
            // Android 12+ setExact requires SCHEDULE_EXACT_ALARM; fall back to inexact repeat
            am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }

    private fun cancelAll(context: Context, am: AlarmManager) {
        val flags = if (Build.VERSION.SDK_INT >= 23)
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        else PendingIntent.FLAG_NO_CREATE

        listOf(ACTION_CONNECT to REQ_CONNECT, ACTION_DISCONNECT to REQ_DISCONNECT).forEach { (action, req) ->
            val intent = Intent(context, ScheduleReceiver::class.java).apply { this.action = action }
            val pi = PendingIntent.getBroadcast(context, req, intent, flags)
            pi?.let { am.cancel(it) }
        }
    }
}

/**
 * Receives daily alarm intents and starts/stops the VPN service.
 */
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppLogger.log("ScheduleReceiver: action=${intent.action}")
        when (intent.action) {
            "com.carnelia.vpn.SCHEDULE_CONNECT" -> {
                val svcIntent = Intent(context, CarheliaVpnService::class.java).apply {
                    action = CarheliaVpnService.ACTION_CONNECT
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svcIntent)
                } else {
                    context.startService(svcIntent)
                }
            }
            "com.carnelia.vpn.SCHEDULE_DISCONNECT" -> {
                context.startService(Intent(context, CarheliaVpnService::class.java).apply {
                    action = CarheliaVpnService.ACTION_DISCONNECT
                })
            }
        }
    }
}
