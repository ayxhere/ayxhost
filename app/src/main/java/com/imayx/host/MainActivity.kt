package com.imayx.host

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File

private val BG=Color(0xFF07080A)
private val CARD=Color(0xFF111318)
private val ACCENT=Color(0xFF8B7CFF)

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        if(Build.VERSION.SDK_INT>=33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),44)
        setContent { HostUi() }
    }

    @Composable
    fun HostUi() {
        var running by remember{mutableStateOf(false)}
        var quick by remember{mutableStateOf(HostConfig.quick(this))}
        var token by remember{mutableStateOf(HostConfig.token(this))}
        var project by remember{mutableStateOf(HostConfig.project(this))}
        var status by remember{mutableStateOf("Ready")}
        var log by remember{mutableStateOf("")}
        val pickZip=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
            if(uri!=null) {
                try {
                    ZipProjectManager.importZip(this,uri,project)
                    HostConfig.setProject(this,project)
                    status="ZIP imported: $project"
                } catch(e:Throwable){status="Import failed: ${e.message}"}
            }
        }
        val cf=remember{CloudflareManager(this)}
        val scope=lifecycleScope
        MaterialTheme(colorScheme=darkColorScheme(background=BG,surface=CARD,primary=ACCENT)) {
            Scaffold(containerColor=BG,topBar={
                CenterAlignedTopAppBar(title={Text("IMAYX Host")},colors=TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor=BG))
            }){pad->
                LazyColumn(Modifier.fillMaxSize().padding(pad),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
                    item{
                        Surface(shape=RoundedCornerShape(24.dp),color=CARD){
                            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                                Text("Hosting Dashboard",style=MaterialTheme.typography.titleLarge)
                                Text(status,color=Color.LightGray)
                                Row(verticalAlignment=Alignment.CenterVertically){
                                    Icon(if(running)Icons.Default.CloudDone else Icons.Default.CloudOff,null,tint=if(running)Color.Green else Color.Gray)
                                    Spacer(Modifier.width(10.dp)); Text(if(running)"ONLINE":"OFFLINE")
                                    Spacer(Modifier.weight(1f))
                                    Button(onClick={
                                        if(running){stopService(Intent(this@MainActivity,HostService::class.java));running=false;status="Stopped"}
                                        else {HostConfig.setProject(this@MainActivity,project);HostConfig.setToken(this@MainActivity,token);HostConfig.setQuick(this@MainActivity,quick);startForegroundService(Intent(this@MainActivity,HostService::class.java));running=true;status="Starting server + tunnel…"}
                                    }){Text(if(running)"Stop Host" else "Start Host")}
                                }
                            }
                        }
                    }
                    item{
                        Surface(shape=RoundedCornerShape(24.dp),color=CARD){
                            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                                Text("Website Project",style=MaterialTheme.typography.titleMedium)
                                OutlinedTextField(project,{project=it},label={Text("Project name")},singleLine=true,modifier=Modifier.fillMaxWidth())
                                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                                    Button(onClick={pickZip.launch("application/zip")}){Icon(Icons.Default.Upload,null);Spacer(Modifier.width(6.dp));Text("Upload ZIP")}
                                    OutlinedButton(onClick={status="Root: ${File(filesDir,"projects/$project").absolutePath}"}){Text("Project path")}
                                }
                            }
                        }
                    }
                    item{
                        Surface(shape=RoundedCornerShape(24.dp),color=CARD){
                            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                                Text("Cloudflare Tunnel",style=MaterialTheme.typography.titleMedium)
                                Text("This is the actual tunnel controller used by IMAYX Host.",color=Color.LightGray)
                                Button(onClick={
                                    scope.launch{
                                        status="Downloading cloudflared ARM64…"
                                        val r=cf.installLatest()
                                        status=if(r.isSuccess)"cloudflared installed" else "Install failed: ${r.exceptionOrNull()?.message}"
                                    }
                                }){Icon(Icons.Default.Download,null);Spacer(Modifier.width(6.dp));Text(if(cf.installed())"Update cloudflared" else "Install cloudflared")}
                                Row(verticalAlignment=Alignment.CenterVertically){
                                    Switch(checked=quick,onCheckedChange={quick=it})
                                    Spacer(Modifier.width(8.dp))
                                    Text("Quick Tunnel")
                                }
                                if(!quick){
                                    OutlinedTextField(token,{token=it},label={Text("Cloudflare Tunnel Token")},singleLine=true,modifier=Modifier.fillMaxWidth())
                                    Text("Named tunnel mode: Cloudflare dashboard me Public Hostname ko http://127.0.0.1:3000 par route karo, phir us tunnel ka token yahan paste karo.",color=Color.Gray)
                                }else{
                                    Text("Quick mode temporary *.trycloudflare.com URL deta hai. Custom imayx.in ke liye Quick Tunnel off karke named tunnel token use karo.",color=Color.Gray)
                                }
                            }
                        }
                    }
                    item{
                        Surface(shape=RoundedCornerShape(24.dp),color=CARD){
                            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                                Text("How imayx.in connects",style=MaterialTheme.typography.titleMedium)
                                Text("Phone → local :3000 → cloudflared → Cloudflare edge → imayx.in",color=Color.LightGray)
                                Text("Cloudflare DNS ko app khud magic se configure nahi karta. Named Tunnel token Cloudflare dashboard se aayega.",color=Color.Gray)
                            }
                        }
                    }
                    item{
                        Surface(shape=RoundedCornerShape(24.dp),color=CARD){
                            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                                Text("Logs",style=MaterialTheme.typography.titleMedium)
                                Text(if(log.isBlank())"No live log yet" else log,color=Color.LightGray)
                            }
                        }
                    }
                }
            }
        }
    }
}
