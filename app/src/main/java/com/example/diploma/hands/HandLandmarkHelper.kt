package com.example.diploma.hands

import android.content.Context
import android.graphics.Bitmap

import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

/**
 * Вспомогательный класс для определения ключевых точек рук
 * с использованием MediaPipe Hand Landmark
 */
class HandLandmarkHelper(
    var minHandDetectionConfidence: Float = DEFAULT_HAND_DETECTION_CONFIDENCE,
    var minHandTrackingConfidence: Float = DEFAULT_HAND_TRACKING_CONFIDENCE,
    var minHandPresenceConfidence: Float = DEFAULT_HAND_PRESENCE_CONFIDENCE,
    var maxNumHands: Int = DEFAULT_NUM_HANDS,
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val context: Context,
    // Используется только при запуске в RunningMode.LIVE_STREAM
    val handLandmarkHelperListener: LandmarkListener? = null
) {
    private var handLandmark: HandLandmarker? = null

    init {
        setupHandLandmark()
    }

    // Очищает ресурсы, связанные с HandLandmark
    fun clearHandLandmark() {
        handLandmark?.close()
        handLandmark = null
    }

    // Инициализирует HandLandmark с текущими настройками
    private fun setupHandLandmark() {
        val baseOptionBuilder = BaseOptions.builder()

        // По умолчанию для запуска модели используется CPU
        when (currentDelegate) {
            DELEGATE_CPU -> {
                baseOptionBuilder.setDelegate(Delegate.CPU)
            }
            DELEGATE_GPU -> {
                baseOptionBuilder.setDelegate(Delegate.GPU)
            }
        }

        baseOptionBuilder.setModelAssetPath(MP_HAND_LANDMARK_TASK)

        // Согласование запуска с handLandmarkHelperListener
        when (runningMode) {
            RunningMode.LIVE_STREAM -> {
                if (handLandmarkHelperListener == null) {
                    throw IllegalStateException(
                        "Должен быть установлен handLandmarkHelperListener, когда runningMode = LIVE_STREAM."
                    )
                }
            }
            else -> {
                //
            }
        }

        try {
            val baseOptions = baseOptionBuilder.build()

            val optionsBuilder =
                HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinHandDetectionConfidence(minHandDetectionConfidence)
                    .setMinTrackingConfidence(minHandTrackingConfidence)
                    .setMinHandPresenceConfidence(minHandPresenceConfidence)
                    .setNumHands(maxNumHands)
                    .setRunningMode(runningMode)

            // ResultListener и ErrorListener используются только для режима LIVE_STREAM.
            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            val options = optionsBuilder.build()
            handLandmark =
                HandLandmarker.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            handLandmarkHelperListener?.onError(
                "Не удалось инициализировать Hand Landmark. Смотри журналы ошибок для " +
                        "подробностей"
            )
            Log.e(
                TAG, "MediaPipe failed to load the task with error: " + e
                    .message
            )
        } catch (e: RuntimeException) {
            // Если используемая модель не поддерживает GPU
            handLandmarkHelperListener?.onError(
                "Не удалось инициализировать Hand Landmark. Смотри журналы ошибок для " +
                        "подробностей", GPU_ERROR
            )
            Log.e(
                TAG,
                "Классификатор изображений не смог загрузить модель с ошибкой: " + e.message
            )
        }
    }

    /**
     * Преобразует ImageProxy в MPImage и передает его в HandLandmark
     * для обработки в режиме реального времени
     */
    @OptIn(ExperimentalGetImage::class)
    fun detectLiveStream(
        imageProxy: ImageProxy,
        isFrontCamera: Boolean
    ) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "Попытка вызвать detectLiveStream" +
                        " при использовании режима, отличного от RunningMode.LIVE_STREAM"
            )
        }
        val frameTime = SystemClock.uptimeMillis()

        try {
            // Получение доступа к изображению
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                Log.e(TAG, "Недопустимый прокси изображения, пропуск кадра")
                imageProxy.close()
                return
            }

            // Размеры изображения
            val width = mediaImage.width
            val height = mediaImage.height

            // Данные изображения (YUV формат)
            val yBuffer = mediaImage.planes[0].buffer // Y
            val uBuffer = mediaImage.planes[1].buffer // U
            val vBuffer = mediaImage.planes[2].buffer // V

            // Размеры данных
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            // Массив для хранения YUV данных
            val nv21 = ByteArray(ySize + uSize + vSize)

            // Копируем данные в массив
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            // Для конвертации в JPEG
            val yuvImage = android.graphics.YuvImage(
                nv21,
                android.graphics.ImageFormat.NV21,
                width,
                height,
                null
            )

            // Конвертация YUV в JPEG
            val out = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                android.graphics.Rect(0, 0, width, height),
                100,
                out
            )

            // JPEG в Bitmap
            val jpegBytes = out.toByteArray()
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

            imageProxy.close()

            // Ротация и отражение
            val matrix = android.graphics.Matrix().apply {
                // Поворот кадра
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

                // Отражение кадра для фронтальной камеры
                if (isFrontCamera) {
                    postScale(
                        -1f,
                        1f,
                        bitmap.width.toFloat(),
                        bitmap.height.toFloat()
                    )
                }
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )

            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }

            // Конвертация в формат MediaPipe
            val mpImage = BitmapImageBuilder(rotatedBitmap).build()

            // Обработка изображения
            detectAsync(mpImage, frameTime)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обработки изображения: ${e.message}")
            e.printStackTrace()
            try {
                imageProxy.close()
            } catch (e2: Exception) {
                // Игнорируем ошибки при закрытии
            }
        }
    }

    /**
     * Запускает определение ключевых точек рук с использованием MediaPipe Hand Landmark API
     * в асинхронном режиме
     */
    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        handLandmark?.detectAsync(mpImage, frameTime)
        // Результат определения ключевых точек будет возвращен в функции returnLivestreamResult
    }

    /**
     * Возвращает результат определения ключевых точек
     */
    private fun returnLivestreamResult(
        result: HandLandmarkerResult,
        input: MPImage
    ) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()

        handLandmarkHelperListener?.onResults(
            ResultBundle(
                listOf(result),
                inferenceTime,
                input.height,
                input.width
            )
        )
    }

    /**
     * Возвращает ошибки, возникшие во время обнаружения
     */
    private fun returnLivestreamError(error: RuntimeException) {
        handLandmarkHelperListener?.onError(
            error.message ?: "Неизвестная ошибка"
        )
    }

    companion object {
        const val TAG = "HandLandmarkHelper"
        private const val MP_HAND_LANDMARK_TASK = "hand_landmarker.task" // официальная модель

        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DEFAULT_HAND_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_HAND_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_HAND_PRESENCE_CONFIDENCE = 0.5F
        const val DEFAULT_NUM_HANDS = 1
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
    }

    /**
     * Класс для хранения результатов определения ключевых точек рук
     */
    data class ResultBundle(
        val results: List<HandLandmarkerResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
    )

    /**
     * Интерфейс для обратного вызова с результатами определения ключевых точек
     */
    interface LandmarkListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}