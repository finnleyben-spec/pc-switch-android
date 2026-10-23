package dev.finnley.pcswitch

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.net.http.SslError
import android.os.Bundle
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * PC-Switch: WebView-Wrapper um die PWA.
 *
 * Die App selbst ist dumm: sie lädt die PWA von GitHub Pages und stellt
 * (a) einen sauberen App-Kontext ohne Browser-Chrome bereit und
 * (b) Vertrauen in die lokale CA des HA-Proxys (res/raw/pcca.pem via
 *     Network Security Config + Sicherheitsnetz in onReceivedSslError).
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

            // Alle Links in der App halten (keine fremden Browser-Apps)
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
        }

        setContentView(web)

        val deepLink = intent?.dataString
        if (savedInstanceState == null) {
            // Lokale Version in die PWA injizieren: der Update-Button in der App
            // vergleicht version_code mit version.json (GitHub Pages).
            val base = deepLink ?: DEFAULT_URL
            val sep = if (base.contains("?")) "&" else "?"
            web.loadUrl("$base${sep}apk=${BuildConfig.VERSION_NAME}&vc=${BuildConfig.VERSION_CODE}")
        }
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
