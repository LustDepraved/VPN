package com.freevpn.app

import android.content.Context

object VpnPrefs {
    private const val PREFS = "vpn_prefs"
    private const val KEY_AUTO_RECONNECT = "auto_reconnect"

    fun isAutoReconnectEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_RECONNECT, true)
    }

    fun setAutoReconnectEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_RECONNECT, enabled)
            .apply()
    }
}
