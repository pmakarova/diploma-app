package com.example.diploma.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * API-сервис для связи с сервером
 */
class SignLanguageApiService(
    private var serverUrl: String
) {
    private var lastSendTime = 0L
    private val MIN_SEND_INTERVAL = 50L
    private val TAG = "ApiService"

    // HTTP-клиент
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(5, 30, TimeUnit.SECONDS))
        .retryOnConnectionFailure(true) // для восстановления соединения
        .build()

    /**
     * Обновление URL сервера
     */
    fun updateServerUrl(newUrl: String) {
        this.serverUrl = newUrl
    }

    /**
     * Отправляет данные признаков на сервер
     * @param features - список из 10 наборов по 126 признаков (всего 1260 значений)
     * @param maxRetries - количество попыток отправки при сбоях
     * @return True если отправлено успешно
     */
    suspend fun sendFeatures(features: List<Float>, maxRetries: Int = 3): Boolean = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()

        // Проверка частоты отправления запросов
        if (currentTime - lastSendTime < MIN_SEND_INTERVAL) {
            return@withContext true
        }

        // Проверка содержимого признаков (features)
        if (features.size != 126 && features.size != 1260) {
            //Log.e(TAG, "Некорректное количество feature: ${features.size}, ожидалось 126 или 1260")
            return@withContext false
        }

        // Проверка наличия пустых landmarks (все нули)
        val nonZeroValues = features.count { it != 0f }
        if (nonZeroValues < features.size * 0.1) {  // если менее 10% ненулевые
            //Log.e(TAG, "Слишком мало ненулевых значений в features: $nonZeroValues из ${features.size}")
            return@withContext false
        }

        // Данные для отправки
        val featuresToSend = features

        // Проверка разнообразия кадров
        if (features.size == 1260) {
            val frameSize = 126
            val numFrames = 10
            var totalDifference = 0.0f

            for (i in 1 until numFrames) {
                var frameDiff = 0.0f
                for (j in 0 until frameSize) {
                    val idx1 = (i-1) * frameSize + j
                    val idx2 = i * frameSize + j
                    if (idx1 < features.size && idx2 < features.size) {
                        frameDiff += abs(features[idx1] - features[idx2])
                    }
                }

                totalDifference += frameDiff / frameSize
                //Log.d(TAG, "Разница между кадрами ${i-1} и $i: ${frameDiff / frameSize}")
            }
        }

        var retries = 0
        var lastError: Exception? = null

        while (retries < maxRetries) {
            try {
                val jsonObject = JSONObject()
                val jsonArray = JSONArray()

                for (feature in featuresToSend) {
                    jsonArray.put(feature)
                }

                jsonObject.put("features", jsonArray)
                jsonObject.put("timestamp", currentTime.toString())

                Log.d(TAG, "Отправка ${featuresToSend.size} features на сервер, $nonZeroValues ненулевых значений")

                val requestBodyJson = jsonObject.toString()
                val requestBody = requestBodyJson.toRequestBody("application/json".toMediaType())

                // Запрос
                val request = Request.Builder()
                    .url("$serverUrl/features")
                    .post(requestBody)
                    .build()

                // Выполнить запрос
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        lastSendTime = currentTime // время последней отправки
                        return@withContext true
                    } else if (response.code == 503) {
                        // Если сервер перегружен (очередь заполнена)
                        Log.w(TAG, "Сервер перегружен, повторное подключение...")
                        delay(300) // задержка перед следующей попыткой
                        retries++
                    } else {
                        val errorBody = response.body?.string() ?: "Нет тела ответа"
                        Log.e(TAG, "Ошибка сервера: ${response.code}, body: $errorBody")
                        lastError = Exception("Ошибка сервера: ${response.code}")
                        retries++ // еще попытка на выполнение запроса
                        delay(300)
                    }
                }
            } catch (e: ConnectException) {
                //Log.e(TAG, "Ошибка соединения при отправке features: ${e.message}")
                lastError = e
                retries++
                if (retries >= maxRetries) {
                    return@withContext false
                }
                delay(300)
            } catch (e: SocketTimeoutException) {
                //Log.e(TAG, "Отправка features по тайм-ауту : ${e.message}")
                lastError = e
                retries++
                if (retries >= maxRetries) {
                    return@withContext false
                }
                delay(300)
            } catch (e: Exception) {
                //Log.e(TAG, "Исключительная ситуация при отправке features: ${e.message}")
                e.printStackTrace()
                lastError = e
                retries++
                if (retries >= maxRetries) {
                    return@withContext false
                }
                delay(300)
            }
        }

        if (lastError != null) {
            Log.e(TAG, "Не удалось отправить features после $maxRetries попыток. Последняя ошибка: ${lastError!!.message}")
        }
        return@withContext false
    }

    /**
     * Получает результат распознавания с сервера
     * @return TranslationResponse с распознанным жестом
     */
    suspend fun getTranslation(): TranslationResponse = withContext(Dispatchers.IO) {
        try {
            // Запрос
            val request = Request.Builder()
                .url("$serverUrl/translation")
                .get()
                .build()

            // Выполнить запрос и правильно закрыть ответ
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: "{}"
                    Log.d(TAG, "Распознан ответ: $responseBody")

                    try {
                        val jsonObject = JSONObject(responseBody)
                        val gesture = jsonObject.optString("gesture", "")
                        val confidence = jsonObject.optDouble("confidence", 0.0).toFloat()
                        val classId = jsonObject.optInt("class_id", -1)
                        val timestamp = jsonObject.optLong("server_timestamp_ms", System.currentTimeMillis())

                        // Жест обнаружен
                        if (gesture.isNotEmpty()) {
                            Log.d(TAG, "Обнаружен жест: '$gesture' с уверенностью $confidence")
                        }

                        return@withContext TranslationResponse(gesture, confidence, classId, timestamp)
                    } catch (e: Exception) {
                        //Log.e(TAG, "Ошибка анализа ответа JSON: ${e.message}, body: $responseBody")
                        return@withContext TranslationResponse("", 0.0f, -1, System.currentTimeMillis())
                    }
                } else {
                    //Log.e(TAG, "Ошибка при получении перевода: ${response.code}")
                    return@withContext TranslationResponse("", 0.0f, -1, System.currentTimeMillis())
                }
            } // Автоматически закрывает тело ответа
        } catch (e: ConnectException) {
            //Log.e(TAG, "Ошибка соединения при получении перевода: ${e.message}")
            return@withContext TranslationResponse("", 0.0f, -1, System.currentTimeMillis())
        } catch (e: SocketTimeoutException) {
            //Log.e(TAG, "Тайм-аут получения перевода: ${e.message}")
            return@withContext TranslationResponse("", 0.0f, -1, System.currentTimeMillis())
        } catch (e: Exception) {
            //Log.e(TAG, "Исключение получения перевода: ${e.message}")
            e.printStackTrace()
            return@withContext TranslationResponse("", 0.0f, -1, System.currentTimeMillis())
        }
    }

    /**
     * Проверка доступности сервера
     * @return True, если сервер доступен
     */
    suspend fun isServerAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Проверка доступности сервера на $serverUrl")
            val request = Request.Builder()
                .url(serverUrl)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val success = response.isSuccessful
                Log.d(TAG, "Проверка доступности сервера: $success")
                return@withContext success
            }
        } catch (e: ConnectException) {
            Log.e(TAG, "Ошибка соединения: ${e.message}")
            return@withContext false
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Тайм-аут сервера: ${e.message}")
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Исключение проверки доступности сервера: ${e.message}")
            e.printStackTrace()
            return@withContext false
        }
    }
}

// Класс данных для ответа перевода
data class TranslationResponse(
    val gesture: String,
    val confidence: Float,
    val class_id: Int,
    val server_timestamp_ms: Long
)