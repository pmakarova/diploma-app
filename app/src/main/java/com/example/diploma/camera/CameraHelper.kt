package com.example.diploma.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Класс для настройки камеры и анализа изображений
 */
class CameraHelper(
    private val context: Context
) {
    private var camera: androidx.camera.core.Camera? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null

    // Выбранная камера (LENS_FACING_FRONT - "задняя" камера)
    private var currentCameraSelector = CameraSelector.LENS_FACING_BACK

    private val processingImage = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Переменная для отслеживания, выполняется ли сейчас переключение камеры
    private val isSwitchingCamera = AtomicBoolean(false)

    // Для обработки кадров
    private var frameListener: ((ImageProxy) -> Unit)? = null

    // Проверка доступной камеры
    init {
        // Выбор доступной камеры по умолчанию
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIdList = cameraManager.cameraIdList

        //Log.d(TAG, "Доступные камеры: ${cameraIdList.joinToString()}")

        // Если камеры не доступны
        if (cameraIdList.isEmpty()) {
            Log.e(TAG, "Нет доступных камер на этом устройстве")
        } else {
            // Проверка фронтальной камеры
            var hasFrontCamera = false
            var hasBackCamera = false

            for (cameraId in cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)

                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    hasFrontCamera = true
                    //Log.d(TAG, "Фронтальная камера: $cameraId")
                } else if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    hasBackCamera = true
                    //Log.d(TAG, "Задняя камера: $cameraId")
                }
            }

            // Установить камеру по умолчанию в зависимости от ее доступности
            currentCameraSelector = when {
                hasFrontCamera -> CameraSelector.LENS_FACING_FRONT
                hasBackCamera -> CameraSelector.LENS_FACING_BACK
                else -> {
                    // По умолчанию
                    CameraSelector.LENS_FACING_BACK
                }
            }

            Log.d(TAG, "Камера по умолчанию установлена: $currentCameraSelector")
        }
    }

    /**
     * Запуск камеры
     */
    @OptIn(ExperimentalGetImage::class)
    suspend fun startCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        cameraSelector: Int = currentCameraSelector
    ): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            currentCameraSelector = cameraSelector
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider

                    // Отвязываем все существующие use cases
                    provider.unbindAll()

                    preview = Preview.Builder()
                        .build()
                        .also {
                            it.surfaceProvider = surfaceProvider
                        }

                    imageCapture = ImageCapture.Builder()
                        .build()

                    imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor, ImageAnalyzer())
                        }

                    val selector = CameraSelector.Builder()
                        .requireLensFacing(cameraSelector)
                        .build()

                    try {
                        // Привязываем use cases к lifecycle
                        camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            selector,
                            preview,
                            imageCapture,
                            imageAnalysis
                        )

                        Log.d(TAG, "Камера успешно запущена с selector: $cameraSelector")

                        if (continuation.isActive) {
                            continuation.resume(true)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Не удалось связать use cases: ${e.message}")
                        e.printStackTrace()

                        if (continuation.isActive) {
                            continuation.resume(false)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Не удалось получить провайдера камеры: ${e.message}")
                    e.printStackTrace()

                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            }, ContextCompat.getMainExecutor(context))

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка запуска камеры: ${e.message}")
            e.printStackTrace()

            if (continuation.isActive) {
                continuation.resume(false)
            }
        }
    }

    /**
     * Переключение камеры между фронтальной и задней с очисткой
     */
    suspend fun toggleCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider
    ): Boolean {
        // Защита от параллельного переключения камеры
        if (isSwitchingCamera.getAndSet(true)) {
            //Log.d(TAG, "Переключение камеры уже выполняется, запрос игнорируется")
            return false
        }

        try {
            //Log.d(TAG, "Камера переключается с $currentCameraSelector")

            shutdownCamera()

            // Задержка для освобождения ресурсов
            delay(500)

            val newCameraSelector = if (currentCameraSelector == CameraSelector.LENS_FACING_FRONT) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }

            //Log.d(TAG, "Переключение на камеру: $newCameraSelector")

            // Запуск новой камеры
            val result = startCamera(lifecycleOwner, surfaceProvider, newCameraSelector)

            return result
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при переключении камеры: ${e.message}")
            e.printStackTrace()
            return false
        } finally {
            // Сбрасываем флаг, что переключение завершено
            isSwitchingCamera.set(false)
        }
    }

    /**
     * Отключение всех ресурсов камеры
     */
    private suspend fun shutdownCamera() = withContext(Dispatchers.Main) {
        try {
            frameListener = null

            // Отмена всех ожидающих задач на главном потоке
            mainHandler.removeCallbacksAndMessages(null)

            // Сброс флага обработки изображения
            processingImage.set(false)

            // Отвязка всех use cases от lifecycle
            cameraProvider?.unbindAll()

            camera = null
            preview = null
            imageCapture = null
            imageAnalysis = null

            //Log.d(TAG, "Выключение камеры завершено")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при выключении камеры: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Прослушиватель для обработки кадров
     */
    fun setFrameListener(listener: (ImageProxy) -> Unit) {
        frameListener = listener
    }

    /**
     * Остановка камеры и сброс ресурсов
     */
    fun shutdown() {
        try {
            // Отмена всех ожидающих задач
            mainHandler.removeCallbacksAndMessages(null)

            // Отвязка всех use cases
            cameraProvider?.unbindAll()

            cameraExecutor.shutdown()
            try {
                // Ожидание завершения всех задач
                if (!cameraExecutor.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                    cameraExecutor.shutdownNow()
                }
            } catch (e: InterruptedException) {
                cameraExecutor.shutdownNow()
            }

            camera = null
            preview = null
            imageCapture = null
            imageAnalysis = null
            cameraProvider = null
            frameListener = null

            //Log.d(TAG, "Camera helper отключен")
        } catch (e: Exception) {
            //Log.e(TAG, "Ошибка при выключении: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Получить текущее направление камеры
     */
    fun getCurrentCameraFacing(): Int {
        return currentCameraSelector
    }

    @ExperimentalGetImage
    private inner class ImageAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            // Пропустить обработку, если мы все еще обрабатываем предыдущий кадр
            if (processingImage.getAndSet(true)) {
                imageProxy.close()
                return
            }

            try {
                frameListener?.invoke(imageProxy) ?: imageProxy.close()
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка анализа изображения: ${e.message}")
                try {
                    imageProxy.close()
                } catch (e2: Exception) {
                    Log.e(TAG, "Ошибка при закрытии imageProxy: ${e2.message}")
                }
            } finally {
                processingImage.set(false)
            }
        }
    }

    companion object {
        private const val TAG = "CameraHelper"
    }
}