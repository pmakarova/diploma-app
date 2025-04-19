package com.example.diploma.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.diploma.hands.HandLandmarkHelper
import androidx.camera.core.CameraSelector


/**
 * ViewModel для экрана камеры и настроек распознавания ключевых точек рук
 */
class CameraViewModel : ViewModel() {

    /**
     * Текущий режим делегирования для обработки модели
     * DELEGATE_CPU - использует процессор для обработки (рекомендуется).
     * DELEGATE_GPU - использует графический процессор для обработки (быстрее, но может не поддерживаться)
     */
    var currentDelegate by mutableIntStateOf(HandLandmarkHelper.DELEGATE_GPU)
        private set

    /**
     * Переменная для отслеживания выбранной камеры
     * LENS_FACING_BACK - задняя камера устройства
     * LENS_FACING_FRONT - фронтальная камера устройства
     */
    var cameraFacing = mutableIntStateOf(CameraSelector.LENS_FACING_FRONT)

}