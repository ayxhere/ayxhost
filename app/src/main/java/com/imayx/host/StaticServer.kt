package com.imayx.host

import java.io.*
import java.net.*
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.util.concurrent.Executors
import kotlin.math.min

class StaticServer(private val root:File, private val port:Int=3000) {
    @Volatile private var running=false
    private var server:ServerSocket?=null
    private val pool=Executors.newCachedThreadPool()

    fun start() {
        if(running) return
        root.mkdirs()
        running=true
        pool.execute {
            try {
                server=ServerSocket(port,64,InetAddress.getByName("127.0.0.1"))
                while(running) {
                    val s=server!!.accept()
                    pool.execute { handle(s) }
                }
            } catch(_:Throwable) {} finally { running=false }
        }
    }
    fun stop() {
        running=false
        try { server?.close() } catch(_:Throwable){}
        server=null
    }
    fun isRunning()=running

    private fun handle(s:Socket) {
        s.use {
            it.soTimeout=5000
            val input=BufferedReader(InputStreamReader(it.getInputStream(), StandardCharsets.US_ASCII))
            val first=input.readLine() ?: return
            val parts=first.split(" ")
            if(parts.size<2) return
            val method=parts[0]
            var path=URLDecoder.decode(parts[1].substringBefore('?'),"UTF-8")
            while(true) { val h=input.readLine() ?: break; if(h.isEmpty()) break }
            if(method!="GET" && method!="HEAD") { sendText(it,405,"Method Not Allowed",""); return }
            if(path.contains("..")) { sendText(it,403,"Forbidden","Forbidden"); return }
            if(path=="/") path="/index.html"
            var f=File(root,path.removePrefix("/"))
            if(f.isDirectory) f=File(f,"index.html")
            if(!f.exists() || !f.isFile) {
                // SPA fallback
                val spa=File(root,"index.html")
                if(spa.exists()) f=spa else { sendText(it,404,"Not Found","Not Found"); return }
            }
            val canonicalRoot=root.canonicalFile
            if(!f.canonicalFile.path.startsWith(canonicalRoot.path + File.separator) && f.canonicalFile!=canonicalRoot) {
                sendText(it,403,"Forbidden","Forbidden"); return
            }
            val bytes=f.readBytes()
            val out=it.getOutputStream()
            val mime=mime(f.name)
            out.write("HTTP/1.1 200 OK\r\nContent-Type: $mime\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n".toByteArray())
            if(method!="HEAD") out.write(bytes)
            out.flush()
        }
    }
    private fun sendText(s:Socket,code:Int,msg:String,body:String) {
        val b=body.toByteArray()
        s.getOutputStream().use { o ->
            o.write("HTTP/1.1 $code $msg\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${b.size}\r\nConnection: close\r\n\r\n".toByteArray())
            o.write(b); o.flush()
        }
    }
    private fun mime(n:String)=when(n.substringAfterLast('.',"" ).lowercase()){
        "html","htm"->"text/html; charset=utf-8"; "css"->"text/css; charset=utf-8"
        "js","mjs"->"application/javascript; charset=utf-8"; "json"->"application/json"
        "xml"->"application/xml"; "svg"->"image/svg+xml"; "png"->"image/png"
        "jpg","jpeg"->"image/jpeg"; "webp"->"image/webp"; "gif"->"image/gif"
        "ico"->"image/x-icon"; "txt"->"text/plain; charset=utf-8"; "pdf"->"application/pdf"
        "woff"->"font/woff"; "woff2"->"font/woff2"; "ttf"->"font/ttf"; "otf"->"font/otf"
        else->"application/octet-stream"
    }
}
