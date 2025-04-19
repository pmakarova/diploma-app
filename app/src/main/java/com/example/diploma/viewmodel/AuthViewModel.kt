package com.example.diploma.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.diploma.api.UserApiService
import com.example.diploma.data.AuthResponse
import com.example.diploma.data.User
import com.example.diploma.data.UserSessionManager
import kotlinx.coroutines.launch

/**
 * ViewModel для работы с авторизацией
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val userApiService = UserApiService(application.applicationContext)
    private val sessionManager = UserSessionManager(application.applicationContext)

    private val _loginResult = MutableLiveData<Result<AuthResponse>>()
    val loginResult: LiveData<Result<AuthResponse>> = _loginResult

    private val _registerResult = MutableLiveData<Result<AuthResponse>>()
    val registerResult: LiveData<Result<AuthResponse>> = _registerResult

    private val _logoutResult = MutableLiveData<Result<Boolean>>()
    val logoutResult: LiveData<Result<Boolean>> = _logoutResult

    private val _currentUser = MutableLiveData<User?>()

    init {
        // При создании ViewModel проверяем наличие активной сессии
        if (sessionManager.isLoggedIn()) {
            _currentUser.value = sessionManager.getCurrentUser()
            // Асинхронно обновляем данные пользователя с сервера
            refreshUserData()
        }
    }

    /**
     * Регистрация нового пользователя
     */
    fun register(name: String, email: String, password: String) {
        viewModelScope.launch {
            try {
                val result = userApiService.register(name, email, password)

                if (result.isSuccess) {
                    val authResponse = result.getOrNull()!!
                    // Сохраняем сессию
                    sessionManager.saveUserSession(authResponse)
                    // Загружаем данные пользователя
                    refreshUserData()
                }

                _registerResult.value = result
            } catch (e: Exception) {
                _registerResult.value = Result.failure(e)
            }
        }
    }

    /**
     * Вход пользователя
     */
    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                val result = userApiService.login(email, password)

                if (result.isSuccess) {
                    val authResponse = result.getOrNull()!!
                    // Сохраняем сессию
                    sessionManager.saveUserSession(authResponse)
                    // Загружаем данные пользователя
                    refreshUserData()
                }

                _loginResult.value = result
            } catch (e: Exception) {
                _loginResult.value = Result.failure(e)
            }
        }
    }

    /**
     * Выход пользователя
     */
    fun logout() {
        viewModelScope.launch {
            try {
                val token = sessionManager.getToken()
                if (token != null) {
                    val result = userApiService.logout(token)
                    // Очищаем сессию независимо от результата запроса
                    sessionManager.clearSession()
                    _currentUser.value = null
                    _logoutResult.value = result
                } else {
                    // Если токена нет - просто очищаем сессию
                    sessionManager.clearSession()
                    _currentUser.value = null
                    _logoutResult.value = Result.success(true)
                }
            } catch (e: Exception) {
                // При ошибке всё равно очищаем локальную сессию
                sessionManager.clearSession()
                _currentUser.value = null
                _logoutResult.value = Result.failure(e)
            }
        }
    }

    /**
     * Обновление данных пользователя с сервера
     */
    private fun refreshUserData() {
        viewModelScope.launch {
            val token = sessionManager.getToken()
            if (token != null) {
                val result = userApiService.getCurrentUser(token)
                if (result.isSuccess) {
                    val user = result.getOrNull()!!
                    sessionManager.updateUserInfo(user)
                    _currentUser.value = user
                } else {
                    // Если не удалось получить данные, используем локальные
                    _currentUser.value = sessionManager.getCurrentUser()
                }
            }
        }
    }

    /**
     * Проверка состояния авторизации
     */
    fun isLoggedIn(): Boolean {
        return sessionManager.isLoggedIn()
    }

    /**
     * Получение имени текущего пользователя
     */
    fun getCurrentUserName(): String {
        return sessionManager.getUserName() ?: "Гость"
    }
}