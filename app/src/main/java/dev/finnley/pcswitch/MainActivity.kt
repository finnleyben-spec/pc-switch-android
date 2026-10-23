package dev.finnley.pcswitch

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Environment
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.net.URL

/**
 * PC-Switch: WebView-Wrapper um die PWA.
 *
 * Die App selbst ist dumm: sie lädt die PWA von GitHub Pages und stellt
 * (a) einen sauberen App-Kontext ohne Browser-Chrome bereit,
 * (b) Vertrauen in die lokale CA des HA-Proxys (res/raw/pcca.pem via
 *     Network Security Config + Sicherheitsnetz in onReceivedSslError) und
 * (c) den Self-Update: Der Update-Button der PWA navigiert auf die APK-URL
 *     aus version.json — hier wird der Download abgefangen und der
 *     System-Installer gestartet. Einstellungen (localStorage der WebView)
 *     bleiben erhalten, weil der Package-Name unverändert bleibt.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)

        web = WebView(this)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        web.setBackgroundColor(Color.WHITE)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            // LAN-Modus verbindet von der HTTPS-Seite aus per ws:// direkt ins Heim-Netz
            // (Mixed Content) — für die App explizit erlaubt.
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowFileAccess = false
            mediaPlaybackRequiresUserGesture = false
        }
        web.webViewClient = object : WebViewClient() {
            /**
             * Sicherheitsnetz: Falls die WebView die NSC-Trust-Anchors nicht
             * beachtet, genau FÜR DEN HA-PROXY weiterlaufen (NOT_TRUSTED).
             * Alles andere (falscher Host, abgelaufen, ...) bleibt hart abgelehnt.
             */
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                val url = view.url ?: return handler.cancel()
                // Unbekannte CA (SSL_UNTRUSTED) genau fuer den HA-Proxy durchlassen
                if (error.hasError(SslError.SSL_UNTRUSTED) &&
                    url.contains("stormlinux.ts.net")
                ) {
                    handler.proceed()
                } else {
                    handler.cancel()
                }
            }

            // Alle Links in der App halten (keine fremden Browser-Apps).
            // Self-Update: APK-URL hier abfangen — VOR dem Service Worker.
            // location.href auf die .apk-URL würde sonst von sw.js interceptet
            // (respondWith) und der DownloadListener würde nie feuern.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.substringBefore('?').endsWith(".apk", ignoreCase = true)) {
                    view.stopLoading()
                    downloadAndInstallApk(url)
                    return true
                }
                return false
            }
        }

        // Self-Update: PWA setzt location.href auf die APK-URL aus version.json.
        // WebView feuert dafür den DownloadListener (nicht renderbarer Content-Typ).
        // Hier: APK herunterladen und den System-Installer starten.
        web.setDownloadListener { url, _, _, mimeType, _ ->
            val isApk = url.substringBefore('?').endsWith(".apk", ignoreCase = true) ||
                mimeType == "application/vnd.android.package-archive"
            if (isApk) {
                web.stopLoading()
                downloadAndInstallApk(url)
            }
        }

        setContentView(web)

        // Self-Update, deterministischer Pfad: Die PWA ruft im APK-Modus das
        // JavascriptInterface auf (statt zu navigieren). Kein Service-Worker-
        // Timing, kein DownloadListener — die APK-URL kommt direkt hier an.
        web.addJavascriptInterface(object {
            @JavascriptInterface
            fun startApkDownload(url: String) {
                downloadAndInstallApk(url)
            }
        }, "PcSwitch")

        val deepLink = intent?.dataString
        if (savedInstanceState == null) {
            // Lokale Version in die PWA injizieren: der Update-Button in der App
            // vergleicht version_code mit version.json (GitHub Pages).
            val base = deepLink ?: DEFAULT_URL
            val sep = if (base.contains("?")) "&" else "?"
            web.loadUrl("$base${sep}apk=${BuildConfig.VERSION_NAME}&vc=${BuildConfig.VERSION_CODE}")
        }
    }

    /** APK im Hintergrund herunterladen, dann Installer auf der UI-Thread starten. */
    private fun downloadAndInstallApk(url: String) {
        Thread {
            try {
                toast("Update wird heruntergeladen …")
                val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
                val apk = File(dir, "pc-switch-update.apk")
                URL(url).openStream().use { input ->
                    apk.outputStream().use { output -> input.copyTo(output) }
                }
                if (apk.length() < 50_000L) throw RuntimeException("Datei zu klein: ${apk.length()} B")
                runOnUiThread { installApk(apk) }
            } catch (e: Exception) {
                runOnUiThread { toast("Update-Fehler: ${e.message}") }
            }
        }.start()
    }

    private fun installApk(apk: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            toast("Kein Installer verfügbar: ${e.message}")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onResume() {
        web.onResume()
        super.onResume()
    }

    companion object {
        const val DEFAULT_URL = "https://finnleyben-spec.github.io/pc-switch/"
    }
}
