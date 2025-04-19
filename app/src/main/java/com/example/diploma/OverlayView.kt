package com.example.diploma

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

/**
 * Вид для отображения ключевых точек рук поверх изображения с камеры.
 * Рисует точки и соединения между ними на основе результатов MediaPipe Hand Landmark.
 */
class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {

    private var results: HandLandmarkerResult? = null
    private var linePaint = Paint()
    private var pointPaint = Paint()

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var isFrontCamera: Boolean = false

    // Константы для размеров точек и линий
    private val LANDMARK_STROKE_WIDTH = 12F // Увеличиваем толщину линий для лучшей видимости
    private val LANDMARK_POINT_SIZE = 16F // Увеличиваем размер точек


    init {
        initPaints()
        Log.d(TAG, "OverlayView инициализирован")
    }

    /**
     * Очищает результаты и сбрасывает настройки отображения
     */
    fun clear() {
        results = null
        linePaint.reset()
        pointPaint.reset()
        invalidate()
        initPaints()
        Log.d(TAG, "OverlayView очищен")
    }

    /**
     * Инициализирует кисти для рисования точек и линий
     */
    private fun initPaints() {
        // Настройка кисти для линий
        linePaint.color = Color.MAGENTA
        linePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        linePaint.style = Paint.Style.STROKE

        // Настройка кисти для точек
        pointPaint.color = Color.CYAN
        pointPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        pointPaint.style = Paint.Style.FILL
    }

    /**
     * Устанавливает результаты определения ключевых точек
     * @param handLandmarkResults Результаты определения ключевых точек рук
     * @param isFrontCamera Используется ли фронтальная камера
     */
    fun setResults(
        handLandmarkResults: HandLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        isFrontCamera: Boolean = false
    ) {
        results = handLandmarkResults
        this.imageHeight = imageHeight
        this.imageWidth = imageWidth
        this.isFrontCamera = isFrontCamera

        // Расчет масштаба с учетом соотношения сторон и ориентации
        calculateScaleFactor()

        // Перерисовка
        invalidate()

        //Log.d(TAG, "Установлены результаты: размер изображения ${imageWidth}x${imageHeight}, коэффициент масштаба: $scaleFactor, фронтальная камера: $isFrontCamera")
    }

    /**
     * Вычисляет коэффициент масштабирования для правильного отображения ключевых точек
     * в соответствии с размерами вида и изображения
     */
    private fun calculateScaleFactor() {
        if (width <= 0 || height <= 0 || imageWidth <= 0 || imageHeight <= 0) {
            scaleFactor = 1f
            return
        }

        // Рассчитываем соотношения сторон
        val viewAspectRatio = width.toFloat() / height
        val imageAspectRatio = imageWidth.toFloat() / imageHeight

        // В зависимости от ориентации изображения и вида выбираем подходящий масштаб
        scaleFactor = if (viewAspectRatio > imageAspectRatio) {
            // Если вид шире, чем изображение - масштабируем по высоте
            height.toFloat() / imageHeight
        } else {
            // Иначе масштабируем по ширине
            width.toFloat() / imageWidth
        }

        // Увеличиваем масштаб для лучшей видимости
        scaleFactor *= 1.5f

        Log.d(TAG, "Коэффициент масштаба: $scaleFactor")
    }

    /**
     * При изменении размеров вида
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Масштаб при изменении размеров view
        calculateScaleFactor()
    }

    /**
     * Рисует ключевые точки рук и соединения между ними
     */
    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        val results = this.results ?: return

        //Log.d(TAG, "Ключевые точки: ${results.landmarks().size}")

        if (results.landmarks().isEmpty()) {
            return
        }

        // Центрирование отображения
        val offsetX = (width - imageWidth * scaleFactor) / 2
        val offsetY = (height - imageHeight * scaleFactor) / 2

        for (landmark in results.landmarks()) {
            for (normalizedLandmark in landmark) {
                val x = normalizedLandmark.x()

                val pointX = x * imageWidth * scaleFactor + offsetX
                val pointY = normalizedLandmark.y() * imageHeight * scaleFactor + offsetY

                // Увеличенная точка
                canvas.drawCircle(
                    pointX,
                    pointY,
                    LANDMARK_POINT_SIZE, // размер точки
                    pointPaint
                )
            }

            // Соединения между точками
            HandLandmarker.HAND_CONNECTIONS.forEach {
                val startX = landmark[it!!.start()].x()
                val endX = landmark[it.end()].x()

                canvas.drawLine(
                    startX * imageWidth * scaleFactor + offsetX,
                    landmark[it.start()].y() * imageHeight * scaleFactor + offsetY,
                    endX * imageWidth * scaleFactor + offsetX,
                    landmark[it.end()].y() * imageHeight * scaleFactor + offsetY,
                    linePaint
                )
            }
        }
    }

    companion object {
        private const val TAG = "OverlayView"
    }
}