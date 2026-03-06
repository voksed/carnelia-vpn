package com.carnelia.vpn.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.carnelia.vpn.R
import com.carnelia.vpn.data.ServerRepository

class ServerListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ServerListRemoteViewsFactory(applicationContext)
    }
}

class ServerListRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var servers: List<com.carnelia.vpn.core.VpnServerConfig> = emptyList()

    override fun onCreate() {
        servers = ServerRepository(context).getServers()
    }

    override fun onDataSetChanged() {
        servers = ServerRepository(context).getServers()
    }

    override fun onDestroy() {
        servers = emptyList()
    }

    override fun getCount(): Int = servers.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position < 0 || position >= servers.size) return null
        val server = servers[position]
        val views = RemoteViews(context.packageName, R.layout.widget_server_list_item)
        views.setTextViewText(R.id.widget_server_name, server.name ?: "Без имени")

        // PendingIntent для выбора сервера
        val fillInIntent = Intent()
        fillInIntent.action = com.carnelia.vpn.widget.VpnWidgetProvider.ACTION_SELECT_SERVER
        fillInIntent.putExtra("server_id", server.id)
        views.setOnClickFillInIntent(R.id.widget_server_name, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
