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
                val apk = j.optJSONObject("apk") ?: return@withContext null
                val code = apk.optInt("version_code", 0)
                if (code <= currentCode) return@withContext null
                UpdateInfo(
                    versionName = apk.optString("version_name", "?"),
                    versionCode = code,
                    url = apk.optString("url", ""),
                    notes = apk.optString("notes", ""),
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Streaming-Download mit Fortschritt (0..100). @return APK-Datei oder null. */
    suspend fun download(info: UpdateInfo, progress: (Int) -> Unit): File? =
        withContext(Dispatchers.IO) {
            val dir = File(ctx.filesDir, "apk").apply { mkdirs() }
            val name = "pc-switch-${info.versionName}.apk"
            val target = File(dir, name)
            val tmp = File(dir, "$name.part")
            try {
                val req = Request.Builder().url(info.url).build()
                client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@withContext null
                    val body = r.body ?: return@withContext null
                    val total = body.contentLength().coerceAtLeast(1L)
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
                }
                if (target.exists()) target.delete()
                tmp.renameTo(target)
                target
            } catch (e: Exception) {
                tmp.delete()
                null
            }
        }

    /** APK an den System-Installer geben (ACTION_VIEW + FileProvider). */
    fun install(file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (ctx.packageManager.resolveActivity(i, 0) != null) {
            ctx.startActivity(i)
        } else {
            // Kein Installer gefunden → "Unbekannte App-Quellen" öffnen
            try {
                val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${pi.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        const val VERSION_URL = "https://finnleyben-spec.github.io/pc-switch/version.json"
    }
}
