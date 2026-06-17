package com.remotedroid.store

import android.content.Context
import java.security.SecureRandom

/** Token ve port kalıcılığı (SharedPreferences). Token ilk erişimde güvenli üretilir. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("remotedroid", Context.MODE_PRIVATE)

    var port: Int
        get() = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        set(value) {
            prefs.edit().putInt(KEY_PORT, value).apply()
        }

    val token: String
        get() = prefs.getString(KEY_TOKEN, null) ?: regenerateToken()

    fun regenerateToken(): String {
        val token = generateToken()
        prefs.edit().putString(KEY_TOKEN, token).apply()
        return token
    }

    private fun generateToken(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val DEFAULT_PORT = 8080
        private const val KEY_PORT = "port"
        private const val KEY_TOKEN = "token"
    }
}
