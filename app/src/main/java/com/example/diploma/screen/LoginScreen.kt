package com.example.diploma.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.diploma.R
import com.example.diploma.navigation.Routes
import com.example.diploma.viewmodel.AuthViewModel

@Composable
fun LoginScreen(navController: NavController) {
    val context = LocalContext.current

    val authViewModel: AuthViewModel = viewModel()

    // Состояние для отслеживания результата входа
    val loginResult by authViewModel.loginResult.observeAsState()

    // Состояние полей ввода
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // Обработка результата входа
    LaunchedEffect(loginResult) {
        loginResult?.let { result ->
            isLoading = false

            if (result.isSuccess) {
                // Успешный вход - переходим на CameraScreen
                navController.navigate(Routes.cameraScreen) {
                    popUpTo(Routes.loginScreen) { inclusive = true }
                }
                // Приветствие при входе
                Toast.makeText(
                    context,
                    "Добро пожаловать, ${authViewModel.getCurrentUserName()}",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                // Обработка ошибки
                showError = true
                errorMessage = result.exceptionOrNull()?.message ?: "Ошибка входа"
            }
        }
    }

    // Проверка, вошел ли пользователь уже в систему
    LaunchedEffect(Unit) {
        if (authViewModel.isLoggedIn()) {
            // Если пользователь уже вошел в систему, перенаправляем на CameraScreen
            navController.navigate(Routes.cameraScreen) {
                popUpTo(Routes.loginScreen) { inclusive = true }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Отступ сверху
        Spacer(modifier = Modifier.height(70.dp))

        // Верхняя панель
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(56.dp),
        ) {

            // Стрелка назад
            IconButton(
                onClick = {
                    navController.popBackStack()
                },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.arrow_back_24px),
                    contentDescription = "Return",
                    modifier = Modifier
                        .size(30.dp)
                )
            }

            Text(
                text = "Войти",
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Spacer(modifier = Modifier.weight(0.25f))

        // Поле для почты
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                showError = false
            },
            label = {
                Text(text = "Почта")
            },
            modifier = Modifier.fillMaxWidth(0.85f),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.tertiary,
            ),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Поле для пароля
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                showError = false
            },
            label = {
                Text(text = "Пароль")
            },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(0.85f),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.tertiary,
            ),
            enabled = !isLoading
        )

        // Сообщение об ошибке
        if (showError) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(0.8f),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.weight(0.35f))

        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    showError = true
                    errorMessage = "Введите почту и пароль"
                    return@Button
                }

                // Запуск входа
                isLoading = true
                authViewModel.login(email, password)
            },
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(80.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = "Войти",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.padding(5.dp))

        // Кнопка для регистрации
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Еще нет аккаунта?",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(
                onClick = {
                    navController.navigate(Routes.signUpScreen)
                },
                enabled = !isLoading
            ) {
                Text(
                    text = "Регистрация",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.2f))
    }
}