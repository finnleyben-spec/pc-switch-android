package dev.finnley.pcswitch.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Self-Update: version.json (GitHub Pages) + APK-Download + System-Installer.
 * Nur manuell (kein Silent-Install).
 */
class UpdateManager(private val ctx: Context) {

    data class UpdateInfo(
        val versionName: String,
        val versionCode: Int,
        val url: String,
        val notes: String,
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** @return null, wenn keine neuere Version verfügbar. */
    suspend fun check(currentCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(VERSION_URL).build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                val body = r.body?.string() ?: return@withContext null
                val j = JSONObject(body)
                val tagName = j.optString("tag_name", "")
                val versionName = tagName.removePrefix("v")
                val assets = j.optJSONArray("assets") ?: return@withContext null
                var apkUrl = ""
                var versionCode = 0
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name == "version.json") {
                        // Download version.json to get version code
                        val versionReq = Request.Builder().url(asset.optString("browser_download_url")).build()
                        client.newCall(versionReq).execute().use { vr ->
                            if (vr.isSuccessful) {
                                val versionBody = vr.body?.string() ?: ""
                                val versionJ = JSONObject(versionBody)
                                versionCode = versionJ.optInt("versionCode", 0)
                            }
                        }
                    } else if (name.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url", "")
                    }
                }
                if (apkUrl.isEmpty() || versionCode <= currentCode) return@withContext null
                UpdateInfo(
                    versionName = versionName,
                    versionCode = versionCode,
                    url = apkUrl,
                    notes = "",
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("UpdateManager", "Check failed", e)
            null
        }
    }

    /** Streaming-Download mit Fortschritt (0..100). @return APK-Datei oder null. */
    suspend fun download(info: UpdateInfo, progress: (Int) -> Unit): File? =
        withContext(Dispatchers.IO) {
            android.util.Log.d("UpdateManager", "Downloading: ${info.url}")
            val dir = File(ctx.filesDir, "apk").apply { mkdirs() }
            val name = "pc-switch-${info.versionName}.apk"
            val target = File(dir, name)
            val tmp = File(dir, "$name.part")
            try {
                val req = Request.Builder().url(info.url).build()
                client.newCall(req).execute().use { r ->
                    android.util.Log.d("UpdateManager", "HTTP Status: ${r.code}")
                    if (!r.isSuccessful) return@withContext null
                    val body = r.body ?: return@withContext null
                    val total = body.contentLength().coerceAtLeast(1L)
                    android.util.Log.d("UpdateManager", "Total size: $total")
                    tmp.outputStream().use { out ->
                        body.byteStream().use { input ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            var n = input.read(buf)
                            while (n >= 0) {
                                out.write(buf, 0, n)
                                done += n
                                progress(((done * 100) / total).toInt().coerceIn(0, 100))
                                n = input.read(buf)
                            }
                        }
                    }
                    android.util.Log.d("UpdateManager", "Download complete, size: ${tmp.length()}")
                    if (!tmp.exists() || tmp.length() == 0L) {
                        android.util.Log.e("UpdateManager", "Downloaded file is empty")
                        return@withContext null
                    }
                    if (target.exists()) target.delete()
                    if (!tmp.renameTo(target)) {
                        android.util.Log.e("UpdateManager", "Failed to rename temp file")
                        return@withContext null
                    }
                    android.util.Log.d("UpdateManager", "Renamed to: ${target.absolutePath}")
                    target
                }
            } catch (e: Exception) {
                android.util.Log.e("UpdateManager", "Download failed", e)
                tmp.delete()
                null
            }
        }

    /** APK an den System-Installer geben (ACTION_VIEW + FileProvider). */
    fun install(file: File) {
        android.util.Log.d("UpdateManager", "Installing: ${file.absolutePath}")
        // Prüfe ob die App berechtigt ist, APKs zu installieren
        if (!canInstallPackages()) {
            android.util.Log.d("UpdateManager", "No install permission, opening settings")
            openInstallSettings()
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        android.util.Log.d("UpdateManager", "Starting install intent")
        ctx.startActivity(i)
    }

    private fun canInstallPackages(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            return ctx.packageManager.canRequestPackageInstalls()
        }
        return true
    }

    private fun openInstallSettings() {
        try {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${pi.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }

    companion object {
        const val VERSION_URL = "https://api.github.com/repos/finnleyben-spec/pc-switch-android/releases/latest"
    }

    fun savePreviousVersion() {
        try {
            val dir = File(ctx.filesDir, "apk").apply { mkdirs() }
            val previous = File(dir, "previous.apk")
            // Find the current APK file (not the new download)
            val current = ctx.applicationInfo.sourceDir
            if (current != null && !current.contains("previous.apk")) {
                android.util.Log.d("UpdateManager", "Saving previous version from: $current")
                if (previous.exists()) previous.delete()
                java.io.File(current).copyTo(previous, false)
            }
        } catch (e: Exception) {
            android.util.Log.e("UpdateManager", "Failed to save previous version", e)
        }
    }

    fun getPreviousVersion(): File? {
        try {
            val dir = File(ctx.filesDir, "apk").apply { mkdirs() }
            val previous = File(dir, "previous.apk")
            if (previous.exists()) {
                return previous
            }
        } catch (e: Exception) {
            android.util.Log.e("UpdateManager", "Failed to get previous version", e)
        }
        return null
    }

    fun hasPreviousVersion(): Boolean {
        return getPreviousVersion() != null
    }
}
