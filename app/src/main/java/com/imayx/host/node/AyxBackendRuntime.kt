package com.imayx.host.node

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.imayx.host.data.LogBus
import com.imayx.host.data.LogCategory
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

/**
 * Owns the embedded AyX Group backend (Node.js + SQLite, running inside the app
 * process via [NodeBridge] — no Termux, no external Node binary needed).
 *
 * - The backend is bundled as `assets/ayx-backend.zip` and extracted on first run
 *   (or after an app update) to `filesDir/ayx-backend`.
 * - The long-lived API server (`node src/server.js`, port 3101) is started exactly
 *   once per OS process via [ensureStarted], guarded by an [AtomicBoolean].
 * - One-shot scripts (e.g. `scripts/create-admin.js`) run through [runNodeScript],
 *   which intentionally bypasses the guard — it must NOT be used for the server.
 * - SESSION_SECRET is generated once (SecureRandom, 32 bytes hex) and kept in
 *   EncryptedSharedPreferences; the SQLite DB lives at
 *   `filesDir/ayx-backend/data/ayx.db`. Neither is ever logged.
 */
object AyxBackendRuntime {
    const val PORT = 3101
    private const val PREFS_FILE = "ayx_backend"
    private const val KEY_SESSION_SECRET = "session_secret"

    private val serverStarted = AtomicBoolean(false)
    @Volatile private var backendDir: File? = null

    /** Starts the API server if it isn't running. Safe to call repeatedly. */
    fun ensureStarted(ctx: Context) {
        if (!NodeBridge.available) {
            LogBus.w(LogCategory.NODE, "Embedded node not bundled (libnode.so missing) — AyX backend disabled")
            return
        }
        if (!serverStarted.compareAndSet(false, true)) return
        val dir = prepare(ctx) ?: run {
            serverStarted.set(false)
            return
        }
        backendDir = dir
        applyEnv(ctx, dir)
        Thread({
            LogBus.i(LogCategory.NODE, "starting AyX backend: ${dir.absolutePath}/src/server.js")
            try {
                val rc = NodeBridge.nativeStart(arrayOf("node", "src/server.js"), dir.absolutePath)
                LogBus.w(LogCategory.NODE, "AyX backend exited rc=$rc")
            } catch (e: Throwable) {
                LogBus.e(LogCategory.NODE, "AyX backend failed to start: ${e.message}")
            } finally {
                serverStarted.set(false)
            }
        }, "ayx-backend").start()
    }

    /**
     * Runs a one-shot node script inside the backend dir (e.g.
     * `scripts/create-admin.js --username=… --password=…`). [onDone] is invoked
     * once with the exit code and the last ~20 output lines. Arguments are never
     * logged — pass one-off secrets (like an admin password) only as args.
     */
    fun runNodeScript(
        ctx: Context,
        scriptRelativePath: String,
        args: List<String>,
        onDone: (exitCode: Int, tail: String) -> Unit
    ) {
        val clean = scriptRelativePath.trim().trimStart('/')
        require(clean.isNotEmpty() && !clean.contains("..")) { "Refusing unsafe script path" }
        if (!NodeBridge.available) {
            onDone(-1, "Embedded node not bundled (libnode.so missing)")
            return
        }
        val dir = prepare(ctx) ?: run {
            onDone(-1, "Backend not ready")
            return
        }
        val script = File(dir, clean)
        require(script.isFile) { "Script not found in backend: $clean" }
        applyEnv(ctx, dir)

        LogBus.i(LogCategory.NODE, "Running one-off script: node $clean")
        val argv = ArrayList<String>(args.size + 2)
        argv.add("node")
        argv.add(script.absolutePath)
        argv.addAll(args)
        val tail = ArrayDeque<String>()
        Thread({
            var code = -1
            try {
                code = NodeBridge.nativeStart(argv.toTypedArray(), dir.absolutePath)
            } catch (e: Throwable) {
                synchronized(tail) { tail.addLast("Error: ${e.message}") }
            }
            val out = synchronized(tail) { tail.joinToString("\n") }
            LogBus.i(LogCategory.NODE, "One-off script exited with code $code")
            try {
                onDone(code, out.ifBlank { "Process exited with code $code" })
            } catch (_: Exception) {
            }
        }, "ayx-backend-script").start()
    }

