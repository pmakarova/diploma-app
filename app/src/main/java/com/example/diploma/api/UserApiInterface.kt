package com.example.diploma.api

import android.content.Context
import android.util.Log
import com.example.diploma.data.AuthResponse
import com.example.diploma.data.LoginRequest
import com.example.diploma.data.RegisterRequest
import com.example.diploma.data.User
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * Интерфейс для определения API-запросов к серверу пользователей
 */
interface UserApiInterface {
    @POST("api/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/logout")
    suspend fun logout(@Header("Authorization") token: String): Response<Map<String, Boolean>>

    @GET("api/user")
    suspend fun getCurrentUser(@Header("Authorization") token: String): Response<Map<String, Any>>
}

/**
 * Класс для работы с API пользователей
 */
class UserApiService(private val context: Context) {
    companion object {
        private const val TAG = "UserApiService"
    }

    private val serverConfigHelper = ServerConfigHelper(context)
    private var retrofit: Retrofit? = null
    private var apiInterface: UserApiInterface? = null

    init {
        createRetrofit()
    }

    /**
     * Создание Retrofit клиента
     */
    private fun createRetrofit() {
        val baseUrl = serverConfigHelper.getServerUrl()

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiInterface = retrofit?.create(UserApiInterface::class.java)
    }

    /**
     * Регистрация нового пользователя
     */
    suspend fun register(name: String, email: String, password: String): Result<AuthResponse> {
        return try {
            val request = RegisterRequest(name, email, password)
            val response = apiInterface?.register(request)

            if (response?.isSuccessful == true && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response?.errorBody()?.string() ?: "Ошибка регистрации"
                Log.e(TAG, "Ошибка при регистрации: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Вход пользователя
     */
    suspend fun login(email: String, password: String): Result<AuthResponse> {
        return try {
            val request = LoginRequest(email, password)
            val response = apiInterface?.login(request)

            if (response?.isSuccessful == true && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response?.errorBody()?.string() ?: "Ошибка при входе"
                Log.e(TAG, "Ошибка при входе: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Выход пользователя
     */
    suspend fun logout(token: String): Result<Boolean> {
        return try {
            val authHeader = "Bearer $token"
            val response = apiInterface?.logout(authHeader)

            if (response?.isSuccessful == true) {
                Result.success(true)
            } else {
                val errorMsg = response?.errorBody()?.string() ?: "Ошибка выхода"
                Log.e(TAG, "Ошибка выхода: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Получение информации о текущем пользователе
     */
    suspend fun getCurrentUser(token: String): Result<User> {
        return try {
            val authHeader = "Bearer $token"
            val response = apiInterface?.getCurrentUser(authHeader)

            if (response?.isSuccessful == true && response.body() != null) {
                val responseMap = response.body()!!

                if (responseMap["success"] == true && responseMap.containsKey("user")) {
                    @Suppress("UNCHECKED_CAST")
                    val userMap = responseMap["user"] as Map<String, Any>

                    val user = User(
                        id = (userMap["id"] as Number).toInt(),
                        name = userMap["name"] as String,
                        email = userMap["email"] as String
                    )
                    Result.success(user)
                } else {
                    Result.failure(Exception("Пользователь не найден"))
                }
            } else {
                val errorMsg = response?.errorBody()?.string() ?: "Ошибка получения пользователя"
                //Log.e(TAG, "Ошибка получения пользователя: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}