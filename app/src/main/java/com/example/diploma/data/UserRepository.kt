package com.example.diploma.data

/**
 * Класс пользователя
 */
data class User(
    val id: Int,
    val name: String,
    val email: String
)

/**
 * Запрос на регистрацию
 */
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String
)

/**
 * Запрос на вход
 */
data class LoginRequest(
    val email: String,
    val password: String
)

/**
 * Ответ авторизации/регистрации
 */
data class AuthResponse(
    val success: Boolean,
    val token: String? = null,
    val user_id: Int? = null,
    val name: String? = null,
    val expires_at: String? = null,
    val message: String? = null
)