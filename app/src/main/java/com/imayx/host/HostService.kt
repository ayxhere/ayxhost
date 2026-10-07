package com.imayx.host

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import java.io.File

class HostService : Service() {

    companion object {
        const val CHANNEL_ID = "imayx_host"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.imayx.host.START"
        const val ACTION_STOP = "com.imayx.host.STOP"

        const val PORT = 3000
    }

    private var server: StaticServer? = null
    private var tunnel: Process? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {
            ACTION_STOP -> {
                stopHosting()
                stopSelf()
            }

            ACTION_START, null -> {
                startHosting()
            }
        }

        return START_STICKY
    }

    private fun startHosting() {
        startForeground(
            NOTIFICATION_ID,
            createNotification()
        )

        if (server != null) return

        val projectDir = File(
            filesDir,
            "projects/default"
        )

        if (!projectDir.exists()) {
            projectDir.mkdirs()
        }

        server = StaticServer(
            port = PORT,
            root = projectDir
        )

        server?.start()

        val cloudflare = CloudflareManager(this)

        // Start Quick Tunnel for now.
        // Custom named tunnel can be wired after the APK builds.
        tunnel = cloudflare.startQuick(PORT)
    }

    private fun stopHosting() {
        try {
            tunnel?.destroy()
        } catch (_: Exception) {
        }

        tunnel = null

        try {
            server?.stop()
        } catch (_: Exception) {
        }

        server = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "IMAYX Host",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(
                NotificationManager::class.java
            ).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("IMAYX Host")
                .setContentText("Hosting server is running on port $PORT")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setOngoing(true)
                .build()

        } else {

            Notification.Builder(this)
                .setContentTitle("IMAYX Host")
                .setContentText("Hosting server is running on port $PORT")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setOngoing(true)
                .build()
        }
    }

    override fun onDestroy() {
        stopHosting()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
