package com.rainnya.chat.data.settings

import android.content.Context
import android.content.SharedPreferences

class AppSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("rainnya_prefs", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, DEFAULT_USERNAME) ?: DEFAULT_USERNAME
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    val taggedUsername: String
        get() {
            val raw = username.trim()
            return if (raw.startsWith("app_")) raw else "app_$raw"
        }

    val wsUrl: String
        get() {
            val base = serverUrl.trimEnd('/')
            return if (base.startsWith("https")) {
                base.replace("https://", "wss://") + "/api/v1/chat/ws"
            } else {
                base.replace("http://", "ws://") + "/api/v1/chat/ws"
            }
        }

    val httpBaseUrl: String
        get() = serverUrl.trimEnd('/')

    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && serverUrl.isNotBlank()

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_USERNAME = "username"
        private const val DEFAULT_SERVER_URL = "http://192.168.1.100:6185"
        private const val DEFAULT_USERNAME = "RainnyaUser"
    }
}
