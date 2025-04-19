package com.example.diploma.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Менеджер управления сессией пользователя
 */
class UserSessionManager(private val context: Context) {

    private val TAG = "UserSessionManager"
    private val PREF_NAME = "user_session"
    private val KEY_TOKEN = "token"
    private val KEY_USER_ID = "user_id"
    private val KEY_USER_NAME = "user_name"
    private val KEY_USER_EMAIL = "user_email"
    private val KEY_IS_LOGGED_IN = "is_logged_in"

    // Получаем SharedPreferences с шифрованием (если возможно)
    private val sharedPreferences: SharedPreferences by lazy {
        try {
            // Создаем или получаем мастер-ключ шифрования
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

            // Создаем зашифрованные SharedPreferences
            EncryptedSharedPreferences.create(
                PREF_NAME, // имя файла
                masterKeyAlias, // ключ шифрования
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, // схема шифрования ключей
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM // схема шифрования значений
            )
        } catch (e: Exception) {
            // Если шифрование не доступно, используем обычные SharedPreferences
            Log.e(TAG, "Ошибка при создании EncryptedSharedPreferences: ${e.message}")
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Сохранение сессии пользователя после успешной аутентификации
     */
    fun saveUserSession(authResponse: AuthResponse) {
        val editor = sharedPreferences.edit()

        authResponse.token?.let { editor.putString(KEY_TOKEN, it) }
        authResponse.user_id?.let { editor.putInt(KEY_USER_ID, it) }
        authResponse.name?.let { editor.putString(KEY_USER_NAME, it) }

        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.apply()

        //Log.d(TAG, "Сессия пользователя '${authResponse.name}' сохранена")
    }

    /**
     * Сохранение дополнительной информации о пользователе
     */
    fun updateUserInfo(user: User) {
        val editor = sharedPreferences.edit()
        editor.putInt(KEY_USER_ID, user.id)
        editor.putString(KEY_USER_NAME, user.name)
        editor.putString(KEY_USER_EMAIL, user.email)
        editor.apply()

        Log.d(TAG, "Информация о пользователе обновлена: ${user.name}")
    }

    /**
     * Получение токена
     */
    fun getToken(): String? {
        return sharedPreferences.getString(KEY_TOKEN, null)
    }

    /**
     * Получение имени пользователя
     */
    fun getUserName(): String? {
        return sharedPreferences.getString(KEY_USER_NAME, null)
    }

    /**
     * Получение ID пользователя
     */
    private fun getUserId(): Int {
        return sharedPreferences.getInt(KEY_USER_ID, -1)
    }

    /**
     * Получение email пользователя
     */
    private fun getUserEmail(): String? {
        return sharedPreferences.getString(KEY_USER_EMAIL, null)
    }

    /**
     * Проверка активной сессии
     */
    fun isLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    /**
     * Получение информации о текущем пользователе
     */
    fun getCurrentUser(): User? {
        val id = getUserId()
        val name = getUserName()
        val email = getUserEmail()

        if (id == -1 || name == null) {
            return null
        }

        return User(
            id = id,
            name = name,
            email = email ?: ""
        )
    }

    /**
     * Очистка сессии при выходе
     */
    fun clearSession() {
        val editor = sharedPreferences.edit()
        editor.clear()
        editor.apply()

        Log.d(TAG, "Сессия пользователя очищена")
    }
}