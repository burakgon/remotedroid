package com.remotedroid.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.remotedroid.protocol.ClientMessage
import com.remotedroid.protocol.Features
import com.remotedroid.protocol.Screen
import com.remotedroid.protocol.ServerMessage
import com.remotedroid.input.CommandExecutor
import com.remotedroid.server.RemoteServer
import com.remotedroid.store.Settings

/**
 * Foreground service that keeps the embedded server alive. Bridges commands to the
 * connected Accessibility Service instance.
 */
class ServerService : Service() {
    private var server: RemoteServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (server == null) {
            val settings = Settings(this)
            server = RemoteServer(settings.port, settings.token, assets, bridgeExecutor()).also { it.start() }
        }
        return START_STICKY
    }

    private fun bridgeExecutor(): CommandExecutor = object : CommandExecutor {
        override fun execute(msg: ClientMessage) {
            RemoteAccessibilityService.instance?.execute(msg)
        }

        override fun welcome(): ServerMessage.Welcome =
            RemoteAccessibilityService.instance?.welcome()
                ?: ServerMessage.Welcome(
                    screen = Screen(0, 0),
                    android = Build.VERSION.SDK_INT,
                    features = Features(imeEnter = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R, scroll = true),
                )
    }

    private fun startInForeground() {
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "RemoteDroid", NotificationManager.IMPORTANCE_LOW),
            )
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("RemoteDroid running")
            .setContentText("Ready for remote connection")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 1
        private const val CHANNEL_ID = "remotedroid"

        fun start(context: Context) {
            val intent = Intent(context, ServerService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ServerService::class.java))
        }
    }
}
