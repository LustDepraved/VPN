package com.freevpn.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.IOException

class LocalVpnService : VpnService() {
    private var tunnelInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> startTunnel()
            ACTION_DISCONNECT -> stopTunnel()
            ACTION_RECONNECT -> {
                stopTunnel()
                startTunnel()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }

    private fun startTunnel() {
        if (tunnelInterface != null) return

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("VPN connecting"))

        val builder = Builder()
            .setSession("FreeVPN")
            .addAddress("10.8.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .setMtu(1400)

        tunnelInterface = builder.establish()
        startForeground(NOTIFICATION_ID, buildNotification("VPN connected (local tunnel)"))
    }

    private fun stopTunnel() {
        try {
            tunnelInterface?.close()
        } catch (_: IOException) {
        }
        tunnelInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(statusText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FreeVPN")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FreeVPN Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_CONNECT = "com.freevpn.app.CONNECT"
        const val ACTION_DISCONNECT = "com.freevpn.app.DISCONNECT"
        const val ACTION_RECONNECT = "com.freevpn.app.RECONNECT"
        private const val CHANNEL_ID = "freevpn_service"
        private const val NOTIFICATION_ID = 1001
    }
}
