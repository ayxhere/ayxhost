package com.imayx.host

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

class CloudflareManager(private val context:Context) {
    private val bin=File(context.filesDir,"bin/cloudflared")
    private var process:Process?=null
    private val running=AtomicBoolean(false)
    @Volatile var publicUrl:String?=null
    @Volatile var lastLog:String=""

    suspend fun installLatest():Result<String> = withContext(Dispatchers.IO) {
        try {
            val api=URL("https://api.github.com/repos/cloudflare/cloudflared/releases/latest")
            val c=api.openConnection() as HttpURLConnection
            c.setRequestProperty("Accept","application/vnd.github+json")
            c.setRequestProperty("User-Agent","IMAYX-Host")
            val json=c.inputStream.bufferedReader().use{it.readText()}
            val regex=Regex(""browser_download_url"\s*:\s*"([^"]*cloudflared-linux-arm64)"")
            val match=regex.find(json) ?: return@withContext Result.failure(Exception("ARM64 cloudflared asset not found"))
            val download=match.groupValues[1].replace("\/","/")
            bin.parentFile?.mkdirs()
            val d=URL(download).openConnection() as HttpURLConnection
            d.setRequestProperty("User-Agent","IMAYX-Host")
            d.inputStream.use { input -> FileOutputStream(bin).use { output -> input.copyTo(output) } }
            if(!bin.setExecutable(true)) throw Exception("Could not mark cloudflared executable")
            Result.success(bin.absolutePath)
        } catch(e:Throwable) { Result.failure(e) }
    }

    fun installed()=bin.exists() && bin.canExecute()

    fun stop() {
        try { process?.destroy() } catch(_:Throwable){}
        process=null; running.set(false)
    }

    fun startQuick(port:Int,onLog:(String)->Unit) {
        stop()
        if(!installed()) throw IllegalStateException("Install cloudflared first")
        Thread {
            try {
                val p=ProcessBuilder(bin.absolutePath,"tunnel","--no-autoupdate","--url","http://127.0.0.1:$port")
                    .redirectErrorStream(true).start()
                process=p; running.set(true)
                p.inputStream.bufferedReader().forEachLine { line ->
                    lastLog=line; onLog(line)
                    val m=Regex("https://[a-z0-9-]+\.trycloudflare\.com").find(line)
                    if(m!=null) publicUrl=m.value
                }
                running.set(false)
            } catch(e:Throwable) { lastLog=e.message ?: "cloudflared error"; running.set(false) }
        }.start()
    }

    fun startNamed(token:String,onLog:(String)->Unit) {
        stop()
        if(token.isBlank()) throw IllegalArgumentException("Cloudflare tunnel token is empty")
        if(!installed()) throw IllegalStateException("Install cloudflared first")
        Thread {
            try {
                val p=ProcessBuilder(bin.absolutePath,"tunnel","--no-autoupdate","run","--token",token)
                    .redirectErrorStream(true).start()
                process=p; running.set(true)
                p.inputStream.bufferedReader().forEachLine { line -> lastLog=line; onLog(line) }
                running.set(false)
            } catch(e:Throwable) { lastLog=e.message ?: "cloudflared error"; running.set(false) }
        }.start()
    }

    fun isRunning()=running.get()
}
