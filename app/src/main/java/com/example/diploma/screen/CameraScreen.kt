package com.example.diploma.screen

import android.Manifest
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.diploma.dialog.HelpDialog
import com.example.diploma.LogoutDialog
import com.example.diploma.api.ServerConfigHelper
import com.example.diploma.api.ServerSettingsDialog
import com.example.diploma.api.SignLanguageApiService
import com.example.diploma.camera.CameraHelper
import com.example.diploma.hands.HandLandmarkHelper
import com.example.diploma.OverlayView
import com.example.diploma.navigation.Routes
import com.example.diploma.viewmodel.AuthViewModel
import com.example.diploma.viewmodel.CameraViewModel
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.*
import kotlin.math.abs

/**
 * Основной экран работы с камерой и распознаванием жестов.
 * Здесь происходит захват видео с камеры, определение положения рук
 * и отправка данных на сервер распознавания.
 */
@Composable
fun CameraScreen(
    navController: NavHostController,
    viewModel: CameraViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Область для запуска корутин
    val coroutineScope = rememberCoroutineScope()

    // AuthViewModel для работы с сессией пользователя
    val authViewModel: AuthViewModel = viewModel()
    val logoutResult by authViewModel.logoutResult.observeAsState()

    // Переменные состояния
    val cameraPermissionGranted = remember { mutableStateOf(false) }
    val showServerDialog = remember { mutableStateOf(false) }
    val showHelpDialog = remember { mutableStateOf(false) }
    val showLogoutDialog = remember { mutableStateOf(false) }
    val isLoggingOut = remember { mutableStateOf(false) }

    val detectedGesture = remember { mutableStateOf("") }
    val gestureConfidence = remember { mutableFloatStateOf(0f) }

    val serverConnected = remember { mutableStateOf(false) }
    val cameraFacing = remember { mutableIntStateOf(viewModel.cameraFacing.intValue) } // Задняя камера по умолчанию

    // Результат распознавания жестов рук
    val handLandmarkResult = remember { mutableStateOf<HandLandmarkerResult?>(null) }
    val inputImageWidth = remember { mutableIntStateOf(720) }
    val inputImageHeight = remember { mutableIntStateOf(1280) }

    // API и конфигурация
    val serverConfigHelper = remember { ServerConfigHelper(context) }
    val apiService = remember { SignLanguageApiService(serverConfigHelper.getServerUrl()) }

    // Помощник для работы с камерой
    val cameraHelper = remember { CameraHelper(context) }

    // Для доступа к OverlayView (визуализация положения рук)
    val overlayViewState = remember { mutableStateOf<OverlayView?>(null) }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    // Обработка результата выхода из системы
    LaunchedEffect(logoutResult) {
        logoutResult?.let { result ->
            isLoggingOut.value = false
            if (result.isSuccess) {
                Toast.makeText(
                    context,
                    "Вы вышли из системы",
                    Toast.LENGTH_SHORT
                ).show()

                // Переход на StartScreen
                navController.navigate(Routes.startScreen) {
                    popUpTo(Routes.cameraScreen) { inclusive = true }
                }
            } else {
                // Если произошла ошибка при выходе
                Toast.makeText(
                    context,
                    "Ошибка при выходе: ${result.exceptionOrNull()?.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // MediaPipe HandLandmark
    val handLandmarkHelper = remember {
        HandLandmarkHelper(
            context = context,
            runningMode = RunningMode.LIVE_STREAM, // режим работы с потоковым видео
            minHandDetectionConfidence = 0.5f,
            minHandTrackingConfidence = 0.5f,
            minHandPresenceConfidence = 0.5f,
            maxNumHands = 2,
            currentDelegate = viewModel.currentDelegate,
            handLandmarkHelperListener = object : HandLandmarkHelper.LandmarkListener {
                override fun onError(error: String, errorCode: Int) {
                    Log.e("CameraScreen", "Ошибка распознавания рук: $error")
                    coroutineScope.launch(Dispatchers.Main) {
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    }
                }

                // Обработка результатов распознавания
                override fun onResults(resultBundle: HandLandmarkHelper.ResultBundle) {
                    // Обновление UI с результатами распознавания рук
                    if (resultBundle.results.isNotEmpty()) {
                        handLandmarkResult.value = resultBundle.results.first()
                        inputImageWidth.intValue = resultBundle.inputImageWidth
                        inputImageHeight.intValue = resultBundle.inputImageHeight

                        // Обновление OverlayView с результатами
                        overlayViewState.value?.setResults(
                            resultBundle.results.first(),
                            resultBundle.inputImageHeight,
                            resultBundle.inputImageWidth,
                            cameraFacing.intValue == CameraSelector.LENS_FACING_FRONT
                        )

                        // Обработка координат жестов и отправка на сервер
                        processHandLandmarks(
                            resultBundle = resultBundle,
                            apiService = apiService,
                            coroutineScope = coroutineScope,
                            detectedGesture = detectedGesture,
                            gestureConfidence = gestureConfidence,
                            cameraFacing = cameraFacing.intValue
                        )
                    }
                }
            }
        )
    }

    // Запрос разрешения на использование камеры
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        cameraPermissionGranted.value = isGranted
        if (isGranted) {
            // Проверка подключения к серверу
            checkServerConnection(apiService, coroutineScope, serverConnected)
            // Показ диалога помощи при первом запуске
            showHelpDialog.value = true
        }
    }

    // Запрос разрешения на использование камеры при запуске экрана
    LaunchedEffect(Unit) {
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Периодическая проверка доступности сервера
    LaunchedEffect(Unit) {
        while (true) {
            checkServerConnection(apiService, coroutineScope, serverConnected)
            delay(5000) // Every 5 seconds
        }
    }

    // UI
    Box(modifier = Modifier.fillMaxSize()) {
        if (cameraPermissionGranted.value) {
            // Превью камеры с наложением визуализации жестов рук
            Box(modifier = Modifier.fillMaxSize()) {
                // Превью камеры
                AndroidView(
                    factory = {
                        val preview = PreviewView(context).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }

                        // Ссылка на PreviewView
                        previewView = preview

                        // Запуск камеры при создании виджета
                        coroutineScope.launch {
                            val success = cameraHelper.startCamera(
                                lifecycleOwner,
                                preview.surfaceProvider,
                                cameraFacing.intValue
                            )

                            if (success) {
                                Log.d("CameraScreen", "Камера успешно запущена")

                                // Установка обработчика кадров
                                cameraHelper.setFrameListener { img ->
                                    processCameraFrame(
                                        handLandmarkHelper = handLandmarkHelper,
                                        cameraFacing = cameraFacing.intValue,
                                        imageProxy = img
                                    )
                                }
                            } else {
                                Log.e("CameraScreen", "Не удалось запустить камеру")
                                Toast.makeText(context, "Не удалось запустить камеру", Toast.LENGTH_SHORT).show()
                            }
                        }
                        preview
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Наложение визуализации положения рук
                AndroidView(
                    factory = { context ->
                        OverlayView(context, null).also {
                            overlayViewState.value = it
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    // Обновление визуализации при изменении состояния
                    update = { view ->
                        // Результаты распознавания не null и размеры изображения корректны
                        if (handLandmarkResult.value != null &&
                            inputImageWidth.intValue > 0 &&
                            inputImageHeight.intValue > 0) {

                            // Используется ли фронтальная камера
                            val isFrontCamera = cameraFacing.intValue == CameraSelector.LENS_FACING_FRONT

                            view.setResults(
                                handLandmarkResult.value!!,
                                inputImageHeight.intValue,
                                inputImageWidth.intValue,
                                isFrontCamera
                            )
                        }
                    }
                )
            }

            // Верхняя панель с индикатором подключения к серверу и кнопками
            TopAppBar(
                serverConnected = serverConnected.value,
                onSettingsClick = { showServerDialog.value = true },
                onLogoutClick = { showLogoutDialog.value = true },
            )

            // Карточка с распознанным жестом
            if (detectedGesture.value.isNotEmpty()) {
                DetectedGestureCard(
                    gesture = detectedGesture.value,
                    confidence = gestureConfidence.floatValue,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp)
                )
            }

            // Кнопки управления внизу экрана
            BottomControls(
                onSwitchCamera = {
                    val currentPreviewView = previewView
                    if (currentPreviewView == null) {
                        Toast.makeText(context, "Не удалось получить доступ к камере", Toast.LENGTH_SHORT).show()
                        return@BottomControls
                    }

                    //Log.d("CameraScreen", "Кнопка переключения камеры нажата")

                    Toast.makeText(context, "Переключение камеры...", Toast.LENGTH_SHORT).show()

                    // Запускаем обработку в корутине
                    coroutineScope.launch {
                        try {
                            // Очищаем overlay перед переключением камеры
                            overlayViewState.value?.clear()

                            // Очищаем историю кадров при переключении камеры
                            clearFrameHistory()

                            // Переключаем камеру и получаем результат
                            val success = cameraHelper.toggleCamera(
                                lifecycleOwner,
                                currentPreviewView.surfaceProvider
                            )

                            // Обновляем состояние, если переключение успешно
                            if (success) {
                                val newFacing = cameraHelper.getCurrentCameraFacing()
                                cameraFacing.intValue = newFacing
                                viewModel.cameraFacing.intValue = newFacing

                                // Очищаем результаты распознавания для нового состояния
                                handLandmarkResult.value = null

                                // Устанавливаем обработчик кадров заново
                                cameraHelper.setFrameListener { img ->
                                    processCameraFrame(
                                        handLandmarkHelper = handLandmarkHelper,
                                        cameraFacing = cameraFacing.intValue,
                                        imageProxy = img
                                    )
                                }

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "Камера переключена",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "Не удалось переключить камеру",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("CameraScreen", "Ошибка при переключении камеры: ${e.message}")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Ошибка: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                },
                onHelpClick = {
                    showHelpDialog.value = true
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
            )

        } else {
            // Экран, если разрешение на камеру не предоставлено
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Для работы приложения необходим доступ к камере",
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                }) {
                    Text("Предоставить доступ")
                }
            }
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

    // Диалог помощи
    if (showHelpDialog.value) {
        HelpDialog(
            onDismiss = { showHelpDialog.value = false },
            viewModel = authViewModel // ViewModel для получения имени пользователя
        )
    }

    // Диалог подтверждения выхода
    if (showLogoutDialog.value) {
        LogoutDialog(
            onDismiss = { showLogoutDialog.value = false },
            onConfirm = {
                // Выполняем через ViewModel
                isLoggingOut.value = true
                authViewModel.logout()
                // Закрываем диалог
                showLogoutDialog.value = false
            }
        )
    }

    // Очистка ресурсов при закрытии экрана
    DisposableEffect(Unit) {
        onDispose {
            handLandmarkHelper.clearHandLandmark()
            cameraHelper.shutdown()
        }
    }
}

/**
 * Верхняя панель с индикатором подключения к серверу и кнопками управления
 * @param serverConnected Статус подключения к серверу
 * @param onSettingsClick Обработчик нажатия на кнопку настроек
 * @param onLogoutClick Обработчик нажатия на кнопку выхода
 */
@Composable
fun TopAppBar(
    serverConnected: Boolean,
    onSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {

    Row(
        modifier = Modifier.fillMaxWidth().height(110.dp).padding(15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {

        // Индикатор статуса сервера
        Row(
            verticalAlignment = Alignment.CenterVertically,
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
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        // Кнопки действий
        Row {
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

            Spacer(modifier = Modifier.width(16.dp))

            // Кнопка выхода
            IconButton(
                onClick = onLogoutClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray.copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Выйти из аккаунта",
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * Карточка с информацией о распознанном жесте
 * @param gesture Текст распознанного жеста
 * @param confidence Уверенность распознавания (0-1)
 * @param modifier Модификатор для настройки внешнего вида
 */
@Composable
fun DetectedGestureCard(
    gesture: String,
    confidence: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(horizontal = 32.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Распознанный жест",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = gesture,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Уверенность: ${(confidence * 100).toInt()}%",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Панель с кнопками управления внизу экрана
 * @param onSwitchCamera Обработчик переключения камеры
 * @param onHelpClick Обработчик открытия справки
 * @param modifier Модификатор для настройки внешнего вида
 */
@Composable
fun BottomControls(
    onSwitchCamera: () -> Unit,
    onHelpClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Кнопка переключения камеры
        FloatingActionButton(
            onClick = onSwitchCamera,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(70.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Переключить камеру",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            )
        }

        // Кнопка справки
        FloatingActionButton(
            onClick = onHelpClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(70.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Справка",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

/**
 * Обработка кадра с камеры и распознавание жестов рук
 * @param imageProxy Кадр с камеры
 * @param handLandmarkHelper Помощник распознавания жестов рук
 * @param cameraFacing Текущее направление камеры (фронтальная/задняя)
 */
private fun processCameraFrame(
    imageProxy: ImageProxy,
    handLandmarkHelper: HandLandmarkHelper,
    cameraFacing: Int
) {
    try {
        // Используется ли фронтальная камера
        val isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT

        // Обрабатываем кадр с помощью HandLandmarkHelper
        // Результат будет передан через handLandmarkHelperListener
        handLandmarkHelper.detectLiveStream(imageProxy, isFrontCamera)
    } catch (e: Exception) {
        Log.e("CameraScreen", "Ошибка обработки кадра: ${e.message}")
        e.printStackTrace()
        imageProxy.close()
    }
}

/**
 * Обработка результатов распознавания жестов рук и отправка на сервер.
 * @param resultBundle Пакет результатов распознавания жестов рук
 * @param apiService Сервис API для взаимодействия с сервером
 * @param coroutineScope Корутин для асинхронной обработки
 * @param detectedGesture Переменная для хранения распознанного жеста
 * @param gestureConfidence Переменная для хранения уверенности распознавания
 * @param cameraFacing Текущее направление камеры (фронтальная/задняя)
 */
private fun processHandLandmarks(
    resultBundle: HandLandmarkHelper.ResultBundle,
    apiService: SignLanguageApiService,
    coroutineScope: CoroutineScope,
    detectedGesture: MutableState<String>,
    gestureConfidence: MutableState<Float>,
    cameraFacing: Int = CameraSelector.LENS_FACING_BACK
) {
    // Если жесты рук не обнаружены, выходим
    if (resultBundle.results.isEmpty() || resultBundle.results.first().landmarks().isEmpty()) {
        Log.d("CameraScreen", "No hand landmarks detected")
        return
    }

    // Данные о жестах рук
    val handLandmarkResult = resultBundle.results.first()

    // Логируем количество обнаруженных рук
    val handsCount = handLandmarkResult.landmarks().size
    Log.d("CameraScreen", "Обнаружено $handsCount рук(и)")

    // Используется ли фронтальная камера
    val isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT

    // Конвертируем координаты жестов в формат для API, учитывая ориентацию камеры
    val features = extractFeaturesFromLandmarks(handLandmarkResult, isFrontCamera)

    // Пропуск, если список координат пуст или содержит только нули
    if (features.isEmpty() || features.all { it == 0f }) {
        Log.d("CameraScreen", "Пустой список координат, пропуск")
        return
    }

    // Если это набор из 126 признаков (один кадр), добавляем его в историю
    if (features.size == 126) {
        // Добавляем текущий кадр в историю
        FrameHistory.addFrame(features)

        // Проверяем, готова ли последовательность для отправки
        val isReady = FrameHistory.isSequenceReady()
        if (isReady) {
            Log.d("CameraScreen", "Последовательность кадров готова для отправки")
        }
    }

    // Отправляем координаты на сервер
    coroutineScope.launch(Dispatchers.IO) {
        try {
            // Получаем последовательность кадров, если она готова
            val sequenceToSend = getFrameSequence()

            if (sequenceToSend.isNotEmpty()) {
                Log.d("CameraScreen", "Отправка последовательности из ${sequenceToSend.size / 126} кадров")
                val success = apiService.sendFeatures(sequenceToSend)

                if (success) {
                    // Получаем результат распознавания с сервера
                    val translation = apiService.getTranslation()

                    // Update UI with detected gesture
                    if (translation.gesture.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            detectedGesture.value = translation.gesture
                            gestureConfidence.value = translation.confidence
                            Log.d("CameraScreen", "Распознан жест: ${detectedGesture.value}, уверенность: ${gestureConfidence.value}")

                            // Очистка жеста после задержки
                            launch {
                                delay(3000)
                                if (detectedGesture.value == translation.gesture) {
                                    detectedGesture.value = ""
                                    gestureConfidence.value = 0f
                                }
                            }
                        }
                    }
                }
            } else {
                if (features.size == 1260) {
                    //Log.d("CameraScreen", "Отправка набора из 1260 признаков (одиночная последовательность)")
                    val success = apiService.sendFeatures(features)

                    if (success) {
                        // Получаем результат распознавания с сервера
                        val translation = apiService.getTranslation()

                        // Обновляем UI с распознанным жестом
                        if (translation.gesture.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                detectedGesture.value = translation.gesture
                                gestureConfidence.value = translation.confidence
                                Log.d("CameraScreen", "Распознан жест: ${detectedGesture.value}, уверенность: ${gestureConfidence.value}")

                                // Очистка жеста после задержки
                                launch {
                                    delay(3000)
                                    if (detectedGesture.value == translation.gesture) {
                                        detectedGesture.value = ""
                                        gestureConfidence.value = 0f
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CameraScreen", "Ошибка отправки координат на сервер: ${e.message}")
            e.printStackTrace()
        }
    }
}

/**
 * Объект для хранения и обработки истории кадров.
 * Используется для накопления последовательностей кадров перед отправкой на сервер.
 * Добавляет вариативность и фильтрацию кадров для улучшения качества распознавания.
 */
private object FrameHistory {
    private const val MAX_FRAMES = 10 // максимальное количество кадров в последовательности
    private val frameHistory = mutableListOf<List<Float>>() // список сохраненных кадров
    private val lock = Any() // объект блокировки для безопасного доступа из разных потоков

    // Увеличенный интервал времени для сбора последовательности
    private const val COLLECTION_TIMEOUT = 5000L // таймаут сбора кадров (5 секунд)
    private const val MIN_FRAME_DIFFERENCE = 0.05f // минимальная разница между кадрами для учета изменений

    private var lastFrameTime = System.currentTimeMillis() // время последнего добавленного кадра
    private var sequenceStartTime = System.currentTimeMillis() // время начала сбора последовательности

    /**
     * Вычисляет различие между двумя кадрами.
     * Учитывает ненулевые координаты с большим весом для точного определения движения.
     * @param frame1 Первый кадр для сравнения
     * @param frame2 Второй кадр для сравнения
     * @return Значение различия (0-1)
     */
    private fun calculateFrameDifference(frame1: List<Float>, frame2: List<Float>): Float {
        if (frame1.size != frame2.size) return 1.0f

        var totalDiff = 0.0f
        var nonZeroCount = 0

        // Проверяем все координаты, уделяя особое внимание ненулевым
        for (i in frame1.indices) {
            val diff = abs(frame1[i] - frame2[i])

            // Если оба значения не равны нулю, считаем разницу с большим весом
            if (frame1[i] != 0f || frame2[i] != 0f) {
                totalDiff += diff * 1.5f // Увеличиваем вес для ненулевых значений
                nonZeroCount++
            } else {
                totalDiff += diff
            }
        }

        // Если нет ненулевых координат, это странно, считаем различие нулевым
        if (nonZeroCount == 0) return 0.0f

        // Вычисляем среднее различие по ненулевым координатам
        return totalDiff / (nonZeroCount + 1)
    }

    /**
     * Добавляет случайный шум к кадру для увеличения вариативности.
     * Это помогает избежать проблем с идентичными кадрами.
     * @param frame Исходный кадр
     * @return Кадр с шумом
     */
    private fun addNoiseToFrame(frame: List<Float>): List<Float> {
        val noisyFrame = mutableListOf<Float>()

        for (value in frame) {
            // Добавляем шум только к ненулевым значениям, чтобы не создавать ложные точки
            if (value != 0f) {
                // Добавляем случайный шум до 2% от значения
                val noise = value * (Math.random() * 0.04f - 0.02f).toFloat()
                noisyFrame.add(value + noise)
            } else {
                noisyFrame.add(0f)
            }
        }
        return noisyFrame
    }

    /**
     * Добавляет новый кадр в историю.
     * Учитывает различия между кадрами и время для обеспечения вариативности данных.
     * @param frame Кадр для добавления (список 126 координат)
     */
    fun addFrame(frame: List<Float>) {
        synchronized(lock) {
            val currentTime = System.currentTimeMillis()

            // Если прошло слишком много времени с начала сбора последовательности,
            // начинаем новую последовательность
            if (currentTime - sequenceStartTime > COLLECTION_TIMEOUT) {
                frameHistory.clear()
                sequenceStartTime = currentTime
                Log.d("FrameHistory", "Начало новой последовательности (тайм-аут)")
            }

            // Проверяем, отличается ли новый кадр от последнего сохраненного
            val isDifferentFrame = if (frameHistory.isEmpty()) {
                true
            } else {
                val lastFrame = frameHistory.last()
                val difference = calculateFrameDifference(lastFrame, frame)
                Log.d("FrameHistory", "Разница между кадрами: $difference (мин. порог: $MIN_FRAME_DIFFERENCE)")
                difference > MIN_FRAME_DIFFERENCE
            }

            // Добавляем кадр только если он отличается или прошло достаточно времени
            if (isDifferentFrame || (currentTime - lastFrameTime > 300)) {
                // Добавляем шум к кадру для улучшения вариативности
                val noisyFrame = addNoiseToFrame(frame)

                frameHistory.add(noisyFrame)
                lastFrameTime = currentTime

                // Ограничиваем размер истории
                if (frameHistory.size > MAX_FRAMES) {
                    frameHistory.removeAt(0)
                }
                //Log.d("FrameHistory", "Кадр добавлен, размер истории: ${frameHistory.size}")
            } else {
                //Log.d("FrameHistory", "Кадр пропущен (слишком похож на предыдущий)")
            }
        }
    }

    /**
     * Возвращает готовую последовательность кадров для отправки на сервер.
     * Обеспечивает нужную длину и вариативность последовательности.
     * @return Список координат из всех кадров или пустой список, если последовательность не готова
     */
    fun getSequence(): List<Float> {
        synchronized(lock) {
            // Требуем хотя бы 5 разных кадров перед отправкой
            if (frameHistory.size < 5) {
                return emptyList()
            }

            // Создаем последовательность из всех кадров с различиями
            val enhancedFrames = mutableListOf<List<Float>>()

            // Добавляем все имеющиеся кадры
            enhancedFrames.addAll(frameHistory)

            // Если кадров меньше MAX_FRAMES, добавляем модифицированные копии последних кадров
            while (enhancedFrames.size < MAX_FRAMES) {
                val baseFrame = frameHistory.last()
                val modifiedFrame = addNoiseToFrame(baseFrame)
                enhancedFrames.add(modifiedFrame)
            }

            // Проверяем различия для всех кадров
            var totalDifference = 0.0f
            for (i in 1 until enhancedFrames.size) {
                val diff = calculateFrameDifference(enhancedFrames[i-1], enhancedFrames[i])
                totalDifference += diff
            }

            val avgDifference = totalDifference / (enhancedFrames.size - 1)
            Log.d("FrameHistory", "Среднее различие между кадрами в последовательности: $avgDifference")

            // Создаем плоский список из всех кадров
            val sequence = mutableListOf<Float>()
            for (frame in enhancedFrames) {
                sequence.addAll(frame)
            }

            // Очищаем историю после использования, но сохраняем последние кадры для сравнения
            val lastFrames = if (frameHistory.size >= 2) {
                frameHistory.takeLast(2)
            } else if (frameHistory.isNotEmpty()) {
                listOf(frameHistory.last())
            } else {
                emptyList()
            }

            frameHistory.clear()
            frameHistory.addAll(lastFrames)

            sequenceStartTime = System.currentTimeMillis()

            Log.d("FrameHistory", "Возвращаем последовательность из ${sequence.size} значений (${enhancedFrames.size} кадров)")
            return sequence
        }
    }

    /**
     * Проверяет, готова ли последовательность для отправки на сервер.
     * @return true, если собрано достаточно кадров
     */
    fun isSequenceReady(): Boolean {
        synchronized(lock) {
            return frameHistory.size >= 5
        }
    }

    /**
     * Очищает историю кадров.
     * Вызывается при переключении камеры или других событиях, требующих сброса.
     */
    fun clear() {
        synchronized(lock) {
            frameHistory.clear()
            sequenceStartTime = System.currentTimeMillis()
            //Log.d("FrameHistory", "История кадров очищена")
        }
    }
}

/**
 * Получает готовую последовательность кадров для отправки на сервер.
 * @return Список координат, подготовленный для отправки
 */
private fun getFrameSequence(): List<Float> {
    return FrameHistory.getSequence()
}

/**
 * Очищает историю кадров.
 * Вызывается при переключении камеры или других событиях, требующих сброса.
 */
private fun clearFrameHistory() {
    FrameHistory.clear()
}

/**
 * Извлекает координаты ключевых точек рук из результатов распознавания.
 * Преобразует внутренний формат MediaPipe в формат для API сервера с нормализацией.
 *
 * @param handLandmarkResult Результат распознавания жестов рук
 * @param isFrontCamera Флаг использования фронтальной камеры (для обработки зеркалирования)
 * @return Список координат ключевых точек рук
 */
private fun extractFeaturesFromLandmarks(
    handLandmarkResult: HandLandmarkerResult,
    isFrontCamera: Boolean = false
): List<Float> {
    val features = mutableListOf<Float>()

    // Добавить минимальный случайный шум для предотвращения полностью идентичных значений
    fun addRandomNoise(value: Float): Float {
        // Очень малый шум, не влияющий на распознавание, но помогающий избежать
        // выявления дубликатов из-за абсолютно идентичных значений
        return value + (Math.random() * 0.0001 - 0.00005).toFloat()
    }

    // Получаем признаки для первой руки, если доступна
    var firstHandFeatures = emptyList<Float>()
    if (handLandmarkResult.landmarks().isNotEmpty()) {
        val landmarks = handLandmarkResult.landmarks()[0]
        if (landmarks.size == 21) {
            // Здесь добавляем нормализацию и шум
            firstHandFeatures = landmarks.flatMap { landmark ->
                // Если это фронтальная камера, инвертируем координату X
                val x = if (isFrontCamera) 1.0f - landmark.x() else landmark.x()

                // Добавляем минимальный шум к координатам
                listOf(
                    addRandomNoise(x),
                    addRandomNoise(landmark.y()),
                    addRandomNoise(landmark.z())
                )
            }
        }
    }

    // Добавляем признаки первой руки или нули, если нет руки
    if (firstHandFeatures.isNotEmpty()) {
        features.addAll(firstHandFeatures)
    } else {
        // Заполняем нулями с минимальным шумом
        features.addAll(List(63) { addRandomNoise(0f) })
    }

    // Получаем признаки для второй руки, если доступна
    var secondHandFeatures = emptyList<Float>()
    if (handLandmarkResult.landmarks().size > 1) {
        val landmarks = handLandmarkResult.landmarks()[1]
        if (landmarks.size == 21) {
            secondHandFeatures = landmarks.flatMap { landmark ->
                val x = if (isFrontCamera) 1.0f - landmark.x() else landmark.x()

                // Добавляем минимальный шум к координатам
                listOf(
                    addRandomNoise(x),
                    addRandomNoise(landmark.y()),
                    addRandomNoise(landmark.z())
                )
            }
        }
    }

    // Добавляем признаки второй руки или нули, если нет руки
    if (secondHandFeatures.isNotEmpty()) {
        features.addAll(secondHandFeatures)
    } else {
        // Заполняем нулями с минимальным шумом
        features.addAll(List(63) { addRandomNoise(0f) })
    }

    return features
}

/**
 * Проверяет доступность сервера и обновляет индикатор подключения.
 * @param apiService Сервис API для взаимодействия с сервером
 * @param coroutineScope Область корутин для асинхронной обработки
 * @param serverConnected Переменная состояния для хранения статуса подключения
 */
private fun checkServerConnection(
    apiService: SignLanguageApiService,
    coroutineScope: CoroutineScope,
    serverConnected: MutableState<Boolean>
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