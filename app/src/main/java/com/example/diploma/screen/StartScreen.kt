package com.example.diploma.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.diploma.R
import com.example.diploma.api.ServerConfigHelper
import com.example.diploma.dialog.ServerSettingsDialog
import com.example.diploma.api.SignLanguageApiService
import com.example.diploma.navigation.Routes
import com.example.diploma.viewmodel.AuthViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StartScreen(navController: NavController) {
    val authViewModel: AuthViewModel = viewModel()
    val context = LocalContext.current

    // Состояния для UI
    val serverConnected = remember { mutableStateOf(false) }
    val showServerDialog = remember { mutableStateOf(false) }

    // API и конфигурация
    val serverConfigHelper = remember { ServerConfigHelper(context) }
    val apiService = remember { SignLanguageApiService(serverConfigHelper.getServerUrl()) }

    // Корутина для периодической проверки доступности сервера
    val coroutineScope = rememberCoroutineScope()

    // Если пользователь уже авторизован, то сразу перенаправляется на CameraScreen
    LaunchedEffect(Unit) {
        if (authViewModel.isLoggedIn()) {
            navController.navigate(Routes.cameraScreen) {
                popUpTo(Routes.startScreen) { inclusive = true }
            }
        }
    }

    // Проверка доступности сервера с периодическим обновлением
    LaunchedEffect(Unit) {
        while (true) {
            checkServerConnection(apiService, coroutineScope, serverConnected)
            delay(5000) // Проверка каждые 5 секунд
        }
    }

    // Диалог настроек сервера
    if (showServerDialog.value) {
        ServerSettingsDialog(
            serverConfigHelper = serverConfigHelper,
            onDismiss = { showServerDialog.value = false },
            onSave = { ip, port ->
                serverConfigHelper.setServerIp(ip)
                serverConfigHelper.setServerPort(port)
                apiService.updateServerUrl(serverConfigHelper.getServerUrl())
                checkServerConnection(apiService, coroutineScope, serverConnected)
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Верхняя панель с индикатором и настройками сервера
        StartScreenTopBar(
            serverConnected = serverConnected.value,
            onSettingsClick = { showServerDialog.value = true }
        )

        // Основной контент экрана
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.15f))

            Image(
                painter = painterResource(id = R.drawable.photo),
                contentDescription = "Sign Up Image",
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .aspectRatio(0.8f, matchHeightConstraintsFirst = true)
            )

            Spacer(modifier = Modifier.weight(0.025f))

            // Кнопка регистрации
            Button(
                onClick = {
                    navController.navigate(Routes.signUpScreen)
                },
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(80.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = "Зарегистрироваться",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.padding(5.dp))

            // Ссылка для входа в аккаунт
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Уже есть аккаунт?",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(
                    onClick = {
                        navController.navigate(Routes.loginScreen)
                    },
                ) {
                    Text(
                        text = "Войти",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))
        }
    }
}

/**
 * Верхняя панель для StartScreen с индикатором состояния сервера и кнопкой настроек
 */
@Composable
fun StartScreenTopBar(
    serverConnected: Boolean,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(120.dp).padding(15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Индикатор состояния сервера
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (serverConnected) Color.Green else Color.Red)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (serverConnected) "Сервер подключен" else "Сервер недоступен",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        // Кнопка настроек
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.DarkGray.copy(alpha = 0.6f))
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Настройки сервера",
                tint = Color.White
            )
        }
    }
}

/**
 * Проверка доступности сервера
 */
private fun checkServerConnection(
    apiService: SignLanguageApiService,
    coroutineScope: CoroutineScope,
    serverConnected: androidx.compose.runtime.MutableState<Boolean>
) {
    coroutineScope.launch(Dispatchers.IO) {
        try {
            val isAvailable = apiService.isServerAvailable()
            withContext(Dispatchers.Main) {
                serverConnected.value = isAvailable
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                serverConnected.value = false
            }
        }
    }
}