    /** True while the API server thread is up. */
    fun isServerRunning(): Boolean = serverStarted.get()

    /** Absolute path of the extracted backend dir, or null if not prepared yet. */
    fun dir(): File? = backendDir

    // ------------------------------------------------------------------ internals

    private fun prepare(ctx: Context): File? {
        return try {
            val dir = File(ctx.filesDir, "ayx-backend")
            extractIfNeeded(ctx, dir)
            File(dir, "data").mkdirs()
            File(dir, "uploads").mkdirs()
            dir
        } catch (e: Exception) {
            LogBus.e(LogCategory.NODE, "Backend prepare failed: ${e.message}")
            null
        }
    }

    private fun applyEnv(ctx: Context, dir: File) {
        val cache = File(ctx.cacheDir, "ayx-node-cache").apply { mkdirs() }
        NodeBridge.nativeSetenv("TMPDIR", ctx.cacheDir.absolutePath)
        NodeBridge.nativeSetenv("HOME", ctx.filesDir.absolutePath)
        NodeBridge.nativeSetenv("NODE_COMPILE_CACHE", cache.absolutePath)
        // V8 sizes old-space from total RAM; cap it so the OS doesn't OOM-kill the app.
        NodeBridge.nativeSetenv("NODE_OPTIONS", "--max-old-space-size-percentage=50")
        NodeBridge.nativeSetenv("NODE_ENV", "production")
        NodeBridge.nativeSetenv("PORT", PORT.toString())
        NodeBridge.nativeSetenv("DB_PATH", File(dir, "data/ayx.db").absolutePath)
        NodeBridge.nativeSetenv("UPLOAD_DIR", File(dir, "uploads").absolutePath)
        NodeBridge.nativeSetenv("SESSION_SECRET", sessionSecret(ctx))
        // Public base URL used to build absolute media URLs; CORS defaults cover imayx.in.
        NodeBridge.nativeSetenv("API_PUBLIC_BASE", "https://api.imayx.in")
    }

    private fun sessionSecret(ctx: Context): String {
        val alias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val prefs = EncryptedSharedPreferences.create(
            PREFS_FILE,
            alias,
            ctx.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        prefs.getString(KEY_SESSION_SECRET, null)?.let { return it }
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val hex = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(KEY_SESSION_SECRET, hex).apply()
        return hex
    }

    private fun extractIfNeeded(ctx: Context, dir: File) {
        val marker = File(dir, ".version")
        val current = ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime.toString()
        if (marker.exists() && marker.readText() == current) return

        LogBus.i(LogCategory.NODE, "extracting ayx-backend.zip (v=$current)")
        dir.deleteRecursively()
        dir.mkdirs()
        ctx.assets.open("ayx-backend.zip").use { raw ->
            ZipInputStream(raw).use { zip ->
                var e = zip.nextEntry
                val buf = ByteArray(64 * 1024)
                while (e != null) {
                    // Zip Slip guard: entries must stay inside dir.
                    val out = File(dir, e.name)
                    require(out.canonicalPath.startsWith(dir.canonicalPath + File.separator)) {
                        "Refusing zip entry outside backend dir: ${e.name}"
                    }
                    if (e.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { fos ->
                            var n = zip.read(buf)
                            while (n >= 0) {
                                fos.write(buf, 0, n)
                                n = zip.read(buf)
                            }
                        }
                    }
                    zip.closeEntry()
                    e = zip.nextEntry
                }
            }
        }
        marker.writeText(current)
        LogBus.i(LogCategory.NODE, "backend extraction done")
    }
}
