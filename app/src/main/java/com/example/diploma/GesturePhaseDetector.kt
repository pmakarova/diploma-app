package com.example.diploma

import android.util.Log
import kotlin.math.abs

/**
 * Упрощенный детектор для определения фаз жеста
 */
object GesturePhaseDetector {
    // Фазы жеста
    enum class GesturePhase {
        NONE,           // Нет активного жеста
        INITIALIZING,   // Рука появилась и начинает движение
        MOVEMENT,       // Активная фаза движения жеста
        STABILIZING,    // Замедление движения, переход к завершению
        FINALIZING,     // Финальная поза жеста
        COMPLETED       // Жест полностью завершен
    }

    // Параметры детектора
    private const val MOVEMENT_THRESHOLD = 0.02f
    private const val STABILIZATION_THRESHOLD = 0.01f
    private const val STABILIZATION_FRAMES = 5
    private const val MIN_GESTURE_FRAMES = 15
    private const val MAX_HISTORY_SIZE = 50
    private const val TARGET_FRAMES = 20

    // Состояние детектора
    private var currentPhase = GesturePhase.NONE
    private var stabilizationCounter = 0
    private var totalFramesInGesture = 0
    private var movementHistoryBuffer = mutableListOf<MovementFrame>()

    // Упрощенная структура для хранения кадра
    private data class MovementFrame(
        val features: List<Float>,
        val timestamp: Long,
        val movementMagnitude: Float = 0f
    )

    /**
     * Обрабатывает новый кадр движения рук
     */
    fun processFrame(features: List<Float>, timestamp: Long): Pair<Boolean, List<List<Float>>> {
        val isEmpty = isEmptyFrame(features)

        // Если рука исчезла и был активный жест - завершаем
        if (isEmpty && currentPhase != GesturePhase.NONE) {
            Log.d("GesturePhaseDetector", "👋 Рука исчезла из кадра. Завершаем жест.")
            val result = finalizeGesture()
            resetState()
            return result
        }

        if (isEmpty) return Pair(false, emptyList())

        // Вычисляем движение
        val movementMagnitude = if (movementHistoryBuffer.isNotEmpty()) {
            calculateMovement(movementHistoryBuffer.last().features, features)
        } else 0f

        // Добавляем кадр в буфер
        movementHistoryBuffer.add(MovementFrame(features.toList(), timestamp, movementMagnitude))
        totalFramesInGesture++

        // Ограничиваем размер буфера
        if (movementHistoryBuffer.size > MAX_HISTORY_SIZE) {
            movementHistoryBuffer.removeAt(0)
        }

        // Обновляем фазу жеста
        updateGesturePhase(movementMagnitude)

        // Если жест завершен
        if (currentPhase == GesturePhase.COMPLETED) {
            val result = finalizeGesture()
            resetState()
            return result
        }

        return Pair(false, emptyList())
    }

    /**
     * Обновляет текущую фазу жеста
     */
    private fun updateGesturePhase(movementMagnitude: Float) {
        when (currentPhase) {
            GesturePhase.NONE -> {
                if (movementMagnitude > MOVEMENT_THRESHOLD) {
                    currentPhase = GesturePhase.INITIALIZING
                    stabilizationCounter = 0
                }
            }

            GesturePhase.INITIALIZING -> {
                if (movementMagnitude > MOVEMENT_THRESHOLD && totalFramesInGesture > 3) {
                    currentPhase = GesturePhase.MOVEMENT
                } else if (movementMagnitude < STABILIZATION_THRESHOLD) {
                    stabilizationCounter++
                    if (stabilizationCounter > 3) {
                        resetState()
                    }
                }
            }

            GesturePhase.MOVEMENT -> {
                if (movementMagnitude <= STABILIZATION_THRESHOLD) {
                    stabilizationCounter++
                    if (stabilizationCounter >= 2) {
                        currentPhase = GesturePhase.STABILIZING
                        stabilizationCounter = 0
                    }
                } else {
                    stabilizationCounter = 0
                }
            }

            GesturePhase.STABILIZING -> {
                if (movementMagnitude > MOVEMENT_THRESHOLD) {
                    currentPhase = GesturePhase.MOVEMENT
                    stabilizationCounter = 0
                } else if (movementMagnitude < STABILIZATION_THRESHOLD) {
                    stabilizationCounter++
                    if (stabilizationCounter >= 3) {
                        currentPhase = GesturePhase.FINALIZING
                        stabilizationCounter = 0
                    }
                }
            }

            GesturePhase.FINALIZING -> {
                if (movementMagnitude > MOVEMENT_THRESHOLD) {
                    currentPhase = GesturePhase.MOVEMENT
                    stabilizationCounter = 0
                } else {
                    stabilizationCounter++
                    if (stabilizationCounter >= STABILIZATION_FRAMES &&
                        totalFramesInGesture >= MIN_GESTURE_FRAMES) {
                        currentPhase = GesturePhase.COMPLETED
                    }
                }
            }

            GesturePhase.COMPLETED -> {
                // Обработано в processFrame()
            }
        }
    }

    /**
     * Финализирует жест
     */
    private fun finalizeGesture(): Pair<Boolean, List<List<Float>>> {
        if (movementHistoryBuffer.size < MIN_GESTURE_FRAMES) {
            return Pair(false, emptyList())
        }

        val selectedFrames = selectFrames()
        return Pair(true, selectedFrames)
    }

    /**
     * Простой отбор кадров - равномерное распределение
     */
    private fun selectFrames(): List<List<Float>> {
        val bufferSize = movementHistoryBuffer.size

        // Если кадров меньше или равно целевому - берем все
        if (bufferSize <= TARGET_FRAMES) {
            return movementHistoryBuffer.map { it.features }
        }

        // Равномерно распределяем кадры
        val result = mutableListOf<List<Float>>()
        val step = (bufferSize - 1).toFloat() / (TARGET_FRAMES - 1)

        for (i in 0 until TARGET_FRAMES) {
            val index = (i * step).toInt().coerceIn(0, bufferSize - 1)
            result.add(movementHistoryBuffer[index].features)
        }

        return result
    }

    /**
     * Проверяет, пустой ли кадр
     */
    private fun isEmptyFrame(features: List<Float>): Boolean {
        val nonZeroCount = features.count { abs(it) > 0.005f }
        return nonZeroCount < features.size * 0.1
    }

    /**
     * Вычисляет движение между кадрами
     */
    private fun calculateMovement(prevFrame: List<Float>, currentFrame: List<Float>): Float {
        if (prevFrame.size != currentFrame.size) return 0f

        var totalMovement = 0f
        var nonZeroCount = 0

        for (i in prevFrame.indices) {
            val diff = abs(currentFrame[i] - prevFrame[i])
            if (prevFrame[i] != 0f || currentFrame[i] != 0f) {
                totalMovement += diff
                nonZeroCount++
            }
        }

        return if (nonZeroCount > 0) totalMovement / nonZeroCount else 0f
    }

    /**
     * Сбрасывает состояние
     */
    private fun resetState() {
        currentPhase = GesturePhase.NONE
        stabilizationCounter = 0
        totalFramesInGesture = 0

        // Сохраняем только последние 3 кадра для плавности
        if (movementHistoryBuffer.size > 3) {
            val lastFrames = movementHistoryBuffer.takeLast(3)
            movementHistoryBuffer.clear()
            movementHistoryBuffer.addAll(lastFrames)
        }
    }

}