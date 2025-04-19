package com.example.diploma.screen

import android.util.Log
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
fun SignUpScreen(navController: NavController) {
    val context = LocalContext.current
    val authViewModel: AuthViewModel = viewModel()

    // Для отслеживания результата регистрации
    val registerResult by authViewModel.registerResult.observeAsState()

    // Состояние полей ввода
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // Обработка результата регистрации
    LaunchedEffect(registerResult) {
        registerResult?.let { result ->
            isLoading = false

            if (result.isSuccess) {
                // Успешная регистрация - переходим на CameraScreen
                navController.navigate(Routes.cameraScreen) {
                    popUpTo(Routes.startScreen) { inclusive = true }
                }
                // Приветствие при регистрации
                Toast.makeText(
                    context,
                    "Регистрация успешна! Добро пожаловать, ${authViewModel.getCurrentUserName()}",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                // Обработка ошибки
                showError = true
                errorMessage = result.exceptionOrNull()?.message ?: "Ошибка регистрации"
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
                .height(56.dp)
            ) {

            // Стрелка назад
            IconButton(
                onClick = {
                    navController.popBackStack()
                },
                modifier = Modifier.align(Alignment.CenterStart),
                enabled = !isLoading
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.arrow_back_24px),
                    contentDescription = "Return",
                    modifier = Modifier.size(30.dp)
                )
            }

            Text(
                text = "Регистрация",
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )

        }

        Spacer(modifier = Modifier.weight(0.25f))

        // Поле для имени
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                showError = false
            },
            label = {
                Text(
                    text = "Имя",
                )
            },
            modifier = Modifier
                .fillMaxWidth(0.85f),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.tertiary,
            ),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Поле для почты
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                showError = false
            },
            label = {
                Text(
                    text = "Почта",
                )
            },
            modifier = Modifier
                .fillMaxWidth(0.85f),
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
                Text(
                    text = "Пароль",
                )
            },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth(0.85f),
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

        Spacer(modifier = Modifier.weight(0.25f))

        // Кнопка регистрации
        Button(
            onClick = {
                // Валидация ввода
                if (name.isBlank() || email.isBlank() || password.isBlank()) {
                    showError = true
                    errorMessage = "Пожалуйста, заполните все поля"
                    return@Button
                }

                // Проверка email
                if (!email.contains("@") || !email.contains(".")) {
                    showError = true
                    errorMessage = "Введите корректный адрес электронной почты"
                    return@Button
                }

                // Проверка пароля
                if (password.length < 6) {
                    showError = true
                    errorMessage = "Пароль должен содержать не менее 6 символов"
                    return@Button
                }

                // Запуск регистрации
                isLoading = true
                //Log.i("Credential", "Регистрация: $email")
                authViewModel.register(name, email, password)
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
                    text= "Зарегистрироваться",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.padding(5.dp))

        // Кнопка для входа в аккаунт
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text= "Уже есть аккаунт?",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(
                onClick = {
                    navController.navigate(Routes.loginScreen)
                },
                enabled = !isLoading
            ) {
                Text(
                    text= "Войти",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.2f))
    }
}