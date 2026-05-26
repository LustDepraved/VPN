package com.freevpn.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager

class NetworkChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ConnectivityManager.CONNECTIVITY_ACTION) return
        if (!VpnPrefs.isAutoReconnectEnabled(context)) return

        val reconnectIntent = Intent(context, LocalVpnService::class.java).apply {
            action = LocalVpnService.ACTION_RECONNECT
        }
        context.startService(reconnectIntent)
    }
}
