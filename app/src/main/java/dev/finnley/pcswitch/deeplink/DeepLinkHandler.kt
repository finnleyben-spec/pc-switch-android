package dev.finnley.pcswitch.deeplink

import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import org.json.JSONObject

object DeepLinkHandler {
    private const val TAG = "DeepLinkHandler"
    private const val SCHEME = "pcswitch"
    private const val ACTION_SETUP = "setup"
    private const val ACTION_TRIGGER = "trigger"

    sealed class Result {
        data class Setup(
            val haUrl: String?,
            val haToken: String?,
            val switches: List<SwitchConfig>?,
            val autoConnect: Boolean
        ) : Result()

        data class Trigger(
            val press: String,
            val switchId: String?
        ) : Result()

        data object Unknown : Result()
    }

    data class SwitchConfig(
        val name: String,
        val type: String,
        val primaryEntity: String,
        val secondaryEntity: String
    )

    fun handle(intent: Intent): Result? {
        val uri = intent.data ?: return null
        Log.i(TAG, "handle() called with URI: $uri")
        if (uri.scheme != SCHEME) return null

        return try {
            when (uri.host) {
                ACTION_SETUP -> parseSetup(uri)
                ACTION_TRIGGER -> parseTrigger(uri)
                else -> {
                    Log.w(TAG, "Unknown deep link host: ${uri.host}")
                    Result.Unknown
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse deep link: $uri", e)
            Result.Unknown
        }
    }

    private fun parseSetup(uri: Uri): Result.Setup {
        Log.i(TAG, "parseSetup() called")
        val cfgParam = uri.getQueryParameter("cfg")
        Log.i(TAG, "cfgParam: $cfgParam")
        val autoConnect = uri.getQueryParameter("autoconnect") == "1"

        var haUrl: String? = null
        var haToken: String? = null
        var switches: List<SwitchConfig>? = null

        if (cfgParam != null) {
            try {
                val decoded = Base64.decode(cfgParam, Base64.URL_SAFE or Base64.NO_WRAP)
                val json = JSONObject(String(decoded))
                Log.i(TAG, "Decoded JSON: $json")

                haUrl = json.optString("ha_url", "")
                if (haUrl.isEmpty()) haUrl = null

                haToken = json.optString("ha_token", "")
                if (haToken.isEmpty()) haToken = null

                val switchesArr = json.optJSONArray("switches")
                if (switchesArr != null) {
                    switches = mutableListOf()
                    for (i in 0 until switchesArr.length()) {
                        val swObj = switchesArr.getJSONObject(i)
                        switches.add(SwitchConfig(
                            name = swObj.getString("name"),
                            type = swObj.optString("type", "dual"),
                            primaryEntity = swObj.getString("primaryEntity"),
                            secondaryEntity = swObj.optString("secondaryEntity", "")
                        ))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse setup config", e)
            }
        }

        return Result.Setup(haUrl, haToken, switches, autoConnect)
    }

    private fun parseTrigger(uri: Uri): Result.Trigger {
        val press = uri.getQueryParameter("press") ?: ""
        val switchId = uri.getQueryParameter("switch")
        return Result.Trigger(press, switchId)
    }
}
