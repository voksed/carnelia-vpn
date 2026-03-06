package com.carnelia.vpn.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.carnelia.vpn.MainActivity
import com.carnelia.vpn.R
import com.carnelia.vpn.core.ConnectionState
import com.carnelia.vpn.service.CarheliaVpnService
import com.carnelia.vpn.core.VpnGlobalState

class VpnWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_UPDATE_WIDGET -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, VpnWidgetProvider::class.java))
                for (id in ids) {
                    updateAppWidget(context, appWidgetManager, id)
                }
            }
            ACTION_SELECT_SERVER -> {
                val serverId = intent.getStringExtra("server_id")
                if (!serverId.isNullOrBlank()) {
                    val repo = com.carnelia.vpn.data.ServerRepository(context)
                    val config = repo.getServers().find { it.id == serverId }
                    if (config != null) {
                        val mainIntent = Intent(context, com.carnelia.vpn.MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra("connect_server_id", serverId)
                        }
                        context.startActivity(mainIntent)
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.carnelia.vpn.UPDATE_WIDGET"
        const val ACTION_SELECT_SERVER = "com.carnelia.vpn.SELECT_SERVER"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.vpn_widget_info)

            val state = CarheliaVpnService.currentState
            val isConnected = state == ConnectionState.CONNECTED

            // Status Text
            views.setTextViewText(R.id.widget_status_text, state.name)

            // Список серверов через RemoteViewsService
            val intent = Intent(context, ServerListWidgetService::class.java)
            views.setRemoteAdapter(R.id.widget_server_list, intent)

            // Template for list item clicks
            val clickIntentTemplate = Intent(context, VpnWidgetProvider::class.java).apply {
                action = ACTION_SELECT_SERVER
            }
            val clickPendingIntentTemplate = PendingIntent.getBroadcast(
                context, 
                0, 
                clickIntentTemplate, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_server_list, clickPendingIntentTemplate)
            
            // Refresh list data
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_server_list)

            // Button Logic
            if (isConnected) {
                views.setTextViewText(R.id.widget_action_button, "Disconnect")
                views.setInt(R.id.widget_status_icon, "setColorFilter", android.graphics.Color.GREEN)

                // Action: Disconnect
                val disconnectIntent = Intent(context, CarheliaVpnService::class.java).apply {
                    action = CarheliaVpnService.ACTION_DISCONNECT
                }
                val pendingIntent = PendingIntent.getService(
                    context, 0, disconnectIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_action_button, pendingIntent)

            } else {
                views.setTextViewText(R.id.widget_action_button, "Connect")
                views.setInt(R.id.widget_status_icon, "setColorFilter", android.graphics.Color.GRAY)

                // Action: Open App to Connect (safer for now as we need config)
                val appIntent = Intent(context, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, appIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_action_button, pendingIntent)
            }

            // Click on widget opens app
            val appOpenIntent = Intent(context, MainActivity::class.java)
            val appPendingIntent = PendingIntent.getActivity(
                context, 1, appOpenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, appPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}