package com.imayx.host

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private val prefs by lazy {
        getSharedPreferences(
            "imayx_host",
            MODE_PRIVATE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    HostScreen()
                }
            }
        }
    }

    private fun saveAndStart(
        projectName: String,
        portText: String,
        quickTunnel: Boolean,
        token: String
    ) {
        val port = portText.toIntOrNull()?.coerceIn(1024, 65535) ?: 3000

        prefs.edit()
            .putString(
                "project_name",
                projectName.ifBlank { "default" }
            )
            .putInt("port", port)
            .putBoolean("quick_tunnel", quickTunnel)
            .putString("tunnel_token", token.trim())
            .apply()

        val intent = Intent(
            this,
            HostService::class.java
        ).setAction(
            HostService.ACTION_START
        )

        startForegroundService(intent)
    }

    private fun stopHost() {
        val intent = Intent(
            this,
            HostService::class.java
        ).setAction(
            HostService.ACTION_STOP
        )

        startService(intent)
    }

    @Composable
    private fun HostScreen() {

        var projectName by remember {
            mutableStateOf(
                prefs.getString(
                    "project_name",
                    "default"
                ) ?: "default"
            )
        }

        var port by remember {
            mutableStateOf(
                prefs.getInt("port", 3000).toString()
            )
        }

        var quickTunnel by remember {
            mutableStateOf(
                prefs.getBoolean(
                    "quick_tunnel",
                    true
                )
            )
        }

        var token by remember {
            mutableStateOf(
                prefs.getString(
                    "tunnel_token",
                    ""
                ) ?: ""
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.Top
        ) {

            Text(
                text = "IMAYX Host",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = "Android hosting runtime"
            )

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            OutlinedTextField(
                value = projectName,
                onValueChange = {
                    projectName = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Project name")
                },
                singleLine = true
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            OutlinedTextField(
                value = port,
                onValueChange = {
                    port = it.filter(Char::isDigit)
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Port")
                },
                singleLine = true
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "Cloudflare Quick Tunnel",
                    modifier = Modifier.weight(1f)
                )

                Switch(
                    checked = quickTunnel,
                    onCheckedChange = {
                        quickTunnel = it
                    }
                )
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            OutlinedTextField(
                value = token,
                onValueChange = {
                    token = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Cloudflare Tunnel Token")
                },
                singleLine = true
            )

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            Button(
                onClick = {
                    saveAndStart(
                        projectName,
                        port,
                        quickTunnel,
                        token
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Host")
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Button(
                onClick = {
                    stopHost()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop Host")
            }
        }
    }
}
