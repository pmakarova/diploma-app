package com.example.diploma.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Вспомогательный класс для управления конфигурацией сервера
 */
class ServerConfigHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "server_config", Context.MODE_PRIVATE
    )

    companion object {
        private const val TAG = "ServerConfigHelper"
        private const val DEFAULT_SERVER_IP = "192.168.0.103" // можно изменить тут или в приложении
        private const val DEFAULT_PORT = 5000 // можно изменить тут или в приложении
        private const val KEY_SERVER_IP = "server_ip"
        private const val KEY_SERVER_PORT = "server_port"
    }

    /**
     * Получить URL сервера
     */
    fun getServerUrl(): String {
        val ip = getServerIp()
        val port = getServerPort()
        val url = "http://$ip:$port"
        Log.d(TAG, "URL: $url")
        return url
    }

    /**
     * Получить IP-адрес сервера
     */
    fun getServerIp(): String {
        return prefs.getString(KEY_SERVER_IP, DEFAULT_SERVER_IP) ?: DEFAULT_SERVER_IP
    }

    /**
     * Установить IP-адрес сервера
     */
    fun setServerIp(ip: String) {
        //Log.d(TAG, "Установленный IP: $ip")
        prefs.edit().putString(KEY_SERVER_IP, ip).apply()
    }

    /**
     * Получить порт сервера
     */
    fun getServerPort(): Int {
        return prefs.getInt(KEY_SERVER_PORT, DEFAULT_PORT)
    }

    /**
     * Установить порт сервера
     */
    fun setServerPort(port: Int) {
        //Log.d(TAG, "Установленный порт: $port")
        prefs.edit().putInt(KEY_SERVER_PORT, port).apply()
    }

    /**
     * Сброс к настройкам по умолчанию
     */
    fun resetToDefault() {
        //Log.d(TAG, "Сброс к настройкам по умолчанию")
        prefs.edit()
            .putString(KEY_SERVER_IP, DEFAULT_SERVER_IP)
            .putInt(KEY_SERVER_PORT, DEFAULT_PORT)
            .apply()
    }
}