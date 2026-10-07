package com.imayx.host

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class CloudflareManager(private val context: Context) {

    private val binDir = File(context.filesDir, "bin")
    private val cloudflared = File(binDir, "cloudflared")

    suspend fun installLatest(): Boolean = withContext(Dispatchers.IO) {
        try {
            binDir.mkdirs()

            val apiUrl =
                URL("https://api.github.com/repos/cloudflare/cloudflared/releases/latest")

            val connection = apiUrl.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "IMAYX-Host")
            connection.connectTimeout = 15000
            connection.readTimeout = 30000

            val json = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val assetUrl = findArm64AssetUrl(json)
                ?: return@withContext false

            val download = URL(assetUrl).openConnection() as HttpURLConnection
            download.requestMethod = "GET"
            download.setRequestProperty("User-Agent", "IMAYX-Host")
            download.connectTimeout = 15000
            download.readTimeout = 120000

            download.inputStream.use { input ->
                cloudflared.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            download.disconnect()

            cloudflared.setExecutable(true, false)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun findArm64AssetUrl(json: String): String? {
        val pattern = Regex(
            """https://[^"]+?/cloudflared-linux-arm64[^"]*"""
        )

        return pattern.find(json)?.value
    }

    fun isInstalled(): Boolean {
        return cloudflared.exists() && cloudflared.canExecute()
    }

    fun startQuick(port: Int): Process? {
        if (!isInstalled()) return null

        return try {
            ProcessBuilder(
                cloudflared.absolutePath,
                "tunnel",
                "--no-autoupdate",
                "--url",
                "http://127.0.0.1:$port"
            )
                .redirectErrorStream(true)
                .start()
        } catch (_: Exception) {
            null
        }
    }

    fun startNamed(token: String): Process? {
        if (!isInstalled() || token.isBlank()) return null

        return try {
            ProcessBuilder(
                cloudflared.absolutePath,
                "tunnel",
                "--no-autoupdate",
                "run",
                "--token",
                token.trim()
            )
                .redirectErrorStream(true)
                .start()
        } catch (_: Exception) {
            null
        }
    }

    fun stop(process: Process?) {
        try {
            process?.destroy()
        } catch (_: Exception) {
        }
    }
}
