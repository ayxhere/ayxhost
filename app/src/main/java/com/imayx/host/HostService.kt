package com.imayx.host

import android.app.*
import android.content.Intent
import android.os.IBinder

class HostService:Service() {
    private var staticServer:StaticServer?=null
    private var cloudflare:CloudflareManager?=null

    override fun onCreate() {
        super.onCreate()
        val ch=NotificationChannel("imayx_host","IMAYX Host",NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        val n=Notification.Builder(this,"imayx_host")
            .setContentTitle("IMAYX Host")
            .setContentText("Hosting service is running")
            .setSmallIcon(android.R.drawable.stat_sys_upload).setOngoing(true).build()
        startForeground(1001,n)

        val project=File(filesDir,"projects/${HostConfig.project(this)}")
        staticServer=StaticServer(project,HostConfig.port(this)).also{it.start()}
        cloudflare=CloudflareManager(this)
        if(cloudflare!!.installed()) {
            if(HostConfig.quick(this)) cloudflare!!.startQuick(HostConfig.port(this)){ }
            else if(HostConfig.token(this).isNotBlank()) cloudflare!!.startNamed(HostConfig.token(this)){ }
        }
    }
    override fun onDestroy() {
        cloudflare?.stop(); staticServer?.stop()
        super.onDestroy()
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int)=START_STICKY
    override fun onBind(intent:Intent?):IBinder?=null
}
