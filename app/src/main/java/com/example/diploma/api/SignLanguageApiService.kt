package com.example.diploma.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Упрощенный API-сервис - все в одном запросе
 */
class SignLanguageApiService(private var serverUrl: String) {
    private val TAG = "SignLanguageApiService"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    fun updateServerUrl(newUrl: String) {
        this.serverUrl = newUrl
    }

    /**
     * Отправляет жест и получает результат
     */
    suspend fun recognizeGesture(features: List<Float>): GestureResult = withContext(Dispatchers.IO) {
        try {
            // Валидация
            if (features.size != 2520) {
                return@withContext GestureResult.error("Invalid features size: ${features.size}")
            }

            // Подготовка запроса
            val json = JSONObject().apply {
                put("features", JSONArray().apply {
                    features.forEach { put(it) }
                })
                put("timestamp", System.currentTimeMillis().toString())
            }

            val request = Request.Builder()
                .url("$serverUrl/features")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            // Выполнение запроса
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext GestureResult.error("HTTP ${response.code}")
                }

                val body = response.body?.string() ?: "{}"
                val result = JSONObject(body)

                return@withContext GestureResult(
                    gesture = result.optString("gesture", ""),
                    confidence = result.optDouble("confidence", 0.0).toFloat(),
                    classId = result.optInt("class_id", -1),
                    success = result.optString("gesture", "").isNotEmpty(),
                    error = result.optString("error", "")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка распознавания: ${e.message}")
            return@withContext GestureResult.error(e.message ?: "Network error")
        }
    }

    /**
     * Проверка доступности сервера
     */
    suspend fun isServerAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(serverUrl).build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Результат распознавания жеста
 */
data class GestureResult(
    val gesture: String,
    val confidence: Float,
    val classId: Int,
    val success: Boolean,
    val error: String = ""
) {
    companion object {
        fun error(message: String) = GestureResult("", 0f, -1, false, message)
    }
}