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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

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

    private fun startHost() {
        val intent = Intent(this, HostService::class.java)
            .setAction(HostService.ACTION_START)

        startForegroundService(intent)
    }

    private fun stopHost() {
        val intent = Intent(this, HostService::class.java)
            .setAction(HostService.ACTION_STOP)

        startService(intent)
    }

    @androidx.compose.runtime.Composable
    private fun HostScreen() {

        var projectName by remember {
            mutableStateOf("default")
        }

        var port by remember {
            mutableStateOf("3000")
        }

        var quickTunnel by remember {
            mutableStateOf(true)
        }

        var token by remember {
            mutableStateOf("")
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.Top
        ) {

            Text(
                text = "IMAYX Host",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Android hosting runtime",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = projectName,
                onValueChange = { projectName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Project name") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Port") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

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
                    onCheckedChange = { quickTunnel = it }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cloudflare Tunnel Token") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    startHost()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Host")
            }

            Spacer(modifier = Modifier.height(12.dp))

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
